// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import java.util.List;

/**
 * The SPARQL-DL query tokenizer.
 * 
 * This tokenizer is heavily influenced by org.coode.manchesterowlsyntax.ManchesterOWLSyntaxTokenizer
 * of the OWL-API.
 * 
 * @author Mario Volke
 */
public interface QueryTokenizer 
{	
	/**
	 * Tokenize a SPARQL-DL query string.
	 * @param buffer
	 * @return A list of tokens.
	 */
	public List<QueryToken> tokenize(String buffer);
}
