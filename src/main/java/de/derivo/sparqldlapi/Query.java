// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.List;
import java.util.Set;

import de.derivo.sparqldlapi.exceptions.QueryParserException;
import de.derivo.sparqldlapi.impl.QueryParserImpl;
import de.derivo.sparqldlapi.impl.QueryTokenizerImpl;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * The Query class represents a SPARQL-DL query
 * and mainly contains query atoms. 
 * 
 * @author Mario Volke
 */
public abstract class Query 
{
	/**
	 * Check whether there are any atoms in the query.
	 * 
	 * @return True if there are no atoms at all.
	 */
	abstract public boolean isEmpty();
	
	/**
	 * Get the type of the query.
	 * 
	 * @return
	 */
	abstract public QueryType getType();
	
	/**
	 * Check whether the given query argument is a result variable.
	 * 
	 * @return True if the query argument is a result variable, false otherwise.
	 */
	abstract public boolean isResultVar(QueryArgument arg);
	
	/**
	 * Get the number of result variables.
	 * 
	 * @return
	 */
	abstract public int numResultVars();
	
	/**
	 * Get an unodifiable set of all result variables.
	 * 
	 * @return
	 */
	abstract public Set<QueryArgument> getResultVars();
	
	/**
	 * Get an unodifiable list of all query atom groups.
	 * 
	 * @return
	 */
	abstract public List<QueryAtomGroup> getAtomGroups();
	
	/**
	 * Check whether the query is of type ASK
	 * 
	 * @return 
	 */
	abstract public boolean isAsk();
	
	/**
	 * Check whether the query is of type SELECT.
	 * 
	 * @return
	 */
	abstract public boolean isSelect();
	
	/**
	 * Check whether the query is of type SELECT DISTINCT.
	 * 
	 * @return
	 */
	abstract public boolean isSelectDistinct();
	
	/**
	 * Print the SPARQL-DL query as string.
	 * 
	 * @return String containing valid SPARQL-DL query.
	 */
	abstract public String toString();
	
	/**
	 * A factory method to create a query from string.
	 * 
	 * @param query
	 * @return
	 * @throws QueryParserException
	 */
	public static Query create(String query) 
		throws QueryParserException
	{
		QueryTokenizer tokenizer = new QueryTokenizerImpl();
		QueryParser parser = new QueryParserImpl();

		return parser.parse(tokenizer.tokenize(query));
	}
}
