// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import jpaul.DataStructs.UnionFind;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtom;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.QueryEngine;
import de.derivo.sparqldlapi.QueryResult;
import de.derivo.sparqldlapi.exceptions.QueryEngineException;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * A concrete implementation of the query engine interface utilizing the OWL-API.
 * 
 * @author Mario Volke
 */
public class QueryEngineImpl extends QueryEngine
{
	private final OWLOntologyManager manager;
	private final LiteralTranslator literalTranslator;
	private OWLReasoner reasoner;
	private OWLDataFactory factory;
	private boolean strictMode;
	public QueryEngineImpl(OWLOntologyManager manager, OWLReasoner reasoner)
	{
		this(manager, reasoner, false);
	}

	/**
	 * QueryEngineImpl constructor
	 * 
	 * @param manager An OWLOntologyManager instance of OWLAPI v3
	 * @param reasoner An OWLReasoner instance.
	 * @param strictMode If strict mode is enabled the query engine will throw a QueryEngineException if data types withing the query are not correct (e.g. Class(URI_OF_AN_INDIVIDUAL))
	 */
	public QueryEngineImpl(OWLOntologyManager manager, OWLReasoner reasoner, boolean strictMode)
	{
		this.manager = manager;
		literalTranslator = new LiteralTranslator(manager.getOWLDataFactory());
		this.reasoner = reasoner;
		this.factory = manager.getOWLDataFactory();
		this.strictMode = strictMode;
	}
	
	public void setStrictMode(boolean strict)
	{
		this.strictMode = strict;
	}
	
	/**
	 * Execute a sparql-dl query and generate the result set.
	 * 
	 * @param query
	 * @return The query result set.
	 */
	public QueryResult execute(Query query)
		throws QueryEngineException
	{
		if(!(query instanceof QueryImpl)) {
			throw new QueryEngineException("Couldn't cast Query to QueryImpl.");
		}
		QueryImpl q = (QueryImpl)query;
		
		// search for unbound results vars
		resvarsLoop: for(QueryArgument arg : q.getResultVars()) {
			for(QueryAtomGroup g : q.getAtomGroups()) {
				for(QueryAtom a : g.getAtoms()) {
					if(a.getArguments().contains(arg)) {
						continue resvarsLoop;
					}
				}
			}
			
//			throw new QueryEngineException("Query contains an unbound result argument " + arg + ".");
		}
		
		Queue<QueryResultImpl> results = new LinkedList<QueryResultImpl>();
		for(QueryAtomGroup g : q.getAtomGroups()) {
		
			QueryAtomGroupImpl group = (QueryAtomGroupImpl)g;
			
			List<QueryAtomGroupImpl> components = findComponents(group);
			Queue<QueryResultImpl> componentResults = new LinkedList<QueryResultImpl>();
			boolean groupAsk = true;
			for(QueryAtomGroupImpl component : components) {
				
				QueryAtomGroupImpl preorderedGroup = preorder(component);
	
				QueryResultImpl result = new QueryResultImpl(query);
				if(eval(q, preorderedGroup, result, new QueryBindingImpl())) {
					if(query.isSelectDistinct()) {
						result = eliminateDuplicates(result);
					}
					
					componentResults.add(result);
				}
				else {
					groupAsk = false;
					break;
				}
			}
			
			if(groupAsk) {
				results.add(combineResults(componentResults, query.getType() == QueryType.SELECT_DISTINCT ? true : false));
			}
			else {
				// return only empty result with no solution for this group
				QueryResultImpl ret =  new QueryResultImpl(query);
				ret.setAsk(false);
				results.add(ret);
			}
		}
		
		return unionResults(q, results, query.getType() == QueryType.SELECT_DISTINCT ? true : false);
	}
	
	/**
	 * Split the query into individual components if possible to avoid cross-products in later evaluation.
	 * The first component will contain all atoms with no variables if there exist some.
	 * 
	 * @param group
	 * @return a set of group components
	 */
	private List<QueryAtomGroupImpl> findComponents(QueryAtomGroupImpl group)
	{
		List<QueryAtom> atoms = new LinkedList<QueryAtom>();
		for(QueryAtom atom : group.getAtoms()) {
			atoms.add(atom);
		}
		
		List<QueryAtomGroupImpl> components = new LinkedList<QueryAtomGroupImpl>();
		
		// if we have no atoms at all we simply return the same query as a single component
		if(atoms.isEmpty()) {
			components.add(group);
			return components;
		}
		
		// find all atoms containing no variables
		// and build a component
		QueryAtomGroupImpl component = new QueryAtomGroupImpl();
			
		for(QueryAtom atom : atoms) {
			boolean noVar = true;
			for(QueryArgument arg : atom.getArguments()) {
				if(arg.isVar()) {
					noVar = false;
					break;
				}
			}
			if(noVar) {
				component.addAtom(atom);
			}
		}
			
		for(QueryAtom atom : component.getAtoms()) {
			atoms.remove(atom);
		}
			
		if(!component.isEmpty()) {
			components.add(component);
		}
		
		// find connected components
		UnionFind<QueryArgument> unionFind = new UnionFind<QueryArgument>();
		for(QueryAtom atom : atoms) {
			QueryArgument firstVar = null;
			for(QueryArgument arg : atom.getArguments()) {
				if(arg.isVar()) {
					if(firstVar == null) {
						firstVar = arg;
					}
					else {
						unionFind.union(firstVar, arg);
					}
				}
			}
		}
		
		while(!atoms.isEmpty()) {
			component = new QueryAtomGroupImpl();
			QueryAtom nextAtom = atoms.get(0);
			atoms.remove(nextAtom);
			component.addAtom(nextAtom);
			QueryArgument args = null;
			for(QueryArgument arg : nextAtom.getArguments()) {
				if(arg.isVar()) {
					args = unionFind.find(arg);
					break;
				}
			}
			
			for(QueryAtom atom : atoms) {
				QueryArgument args2 = null;
				for(QueryArgument arg : atom.getArguments()) {
					if(arg.isVar()) {
						args2 = unionFind.find(arg);
						break;
					}
				}
				
				if(args.equals(args2)) {
					component.addAtom(atom);
				}
			}
			
			for(QueryAtom atom : component.getAtoms()) {
				atoms.remove(atom);
			}
			
			components.add(component);
		}
		
		return components;
	}
	
	/**
	 * Combine the results of the individual components with the cartesian product.
	 * 
	 * @param results
	 * @param distinct
	 * @return the combined result
	 */
	private QueryResultImpl combineResults(Queue<QueryResultImpl> results, boolean distinct)
	{
		while(results.size() > 1) {
			QueryResultImpl a = results.remove();
			QueryResultImpl b = results.remove();
			results.add(combineResults(a, b, distinct));
		}
		
		return results.remove();
	}
	
	/**
	 * Combine two results with the cartesian product.
	 * 
	 * @param a
	 * @param b
	 * @param distinct 
	 * @return the combined result
	 */
	private QueryResultImpl combineResults(QueryResultImpl a, QueryResultImpl b, boolean distinct)
	{
		QueryResultImpl result = new QueryResultImpl(a.getQuery());
		
		for(QueryBindingImpl bindingA : a.getBindings()) {
			for(QueryBindingImpl bindingB : b.getBindings()) {
				QueryBindingImpl binding = new QueryBindingImpl();
				binding.set(bindingA);
				binding.set(bindingB);
				result.add(binding);
			}
		}
		
		if(distinct) {
			return eliminateDuplicates(result);
		}
		return result;
	}
	
	/**
	 * Union results
	 * 
	 * @param results
	 * @param distinct
	 * @return the union result
	 */
	private QueryResultImpl unionResults(QueryImpl query, Queue<QueryResultImpl> results, boolean distinct)
	{
		QueryResultImpl result = new QueryResultImpl(query);
		
		boolean ask = false;
		
		for(QueryResultImpl r : results) {
			if(r.ask()) {
				ask = true;
			}
			
			for(QueryBindingImpl b : r.getBindings()) {
				result.add(b);
			}
		}
		
		result.setAsk(ask);
		
		if(distinct) {
			return eliminateDuplicates(result);
		}
		return result;
	}
	
	/**
	 * Eliminate duplicate bindings.
	 * 
	 * @param result
	 * @return A new QueryResultImpl instance without diplicates
	 */
	private QueryResultImpl eliminateDuplicates(QueryResultImpl result) 
	{
		QueryResultImpl ret = new QueryResultImpl(result.getQuery());
		
		Set<QueryBindingImpl> distinctSet = new HashSet<QueryBindingImpl>(result.getBindings());
		for(QueryBindingImpl binding : distinctSet) {
			ret.add(binding);
		}
		
		return ret;
	}
	
	private boolean eval(QueryImpl query, QueryAtomGroupImpl group, QueryResultImpl result, QueryBindingImpl binding)
		throws QueryEngineException
	{
		if(group.isEmpty()) {
			if(query.isSelect() || query.isSelectDistinct()) {
				result.add(binding.cloneAndFilter(query.getResultVars()));
			}
			return true;
		}

		QueryAtom atom = group.nextAtom();
		try {
			checkArgs(atom);
		}
		catch(QueryEngineException e) {	
			// if strict mode is enabled we will throw an exception here
			if(strictMode) {
				throw e;
			}
			return false;
		}

		if(atom.isBound()) {
			if(checkBound(atom)) {
				return eval(query, group.pop(), result, binding);
			}
			
			return false;
		}
		
		List<QueryArgument> args = atom.getArguments();
		QueryArgument arg0, arg1, arg2;
		QueryBindingImpl new_binding;
		boolean ret = false;
		boolean strict = false;
		
		switch(atom.getType()) {
		case CLASS:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case INDIVIDUAL:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidates = getIndividuals();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case STRICT_SUB_CLASS_OF:
			strict = true;
		case SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				OWLClass class1 = asClass(arg1);
				Set<OWLClass> candidates = reasoner.getSubClasses(class1, false).getFlattened();
				
				// if not strict we also include all equivalent classes
				if(!strict) {
					candidates.addAll(reasoner.getEquivalentClasses(asClass(arg1)).getEntities());
				}
				
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				OWLClass class0 = asClass(arg0);
				Set<OWLClass> candidates = reasoner.getSuperClasses(class0, false).getFlattened();
				
				// if not strict we also include all equivalent classes
				if(!strict) {
					candidates.addAll(reasoner.getEquivalentClasses(asClass(arg1)).getEntities());
				}
				
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DIRECT_SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				OWLClass class1 = asClass(arg1);
				Set<OWLClass> candidates = reasoner.getSubClasses(class1, true).getFlattened();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				OWLClass class0 = asClass(arg0);
				Set<OWLClass> candidates = reasoner.getSuperClasses(class0, true).getFlattened();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case EQUIVALENT_CLASS:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLClass> candidates = reasoner.getEquivalentClasses(asClass(arg1)).getEntities();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLClass> candidates = reasoner.getEquivalentClasses(asClass(arg0)).getEntities();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DOMAIN:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar() || arg0.isVar()) {
				if(isDeclaredObjectProperty(arg0)) {
					for(OWLObjectProperty property : getObjectProperties()) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(property.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclaredDataProperty(arg0)) {
					for(OWLDataProperty property : getDataProperties()) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(property.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclaredAnnotationProperty(arg0)) {
					for(OWLAnnotationProperty property : getAnnotationProperties()) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(property.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			else if(arg1.isVar()) {
				// Looking for domains
				if(isDeclaredObjectProperty(arg0)) {
					OWLObjectProperty property = asObjectProperty(arg0);
					Set<OWLClass> candidates = reasoner.getObjectPropertyDomains(property, false).getFlattened();
					for(OWLClass c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclaredDataProperty(arg0)) {
					OWLDataProperty property = asDataProperty(arg0);
					Set<OWLClass> candidates = reasoner.getDataPropertyDomains(property, false).getFlattened();
					for(OWLClass c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclaredAnnotationProperty(arg0)) {
					ret = false;
				}
			}
			break;
		case COMPLEMENT_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLClass> candidates = reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(arg1))).getEntities();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLClass> candidates = reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(arg0))).getEntities();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DISJOINT_WITH:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLClass> candidates = getClasses();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLClass> candidates = reasoner.getDisjointClasses(asClass(arg1)).getFlattened();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLClass> candidates = reasoner.getDisjointClasses(asClass(arg0)).getFlattened();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DIRECT_TYPE:
			strict = true;
		case TYPE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLNamedIndividual> candidates = getIndividuals();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidates = reasoner.getInstances(asClass(arg1), strict).getFlattened();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLClass> candidates = reasoner.getTypes(asIndividual(arg0), strict).getFlattened();
				for(OWLClass c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case SAME_AS:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLNamedIndividual> candidates = getIndividuals();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidates = reasoner.getSameIndividuals(asIndividual(arg1)).getEntities();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLNamedIndividual> candidates = reasoner.getSameIndividuals(asIndividual(arg0)).getEntities();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DIFFERENT_FROM:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLNamedIndividual> candidates = getIndividuals();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidates = reasoner.getDifferentIndividuals(asIndividual(arg1)).getFlattened();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLNamedIndividual> candidates = reasoner.getDifferentIndividuals(asIndividual(arg0)).getFlattened();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case PROPERTY_VALUE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidates = getIndividuals();
				for(OWLNamedIndividual c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				boolean object = true, data = true;
				if(arg2.isLiteral()) {
					object = false;
				}
				else if(arg2.isURI()) {
					data = false;
				}

				if(object) {
					Set<OWLObjectProperty> candidates = getObjectProperties();
					for(OWLObjectProperty p : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg1, QueryArgument.newURI(p.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}

				if(data) {
					Set<OWLDataProperty> candidates = getDataProperties();
					for(OWLDataProperty p : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg1, QueryArgument.newURI(p.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			else if(arg2.isVar()) {
				OWLNamedIndividual ind0 = asIndividual(arg0);
				OWLObjectProperty op1 = asObjectProperty(arg1);
				OWLDataProperty dp1 = asDataProperty(arg1);
				if(isDeclared(op1)) {
					Set<OWLNamedIndividual> candidates = reasoner.getObjectPropertyValues(ind0, op1).getFlattened();
					for(OWLNamedIndividual c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg2, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclared(dp1)) {
					Set<OWLLiteral> candidates = reasoner.getDataPropertyValues(ind0, dp1);
					for(OWLLiteral c : candidates) {
						new_binding = binding.clone();
                        new_binding.set(arg2, literalTranslator.toQueryArgument(c));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case PROPERTY:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> opCandidates = getObjectProperties();
				for(OWLObjectProperty c :opCandidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}

				Set<OWLDataProperty> dpCandidates = getDataProperties();
				for(OWLDataProperty c :dpCandidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case OBJECT_PROPERTY:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
		case DATA_PROPERTY:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLDataProperty> candidates = getDataProperties();
				for(OWLDataProperty c :candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			break;
        case ANNOTATION_PROPERTY:
                arg0 = args.get(0);
                if(arg0.isVar()) {
                    Set<OWLAnnotationProperty> candidates = getAnnotationProperties();
                    for(OWLAnnotationProperty c :candidates) {
                        new_binding = binding.clone();
                        new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
                        if(eval(query, group.bind(new_binding), result, new_binding)) {
                            ret = true;
                        }
                    }
                }
                break;
		case FUNCTIONAL:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLDataProperty> candidates = getDataProperties();
				for(OWLDataProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLFunctionalDataPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}

				Set<OWLObjectProperty> candidates2 = getObjectProperties();
				for(OWLObjectProperty c :candidates2) {
					if(reasoner.isEntailed(factory.getOWLFunctionalObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case INVERSE_FUNCTIONAL:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLInverseFunctionalObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case REFLEXIVE:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLReflexiveObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case IRREFLEXIVE:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLIrreflexiveObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case TRANSITIVE:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLTransitiveObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case SYMMETRIC:
			arg0 = args.get(0);
			if(arg0.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c :candidates) {
					if(reasoner.isEntailed(factory.getOWLSymmetricObjectPropertyAxiom(c))) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case STRICT_SUB_PROPERTY_OF:
			strict = true;
		case SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}

				Set<OWLDataProperty> candidates2 = getDataProperties();
				for(OWLDataProperty c : candidates2) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg1);
				OWLDataProperty dp = asDataProperty(arg1);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getSubObjectProperties(op, false).getFlattened();
					
					// if not strict we also add all equivalent properties
					if(!strict) {
						candidates.addAll(reasoner.getEquivalentObjectProperties(op).getEntities());
					}
							
					for(OWLObjectPropertyExpression p : candidates) {
						if(!p.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg0, QueryArgument.newURI(p.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getSubDataProperties(dp, false).getFlattened();
					
					// if not strict we also add all equivalent properties
					if(!strict) {
						candidates.addAll(reasoner.getEquivalentDataProperties(dp).getEntities());
					}
					
					for(OWLDataProperty p : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(p.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			else if(arg1.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg0);
				OWLDataProperty dp = asDataProperty(arg0);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getSuperObjectProperties(op, false).getFlattened();
					
					// if not strict we also add all equivalent properties
					if(!strict) {
						candidates.addAll(reasoner.getEquivalentObjectProperties(op).getEntities());
					}
					
					for(OWLObjectPropertyExpression p : candidates) {
						if(!p.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg0, QueryArgument.newURI(p.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getSuperDataProperties(dp, false).getFlattened();
					
					// if not strict we also add all equivalent properties
					if(!strict) {
						candidates.addAll(reasoner.getEquivalentDataProperties(dp).getEntities());
					}
					
					for(OWLDataProperty p : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(p.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case DIRECT_SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}

				Set<OWLDataProperty> candidates2 = getDataProperties();
				for(OWLDataProperty c : candidates2) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg1);
				OWLDataProperty dp = asDataProperty(arg1);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getSubObjectProperties(op, true).getFlattened();
					for(OWLObjectPropertyExpression c : candidates) {
						if(!c.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg0, QueryArgument.newURI(c.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getSubDataProperties(dp, true).getFlattened();
					for(OWLDataProperty c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			else if(arg1.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg0);
				OWLDataProperty dp = asDataProperty(arg0);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getSuperObjectProperties(op, true).getFlattened();
					for(OWLObjectPropertyExpression c : candidates) {
						if(!c.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg0, QueryArgument.newURI(c.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getSuperDataProperties(dp, true).getFlattened();
					for(OWLDataProperty c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case EQUIVALENT_PROPERTY:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(arg0.isVar() && arg1.isVar()) {
				Set<OWLObjectProperty> candidates = getObjectProperties();
				for(OWLObjectProperty c : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}

				Set<OWLDataProperty> candidates2 = getDataProperties();
				for(OWLDataProperty c : candidates2) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg0.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg1);
				OWLDataProperty dp = asDataProperty(arg1);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getEquivalentObjectProperties(op).getEntities();
					for(OWLObjectPropertyExpression c : candidates) {
						if(!c.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg0, QueryArgument.newURI(c.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getEquivalentDataProperties(dp).getEntities();
					for(OWLDataProperty c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			else if(arg1.isVar()) {
				OWLObjectProperty op = asObjectProperty(arg0);
				OWLDataProperty dp = asDataProperty(arg0);

				if(isDeclared(op)) {
					Set<OWLObjectPropertyExpression> candidates = reasoner.getEquivalentObjectProperties(op).getEntities();
					for(OWLObjectPropertyExpression c : candidates) {
						if(!c.isAnonymous()) {
							new_binding = binding.clone();
							new_binding.set(arg1, QueryArgument.newURI(c.getNamedProperty().getIRI()));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
				else if(isDeclared(dp)) {
					Set<OWLDataProperty> candidates = reasoner.getEquivalentDataProperties(dp).getEntities();
					for(OWLDataProperty c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg1, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
			}
			break;
		case ANNOTATION:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			if(arg0.isVar()) {
				Set<OWLNamedIndividual> candidatesI = getIndividuals();
				for(OWLNamedIndividual c : candidatesI) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
				
				Set<OWLClass> candidatesC = getClasses();
				for(OWLClass c : candidatesC) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
				
				Set<OWLDataProperty> candidatesDP = getDataProperties();
				for(OWLDataProperty c : candidatesDP) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
				
				Set<OWLObjectProperty> candidatesOP = getObjectProperties();
				for(OWLObjectProperty c : candidatesOP) {
					new_binding = binding.clone();
					new_binding.set(arg0, QueryArgument.newURI(c.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg1.isVar()) {
				Set<OWLAnnotationProperty> candidates = getAnnotationProperties();
				for(OWLAnnotationProperty p : candidates) {
					new_binding = binding.clone();
					new_binding.set(arg1, QueryArgument.newURI(p.getIRI()));
					if(eval(query, group.bind(new_binding), result, new_binding)) {
						ret = true;
					}
				}
			}
			else if(arg2.isVar()) {
				OWLNamedIndividual ind0 = asIndividual(arg0);
				OWLObjectProperty op1 = asObjectProperty(arg1);
				OWLDataProperty dp1 = asDataProperty(arg1);
				if(isDeclared(op1)) {
					Set<OWLNamedIndividual> candidates = reasoner.getObjectPropertyValues(ind0, op1).getFlattened();
					for(OWLNamedIndividual c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg2, QueryArgument.newURI(c.getIRI()));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				else if(isDeclared(dp1)) {
					Set<OWLLiteral> candidates = reasoner.getDataPropertyValues(ind0, dp1);
					for(OWLLiteral c : candidates) {
						new_binding = binding.clone();
						new_binding.set(arg2, literalTranslator.toQueryArgument(c));
						if(eval(query, group.bind(new_binding), result, new_binding)) {
							ret = true;
						}
					}
				}
				
				OWLEntity anEntity = null;
				if(isDeclaredIndividual(arg0)) {
					anEntity = asIndividual(arg0);
				}
				else if(isDeclaredDataProperty(arg0)) {
					anEntity = asDataProperty(arg0);
				}
				else if(isDeclaredObjectProperty(arg0)) {
					anEntity = asObjectProperty(arg0);
				}
				else if(isDeclaredClass(arg0)) {
					anEntity = asClass(arg0);
				}
				
				if(anEntity != null) {
					OWLAnnotationProperty anProp = asAnnotationProperty(arg1);
					
					Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
					for(OWLOntology o : reasoner.getRootOntology().getImportsClosure()) {
						annotations.addAll(anEntity.getAnnotations(o, anProp));
					}

					for(OWLAnnotation a : annotations) {
						if(a.getValue() instanceof IRI) {
							IRI i = (IRI)a.getValue();
							new_binding = binding.clone();
							new_binding.set(arg2, QueryArgument.newURI(i));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
						else if(a.getValue() instanceof OWLLiteral) {
							OWLLiteral l = (OWLLiteral)a.getValue();
							new_binding = binding.clone();
							new_binding.set(arg2, literalTranslator.toQueryArgument(l));
							if(eval(query, group.bind(new_binding), result, new_binding)) {
								ret = true;
							}
						}
					}
				}
			}
				break;
		default:
			throw new QueryEngineException("Unsupported or unknown atom type.");
		}
		
		return ret;
	}
	
	private boolean checkArgs(QueryAtom atom)
		throws QueryEngineException
	{
		List<QueryArgument> args = atom.getArguments();
		QueryArgument arg0, arg1, arg2;

		switch(atom.getType()) {
		case CLASS:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Class().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in atom Class() is not a class.");
			}
			return true;
		case INDIVIDUAL:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Individual().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in atom Individual() is not an individual.");
			}
			return true;
		case TYPE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom Type().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom Type() is not an individual.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom Type().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom Type() is not a class.");
			}
			return true;
		case DIRECT_TYPE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DirectType().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom DirectType() is not an individual.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom DirectType().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom DirectType() is not a class.");
			}
			return true;
		case PROPERTY_VALUE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom PropertyValue().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom PropertyValue() is not an individual.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom PropertyValue().");
			}
			if(arg1.isURI()) {
				if(isDeclaredDataProperty(arg1)) {
					if(!arg2.isLiteral() && !arg2.isVar()) {
						throw new QueryEngineException("Expected literal or variable in third argument of atom PropertyValue().");
					}
				}
				else if(isDeclaredObjectProperty(arg1)) {
					if(!arg2.isURI() && !arg2.isVar()) {
						throw new QueryEngineException("Expected URI or variable in third argument of atom PropertyValue().");
					}
					if(arg2.isURI() && !isDeclaredIndividual(arg2)) {
						throw new QueryEngineException("Given entity in third argument of atom PropertyValue() is not an individual.");
					}
				}
				else {
					throw new QueryEngineException("Given entity in second argument of atom PropertyValue() is not a property.");
				}
			}
			return true;
		case SAME_AS:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom SameAs().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom SameAs() is not an individual.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom SameAs().");
			}
			if(arg1.isURI() && !isDeclaredIndividual(arg1)) {
				throw new QueryEngineException("Given entity in first argument of atom SameAs() is not an individual.");
			}
			return true;
		case DIFFERENT_FROM:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DifferentFrom().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom DifferentFrom() is not an individual.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DifferentFrom().");
			}
			if(arg1.isURI() && !isDeclaredIndividual(arg1)) {
				throw new QueryEngineException("Given entity in first argument of atom DifferentFrom() is not an individual.");
			}
			return true;
		case SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom SubClassOf().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom SubClassOf() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom SubClassOf().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom SubClassOf() is not a class.");
			}
			return true;
		case DIRECT_SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DirectSubClassOf().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom DirectSubClassOf() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom DirectSubClassOf().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom DirectSubClassOf() is not a class.");
			}
			return true;
		case STRICT_SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom StrictSubClassOf().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom StrictSubClassOf() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom StrictSubClassOf().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom StrictSubClassOf() is not a class.");
			}
			return true;
		case EQUIVALENT_CLASS:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom EquivalentClass().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom EquivalentClass() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom EquivalentClass().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom EquivalentClass() is not a class.");
			}
			return true;
		case DISJOINT_WITH:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DisjointWith().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom DisjointWith() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom DisjointWith().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom DisjointWith() is not a class.");
			}
			return true;
		case COMPLEMENT_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom ComplementOf().");
			}
			if(arg0.isURI() && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom ComplementOf() is not a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom ComplementOf().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom ComplementOf() is not a class.");
			}
			return true;
		case SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom SubPropertyOf().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom SubPropertyOf() is not a property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom SubPropertyOf().");
			}
			if(arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom SubPropertyOf() is not a property.");
			}
			return true;
		case STRICT_SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom StrictSubPropertyOf().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom StrictSubPropertyOf() is not a property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom StrictSubPropertyOf().");
			}
			if(arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom StrictSubPropertyOf() is not a property.");
			}
			return true;
		case DIRECT_SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom DirectSubPropertyOf().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom DirectSubPropertyOf() is not a property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom DirectSubPropertyOf().");
			}
			if(arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom DirectSubPropertyOf() is not a property.");
			}
			return true;
		case EQUIVALENT_PROPERTY:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom EquivalentProperty().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom EquivalentProperty() is not a property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom EquivalentProperty().");
			}
			if(arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom EquivalentProperty() is not a property.");
			}
			return true;
		case INVERSE_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom InverseOf().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom InverseOf() is not a property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom InverseOf().");
			}
			if(arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom InverseOf() is not a property.");
			}
			return true;
		case OBJECT_PROPERTY:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom ObjectProperty().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom ObjectProperty() is not an object property.");
			}
			return true;
		case INVERSE_FUNCTIONAL:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom InverseFunctional().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom InverseFunctional() is not an object property.");
			}
			return true;
		case SYMMETRIC:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Symmetric().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom Symmetric() is not an object property.");
			}
			return true;
		case TRANSITIVE:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Transitive().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom Transitive() is not an object property.");
			}
			return true;
		case REFLEXIVE:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Reflexive().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom Reflexive() is not an object property.");
			}
			return true;
		case IRREFLEXIVE:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Irreflexive().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom Irreflexive() is not an object property.");
			}
			return true;
		case DATA_PROPERTY:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom DataProperty().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom DataProperty() is not a data property.");
			}
			return true;
        case ANNOTATION_PROPERTY:
            arg0 = args.get(0);
            if(!arg0.isURI() && !arg0.isVar()) {
                throw new QueryEngineException("Expected URI of variable in atom AnnotationProperty().");
            }
            if(arg0.isURI() && !isDeclaredAnnotationProperty(arg0)) {
                throw new QueryEngineException("Given entity in atom AnnotationProperty() is not an annotation property");
            }
            return true;
		case PROPERTY:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Property().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
				throw new QueryEngineException("Given entity in atom Property() is not a property.");
			}
			return true;
		case FUNCTIONAL:
			arg0 = args.get(0);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in atom Functional().");
			}
			if(arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0))  {
				throw new QueryEngineException("Given entity in atom Functional() is not a property.");
			}
			return true;
		case ANNOTATION:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom Annotation().");
			}
			if(arg0.isURI() && !isDeclaredIndividual(arg0) && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0) && !isDeclaredClass(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom Annotation() is not an individual, nor a data property, nor an object property, nor a class.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom Annotation().");
			}
			if(arg1.isURI() && !isDeclaredAnnotationProperty(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom Annotation() is not an annotation property.");
			}
			return true;
		case DOMAIN:
			arg0 = args.get(0);
			arg1 = args.get(1);
			if(!arg0.isURI() && !arg0.isVar()) {
				throw new QueryEngineException("Expected URI or variable in first argument of atom Domain().");
			}
			if(arg0.isURI() && !isDeclaredObjectProperty(arg0) && !isDeclaredDataProperty(arg0) && !isDeclaredAnnotationProperty(arg0)) {
				throw new QueryEngineException("Given entity in first argument of atom Domain() is not an object, data or annotation property.");
			}
			if(!arg1.isURI() && !arg1.isVar()) {
				throw new QueryEngineException("Expected URI or variable in second argument of atom Domain().");
			}
			if(arg1.isURI() && !isDeclaredClass(arg1)) {
				throw new QueryEngineException("Given entity in second argument of atom Domain() is not a class.");
			}
			return true;
		default:
			return false;
		}
	}
	
	private boolean checkBound(QueryAtom atom)
		throws QueryEngineException
	{
		List<QueryArgument> args = atom.getArguments();
		
		QueryArgument arg0, arg1, arg2;
		
		switch(atom.getType()) {
		case TYPE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getTypes(asIndividual(arg0), false).containsEntity(asClass(arg1));
		case DIRECT_TYPE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getTypes(asIndividual(arg0), true).containsEntity(asClass(arg1));
		case PROPERTY_VALUE:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			if(arg2.isURI()) {
				return reasoner.getObjectPropertyValues(asIndividual(arg0), asObjectProperty(arg1)).containsEntity(asIndividual(arg2));
			}
			else if(arg2.isLiteral()) {
				for(OWLLiteral l : reasoner.getDataPropertyValues(asIndividual(arg0), asDataProperty(arg1))) {
					if(l.getLiteral().equals(asLiteral(arg2).getLiteral())) {
						return true;
					}
				}
				return false;
			}
			return false;
		case SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getSubClasses(asClass(arg1), false).containsEntity(asClass(arg0)) ||
				reasoner.getEquivalentClasses(asClass(arg1)).contains(asClass(arg0));
		case STRICT_SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getSubClasses(asClass(arg1), false).containsEntity(asClass(arg0));
		case DIRECT_SUB_CLASS_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getSubClasses(asClass(arg1), true).containsEntity(asClass(arg0));
		case EQUIVALENT_CLASS:
			arg0 = args.get(0);
			arg1 = args.get(1);
			return reasoner.getEquivalentClasses(asClass(arg0)).contains(asClass(arg1));
		case FUNCTIONAL:
			arg0 = args.get(0);
			OWLObjectProperty functional_op = asObjectProperty(arg0);
			OWLDataProperty functional_dp = asDataProperty(arg0);
			if(isDeclared(functional_op)) {
				return reasoner.isEntailed(factory.getOWLFunctionalObjectPropertyAxiom(functional_op));
			}
			else if(isDeclared(functional_dp)) {
				return reasoner.isEntailed(factory.getOWLFunctionalDataPropertyAxiom(functional_dp));
			}
			return false;
		case INVERSE_FUNCTIONAL:
			arg0 = args.get(0);
			return reasoner.isEntailed(factory.getOWLInverseFunctionalObjectPropertyAxiom(asObjectProperty(arg0)));
		case TRANSITIVE:
			arg0 = args.get(0);
			return reasoner.isEntailed(factory.getOWLTransitiveObjectPropertyAxiom(asObjectProperty(arg0)));
		case SYMMETRIC:
			arg0 = args.get(0);
			return reasoner.isEntailed(factory.getOWLSymmetricObjectPropertyAxiom(asObjectProperty(arg0)));
		case REFLEXIVE:
			arg0 = args.get(0);
			return reasoner.isEntailed(factory.getOWLReflexiveObjectPropertyAxiom(asObjectProperty(arg0)));
		case IRREFLEXIVE:
			arg0 = args.get(0);
			return reasoner.isEntailed(factory.getOWLIrreflexiveObjectPropertyAxiom(asObjectProperty(arg0)));
		case SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);

			OWLObjectProperty sub_prop_op0 = asObjectProperty(arg0);
			OWLDataProperty sub_prop_dp0 = asDataProperty(arg0);

			if(isDeclared(sub_prop_op0)) {
				return reasoner.getSubObjectProperties(asObjectProperty(arg1), false).containsEntity(sub_prop_op0) ||
					reasoner.getEquivalentObjectProperties(asObjectProperty(arg1)).contains(sub_prop_op0);
			}
			else if(isDeclared(sub_prop_dp0)) {
				return reasoner.getSubDataProperties(asDataProperty(arg1), false).containsEntity(sub_prop_dp0) ||
					reasoner.getEquivalentDataProperties(asDataProperty(arg1)).contains(sub_prop_dp0);
			}
			return false;
		case STRICT_SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);

			OWLObjectProperty strict_sub_prop_op0 = asObjectProperty(arg0);
			OWLDataProperty strict_sub_prop_dp0 = asDataProperty(arg0);

			if(isDeclared(strict_sub_prop_op0)) {
				return reasoner.getSubObjectProperties(asObjectProperty(arg1), false).containsEntity(strict_sub_prop_op0);
			}
			else if(isDeclared(strict_sub_prop_dp0)) {
				return reasoner.getSubDataProperties(asDataProperty(arg1), false).containsEntity(strict_sub_prop_dp0);
			}
			return false;
		case DIRECT_SUB_PROPERTY_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);

			OWLObjectProperty direct_sub_prop_op0 = asObjectProperty(arg0);
			OWLDataProperty direct_sub_prop_dp0 = asDataProperty(arg0);

			if(isDeclared(direct_sub_prop_op0)) {
				return reasoner.getSubObjectProperties(asObjectProperty(arg1), true).containsEntity(direct_sub_prop_op0);
			}
			else if(isDeclared(direct_sub_prop_dp0)) {
				return reasoner.getSubDataProperties(asDataProperty(arg1), true).containsEntity(direct_sub_prop_dp0);
			}
			return false;
		case INVERSE_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);

			OWLObjectProperty inv_prop_op0 = asObjectProperty(arg0);

			if(isDeclared(inv_prop_op0)) {
				return reasoner.getInverseObjectProperties(inv_prop_op0).contains(asObjectProperty(arg1));
			}
			return false;
        case EQUIVALENT_PROPERTY:
			arg0 = args.get(0);
			arg1 = args.get(1);

			OWLObjectProperty equiv_prop_op0 = asObjectProperty(arg0);
			OWLDataProperty equiv_prop_dp0 = asDataProperty(arg0);

			if(isDeclared(equiv_prop_op0)) {
				return reasoner.getEquivalentObjectProperties(equiv_prop_op0).contains(asObjectProperty(arg1));
			}
			else if(isDeclared(equiv_prop_dp0)) {
				return reasoner.getEquivalentDataProperties(equiv_prop_dp0).contains(asDataProperty(arg1));
			}
			return false;
		case DOMAIN:
			arg0 = args.get(0);
			arg1 = args.get(1);
			OWLObjectProperty op_0 = asObjectProperty(arg0);
			OWLDataProperty dp_0 = asDataProperty(arg0);
			OWLAnnotationProperty ap_0 = asAnnotationProperty(arg0);
			if(isDeclared(op_0)) {
				return reasoner.getObjectPropertyDomains(op_0, false).containsEntity(asClass(arg1));
			}
			else if(isDeclared(dp_0)) {
				return reasoner.getDataPropertyDomains(dp_0, false).containsEntity(asClass(arg1));
			}
			else if(isDeclared(ap_0)) {
				Set<OWLAnnotationPropertyDomainAxiom> axioms = reasoner.getRootOntology().getAxioms(AxiomType.ANNOTATION_PROPERTY_DOMAIN, true);
				for(OWLAnnotationPropertyDomainAxiom ax : axioms) {
					if(ax.getProperty().equals(ap_0)) {
						if(ax.getDomain().toString().equals(arg1.getValue())) {
							return true;
						}
					}
				}
			}
			return false;
		case SAME_AS:
			arg0 = args.get(0);
			arg1 = args.get(1);
				
			return reasoner.getSameIndividuals(asIndividual(arg0)).contains(asIndividual(arg1));
		case DIFFERENT_FROM:
			arg0 = args.get(0);
			arg1 = args.get(1);
				
			return reasoner.getDifferentIndividuals(asIndividual(arg0)).containsEntity(asIndividual(arg1));
		case DISJOINT_WITH:
			arg0 = args.get(0);
			arg1 = args.get(1);
				
			return reasoner.getDisjointClasses(asClass(arg0)).containsEntity(asClass(arg1));
		case COMPLEMENT_OF:
			arg0 = args.get(0);
			arg1 = args.get(1);
			
			return reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(arg0))).contains(asClass(arg1));
		case ANNOTATION:
			arg0 = args.get(0);
			arg1 = args.get(1);
			arg2 = args.get(2);
			OWLEntity anEntity = null;
			OWLAnnotationProperty anProp = asAnnotationProperty(arg1);
			if(isDeclaredIndividual(arg0)) {
				anEntity = asIndividual(arg0);
			}
			else if(isDeclaredDataProperty(arg0)) {
				anEntity = asDataProperty(arg0);
			}
			else if(isDeclaredObjectProperty(arg0)) {
				anEntity = asObjectProperty(arg0);
			}
			else if(isDeclaredClass(arg0)) {
				anEntity = asClass(arg0);
			}
			
			if(anEntity == null) {
				return false;
			}
			
			Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
			for(OWLOntology o : reasoner.getRootOntology().getImportsClosure()) {
				annotations.addAll(anEntity.getAnnotations(o, anProp));
			}
			
			if(arg2.isURI()) {
				for(OWLAnnotation a : annotations) {
					if(a.getValue() instanceof IRI) {
						IRI i = (IRI)a.getValue();
						if(i.toString().equals(arg2.getValue())) {
							return true;
						}
					}
				}
			}
			else if(arg2.isLiteral()) {
				for(OWLAnnotation a : annotations) {
					if(a.getValue() instanceof OWLLiteral) {
						OWLLiteral l = (OWLLiteral)a.getValue();
//						if(l.equals()) {
							return true;
//						}
					}
				}
			}
			return false;
		case CLASS:
			return isDeclaredClass(args.get(0));
		case INDIVIDUAL:
			return isDeclaredIndividual(args.get(0));
		case PROPERTY:
			return isDeclaredObjectProperty(args.get(0)) || isDeclaredDataProperty(args.get(0));
		case OBJECT_PROPERTY:
			return isDeclaredObjectProperty(args.get(0));
		case DATA_PROPERTY:
			return isDeclaredDataProperty(args.get(0));
        case ANNOTATION_PROPERTY:
            return isDeclaredAnnotationProperty(args.get(0));
		default:
			throw new QueryEngineException("Unsupported or unknown atom type.");
		}
	}
	
	private float estimateCost(QueryAtom atom)
	{
		List<QueryArgument> args = atom.getArguments();
		
		switch(atom.getType()) {
		case TYPE:
		case DIRECT_TYPE:
		case PROPERTY_VALUE:
		case SUB_CLASS_OF:
		case STRICT_SUB_CLASS_OF:
		case DIRECT_SUB_CLASS_OF:
		case EQUIVALENT_CLASS:
		case FUNCTIONAL:
		case INVERSE_FUNCTIONAL:
		case TRANSITIVE:
		case SYMMETRIC:
		case SUB_PROPERTY_OF:
		case STRICT_SUB_PROPERTY_OF:
		case DIRECT_SUB_PROPERTY_OF:
		case EQUIVALENT_PROPERTY:
		case CLASS:
		case INDIVIDUAL:
		case PROPERTY:
		case OBJECT_PROPERTY:
		case DATA_PROPERTY:
		case ANNOTATION_PROPERTY:
		case INVERSE_OF:
		case REFLEXIVE:
		case IRREFLEXIVE:
		case SAME_AS:
		case DISJOINT_WITH:
		case DIFFERENT_FROM:
		case COMPLEMENT_OF:
		case ANNOTATION:
			float cost = 0f;
			for(QueryArgument arg : args) {
				if(arg.isVar()) {
					cost += 1f;
				}
			}
			return cost;
		default:
			return 1e25f;
		}
	}
	
	private QueryAtomGroupImpl preorder(QueryAtomGroupImpl group)
	{
		List<QueryAtom> atoms = new LinkedList<QueryAtom>();
		for(QueryAtom atom : group.getAtoms()) {
			atoms.add(atom);
		}
		
		Collections.sort(atoms, new Comparator<QueryAtom>() {
			public int compare(QueryAtom a1, QueryAtom a2) 
			{
				float c1 = QueryEngineImpl.this.estimateCost(a1);
				float c2 = QueryEngineImpl.this.estimateCost(a2);
				
				if(c1 == c2) return 0;
				return (c1 < c2) ? -1 : +1;
			}
		});
		
		QueryAtomGroupImpl ret = new QueryAtomGroupImpl();
		for(QueryAtom atom : atoms) {
			ret.addAtom(atom);
		}
		
		return ret;
	}
	
	private OWLClass asClass(QueryArgument arg)
	{
		return manager.getOWLDataFactory().getOWLClass(IRI.create(arg.getValue()));
	}
	
	private OWLObjectProperty asObjectProperty(QueryArgument arg)
	{
		return manager.getOWLDataFactory().getOWLObjectProperty(IRI.create(arg.getValue()));
	}
	
	private OWLDataProperty asDataProperty(QueryArgument arg)
	{
		return manager.getOWLDataFactory().getOWLDataProperty(IRI.create(arg.getValue()));
	}
	
	private OWLNamedIndividual asIndividual(QueryArgument arg)
	{	
		return manager.getOWLDataFactory().getOWLNamedIndividual(IRI.create(arg.getValue()));
	}
	
	private OWLLiteral asLiteral(QueryArgument arg)
	{
		return literalTranslator.toOWLLiteral(arg);
	}
	
	private OWLAnnotationProperty asAnnotationProperty(QueryArgument arg)
	{
		return manager.getOWLDataFactory().getOWLAnnotationProperty(IRI.create(arg.getValue()));
	}
	
	private Set<OWLClass> getClasses()
	{
		return reasoner.getRootOntology().getClassesInSignature(true);
	}
	
	private Set<OWLNamedIndividual> getIndividuals()
	{
		return reasoner.getRootOntology().getIndividualsInSignature(true);
	}
	
	private Set<OWLObjectProperty> getObjectProperties()
	{
		return reasoner.getRootOntology().getObjectPropertiesInSignature(true);
	}
	
	private Set<OWLDataProperty> getDataProperties()
	{
		return reasoner.getRootOntology().getDataPropertiesInSignature(true);
	}
	
	private Set<OWLAnnotationProperty> getAnnotationProperties()
	{
		Set<OWLAnnotationProperty> ret = new HashSet<OWLAnnotationProperty>();
		for(OWLOntology o : reasoner.getRootOntology().getImportsClosure()) {
			ret.addAll(o.getAnnotationPropertiesInSignature());
		}
		return ret;
	}
	
	private boolean isDeclaredIndividual(QueryArgument arg)
	{
		return isDeclared(asIndividual(arg));
	}
	
	private boolean isDeclaredClass(QueryArgument arg)
	{
		return isDeclared(asClass(arg));
	}
	
	private boolean isDeclaredObjectProperty(QueryArgument arg)
	{
		return isDeclared(asObjectProperty(arg));
	}
	
	private boolean isDeclaredDataProperty(QueryArgument arg)
	{
		return isDeclared(asDataProperty(arg));
	}
	
	private boolean isDeclaredAnnotationProperty(QueryArgument arg)
	{
		return isDeclared(asAnnotationProperty(arg));
	}
	
	private boolean isDeclared(OWLNamedIndividual i)
	{
		return i.isBuiltIn() || reasoner.getRootOntology().containsIndividualInSignature(i.getIRI(), true);
	}
	
	private boolean isDeclared(OWLClass c)
	{
		return c.isBuiltIn() || reasoner.getRootOntology().containsClassInSignature(c.getIRI(), true);
	}
	
	private boolean isDeclared(OWLObjectProperty p)
	{
		return p.isBuiltIn() || reasoner.getRootOntology().containsObjectPropertyInSignature(p.getIRI(), true);
	}
	
	private boolean isDeclared(OWLDataProperty p)
	{
		return p.isBuiltIn() || reasoner.getRootOntology().containsDataPropertyInSignature(p.getIRI(), true);
	}

	private boolean isDeclared(OWLAnnotationProperty p)
	{
		return p.isBuiltIn() || reasoner.getRootOntology().containsAnnotationPropertyInSignature(p.getIRI(), true);
	}
}
