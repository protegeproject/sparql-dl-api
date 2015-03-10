// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.List;

import de.derivo.sparqldlapi.exceptions.QueryParserException;

/**
 * The SPARQL-DL query parser.
 * 
 * @author Mario Volke
 */
public interface QueryParser 
{
	/**
	 * Parse the query.
	 * @param tokens The tokens that you got from the QueryTokenizer.
	 * @return A Query instance.
	 * @throws QueryParserException
	 */
	public Query parse(List<QueryToken> tokens)
		throws QueryParserException;
}
