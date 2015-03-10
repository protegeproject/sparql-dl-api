// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.Iterator;

import org.jdom.Document;

/**
 * QueryResult contains the result set of an executed query
 * with all bindings. This class also provides some methods 
 * to export the result set in different formats like SPARQL-XML or JSON.
 * 
 * @author Mario Volke
 */
public interface QueryResult extends Iterable<QueryBinding> 
{
	/**
	 * Get the query that belongs to this result.
	 * 
	 * @return
	 */
	public Query getQuery();
	
	/**
	 * An iterator over the result set.
	 * 
	 * @return
	 */
	public Iterator<QueryBinding> iterator();
	
	/**
	 * Returns the QueryBinding at the specified position of the result.
	 *  
	 * @param index
	 * @return The QueryBinding at the specified position. 
	 */
	public QueryBinding get(int index);
	
	/**
	 * Get the size of the result set.
	 * 
	 * @return The size of the result set.
	 */
	public int size();
	
	/**
	 * Check whether the result set is empty.
	 * 
	 * @return True if the result set is empty.
	 */
	public boolean isEmpty();
	
	/**
	 * Ask if the query had a solution.
	 * This is the only result you get if the query was of type ASK.
	 * This could also be true if the result set is empty.
	 * 
	 * @return True if the query had a solution.
	 */
	public boolean ask();
	
	/**
	 * Output query results as JDOM XML document containing the standard 
	 * SPARQL query results XML format (http://www.w3.org/TR/rdf-sparql-XMLres/).
	 * Supports both: Variable binding results and Boolean results.
	 * 
	 * @return A JDOM XML document.
	 */
	public Document toXML();
	
	/**
	 * Output query results in JSON format as standardized in http://www.w3.org/TR/rdf-sparql-json-res/.
	 * Supports both: Variable binding results and Boolean results.
	 * 
	 * @return The JSON result as string.
	 */
	public String toJSON();
	
	/**
	 * Use this method for debugging purposes.
	 * This is no standard format like SPARQL-XML or JSON.
	 *
	 * @return A nicely formatted string containing the results and bindings.
	 */
	public String toString();
}
