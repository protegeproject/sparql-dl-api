// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import de.derivo.sparqldlapi.QueryAtom;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.QueryBinding;

/**
 * Concrete implementation of the abstract Query class.
 * 
 * @author Mario Volke
 */
public class QueryAtomGroupImpl implements QueryAtomGroup
{
	private List<QueryAtom> atoms;
	
	public QueryAtomGroupImpl()
	{
		atoms = new LinkedList<QueryAtom>();
	}
	
	/**
	 * Add an atom to the group.
	 * This means intersection in a logical sense.
	 * 
	 * @param atom
	 */
	public void addAtom(QueryAtom atom)
	{
		atoms.add(atom);
	}
	
	/**
	 * Remove an atom from the group.
	 * 
	 * @param atom
	 * @return
	 */
	public boolean removeAtom(QueryAtom atom)
	{
		return atoms.remove(atom);
	}
	
	/**
	 * Check whether there are any atoms in the group.
	 * 
	 * @return True if there are no atoms at all.
	 */
	public boolean isEmpty()
	{
		return atoms.isEmpty();
	}
	
	/**
	 * Get the next atom to process.
	 * 
	 * @return
	 */
	public QueryAtom nextAtom()
	{
		if(isEmpty()) {
			return null;
		}
		return atoms.get(0);
	}
	
	/**
	 * Get an unodifiable list of all query atoms.
	 * 
	 * @return
	 */
	public List<QueryAtom> getAtoms()
	{
		return Collections.unmodifiableList(atoms);
	}
	
	/**
	 * A convenience method to clone the atom group instance and pop the first atom.
	 * Only the instance itself will be cloned not the atoms.
	 * 
	 * @return A new query instance with the first atom removed.
	 */
	public QueryAtomGroupImpl pop()
	{
		QueryAtomGroupImpl group = new QueryAtomGroupImpl();
		
		boolean first = true;
		for(QueryAtom atom : atoms) {
			if(first) {
				first = false;
			}
			else {
				group.addAtom(atom);
			}
		}
		return group;
	}
	
	/**
	 * A convenience method to clone the atom group instance while inserting a new binding
	 * to all atoms of the group.
	 * The instance and the atoms will be cloned.
	 * 
	 * @param binding
	 * @return
	 */
	public QueryAtomGroupImpl bind(QueryBinding binding) 
	{
		QueryAtomGroupImpl group = new QueryAtomGroupImpl();
		
		for(QueryAtom atom : atoms) {
			group.addAtom(atom.bind(binding));
		}
		return group;
	}
		
	/**
	 * Print the group as string.
	 * 
	 * @return a string.
	 */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		for(QueryAtom atom : atoms) {
			if(first) {
				first = false;
			}
			else {
				sb.append(',');
				sb.append(' ');
			}
			sb.append(atom);
		}
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj) 
	{
		QueryAtomGroupImpl group = (QueryAtomGroupImpl)obj;
		if(!atoms.equals(group.atoms)) {
			return false;
		}
		
		return true;
	}
	
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 31 * atoms.hashCode() + hash;
		return hash;
	}
}
