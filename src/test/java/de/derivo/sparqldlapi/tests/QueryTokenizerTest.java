// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import de.derivo.sparqldlapi.QueryToken;
import de.derivo.sparqldlapi.QueryTokenizer;
import de.derivo.sparqldlapi.impl.QueryTokenizerImpl;

/**
 * A jUnit 4.0 test class to test the implementation of QueryTokenizer
 * 
 * @author Mario Volke
 */
public class QueryTokenizerTest 
{
	@Test
	public void testTokenize() 
	{
		QueryTokenizer tokenizer = new QueryTokenizerImpl();
		List<QueryToken> tokens = tokenizer.tokenize(
			"PREFIX wine: http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#\n" +
			"PREFIX foaf: http://xmlns.com/foaf/0.1/\n" +
			"SELECT * WHERE PropertyValue(?i, wine:hasColor, ?v), PropertyValue(?p, foaf:name, \"foo \\\" bar\")"
		);
		
		List<QueryToken> shouldbe = new ArrayList<QueryToken>();
		shouldbe.add(new QueryToken("PREFIX", 0, 1, 1));
		shouldbe.add(new QueryToken("wine:", 7, 8, 1));
		shouldbe.add(new QueryToken("http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#", 13, 14, 1));
		shouldbe.add(new QueryToken("PREFIX", 67, 0, 2));
		shouldbe.add(new QueryToken("foaf:", 74, 7, 2));
		shouldbe.add(new QueryToken("http://xmlns.com/foaf/0.1/", 80, 13, 2));
		shouldbe.add(new QueryToken("SELECT", 107, 0, 3));
		shouldbe.add(new QueryToken("*", 114, 7, 3));
		shouldbe.add(new QueryToken("WHERE", 116, 9, 3));
		shouldbe.add(new QueryToken("PropertyValue", 122, 15, 3));
		shouldbe.add(new QueryToken("(", 136, 29, 3));
		shouldbe.add(new QueryToken("?i", 136, 29, 3));
		shouldbe.add(new QueryToken(",", 139, 32, 3));
		shouldbe.add(new QueryToken("wine:hasColor", 140, 33, 3));
		shouldbe.add(new QueryToken(",", 154, 47, 3));
		shouldbe.add(new QueryToken("?v", 155, 48, 3));
		shouldbe.add(new QueryToken(")", 158, 51, 3));
		shouldbe.add(new QueryToken(",", 159, 52, 3));
		shouldbe.add(new QueryToken("PropertyValue", 160, 53, 3));
		shouldbe.add(new QueryToken("(", 174, 67, 3));
		shouldbe.add(new QueryToken("?p", 174, 67, 3));
		shouldbe.add(new QueryToken(",", 177, 70, 3));
		shouldbe.add(new QueryToken("foaf:name", 178, 71, 3));
		shouldbe.add(new QueryToken(",", 188, 81, 3));
		shouldbe.add(new QueryToken("\"foo \" bar\"", 189, 82, 3));
		shouldbe.add(new QueryToken(")", 202, 95, 3));
		shouldbe.add(new QueryToken("<EOF>", 202, 95, 3));
		
		assertEquals(tokens, shouldbe);
	}
}
