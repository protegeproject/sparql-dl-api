// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.Set;

/**
 * A QueryBinding is one single entry in the result set of an executed query.
 * A binding consists of a mapping from variables to concrete values (e.g. URIs or literals).
 * 
 * @author Mario Volke
 */
public interface QueryBinding extends Cloneable
{	
	/**
	 * Get the binding of a query argument.
	 * 
	 * @param arg
	 * @return Null if argument is not bound, yet, or the binding otherwise.
	 */
	public QueryArgument get(QueryArgument arg);
	
	/**
	 * Get all bound arguments.
	 * 
	 * @return A set of bound arguments (contains usually only variables).
	 */
	public Set<QueryArgument> getBoundArgs();
	
	/**
	 * Check whether an argument is bound.
	 * 
	 * @param arg
	 * @return True if the argument is bound.
	 */
	public boolean isBound(QueryArgument arg);
	
	/**
	 * Get the number of bound arguments.
	 * 
	 * @return The number of bound arguments.
	 */
	public int size();
	
	/**
	 * Check whether there are any bindings.
	 * 
	 * @return True if there are no bindings at all.
	 */
	public boolean isEmpty();
}
