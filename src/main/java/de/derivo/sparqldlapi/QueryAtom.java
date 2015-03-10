// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.derivo.sparqldlapi.types.QueryAtomType;

/**
 * This class represents a query atom.
 * 
 * @author Mario Volke
 */
public class QueryAtom
{
	protected final QueryAtomType type;
	protected final List<QueryArgument> args;
	protected boolean bound;
	
	public QueryAtom(final QueryAtomType type, final QueryArgument ... args)
	{
		this(type, Arrays.asList(args));
	}
	
	public QueryAtom(final QueryAtomType type, final List<QueryArgument> args)
	{
		this.type = type;
		this.args = args;
		
		// check if atom is bound
		bound = true;
		for(QueryArgument arg : args) {
			if(arg.isVar() || arg.isBnode()) {
				bound = false;
				break;
			}
		}
	}
	
	/**
	 * Check whether the atom contains any variables or not.
	 * 
	 * @return True if there are no variables left.
	 */
	public boolean isBound()
	{
		return bound;
	}
	
	/**
	 * Get the exact type of the atom.
	 * 
	 * @return The query atom type.
	 */
	public QueryAtomType getType()
	{
		return type;
	}
	
	/**
	 * Check whether this atom has a concrete type.
	 * 
	 * @param type
	 * @return True if the atom has this type.
	 */
	public boolean hasType(QueryAtomType type)
	{
		return this.type == type;
	}
	
	/**
	 * Get the arguments of the atom.
	 * 
	 * @return
	 */
	public List<QueryArgument> getArguments()
	{
		return args;
	}
	
	/**
	 * A convenience method to clone the QueryAtom instance while 
	 * inserting a new binding.
	 * 
	 * @param binding
	 * @return
	 */
	public QueryAtom bind(QueryBinding binding) 
	{
		List<QueryArgument> args = new ArrayList<QueryArgument>();
		for(QueryArgument arg : this.args) {
			if(binding.isBound(arg)) {
				args.add(binding.get(arg));
			}
			else {
				args.add(arg);
			}
		}
		
		return new QueryAtom(type, args);
	}
	
	/**
	 * Get the atom as string.
	 * 
	 * @return String containing valid SPARQL-DL atom format.
	 */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append(type);
		sb.append('(');
		boolean first = true;
		for(QueryArgument arg : args) {
			if(first) {
				first = false;
			}
			else {
				sb.append(',');
				sb.append(' ');
			}
			sb.append(arg);
		}
		sb.append(')');
		return sb.toString();
	}

	@Override
	public boolean equals(Object obj) 
	{
		QueryAtom atom = (QueryAtom)obj;
		if(!type.equals(atom.type)) {
			return false;
		}
		
		if(!args.equals(atom.args)) {
			return false;
		}
		
		return true;
	}
	
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 31 * type.hashCode() + hash;
		hash = 31 * args.hashCode() + hash;
		return hash;
	}
}
