// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import de.derivo.sparqldlapi.*;
import de.derivo.sparqldlapi.exceptions.QueryEngineException;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import jpaul.DataStructs.UnionFind;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.AxiomAnnotations;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.search.EntitySearcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static de.derivo.sparqldlapi.QueryArgument.newLiteral;
import static de.derivo.sparqldlapi.QueryArgument.newURI;
import static de.derivo.sparqldlapi.types.QueryType.SELECT_DISTINCT;
import static java.util.stream.Collectors.toSet;

/**
 * A concrete implementation of the query engine interface utilizing the OWL-API.
 *
 * @author Mario Volke
 */
public class QueryEngineImpl extends QueryEngine {


    private enum BoundChecking {
        CHECK_BOUND,
        DO_NOT_CHECK_BOUND
    }

    private final OWLOntologyManager manager;

    private OWLReasoner reasoner;

    private OWLDataFactory factory;

    private boolean strictMode;

    private boolean performArgumentChecking = true;

    private Set<OWLAnnotationProperty> cachedAnnotationProperties = new HashSet<>();

    private Set<OWLAnnotationAssertionAxiom> unannotatedAxioms = new HashSet<>();

    private Multimap<IRI, OWLAnnotationAssertionAxiom> annotationAssertionsBySubject = ArrayListMultimap.create();

    private ImmutableSet<IRI> classIris;

    private ImmutableSet<OWLClass> classes;

    public QueryEngineImpl(OWLOntologyManager manager, OWLReasoner reasoner) {
        this(manager, reasoner, false);
    }

    /**
     * QueryEngineImpl constructor
     *
     * @param manager    An OWLOntologyManager instance of OWLAPI v3
     * @param reasoner   An OWLReasoner instance.
     * @param strictMode If strict mode is enabled the query engine will throw a QueryEngineException if data types withing the query are not correct (e.g. Class(URI_OF_AN_INDIVIDUAL))
     */
    public QueryEngineImpl(OWLOntologyManager manager, OWLReasoner reasoner, boolean strictMode) {
        this.manager = manager;
        this.reasoner = reasoner;
        this.factory = manager.getOWLDataFactory();
        this.strictMode = strictMode;
        long t0 = System.currentTimeMillis();
        reasoner.getRootOntology()
                .getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED)
                .stream()
                .filter(ax -> ax.getSubject() instanceof IRI)
                .forEach(ax -> {
                    unannotatedAxioms.add(ax.getAxiomWithoutAnnotations());
                    annotationAssertionsBySubject.put((IRI) ax.getSubject(), ax);
                });
        Set<OWLClass> classesInSignature = reasoner.getRootOntology().getClassesInSignature(Imports.INCLUDED);
        classesInSignature.add(factory.getOWLThing());
        classesInSignature.add(factory.getOWLNothing());
        classes = ImmutableSet.copyOf(classesInSignature);
        classIris = ImmutableSet.copyOf(classes.stream().map(OWLClass::getIRI).collect(toSet()));
        long t1 = System.currentTimeMillis();
        System.out.println("Built indexes in " + (t1 - t0) + " ms");
    }

    public void setStrictMode(boolean strict) {
        this.strictMode = strict;
    }

    /**
     * If the client is sure that the query is well formed then args checking can be disabled.
     */
    public void setPerformArgumentChecking(boolean performArgumentChecking) {
        this.performArgumentChecking = performArgumentChecking;
    }

    /**
     * Execute a sparql-dl query and generate the result set.
     *
     * @return The query result set.
     */
    public QueryResult execute(Query query) throws QueryEngineException {
        if (!(query instanceof QueryImpl)) {
            throw new QueryEngineException("Couldn't cast Query to QueryImpl.");
        }
        QueryImpl q = (QueryImpl) query;


        // search for unbound results vars
        resvarsLoop:
        for (QueryArgument arg : q.getResultVars()) {
            for (QueryAtomGroup g : q.getAtomGroups()) {
                for (QueryAtom a : g.getAtoms()) {
                    if (a.getArguments().contains(arg)) {
                        continue resvarsLoop;
                    }
                }
            }
//			throw new QueryEngineException("Query contains an unbound result argument " + arg + ".");
        }

        Queue<QueryResultImpl> results = new LinkedList<>();
        for (QueryAtomGroup g : q.getAtomGroups()) {

            QueryAtomGroupImpl group = (QueryAtomGroupImpl) g;

            List<QueryAtomGroupImpl> components = findComponents(group);
            Queue<QueryResultImpl> componentResults = new LinkedList<>();
            boolean groupAsk = true;
            for (QueryAtomGroupImpl component : components) {

                QueryAtomGroupImpl preorderedGroup = preorder(component);

                QueryResultImpl result = new QueryResultImpl(query);
                if (eval(q, preorderedGroup, result, new QueryBindingImpl(), BoundChecking.CHECK_BOUND)) {
                    if (query.isSelectDistinct()) {
                        result = eliminateDuplicates(result);
                    }

                    componentResults.add(result);
                }
                else {
                    groupAsk = false;
                    break;
                }
            }

            if (groupAsk) {
                results.add(combineResults(componentResults,
                                           query.getType() == SELECT_DISTINCT));
            }
            else {
                // return only empty result with no solution for this group
                QueryResultImpl ret = new QueryResultImpl(query);
                ret.setAsk(false);
                results.add(ret);
            }
        }

        return unionResults(q, results, query.getType() == SELECT_DISTINCT);
    }

    /**
     * Split the query into individual components if possible to avoid cross-products in later evaluation.
     * The first component will contain all atoms with no variables if there exist some.
     *
     * @return a set of group components
     */
    private List<QueryAtomGroupImpl> findComponents(QueryAtomGroupImpl group) {
        List<QueryAtom> atoms = new LinkedList<>();
        atoms.addAll(group.getAtoms());
        List<QueryAtomGroupImpl> components = new LinkedList<>();

        // if we have no atoms at all we simply return the same query as a single component
        if (atoms.isEmpty()) {
            components.add(group);
            return components;
        }

        // find all atoms containing no variables
        // and build a component
        QueryAtomGroupImpl component = new QueryAtomGroupImpl();

        for (QueryAtom atom : atoms) {
            boolean noVar = true;
            for (QueryArgument arg : atom.getArguments()) {
                if (arg.isVar()) {
                    noVar = false;
                    break;
                }
            }
            if (noVar) {
                component.addAtom(atom);
            }
        }

        component.getAtoms().forEach(atoms::remove);

        if (!component.isEmpty()) {
            components.add(component);
        }

        // find connected components
        UnionFind<QueryArgument> unionFind = new UnionFind<>();
        for (QueryAtom atom : atoms) {
            QueryArgument firstVar = null;
            for (QueryArgument arg : atom.getArguments()) {
                if (arg.isVar()) {
                    if (firstVar == null) {
                        firstVar = arg;
                    }
                    else {
                        unionFind.union(firstVar, arg);
                    }
                }
            }
        }

        while (!atoms.isEmpty()) {
            component = new QueryAtomGroupImpl();
            QueryAtom nextAtom = atoms.get(0);
            atoms.remove(nextAtom);
            component.addAtom(nextAtom);
            QueryArgument args = null;
            for (QueryArgument arg : nextAtom.getArguments()) {
                if (arg.isVar()) {
                    args = unionFind.find(arg);
                    break;
                }
            }

            for (QueryAtom atom : atoms) {
                QueryArgument args2 = null;
                for (QueryArgument arg : atom.getArguments()) {
                    if (arg.isVar()) {
                        args2 = unionFind.find(arg);
                        break;
                    }
                }

                if (args.equals(args2)) {
                    component.addAtom(atom);
                }
            }

            for (QueryAtom atom : component.getAtoms()) {
                atoms.remove(atom);
            }

            components.add(component);
        }

        return components;
    }

    /**
     * Combine the results of the individual components with the cartesian product.
     *
     * @return the combined result
     */
    private QueryResultImpl combineResults(Queue<QueryResultImpl> results, boolean distinct) {
        while (results.size() > 1) {
            QueryResultImpl a = results.remove();
            QueryResultImpl b = results.remove();
            results.add(combineResults(a, b, distinct));
        }

        return results.remove();
    }

    /**
     * Combine two results with the cartesian product.
     *
     * @return the combined result
     */
    private QueryResultImpl combineResults(QueryResultImpl a, QueryResultImpl b, boolean distinct) {
        QueryResultImpl result = new QueryResultImpl(a.getQuery());

        for (QueryBindingImpl bindingA : a.getBindings()) {
            for (QueryBindingImpl bindingB : b.getBindings()) {
                QueryBindingImpl binding = new QueryBindingImpl();
                binding.set(bindingA);
                binding.set(bindingB);
                result.add(binding);
            }
        }

        if (distinct) {
            return eliminateDuplicates(result);
        }
        return result;
    }

    /**
     * Union results
     *
     * @return the union result
     */
    private QueryResultImpl unionResults(QueryImpl query, Queue<QueryResultImpl> results, boolean distinct) {
        QueryResultImpl result = new QueryResultImpl(query);

        boolean ask = false;

        for (QueryResultImpl r : results) {
            if (r.ask()) {
                ask = true;
            }
            r.getBindings().forEach(result::add);
        }

        result.setAsk(ask);

        if (distinct) {
            return eliminateDuplicates(result);
        }
        return result;
    }

    /**
     * Eliminate duplicate bindings.
     *
     * @return A new QueryResultImpl instance without diplicates
     */
    private QueryResultImpl eliminateDuplicates(QueryResultImpl result) {
        QueryResultImpl ret = new QueryResultImpl(result.getQuery());
        Set<QueryBindingImpl> distinctSet = new HashSet<>(result.getBindings());
        distinctSet.forEach(ret::add);
        return ret;
    }

    private boolean eval(QueryImpl query,
                         QueryAtomGroupImpl group,
                         QueryResultImpl result,
                         QueryBindingImpl binding,
                         BoundChecking checkBound) {
        // Check for termination.  If all the atoms have been processed in this group then we are done.
        if (group.isEmpty()) {
            if (query.isSelect() || query.isSelectDistinct()) {
                result.add(binding.cloneAndFilter(query.getResultVars()));
            }
            return true;
        }

        QueryAtom atom = group.nextAtom();
        if (performArgumentChecking) {
            try {
                checkArgs(atom);
            } catch (QueryEngineException e) {
                // if strict mode is enabled we will throw an exception here
                if (strictMode) {
                    throw new RuntimeException(e);
                }
                return false;
            }
        }

        if (atom.isBound()) {
            if (checkBound(atom)) {
                // If the binding is entailed by the ontology then pop the atom and move on to the next one
                if (eval(query,
                         group.pop(),
                         result,
                         binding,
                         checkBound)) {
                    return true;
                }
            }
            return false;
        }
        switch (atom.getType()) {
            case CLASS:
                return evalClass(query, group, result, binding, atom);
            case INDIVIDUAL:
                return evalIndividual(query, group, result, binding, atom);
            case STRICT_SUB_CLASS_OF:
                return evalSubClassOf(query, group, result, binding, atom, SubClassOfMode.STRICT);
            case SUB_CLASS_OF:
                return evalSubClassOf(query, group, result, binding, atom, SubClassOfMode.NON_STRICT);
            case DIRECT_SUB_CLASS_OF:
                return evalDirectSubClassOf(query, group, result, binding, atom);
            case EQUIVALENT_CLASS:
                return evalEquivalentClasses(query, group, result, binding, atom);
            case DOMAIN:
                return evalDomain(query, group, result, binding, atom);
            case RANGE:
                return evalRange(query, group, result, binding, atom);
            case COMPLEMENT_OF:
                return evalComplementOf(query, group, result, binding, atom);
            case DISJOINT_WITH:
                return evalDisjointWith(query, group, result, binding, atom);
            case DIRECT_TYPE:
                return evalType(query, group, result, binding, atom, true);
            case TYPE:
                return evalType(query, group, result, binding, atom, false);
            case SAME_AS:
                return evalSameAs(query, group, result, binding, atom);
            case DIFFERENT_FROM:
                return evalDifferentIndividuals(query, group, result, binding, atom);
            case PROPERTY_VALUE:
                return evalPropertyValue(query, group, result, binding, atom);
            case PROPERTY:
                return evalProperty(query, group, result, binding, atom);
            case OBJECT_PROPERTY:
                return evalObjectProperty(query, group, result, binding, atom);
            case DATA_PROPERTY:
                return evalDataProperty(query, group, result, binding, atom);
            case ANNOTATION_PROPERTY:
                return evalAnnotationProperty(query, group, result, binding, atom);
            case FUNCTIONAL:
                return evalFunctional(query, group, result, binding, atom);
            case INVERSE_FUNCTIONAL:
                return evalInverseFunctional(query, group, result, binding, atom);
            case REFLEXIVE:
                return evalReflexive(query, group, result, binding, atom);
            case IRREFLEXIVE:
                return evalIrreflexive(query, group, result, binding, atom);
            case TRANSITIVE:
                return evalTransitive(query, group, result, binding, atom);
            case SYMMETRIC:
                return evalSymmetric(query, group, result, binding, atom);
            case STRICT_SUB_PROPERTY_OF:
                return evalSubPropertyOf(query, group, result, binding, atom, true);
            case SUB_PROPERTY_OF:
                return evalSubPropertyOf(query, group, result, binding, atom, false);
            case DIRECT_SUB_PROPERTY_OF:
                return evalDirectSubPropertyOf(query, group, result, binding, atom);
            case EQUIVALENT_PROPERTY:
                return evalEquivalentProperty(query, group, result, binding, atom);
            case ANNOTATION:
                return evalAnnotationAssertion(query, group, result, binding, atom);
            default:
                throw new RuntimeException("Unsupported or unknown atom type.");
        }
    }

    private boolean evalInverseFunctional(QueryImpl query,
                                          QueryAtomGroupImpl group,
                                          QueryResultImpl result,
                                          QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            for (OWLObjectProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLInverseFunctionalObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalEquivalentProperty(QueryImpl query,
                                           QueryAtomGroupImpl group,
                                           QueryResultImpl result,
                                           QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument prop0Arg = arguments.get(0);
        QueryArgument prop1Arg = arguments.get(1);
        if (prop0Arg.isVar() && prop1Arg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            ret = bindAndEvalObjectPropertyCandidates(query, group, result, binding, prop0Arg, candidates);

            Set<OWLDataProperty> candidates2 = getDataProperties();
            if (bindAndEvalDataPropertyCandidates(query, group, result, binding, prop0Arg, candidates2)) {
                ret = true;
            }
        }
        else if (prop0Arg.isVar()) {
            OWLObjectProperty op = asObjectProperty(prop1Arg);
            OWLDataProperty dp = asDataProperty(prop1Arg);

            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getEquivalentObjectProperties(op)
                                                                      .getEntities();
                for (OWLObjectPropertyExpression c : candidates) {
                    if (!c.isAnonymous()) {
                        final QueryBindingImpl new_binding = binding.clone();
                        new_binding.set(prop0Arg, newURI(c.getNamedProperty().getIRI()));
                        if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                            ret = true;
                        }
                    }
                }
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getEquivalentDataProperties(dp).getEntities();
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, prop0Arg, candidates)) {
                    ret = true;
                }
            }
        }
        else if (prop1Arg.isVar()) {
            OWLObjectProperty op = asObjectProperty(prop0Arg);
            OWLDataProperty dp = asDataProperty(prop0Arg);

            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getEquivalentObjectProperties(op)
                                                                      .getEntities();
                if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, prop1Arg, candidates)) {
                    ret = true;
                }
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getEquivalentDataProperties(dp).getEntities();
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, prop1Arg, candidates)) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private boolean evalDirectSubPropertyOf(QueryImpl query,
                                            QueryAtomGroupImpl group,
                                            QueryResultImpl result,
                                            QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subPropArg = arguments.get(0);
        QueryArgument superPropArg = arguments.get(1);
        if (subPropArg.isVar() && superPropArg.isVar()) {
            Set<OWLObjectProperty> objectPropCandidates = getObjectProperties();
            if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, subPropArg, objectPropCandidates)) {
                ret = true;
            }
            Set<OWLDataProperty> dataPropCandidates = getDataProperties();
            if (bindAndEvalDataPropertyCandidates(query, group, result, binding, subPropArg, dataPropCandidates)) {
                ret = true;
            }
        }
        else if (subPropArg.isVar()) {
            OWLObjectProperty op = asObjectProperty(superPropArg);
            OWLDataProperty dp = asDataProperty(superPropArg);
            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getSubObjectProperties(op, true)
                                                                      .getFlattened();
                if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, subPropArg, candidates)) {
                    ret = true;
                }
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getSubDataProperties(dp, true).getFlattened();
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, subPropArg, candidates)) {
                    ret = true;
                }
            }
        }
        else if (superPropArg.isVar()) {
            OWLObjectProperty op = asObjectProperty(subPropArg);
            OWLDataProperty dp = asDataProperty(subPropArg);

            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getSuperObjectProperties(op, true)
                                                                      .getFlattened();
                if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, superPropArg, candidates)) {
                    ret = true;
                }
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getSuperDataProperties(dp, true).getFlattened();
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, superPropArg, candidates)) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private boolean evalSubPropertyOf(QueryImpl query,
                                      QueryAtomGroupImpl group,
                                      QueryResultImpl result,
                                      QueryBindingImpl binding, QueryAtom atom, boolean strict) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subPropArg = arguments.get(0);
        QueryArgument superPropArg = arguments.get(1);
        if (subPropArg.isVar() && superPropArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, subPropArg, candidates)) {
                ret = true;
            }
            Set<OWLDataProperty> dataPropertyCandidates = getDataProperties();
            if (bindAndEvalDataPropertyCandidates(query, group, result, binding, subPropArg, dataPropertyCandidates)) {
                ret = true;
            }
        }
        else if (subPropArg.isVar()) {
            OWLObjectProperty op = asObjectProperty(superPropArg);
            OWLDataProperty dp = asDataProperty(superPropArg);

            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getSubObjectProperties(op, false)
                                                                      .getFlattened();

                // if not strict we also add all equivalent properties
                if (!strict) {
                    candidates.addAll(reasoner.getEquivalentObjectProperties(op).getEntities());
                }
                bindAndEvalObjectPropertyCandidates(query, group, result, binding, subPropArg, candidates);
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getSubDataProperties(dp, false).getFlattened();
                // if not strict we also add all equivalent properties
                if (!strict) {
                    candidates.addAll(reasoner.getEquivalentDataProperties(dp).getEntities());
                }
                ret = bindAndEvalDataPropertyCandidates(query, group, result, binding, subPropArg, candidates);
            }
        }
        else if (superPropArg.isVar()) {
            OWLObjectProperty op = asObjectProperty(subPropArg);
            OWLDataProperty dp = asDataProperty(subPropArg);

            if (isDeclared(op)) {
                Set<OWLObjectPropertyExpression> candidates = reasoner.getSuperObjectProperties(op, false)
                                                                      .getFlattened();

                // if not strict we also add all equivalent properties
                if (!strict) {
                    candidates.addAll(reasoner.getEquivalentObjectProperties(op).getEntities());
                }
                ret = bindAndEvalObjectPropertyCandidates(query, group, result, binding, superPropArg, candidates);
            }
            else if (isDeclared(dp)) {
                Set<OWLDataProperty> candidates = reasoner.getSuperDataProperties(dp, false).getFlattened();
                // if not strict we also add all equivalent properties
                if (!strict) {
                    candidates.addAll(reasoner.getEquivalentDataProperties(dp).getEntities());
                }
                ret = bindAndEvalDataPropertyCandidates(query, group, result, binding, subPropArg, candidates);
            }
        }
        return ret;
    }

    private boolean bindAndEvalObjectPropertyCandidates(QueryImpl query,
                                                        QueryAtomGroupImpl group,
                                                        QueryResultImpl result,
                                                        QueryBindingImpl binding,
                                                        QueryArgument subPropArg,
                                                        Set<? extends OWLObjectPropertyExpression> candidates) {
        boolean ret = false;
        for (OWLObjectPropertyExpression propExp : candidates) {
            if (!propExp.isAnonymous()) {
                final QueryBindingImpl new_binding = binding.clone();
                new_binding.set(subPropArg, newURI(propExp.asOWLObjectProperty().getIRI()));
                if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private boolean evalSymmetric(QueryImpl query,
                                  QueryAtomGroupImpl group,
                                  QueryResultImpl result,
                                  QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            for (OWLObjectProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLSymmetricObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalTransitive(QueryImpl query,
                                   QueryAtomGroupImpl group,
                                   QueryResultImpl result,
                                   QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            for (OWLObjectProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLTransitiveObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalIrreflexive(QueryImpl query,
                                    QueryAtomGroupImpl group,
                                    QueryResultImpl result,
                                    QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            for (OWLObjectProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLIrreflexiveObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalReflexive(QueryImpl query,
                                  QueryAtomGroupImpl group,
                                  QueryResultImpl result,
                                  QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            for (OWLObjectProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLReflexiveObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalFunctional(QueryImpl query,
                                   QueryAtomGroupImpl group,
                                   QueryResultImpl result,
                                   QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLDataProperty> candidates = getDataProperties();
            for (OWLDataProperty c : candidates) {
                if (reasoner.isEntailed(factory.getOWLFunctionalDataPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }

            Set<OWLObjectProperty> candidates2 = getObjectProperties();
            for (OWLObjectProperty c : candidates2) {
                if (reasoner.isEntailed(factory.getOWLFunctionalObjectPropertyAxiom(c))) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(propArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalAnnotationProperty(QueryImpl query,
                                           QueryAtomGroupImpl group,
                                           QueryResultImpl result,
                                           QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLAnnotationProperty> candidates = getAnnotationProperties();
            for (OWLAnnotationProperty c : candidates) {
                final QueryBindingImpl new_binding = binding.clone();
                new_binding.set(propArg, newURI(c.getIRI()));
                if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private boolean evalDataProperty(QueryImpl query,
                                     QueryAtomGroupImpl group,
                                     QueryResultImpl result,
                                     QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLDataProperty> candidates = getDataProperties();
            if (bindAndEvalDataPropertyCandidates(query, group, result, binding, propArg, candidates)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalProperty(QueryImpl query,
                                 QueryAtomGroupImpl group,
                                 QueryResultImpl result,
                                 QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> opCandidates = getObjectProperties();
            if (bindAndEvalObjectPropertyCandidates(query, group, result, binding, propArg, opCandidates)) {
                ret = true;
            }
            Set<OWLDataProperty> dpCandidates = getDataProperties();
            if (bindAndEvalDataPropertyCandidates(query, group, result, binding, propArg, dpCandidates)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalPropertyValue(QueryImpl query,
                                      QueryAtomGroupImpl group,
                                      QueryResultImpl result,
                                      QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subjectArg = arguments.get(0);
        QueryArgument propertyArg = arguments.get(1);
        QueryArgument valueArg = arguments.get(2);
        if (subjectArg.isVar()) {
            Set<OWLNamedIndividual> candidates = getIndividuals();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, subjectArg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        else if (propertyArg.isVar()) {
            boolean object = true, data = true;
            if (valueArg.isLiteral()) {
                object = false;
            }
            else if (valueArg.isURI()) {
                data = false;
            }

            if (object) {
                Set<OWLObjectProperty> candidates = getObjectProperties();
                ret = bindAndEvalObjectPropertyCandidates(query, group, result, binding, propertyArg, candidates);
            }

            if (data) {
                Set<OWLDataProperty> candidates = getDataProperties();
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, propertyArg, candidates)) {
                    ret = true;
                }
            }
        }
        else if (valueArg.isVar()) {
            OWLNamedIndividual ind0 = asIndividual(subjectArg);
            OWLObjectProperty op1 = asObjectProperty(propertyArg);
            OWLDataProperty dp1 = asDataProperty(propertyArg);
            if (isDeclared(op1)) {
                Set<OWLNamedIndividual> candidates = reasoner.getObjectPropertyValues(ind0, op1).getFlattened();
                ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, valueArg, candidates,
                                                           BoundChecking.CHECK_BOUND);
            }
            else if (isDeclared(dp1)) {
                Set<OWLLiteral> candidates = reasoner.getDataPropertyValues(ind0, dp1);
                for (OWLLiteral c : candidates) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(valueArg, QueryArgument.newLiteral(c));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean evalObjectProperty(QueryImpl query,
                                       QueryAtomGroupImpl group,
                                       QueryResultImpl result,
                                       QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        QueryArgument propArg = atom.getArguments().get(0);
        if (propArg.isVar()) {
            Set<OWLObjectProperty> candidates = getObjectProperties();
            ret = bindAndEvalObjectPropertyCandidates(query, group, result, binding, propArg, candidates);
        }
        return ret;
    }

    private boolean evalDifferentIndividuals(QueryImpl query,
                                             QueryAtomGroupImpl group,
                                             QueryResultImpl result,
                                             QueryBindingImpl binding,
                                             QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument ind0Arg = arguments.get(0);
        QueryArgument ind1Arg = arguments.get(1);
        if (ind0Arg.isVar() && ind1Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = getIndividuals();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind0Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        else if (ind0Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = reasoner.getDifferentIndividuals(asIndividual(ind1Arg))
                                                         .getFlattened();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind0Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        else if (ind1Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = reasoner.getDifferentIndividuals(asIndividual(ind0Arg))
                                                         .getFlattened();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind1Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        return ret;
    }

    private boolean evalSameAs(QueryImpl query,
                               QueryAtomGroupImpl group,
                               QueryResultImpl result,
                               QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        QueryArgument ind0Arg = atom.getArguments().get(0);
        QueryArgument ind1Arg = atom.getArguments().get(1);
        if (ind0Arg.isVar() && ind1Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = getIndividuals();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind0Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        else if (ind0Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = reasoner.getSameIndividuals(asIndividual(ind1Arg)).getEntities();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind0Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        else if (ind1Arg.isVar()) {
            Set<OWLNamedIndividual> candidates = reasoner.getSameIndividuals(asIndividual(ind0Arg)).getEntities();
            ret = bindAndEvalNamedIndividualCandidates(query, group, result, binding, ind1Arg, candidates,
                                                       BoundChecking.CHECK_BOUND);
        }
        return ret;
    }

    private boolean evalType(QueryImpl query,
                             QueryAtomGroupImpl group,
                             QueryResultImpl result,
                             QueryBindingImpl binding, QueryAtom atom, boolean strict) {
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument indArg = arguments.get(0);
        QueryArgument typeArg = arguments.get(1);
        if (indArg.isVar() && typeArg.isVar()) {
            Set<OWLNamedIndividual> candidates = getIndividuals();
            return bindAndEvalNamedIndividualCandidates(query, group, result, binding, indArg, candidates,
                                                        BoundChecking.CHECK_BOUND);
        }
        else if (indArg.isVar()) {
            OWLClass type = asClass(typeArg);
            Set<OWLNamedIndividual> candidates = reasoner.getInstances(type, strict).getFlattened();
            return bindAndEvalNamedIndividualCandidates(query, group, result, binding, indArg, candidates,
                                                        BoundChecking.CHECK_BOUND);
        }
        else if (typeArg.isVar()) {
            Set<OWLClass> candidates = reasoner.getTypes(asIndividual(indArg), strict).getFlattened();
            return bindAndEvalClassCandidates(query, group, result, binding, typeArg, candidates, BoundChecking.CHECK_BOUND);
        }
        return false;
    }

    private boolean bindAndEvalNamedIndividualCandidates(QueryImpl query,
                                                         QueryAtomGroupImpl group,
                                                         QueryResultImpl result,
                                                         QueryBindingImpl binding,
                                                         QueryArgument indArg,
                                                         Set<OWLNamedIndividual> candidates,
                                                         BoundChecking checkBound) {
        boolean ret = false;
        for (OWLNamedIndividual c : candidates) {
            final QueryBindingImpl new_binding = binding.clone();
            new_binding.set(indArg, newURI(c.getIRI()));
            if (eval(query, group.bind(new_binding), result, new_binding, checkBound)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalDisjointWith(QueryImpl query,
                                     QueryAtomGroupImpl group,
                                     QueryResultImpl result,
                                     QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        QueryArgument cls0Arg = atom.getArguments().get(0);
        QueryArgument cls1Arg = atom.getArguments().get(1);
        if (cls0Arg.isVar() && cls1Arg.isVar()) {
            Set<OWLClass> candidates = getClasses();
            ret = bindAndEvalClassCandidates(query, group, result, binding, cls0Arg, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (cls0Arg.isVar()) {
            Set<OWLClass> candidates = reasoner.getDisjointClasses(asClass(cls1Arg)).getFlattened();
            ret = bindAndEvalClassCandidates(query, group, result, binding, cls0Arg, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (cls1Arg.isVar()) {
            Set<OWLClass> candidates = reasoner.getDisjointClasses(asClass(cls0Arg)).getFlattened();
            ret = bindAndEvalClassCandidates(query, group, result, binding, cls1Arg, candidates, BoundChecking.CHECK_BOUND);
        }
        return ret;
    }

    private boolean bindAndEvalClassCandidates(QueryImpl query,
                                               QueryAtomGroupImpl group,
                                               QueryResultImpl result,
                                               QueryBindingImpl binding,
                                               QueryArgument clsArg,
                                               Collection<OWLClass> candidates,
                                               BoundChecking checkBound) {
        boolean ret = false;
        for (OWLClass c : candidates) {
            final QueryBindingImpl new_binding = binding.clone();
            new_binding.set(clsArg, newURI(c.getIRI()));
            if (eval(query, group.bind(new_binding), result, new_binding, checkBound)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalComplementOf(QueryImpl query,
                                     QueryAtomGroupImpl group,
                                     QueryResultImpl result,
                                     QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument arg0 = arguments.get(0);
        QueryArgument arg1 = arguments.get(1);
        if (arg0.isVar() && arg1.isVar()) {
            Set<OWLClass> candidates = getClasses();
            ret = bindAndEvalClassCandidates(query, group, result, binding, arg0, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (arg0.isVar()) {
            Set<OWLClass> candidates = reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(
                    arg1))).getEntities();
            ret = bindAndEvalClassCandidates(query, group, result, binding, arg0, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (arg1.isVar()) {
            Set<OWLClass> candidates = reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(
                    arg0))).getEntities();
            ret = bindAndEvalClassCandidates(query, group, result, binding, arg1, candidates, BoundChecking.CHECK_BOUND);
        }
        return ret;
    }

    private boolean evalRange(QueryImpl query,
                              QueryAtomGroupImpl group,
                              QueryResultImpl result,
                              QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propArg = arguments.get(0);
        QueryArgument rngArg = arguments.get(1);
        if (propArg.isVar() && rngArg.isVar() || propArg.isVar()) {
            if (isDeclaredObjectProperty(propArg)) {
                ret = bindAndEvalObjectPropertyCandidates(query,
                                                          group,
                                                          result,
                                                          binding,
                                                          propArg,
                                                          getObjectProperties());
            }
            else if (isDeclaredDataProperty(propArg)) {
                if (bindAndEvalDataPropertyCandidates(query, group, result, binding, propArg, getDataProperties())) {
                    ret = true;
                }
            }
            else if (isDeclaredAnnotationProperty(propArg)) {
                if (bindAndEvalAnnotationPropertyCandidates(query, group, result, binding, propArg)) {
                    ret = true;
                }
            }
        }
        else if (rngArg.isVar()) {
            // Looking for ranges
            if (isDeclaredObjectProperty(propArg)) {
                OWLObjectProperty property = asObjectProperty(propArg);
                Set<OWLClass> candidates = reasoner.getObjectPropertyRanges(property, false).getFlattened();
                ret = bindAndEvalClassCandidates(query, group, result, binding, rngArg, candidates, BoundChecking.CHECK_BOUND);
            }
            else if (isDeclaredDataProperty(propArg)) {
                Set<OWLDatatype> candidates = reasoner.getRootOntology().getDatatypesInSignature();
                for (OWLDatatype c : candidates) {
                    final QueryBindingImpl new_binding = binding.clone();
                    new_binding.set(rngArg, newURI(c.getIRI()));
                    if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                        ret = true;
                    }
                }
            }
            else if (isDeclaredAnnotationProperty(propArg)) {
                ret = false;
            }
        }
        return ret;
    }

    private boolean bindAndEvalAnnotationPropertyCandidates(QueryImpl query,
                                                            QueryAtomGroupImpl group,
                                                            QueryResultImpl result,
                                                            QueryBindingImpl binding,
                                                            QueryArgument propArg) {
        boolean ret = false;
        for (OWLAnnotationProperty property : getAnnotationProperties()) {
            final QueryBindingImpl new_binding = binding.clone();
            new_binding.set(propArg, newURI(property.getIRI()));
            if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalDomain(QueryImpl query,
                               QueryAtomGroupImpl group,
                               QueryResultImpl result,
                               QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument propertyArg = arguments.get(0);
        QueryArgument domainArg = arguments.get(1);
        if (propertyArg.isVar() && domainArg.isVar() || propertyArg.isVar()) {
            if (isDeclaredObjectProperty(propertyArg)) {
                ret = bindAndEvalObjectPropertyCandidates(query,
                                                          group,
                                                          result,
                                                          binding,
                                                          propertyArg,
                                                          getObjectProperties());
            }
            else if (isDeclaredDataProperty(propertyArg)) {
                ret = bindAndEvalDataPropertyCandidates(query,
                                                        group,
                                                        result,
                                                        binding,
                                                        propertyArg,
                                                        getDataProperties());
            }
            else if (isDeclaredAnnotationProperty(propertyArg)) {
                ret = bindAndEvalAnnotationPropertyCandidates(query, group, result, binding, propertyArg);
            }
        }
        else if (domainArg.isVar()) {
            // Looking for domains
            if (isDeclaredObjectProperty(propertyArg)) {
                OWLObjectProperty property = asObjectProperty(propertyArg);
                Set<OWLClass> candidates = reasoner.getObjectPropertyDomains(property, false).getFlattened();
                ret = bindAndEvalClassCandidates(query, group, result, binding, domainArg, candidates,
                                                 BoundChecking.CHECK_BOUND);
            }
            else if (isDeclaredDataProperty(propertyArg)) {
                OWLDataProperty property = asDataProperty(propertyArg);
                Set<OWLClass> candidates = reasoner.getDataPropertyDomains(property, false).getFlattened();
                ret = bindAndEvalClassCandidates(query, group, result, binding, domainArg, candidates,
                                                 BoundChecking.CHECK_BOUND);
            }
            else if (isDeclaredAnnotationProperty(propertyArg)) {
                ret = false;
            }
        }
        return ret;
    }

    private boolean bindAndEvalDataPropertyCandidates(QueryImpl query,
                                                      QueryAtomGroupImpl group,
                                                      QueryResultImpl result,
                                                      QueryBindingImpl binding,
                                                      QueryArgument propertyArg,
                                                      Set<OWLDataProperty> candidates) {
        boolean ret = false;
        for (OWLDataProperty property : candidates) {
            final QueryBindingImpl new_binding = binding.clone();
            new_binding.set(propertyArg, newURI(property.getIRI()));
            if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                ret = true;
            }
        }
        return ret;
    }

    private boolean evalEquivalentClasses(QueryImpl query,
                                          QueryAtomGroupImpl group,
                                          QueryResultImpl result,
                                          QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument clsArg0 = arguments.get(0);
        QueryArgument clsArg1 = arguments.get(1);
        if (clsArg0.isVar() && clsArg1.isVar()) {
            Set<OWLClass> candidates = getClasses();
            ret = bindAndEvalClassCandidates(query, group, result, binding, clsArg0, candidates, BoundChecking.DO_NOT_CHECK_BOUND);
        }
        else if (clsArg0.isVar()) {
            Set<OWLClass> candidates = reasoner.getEquivalentClasses(asClass(clsArg1)).getEntities();
            ret = bindAndEvalClassCandidates(query, group, result, binding, clsArg0, candidates, BoundChecking.DO_NOT_CHECK_BOUND);
        }
        else if (clsArg1.isVar()) {
            Set<OWLClass> candidates = reasoner.getEquivalentClasses(asClass(clsArg0)).getEntities();
            ret = bindAndEvalClassCandidates(query, group, result, binding, clsArg0, candidates, BoundChecking.DO_NOT_CHECK_BOUND);
        }
        return ret;
    }

    private boolean evalDirectSubClassOf(QueryImpl query,
                                         QueryAtomGroupImpl group,
                                         QueryResultImpl result,
                                         QueryBindingImpl binding, QueryAtom atom) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subClsArg = arguments.get(0);
        QueryArgument superClsArg = arguments.get(1);
        if (subClsArg.isVar() && superClsArg.isVar()) {
            Set<OWLClass> candidates = getClasses();
            ret = bindAndEvalClassCandidates(query, group, result, binding, subClsArg, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (subClsArg.isVar()) {
            OWLClass superCls = asClass(superClsArg);
            Set<OWLClass> candidates = reasoner.getSubClasses(superCls, true).getFlattened();
            ret = bindAndEvalClassCandidates(query, group, result, binding, subClsArg, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (superClsArg.isVar()) {
            OWLClass subCls = asClass(subClsArg);
            Set<OWLClass> candidates = reasoner.getSuperClasses(subCls, true).getFlattened();
            ret = bindAndEvalClassCandidates(query, group, result, binding, superClsArg, candidates,
                                             BoundChecking.CHECK_BOUND);
        }
        return ret;
    }

    private enum SubClassOfMode {
        STRICT,
        NON_STRICT
    }

    private boolean evalSubClassOf(QueryImpl query,
                                   QueryAtomGroupImpl group,
                                   QueryResultImpl result,
                                   QueryBindingImpl binding, QueryAtom atom, SubClassOfMode mode) {
        boolean ret = false;
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subClsArg = arguments.get(0);
        QueryArgument superClsArg = arguments.get(1);
        QueryBindingImpl new_binding;
        if (subClsArg.isVar() && superClsArg.isVar()) {
            Set<OWLClass> candidates = getClasses();
            ret = bindAndEvalClassCandidates(query, group, result, binding, subClsArg, candidates, BoundChecking.CHECK_BOUND);
        }
        else if (subClsArg.isVar()) {
            // SubClassOf(?x C)
            OWLClass superCls = asClass(superClsArg);
            Set<OWLClass> candidates;
            if(superCls.isOWLThing()) {
                candidates = getClasses();
            }
            else {
                candidates = reasoner.getSubClasses(superCls, false).getFlattened();
            }
            // if not strict we also include all equivalent classIris
            if (mode == SubClassOfMode.NON_STRICT && !superCls.isOWLThing()) {
                candidates.addAll(reasoner.getEquivalentClasses(asClass(superClsArg)).getEntities());
            }
            // Standard reasoning task, so we don't need to check the bound again
            if(bindAndEvalClassCandidates(query, group, result, binding, subClsArg, candidates, BoundChecking.DO_NOT_CHECK_BOUND)) {
                ret = true;
            }
        }
        else if (superClsArg.isVar()) {
            // SubClassOf(C ?x)
            OWLClass class0 = asClass(subClsArg);
            Set<OWLClass> candidates = reasoner.getSuperClasses(class0, false).getFlattened();

            // if not strict we also include all equivalent classIris
            if (mode == SubClassOfMode.NON_STRICT) {
                candidates.addAll(reasoner.getEquivalentClasses(asClass(subClsArg)).getEntities());
            }
            for (OWLClass c : candidates) {
                new_binding = binding.clone();
                new_binding.set(superClsArg, newURI(c.getIRI()));
                if (eval(query, group.bind(new_binding), result, new_binding, BoundChecking.CHECK_BOUND)) {
                    ret = true;
                }
            }
        }
        return ret;
    }

    private boolean evalIndividual(QueryImpl query,
                                   QueryAtomGroupImpl group,
                                   QueryResultImpl result,
                                   QueryBindingImpl binding,
                                   QueryAtom atom) {
        QueryArgument indArg = atom.getArguments().get(0);
        if (!indArg.isVar()) {
            return false;
        }
        BoundChecking boundChecking = !binding.isBound(indArg) ? BoundChecking.DO_NOT_CHECK_BOUND : BoundChecking.CHECK_BOUND;
        Set<OWLNamedIndividual> candidates = getIndividuals();
        return bindAndEvalNamedIndividualCandidates(query, group, result, binding, indArg, candidates,
                                                    BoundChecking.CHECK_BOUND);
    }

    /**
     * Finds solutions to Class(?x)
     */
    private boolean evalClass(QueryImpl query,
                              QueryAtomGroupImpl group,
                              QueryResultImpl result,
                              QueryBindingImpl binding, QueryAtom atom) {
        QueryArgument clsArg = atom.getArguments().get(0);
        if (!clsArg.isVar()) {
            return false;
        }
        BoundChecking boundChecking;
        // Need to check this but I think that if the variable is not bound then there's no need to recheck solutions
        Set<OWLClass> candidates = getClasses();
        return bindAndEvalClassCandidates(query, group, result, binding, clsArg, candidates, BoundChecking.DO_NOT_CHECK_BOUND);
    }

    private boolean evalAnnotationAssertion(@Nonnull QueryImpl query,
                                            @Nonnull QueryAtomGroupImpl group,
                                            @Nonnull QueryResultImpl result,
                                            @Nonnull QueryBindingImpl binding,
                                            @Nonnull QueryAtom atom) {
        List<QueryArgument> arguments = atom.getArguments();
        QueryArgument subjectArg = arguments.get(0);
        QueryArgument propertyArg = arguments.get(1);
        QueryArgument valueArg = arguments.get(2);
        boolean ret = false;
        boolean subjectMatched = !subjectArg.isVar() || binding.isBound(subjectArg);
        boolean propertyMatched = !propertyArg.isVar() || binding.isBound(propertyArg);
        boolean valueMatched = !valueArg.isVar() || binding.isBound(valueArg);
        if (subjectMatched) {
            if (propertyMatched) {
                if (!valueMatched) {
                    // Given subject and property
                    // Fill in values
                    for (OWLAnnotationAssertionAxiom ax : getAnnotationAssertionAxiomsForBoundSubject(subjectArg)) {
                        if (isBoundToAnnotationAssertionProperty(propertyArg, ax)) {
                            QueryBindingImpl new_binding = binding.clone();
                            bindAnnotationAssertionValue(valueArg, ax, new_binding);
                            eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                            ret = true;
                        }
                    }
                }
                else {
                    System.out.println("Matched everything.... is it right?");
                    ret = eval(query, group.pop(), result, binding, BoundChecking.CHECK_BOUND);
                }
            }
            else {
                if (valueMatched) {
                    // Given subject and value
                    for (OWLAnnotationAssertionAxiom ax : getAnnotationAssertionAxiomsForBoundSubject(subjectArg)) {
                        // Check value is equal
                        OWLAnnotationValue value = getBoundAnnotationValue(valueArg);
                        // Match any property
                        if (ax.getValue().equals(value)) {
                            QueryBindingImpl new_binding = binding.clone();
                            bindAnnotationProperty(ax, propertyArg, new_binding);
                            eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                            ret = true;
                        }
                    }
                }
                else {
                    // Just given subject
                    for (OWLAnnotationAssertionAxiom ax : getAnnotationAssertionAxiomsForBoundSubject(subjectArg)) {
                        QueryBindingImpl new_binding = binding.clone();
                        bindAnnotationProperty(ax, propertyArg, new_binding);
                        bindAnnotationValue(ax, valueArg, new_binding);
                        eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                        ret = true;
                    }
                }
            }
        }
        else {
            if (propertyMatched) {
                if (valueMatched) {
                    // Given property and value
                    for (OWLAnnotationAssertionAxiom ax : unannotatedAxioms) {
                        if (ax.getProperty().equals(propertyArg.getValueAsIRI())) {
                            QueryBindingImpl new_binding = binding.clone();
                            OWLAnnotationValue value = getBoundAnnotationValue(valueArg);
                            if (ax.getValue().equals(value)) {
                                // Any subject
                                bindAnnotationSubject(ax, subjectArg, new_binding);
                                eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                                ret = true;
                            }
                        }
                    }
                }
                else {
                    // Given property
                    for (OWLAnnotationAssertionAxiom ax : unannotatedAxioms) {
                        if (isBoundToAnnotationAssertionProperty(propertyArg, ax)) {
                            // Any subject, Any value
                            QueryBindingImpl new_binding = binding.clone();
                            bindAnnotationSubject(ax, subjectArg, new_binding);
                            bindAnnotationAssertionValue(valueArg, ax, new_binding);
                            eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                            ret = true;
                        }
                    }
                }
            }
            else {
                if (valueMatched) {
                    // Annotation assertions with the specified value count
                    for (OWLAnnotationAssertionAxiom ax : unannotatedAxioms) {
                        QueryBindingImpl new_binding = binding.clone();
                        OWLAnnotationValue value = getBoundAnnotationValue(valueArg);
                        if (ax.getValue().equals(value)) {
                            bindAnnotationSubject(ax, subjectArg, new_binding);
                            bindAnnotationProperty(ax, propertyArg, new_binding);
                            eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                            ret = true;
                        }
                    }
                }
                else {
                    // Nothing matched - every annotation assertion counts
                    for (OWLAnnotationAssertionAxiom ax : unannotatedAxioms) {
                        QueryBindingImpl new_binding = binding.clone();
                        bindAnnotationSubject(ax, subjectArg, new_binding);
                        bindAnnotationProperty(ax, propertyArg, new_binding);
                        bindAnnotationValue(ax, valueArg, new_binding);
                        eval(query, group.bind(new_binding), result, new_binding, BoundChecking.DO_NOT_CHECK_BOUND);
                        ret = true;
                    }
                }
            }
        }
        return ret;
    }

    private boolean isBoundToAnnotationAssertionProperty(QueryArgument propertyArg, OWLAnnotationAssertionAxiom ax) {
        return ax.getProperty().getIRI().equals(propertyArg.getValueAsIRI());
    }

    private Collection<OWLAnnotationAssertionAxiom> getAnnotationAssertionAxiomsForBoundSubject(QueryArgument subjectArg) {
        return annotationAssertionsBySubject.get(subjectArg.getValueAsIRI());
    }

    @Nullable
    private OWLAnnotationValue getBoundAnnotationValue(@Nonnull QueryArgument valueArg) {
        OWLAnnotationValue value = null;
        if (valueArg.isURI()) {
            value = valueArg.getValueAsIRI();
        }
        else if (valueArg.isLiteral()) {
            value = valueArg.getValueAsLiteral();
        }
        return value;
    }

    private void bindAnnotationSubject(OWLAnnotationAssertionAxiom ax,
                                       QueryArgument subjectArg,
                                       QueryBindingImpl new_binding) {
        new_binding.set(subjectArg, newURI((IRI) ax.getSubject()));
    }

    private void bindAnnotationProperty(OWLAnnotationAssertionAxiom ax,
                                        QueryArgument propertyArg,
                                        QueryBindingImpl new_binding) {
        new_binding.set(propertyArg, newURI(ax.getProperty().getIRI()));
    }

    private void bindAnnotationValue(OWLAnnotationAssertionAxiom ax,
                                     QueryArgument valueArg,
                                     QueryBindingImpl new_binding) {
        if (ax.getValue() instanceof IRI) {
            new_binding.set(valueArg, newURI((IRI) ax.getValue()));
        }
        else if (ax.getValue() instanceof OWLLiteral) {
            new_binding.set(valueArg, newLiteral((OWLLiteral) ax.getValue()));
        }
    }

    private void bindAnnotationAssertionValue(QueryArgument valueArg,
                                              OWLAnnotationAssertionAxiom ax,
                                              QueryBindingImpl new_binding) {
        if (ax.getValue() instanceof IRI) {
            new_binding.set(valueArg, newURI((IRI) ax.getValue()));
        }
        else if (ax.getValue() instanceof OWLLiteral) {
            new_binding.set(valueArg, newLiteral((OWLLiteral) ax.getValue()));
        }
    }

    private Set<OWLOntology> getImportsClosure() {
        OWLOntology rootOntology = reasoner.getRootOntology();
        return rootOntology.getOWLOntologyManager().getImportsClosure(rootOntology);
    }

    private boolean checkArgs(QueryAtom atom)
            throws QueryEngineException {
        List<QueryArgument> args = atom.getArguments();
        QueryArgument arg0, arg1, arg2;

        switch (atom.getType()) {
            case CLASS:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Class().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException("Given entity in atom Class() is not a class.");
                }
                return true;
            case INDIVIDUAL:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Individual().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException("Given entity in atom Individual() is not an individual.");
                }
                return true;
            case TYPE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom Type().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException("Given entity in first argument of atom Type() is not an individual.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom Type().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException("Given entity in second argument of atom Type() is not a class.");
                }
                return true;
            case DIRECT_TYPE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom DirectType().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DirectType() is not an individual.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom DirectType().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom DirectType() is not a class.");
                }
                return true;
            case PROPERTY_VALUE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                arg2 = args.get(2);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom PropertyValue().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom PropertyValue() is not an individual.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom PropertyValue().");
                }
                if (arg1.isURI()) {
                    if (isDeclaredDataProperty(arg1)) {
                        if (!arg2.isLiteral() && !arg2.isVar()) {
                            throw new QueryEngineException(
                                    "Expected literal or variable in third argument of atom PropertyValue().");
                        }
                    }
                    else if (isDeclaredObjectProperty(arg1)) {
                        if (!arg2.isURI() && !arg2.isVar()) {
                            throw new QueryEngineException(
                                    "Expected URI or variable in third argument of atom PropertyValue().");
                        }
                        if (arg2.isURI() && !isDeclaredIndividual(arg2)) {
                            throw new QueryEngineException(
                                    "Given entity in third argument of atom PropertyValue() is not an individual.");
                        }
                    }
                    else {
                        throw new QueryEngineException(
                                "Given entity in second argument of atom PropertyValue() is not a property.");
                    }
                }
                return true;
            case SAME_AS:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom SameAs().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom SameAs() is not an individual.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom SameAs().");
                }
                if (arg1.isURI() && !isDeclaredIndividual(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom SameAs() is not an individual.");
                }
                return true;
            case DIFFERENT_FROM:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom DifferentFrom().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DifferentFrom() is not an individual.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom DifferentFrom().");
                }
                if (arg1.isURI() && !isDeclaredIndividual(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DifferentFrom() is not an individual.");
                }
                return true;
            case SUB_CLASS_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom SubClassOf().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException("Given entity in first argument of atom SubClassOf() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom SubClassOf().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom SubClassOf() is not a class.");
                }
                return true;
            case DIRECT_SUB_CLASS_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom DirectSubClassOf().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DirectSubClassOf() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom DirectSubClassOf().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom DirectSubClassOf() is not a class.");
                }
                return true;
            case STRICT_SUB_CLASS_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom StrictSubClassOf().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom StrictSubClassOf() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom StrictSubClassOf().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom StrictSubClassOf() is not a class.");
                }
                return true;
            case EQUIVALENT_CLASS:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom EquivalentClass().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom EquivalentClass() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom EquivalentClass().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom EquivalentClass() is not a class.");
                }
                return true;
            case DISJOINT_WITH:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom DisjointWith().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DisjointWith() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom DisjointWith().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom DisjointWith() is not a class.");
                }
                return true;
            case COMPLEMENT_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom ComplementOf().");
                }
                if (arg0.isURI() && !isDeclaredClass(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom ComplementOf() is not a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom ComplementOf().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom ComplementOf() is not a class.");
                }
                return true;
            case SUB_PROPERTY_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom SubPropertyOf().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom SubPropertyOf() is not a property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom SubPropertyOf().");
                }
                if (arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom SubPropertyOf() is not a property.");
                }
                return true;
            case STRICT_SUB_PROPERTY_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom StrictSubPropertyOf().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom StrictSubPropertyOf() is not a property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom StrictSubPropertyOf().");
                }
                if (arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom StrictSubPropertyOf() is not a property.");
                }
                return true;
            case DIRECT_SUB_PROPERTY_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom DirectSubPropertyOf().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom DirectSubPropertyOf() is not a property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom DirectSubPropertyOf().");
                }
                if (arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom DirectSubPropertyOf() is not a property.");
                }
                return true;
            case EQUIVALENT_PROPERTY:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in first argument of atom EquivalentProperty().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom EquivalentProperty() is not a property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException(
                            "Expected URI or variable in second argument of atom EquivalentProperty().");
                }
                if (arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom EquivalentProperty() is not a property.");
                }
                return true;
            case INVERSE_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom InverseOf().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom InverseOf() is not a property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom InverseOf().");
                }
                if (arg1.isURI() && !isDeclaredDataProperty(arg1) && !isDeclaredObjectProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom InverseOf() is not a property.");
                }
                return true;
            case OBJECT_PROPERTY:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom ObjectProperty().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom ObjectProperty() is not an object property.");
                }
                return true;
            case INVERSE_FUNCTIONAL:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom InverseFunctional().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom InverseFunctional() is not an object property.");
                }
                return true;
            case SYMMETRIC:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Symmetric().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Symmetric() is not an object property.");
                }
                return true;
            case TRANSITIVE:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Transitive().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Transitive() is not an object property.");
                }
                return true;
            case REFLEXIVE:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Reflexive().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Reflexive() is not an object property.");
                }
                return true;
            case IRREFLEXIVE:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Irreflexive().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Irreflexive() is not an object property.");
                }
                return true;
            case DATA_PROPERTY:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom DataProperty().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom DataProperty() is not a data property.");
                }
                return true;
            case ANNOTATION_PROPERTY:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI of variable in atom AnnotationProperty().");
                }
                if (arg0.isURI() && !isDeclaredAnnotationProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in atom AnnotationProperty() is not an annotation property");
                }
                return true;
            case PROPERTY:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Property().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Property() is not a property.");
                }
                return true;
            case FUNCTIONAL:
                arg0 = args.get(0);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in atom Functional().");
                }
                if (arg0.isURI() && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(arg0)) {
                    throw new QueryEngineException("Given entity in atom Functional() is not a property.");
                }
                return true;
            case ANNOTATION:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom Annotation().");
                }
                if (arg0.isURI() && !isDeclaredIndividual(arg0) && !isDeclaredDataProperty(arg0) && !isDeclaredObjectProperty(
                        arg0) && !isDeclaredClass(arg0) && !isDeclaredAnnotationProperty(arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom Annotation() is not an individual, nor a data property, nor an object property, nor a class.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom Annotation().");
                }
                if (arg1.isURI() && !isDeclaredAnnotationProperty(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom Annotation() is not an annotation property.");
                }
                return true;
            case DOMAIN:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom Domain().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0) && !isDeclaredDataProperty(arg0) && !isDeclaredAnnotationProperty(
                        arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom Domain() is not an object, data or annotation property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom Domain().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1)) {
                    throw new QueryEngineException("Given entity in second argument of atom Domain() is not a class.");
                }
                return true;
            case RANGE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                if (!arg0.isURI() && !arg0.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in first argument of atom Range().");
                }
                if (arg0.isURI() && !isDeclaredObjectProperty(arg0) && !isDeclaredDataProperty(arg0) && !isDeclaredAnnotationProperty(
                        arg0)) {
                    throw new QueryEngineException(
                            "Given entity in first argument of atom Range() is not an object, data or annotation property.");
                }
                if (!arg1.isURI() && !arg1.isVar()) {
                    throw new QueryEngineException("Expected URI or variable in second argument of atom Range().");
                }
                if (arg1.isURI() && !isDeclaredClass(arg1) && !isDeclaredDatatype(arg1)) {
                    throw new QueryEngineException(
                            "Given entity in second argument of atom Range() is not a class or datatype.");
                }
                return true;
            default:
                return false;
        }
    }

    /**
     * Determines if the specified binding is actually entailed by the ontology
     *
     * @param atom The atom to check.
     * @return true if the binding is entailed by the ontology, otherwise false
     */
    private boolean checkBound(@Nonnull QueryAtom atom) {
        List<QueryArgument> args = atom.getArguments();

        QueryArgument arg0, arg1, arg2;

        switch (atom.getType()) {
            case TYPE: {
                arg0 = args.get(0);
                arg1 = args.get(1);
                OWLNamedIndividual ind = asIndividual(arg0);
                OWLClass cls = asClass(arg1);
                OWLClassAssertionAxiom ax = factory.getOWLClassAssertionAxiom(cls, ind);
                return reasoner.getRootOntology()
                               .containsAxiom(ax,
                                              Imports.INCLUDED,
                                              AxiomAnnotations.CONSIDER_AXIOM_ANNOTATIONS)
                        || reasoner.isEntailed(factory.getOWLClassAssertionAxiom(cls, ind));
            }
            case DIRECT_TYPE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                return reasoner.getTypes(asIndividual(arg0), true).containsEntity(asClass(arg1));
            case PROPERTY_VALUE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                arg2 = args.get(2);
                if (arg2.isURI()) {
                    OWLNamedIndividual subject = asIndividual(arg0);
                    OWLObjectProperty property = asObjectProperty(arg1);
                    OWLNamedIndividual object = asIndividual(arg2);
                    OWLObjectPropertyAssertionAxiom ax = factory.getOWLObjectPropertyAssertionAxiom(property,
                                                                                                    subject,
                                                                                                    object);
                    return reasoner.getRootOntology().containsAxiom(ax)
                            || reasoner.getObjectPropertyValues(subject, property).containsEntity(object);
                }
                else if (arg2.isLiteral()) {
                    OWLNamedIndividual subject = asIndividual(arg0);
                    OWLDataProperty property = asDataProperty(arg1);
                    OWLLiteral object = asLiteral(arg2);
                    OWLDataPropertyAssertionAxiom ax = factory.getOWLDataPropertyAssertionAxiom(property,
                                                                                                subject,
                                                                                                object);
                    if (reasoner.getRootOntology().containsAxiom(ax)) {
                        return true;
                    }
                    // Not sure about this
                    for (OWLLiteral l : reasoner.getDataPropertyValues(subject, property)) {
                        if (l.getLiteral().equals(asLiteral(arg2).getLiteral())) {
                            return true;
                        }
                    }
                    return false;
                }
                return false;
            case SUB_CLASS_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);
                OWLClass subCls = asClass(arg0);
                if (subCls.isOWLNothing()) {
                    return true;
                }
                OWLClass superCls = asClass(arg1);
                return superCls.isOWLThing()
                        || reasoner.isEntailed(factory.getOWLSubClassOfAxiom(subCls, superCls));
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
                if (isDeclared(functional_op)) {
                    return reasoner.isEntailed(factory.getOWLFunctionalObjectPropertyAxiom(functional_op));
                }
                else if (isDeclared(functional_dp)) {
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

                if (isDeclared(sub_prop_op0)) {
                    return reasoner.getSubObjectProperties(asObjectProperty(arg1), false)
                                   .containsEntity(sub_prop_op0) ||
                            reasoner.getEquivalentObjectProperties(asObjectProperty(arg1)).contains(sub_prop_op0);
                }
                else if (isDeclared(sub_prop_dp0)) {
                    return reasoner.getSubDataProperties(asDataProperty(arg1), false).containsEntity(sub_prop_dp0) ||
                            reasoner.getEquivalentDataProperties(asDataProperty(arg1)).contains(sub_prop_dp0);
                }
                return false;
            case STRICT_SUB_PROPERTY_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);

                OWLObjectProperty strict_sub_prop_op0 = asObjectProperty(arg0);
                OWLDataProperty strict_sub_prop_dp0 = asDataProperty(arg0);

                if (isDeclared(strict_sub_prop_op0)) {
                    return reasoner.getSubObjectProperties(asObjectProperty(arg1), false)
                                   .containsEntity(strict_sub_prop_op0);
                }
                else if (isDeclared(strict_sub_prop_dp0)) {
                    return reasoner.getSubDataProperties(asDataProperty(arg1), false)
                                   .containsEntity(strict_sub_prop_dp0);
                }
                return false;
            case DIRECT_SUB_PROPERTY_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);

                OWLObjectProperty direct_sub_prop_op0 = asObjectProperty(arg0);
                OWLDataProperty direct_sub_prop_dp0 = asDataProperty(arg0);

                if (isDeclared(direct_sub_prop_op0)) {
                    return reasoner.getSubObjectProperties(asObjectProperty(arg1), true)
                                   .containsEntity(direct_sub_prop_op0);
                }
                else if (isDeclared(direct_sub_prop_dp0)) {
                    return reasoner.getSubDataProperties(asDataProperty(arg1), true)
                                   .containsEntity(direct_sub_prop_dp0);
                }
                return false;
            case INVERSE_OF:
                arg0 = args.get(0);
                arg1 = args.get(1);

                OWLObjectProperty inv_prop_op0 = asObjectProperty(arg0);

                return isDeclared(inv_prop_op0) && reasoner.getInverseObjectProperties(inv_prop_op0)
                                                           .contains(asObjectProperty(arg1));
            case EQUIVALENT_PROPERTY:
                arg0 = args.get(0);
                arg1 = args.get(1);

                OWLObjectProperty equiv_prop_op0 = asObjectProperty(arg0);
                OWLDataProperty equiv_prop_dp0 = asDataProperty(arg0);

                if (isDeclared(equiv_prop_op0)) {
                    return reasoner.getEquivalentObjectProperties(equiv_prop_op0).contains(asObjectProperty(arg1));
                }
                else if (isDeclared(equiv_prop_dp0)) {
                    return reasoner.getEquivalentDataProperties(equiv_prop_dp0).contains(asDataProperty(arg1));
                }
                return false;
            case DOMAIN:
                arg0 = args.get(0);
                arg1 = args.get(1);
                OWLObjectProperty op_0 = asObjectProperty(arg0);
                OWLDataProperty dp_0 = asDataProperty(arg0);
                OWLAnnotationProperty ap_0 = asAnnotationProperty(arg0);
                if (isDeclared(op_0)) {
                    return reasoner.getObjectPropertyDomains(op_0, false).containsEntity(asClass(arg1));
                }
                else if (isDeclared(dp_0)) {
                    return reasoner.getDataPropertyDomains(dp_0, false).containsEntity(asClass(arg1));
                }
                else if (isDeclared(ap_0)) {
                    if (!arg1.isURI()) {
                        return false;
                    }
                    Set<OWLAnnotationPropertyDomainAxiom> axioms = reasoner.getRootOntology()
                                                                           .getAxioms(AxiomType.ANNOTATION_PROPERTY_DOMAIN,
                                                                                      Imports.INCLUDED);
                    for (OWLAnnotationPropertyDomainAxiom ax : axioms) {
                        if (ax.getProperty().equals(ap_0)) {
                            if (ax.getDomain().equals(arg1.getValueAsIRI())) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            case RANGE:
                arg0 = args.get(0);
                arg1 = args.get(1);
                OWLObjectProperty rng_op_0 = asObjectProperty(arg0);
                OWLDataProperty rng_dp_0 = asDataProperty(arg0);
                OWLAnnotationProperty rng_ap_0 = asAnnotationProperty(arg0);
                if (isDeclared(rng_op_0)) {
                    return reasoner.getObjectPropertyRanges(rng_op_0, false).containsEntity(asClass(arg1));
                }
                else if (isDeclared(rng_dp_0)) {
                    OWLDataPropertyRangeAxiom ax = manager.getOWLDataFactory()
                                                          .getOWLDataPropertyRangeAxiom(rng_dp_0, asDatatype(arg1));
                    return reasoner.isEntailed(ax);
                }
                else if (isDeclared(rng_ap_0)) {
                    if (!arg1.isURI()) {
                        return false;
                    }
                    Set<OWLAnnotationPropertyRangeAxiom> axioms = reasoner.getRootOntology()
                                                                          .getAxioms(AxiomType.ANNOTATION_PROPERTY_RANGE,
                                                                                     Imports.INCLUDED);
                    for (OWLAnnotationPropertyRangeAxiom ax : axioms) {
                        if (ax.getProperty().equals(rng_ap_0)) {
                            if (ax.getRange().equals(arg1.getValueAsIRI())) {
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

                return reasoner.getEquivalentClasses(factory.getOWLObjectComplementOf(asClass(arg0)))
                               .contains(asClass(arg1));
            case ANNOTATION:
                arg0 = args.get(0); // Subject
                arg1 = args.get(1); // Property
                arg2 = args.get(2); // Object
                OWLEntity anEntity = null;
                OWLAnnotationProperty anProp = asAnnotationProperty(arg1);
                final OWLAnnotationAssertionAxiom ax;
                if (arg2.getType() == QueryArgumentType.URI) {
                    ax = factory.getOWLAnnotationAssertionAxiom(anProp, arg0.getValueAsIRI(), arg2.getValueAsIRI());
                }
                else if (arg2.getType() == QueryArgumentType.BNODE) {
                    ax = factory.getOWLAnnotationAssertionAxiom(anProp, arg0.getValueAsIRI(), arg2.getValueAsBNode());
                }
                else {
                    ax = factory.getOWLAnnotationAssertionAxiom(anProp, arg0.getValueAsIRI(), arg2.getValueAsLiteral());
                }
                return  unannotatedAxioms.contains(ax);
//                if (unannotatedAxioms.contains(ax)) {
//                    return true;
//                }
//
//                if (isDeclaredIndividual(arg0)) {
//                    anEntity = asIndividual(arg0);
//                }
//                else if (isDeclaredDataProperty(arg0)) {
//                    anEntity = asDataProperty(arg0);
//                }
//                else if (isDeclaredObjectProperty(arg0)) {
//                    anEntity = asObjectProperty(arg0);
//                }
//                else if (isDeclaredClass(arg0)) {
//                    anEntity = asClass(arg0);
//                }
//
//                if (anEntity == null) {
//                    return false;
//                }
//
//                Set<OWLAnnotation> annotations = new HashSet<>();
//                for (OWLOntology o : getImportsClosure()) {
//                    annotations.addAll(EntitySearcher.getAnnotations(anEntity, o, anProp));
//                }
//
//                if (arg2.isURI()) {
//                    for (OWLAnnotation a : annotations) {
//                        if (a.getValue() instanceof IRI) {
//                            IRI i = (IRI) a.getValue();
//                            if (i.equals(arg2.getValueAsIRI())) {
//                                return true;
//                            }
//                        }
//                    }
//                }
//                else if (arg2.isLiteral()) {
//                    for (OWLAnnotation a : annotations) {
//                        if (a.getValue() instanceof OWLLiteral) {
//                            OWLLiteral l = (OWLLiteral) a.getValue();
//                            if (l.equals(arg2.getValueAsLiteral())) {
//                                return true;
//                            }
//                        }
//                    }
//                }
//                return false;
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
                throw new RuntimeException("Unsupported or unknown atom type.");
        }
    }

    private float estimateCost(QueryAtom atom) {
        List<QueryArgument> args = atom.getArguments();

        switch (atom.getType()) {
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
            case DOMAIN:
            case RANGE:
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
                for (QueryArgument arg : args) {
                    if (arg.isVar()) {
                        cost += 1f;
                    }
                }
                return cost;
            default:
                return 1e25f;
        }
    }

    private QueryAtomGroupImpl preorder(QueryAtomGroupImpl group) {
        List<QueryAtom> atoms = new LinkedList<>();
        atoms.addAll(group.getAtoms());
        Collections.sort(atoms, (a1, a2) -> {
            float c1 = QueryEngineImpl.this.estimateCost(a1);
            float c2 = QueryEngineImpl.this.estimateCost(a2);

            if (c1 == c2) {
                return 0;
            }
            return (c1 < c2) ? -1 : +1;
        });

        QueryAtomGroupImpl ret = new QueryAtomGroupImpl();
        atoms.forEach(ret::addAtom);
        return ret;
    }

    private OWLClass asClass(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLClass(arg.getValueAsIRI());
    }

    private OWLObjectProperty asObjectProperty(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLObjectProperty(arg.getValueAsIRI());
    }

    private OWLDataProperty asDataProperty(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLDataProperty(arg.getValueAsIRI());
    }

    private OWLDatatype asDatatype(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLDatatype(arg.getValueAsIRI());
    }


    private OWLNamedIndividual asIndividual(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLNamedIndividual(arg.getValueAsIRI());
    }

    private OWLLiteral asLiteral(QueryArgument arg) {
        return arg.getValueAsLiteral();
    }

    private OWLAnnotationProperty asAnnotationProperty(QueryArgument arg) {
        return manager.getOWLDataFactory().getOWLAnnotationProperty(arg.getValueAsIRI());
    }

    private Set<OWLClass> getClasses() {
        return classes;
    }

    private Set<OWLNamedIndividual> getIndividuals() {
        return reasoner.getRootOntology().getIndividualsInSignature(Imports.INCLUDED);
    }

    private Set<OWLObjectProperty> getObjectProperties() {
        return reasoner.getRootOntology().getObjectPropertiesInSignature(Imports.INCLUDED);
    }

    private Set<OWLDataProperty> getDataProperties() {
        return reasoner.getRootOntology().getDataPropertiesInSignature(Imports.INCLUDED);
    }

    private Set<OWLAnnotationProperty> getAnnotationProperties() {
        if (!cachedAnnotationProperties.isEmpty()) {
            return cachedAnnotationProperties;
        }
        for (OWLOntology o : getImportsClosure()) {
            cachedAnnotationProperties.addAll(o.getAnnotationPropertiesInSignature());
        }
        return cachedAnnotationProperties;
    }

    private boolean isDeclaredIndividual(QueryArgument arg) {
        return isDeclared(asIndividual(arg));
    }

    private boolean isDeclaredClass(QueryArgument arg) {
        return classIris.contains(arg.getValueAsIRI());
    }

    private boolean isDeclaredObjectProperty(QueryArgument arg) {
        return isDeclared(asObjectProperty(arg));
    }

    private boolean isDeclaredDataProperty(QueryArgument arg) {
        return isDeclared(asDataProperty(arg));
    }

    private boolean isDeclaredAnnotationProperty(QueryArgument arg) {
        return isDeclared(asAnnotationProperty(arg));
    }

    private boolean isDeclaredDatatype(QueryArgument arg) {
        return isDeclared(asDatatype(arg));
    }

    private boolean isDeclared(OWLNamedIndividual i) {
        return i.isBuiltIn() || reasoner.getRootOntology().containsIndividualInSignature(i.getIRI(), Imports.INCLUDED);
    }


    private boolean isDeclared(OWLObjectProperty p) {
        return p.isBuiltIn() || reasoner.getRootOntology().containsObjectPropertyInSignature(p.getIRI(), Imports.INCLUDED);
    }

    private boolean isDeclared(OWLDataProperty p) {
        return p.isBuiltIn() || reasoner.getRootOntology().containsDataPropertyInSignature(p.getIRI(), Imports.INCLUDED);
    }

    private boolean isDeclared(OWLAnnotationProperty p) {
        return p.isBuiltIn() || reasoner.getRootOntology().containsAnnotationPropertyInSignature(p.getIRI(), Imports.INCLUDED);
    }

    private boolean isDeclared(OWLDatatype d) {
        return d.isBuiltIn() || reasoner.getRootOntology().containsDatatypeInSignature(d.getIRI(), Imports.INCLUDED);
    }
}
