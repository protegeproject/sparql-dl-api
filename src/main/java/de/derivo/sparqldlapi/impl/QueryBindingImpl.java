// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryBinding;

/**
 * Concrete implementation of the QueryBinding interface.
 * 
 * @author mvolke
 */
public class QueryBindingImpl implements QueryBinding
{
	private Map<QueryArgument, QueryArgument> bindingMap = new HashMap<QueryArgument, QueryArgument>();
	
	public QueryBindingImpl()
	{}
	
	public QueryBindingImpl(Map<QueryArgument, QueryArgument> bindings)
	{
		bindingMap.putAll(bindings);
	}
	
	/**
	 * Get the binding of a query argument.
	 * 
	 * @param arg
	 * @return Null if argument is not bound, yet, or the binding otherwise.
	 */
	public QueryArgument get(QueryArgument arg)
	{
		return bindingMap.get(arg);
	}
	
	/**
	 * Set a binding.
	 * Usually a binding should map from a variable to an URI or literal.
	 * Already specified bindings will be overwritten.
	 * 
	 * @param arg
	 * @param binding
	 */
	public void set(QueryArgument arg, QueryArgument binding)
	{
		bindingMap.put(arg, binding);
	}
	
	/**
	 * Set multiple bindings at once.
	 * 
	 * @param bindings
	 */
	public void set(Map<QueryArgument, QueryArgument> bindings)
	{
		bindingMap.putAll(bindings);
	}
	
	/**
	 * Set all bindings that are already set in query binding b.
	 * 
	 * @param b
	 */
	public void set(QueryBindingImpl b)
	{
		bindingMap.putAll(b.bindingMap);
	}
	
	/**
	 * Get all bound arguments.
	 * 
	 * @return A set of bound arguments (contains usually only variables).
	 */
	public Set<QueryArgument> getBoundArgs()
	{
		return bindingMap.keySet();
	}
	
	/**
	 * Check whether an argument is bound.
	 * 
	 * @param arg
	 * @return True if the argument is bound.
	 */
	public boolean isBound(QueryArgument arg)
	{
		return bindingMap.containsKey(arg);
	}
	
	/**
	 * Get the number of bound arguments.
	 * 
	 * @return The number of bound arguments.
	 */
	public int size()
	{
		return bindingMap.size();
	}
	
	/**
	 * Check whether there are any bindings.
	 * 
	 * @return True if there are no bindings at all.
	 */
	public boolean isEmpty()
	{
		return bindingMap.isEmpty();
	}
	
	/**
	 * Clone this instance of QueryBinding.
	 * Only the QueryBinding class itself will be cloned,
	 * but not the query arguments.
	 * 
	 * @return
	 */
	public QueryBindingImpl clone()
	{
		return new QueryBindingImpl(bindingMap);
	}
	
	/**
	 * Clone this instance of QueryBinding and filter the binding map given by args.
	 * Only query arguments within the set of args will be available in the result.
	 * Only the QueryBinding class itself will be cloned,
	 * but not the query arguments.
	 * 
	 * @return
	 */
	public QueryBindingImpl cloneAndFilter(Set<QueryArgument> args)
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		for(QueryArgument arg : getBoundArgs()) {
			if(args.contains(arg)) {
				binding.set(arg, bindingMap.get(arg));
			}
		}
		return binding;
	}
	
	@Override
	public boolean equals(Object obj)
	{
		QueryBindingImpl arg = (QueryBindingImpl)obj;
		return bindingMap.equals(arg.bindingMap);
	}
	
	@Override
	public int hashCode()
	{
		return bindingMap.hashCode();
	}
}
