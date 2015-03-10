// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * Concrete implementation of the abstract Query class.
 * 
 * @author Mario Volke
 */
public class QueryImpl extends Query
{
	private QueryType type;
	private List<QueryAtomGroup> groups;
	private Set<QueryArgument> resultVars;
	
	public QueryImpl(QueryType type)
	{
		this.type = type;
		groups = new ArrayList<QueryAtomGroup>();
		resultVars = new HashSet<QueryArgument>();
	}
	
	/**
	 * Get the type of the query.
	 * 
	 * @return
	 */
	public QueryType getType()
	{
		return type;
	}
	
	/**
	 * Add a result variable to the query.
	 * 
	 * @param arg QueryArgument has to be a variable.
	 */
	public void addResultVar(QueryArgument arg)
	{
		if(arg.getType() == QueryArgumentType.VAR) {
			resultVars.add(arg);
		}
	}
	
	/**
	 * Remove a result variable from the query.
	 * 
	 * @param atom
	 * @return
	 */
	public boolean removeResultVar(QueryArgument arg)
	{
		return resultVars.remove(arg);
	}
	
	/**
	 * Check whether the given query argument is a result variable.
	 * 
	 * @return True if the query argument is a result variable, false otherwise.
	 */
	public boolean isResultVar(QueryArgument arg)
	{
		return resultVars.contains(arg);
	}
	
	/**
	 * Get the number of result variables.
	 * 
	 * @return
	 */
	public int numResultVars()
	{
		return resultVars.size();
	}
	
	/**
	 * Add an atom group to the query.
	 * This means union in a logical sense.
	 * 
	 * @param group
	 */
	public void addAtomGroup(QueryAtomGroup group)
	{
		groups.add(group);
	}
	
	/**
	 * Remove an atom group from the query.
	 * 
	 * @param group
	 * @return
	 */
	public boolean removeAtomGroup(QueryAtomGroup group)
	{
		return groups.remove(group);
	}
	
	/**
	 * Check whether there are any groups in the query.
	 * 
	 * @return True if there are no atoms at all.
	 */
	public boolean isEmpty()
	{
		return groups.isEmpty();
	}
	
	/**
	 * Get the next group to process.
	 * 
	 * @return
	 */
	public QueryAtomGroup nextAtomGroup()
	{
		if(isEmpty()) {
			return null;
		}
		return groups.get(0);
	}
	
	/**
	 * Get an unodifiable list of all query atom groups.
	 * 
	 * @return
	 */
	public List<QueryAtomGroup> getAtomGroups()
	{
		return Collections.unmodifiableList(groups);
	}
	
	/**
	 * Get an unodifiable set of all result variables.
	 * 
	 * @return
	 */
	public Set<QueryArgument> getResultVars()
	{
		return Collections.unmodifiableSet(resultVars);
	}
	
	/**
	 * Check whether the query is of type ASK
	 * 
	 * @return 
	 */
	public boolean isAsk()
	{
		return type == QueryType.ASK;
	}
	
	/**
	 * Check whether the query is of type SELECT.
	 * 
	 * @return
	 */
	public boolean isSelect()
	{
		return type == QueryType.SELECT;
	}
	
	/**
	 * Check whether the query is of type SELECT DISTINCT.
	 * 
	 * @return
	 */
	public boolean isSelectDistinct()
	{
		return type == QueryType.SELECT_DISTINCT;
	}
	
	/**
	 * Print the SPARQL-DL query as string.
	 * 
	 * @return String containing valid SPARQL-DL query.
	 */
	public String toString()
	{	
		StringBuffer sb = new StringBuffer();
		
		switch(type) {
		case ASK:
			sb.append("ASK");
			break;
		case SELECT:
			sb.append("SELECT");
			break;
		case SELECT_DISTINCT:
			sb.append("SELECT_DISTINCT");
			break;
		}
		
		for(QueryArgument arg : resultVars) {
			sb.append(' ');
			sb.append(arg.toString());
		}
		
		sb.append('\n');
		
		boolean first = true;
		for(QueryAtomGroup group : groups) {
			if(first) {
				first = false;
				sb.append("WHERE ");
			}
			else {
				sb.append("UNION ");
			}
			sb.append("{ ");
			sb.append(group);
			sb.append(" }\n");
		}
		return sb.toString();
	}
}
