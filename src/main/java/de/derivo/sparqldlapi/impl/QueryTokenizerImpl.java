// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.derivo.sparqldlapi.QueryToken;
import de.derivo.sparqldlapi.QueryTokenizer;

/**
 * Concrete implementation of the QueryTokenizer interface.
 * 
 * This tokenizer is heavily influenced by org.coode.manchesterowlsyntax.ManchesterOWLSyntaxTokenizer
 * of the OWL-API.
 * 
 * @author Mario Volke
 */
public class QueryTokenizerImpl implements QueryTokenizer
{
	public static final String EOF = "<EOF>";
	public static final char LITERAL_ESCAPE_CHAR = '\\';
	private Set<Character> skip = new HashSet<Character>();
	private Set<Character> delims = new HashSet<Character>();
	private String buffer;
	private List<QueryToken> tokens = new ArrayList<QueryToken>();
	private int pos;
	private int col;
	private int row;
	private int startPos;
	private int startCol;
	private int startRow;
	private StringBuilder sb;

	public QueryTokenizerImpl()
	{
		skip.add(' ');
		skip.add('\n');
		skip.add('\r');
		skip.add('\t');
		delims.add(',');
		delims.add('(');
		delims.add(')');
		delims.add('{');
		delims.add('}');
	}
	
	private void reset()
	{
		sb = new StringBuilder();
		tokens.clear();
		pos = 0;
		col = 1;
		row = 1;
		startPos = 0;
		startCol = 1;
		startRow = 1;
		buffer = null;
	}
	
	public List<QueryToken> tokenize(String buffer)
	{
		reset();
		
		this.buffer = buffer;
		
		while(pos < buffer.length()) {
			char ch = readChar();
			if(ch == '\"') {
				readLiteral();
			}
			else if(skip.contains(ch)) {
				consumeToken();
			}
			else if(delims.contains(ch)) {
				consumeToken();
				sb.append(ch);
				consumeToken();
			}
			else {
				sb.append(ch);
			}
		}
		consumeToken();
		tokens.add(new QueryToken(EOF, pos, col, row));
		
		return tokens;
	}
	
	private void readLiteral()
	{
		sb.append('\"');
		while(pos < buffer.length()) {
			char ch = readChar();
			if(ch == LITERAL_ESCAPE_CHAR) {
				if(pos + 1 < buffer.length()) {
					char escapedChar = readChar();
					if(escapedChar == '"' || escapedChar == '\\') {
						sb.append(escapedChar);
					}
					else {
						sb.append(ch);
						sb.append(escapedChar);
					}
				}
				else {
					sb.append(ch);
				}
			}
			else if(ch == '\"') {
				sb.append(ch);
				break;
			}
			else {
				sb.append(ch);
			}
		}
		
		consumeToken();
	}
	
	private void consumeToken()
	{
		if(sb.length() > 0) {
			tokens.add(new QueryToken(sb.toString(), startPos, startCol, startRow));
			sb = new StringBuilder();
		}
		startPos = pos;
		startCol = col;
		startRow = row;
	}
	
	private char readChar()
	{
		char ch = buffer.charAt(pos);
		pos++;
		col++;
		if(ch == '\n') {
			row++;
			col = 0;
		}
		return ch;
	}
	
	public static void main(String[] args)
	{
		QueryTokenizerImpl tokenizer = new QueryTokenizerImpl();
		List<QueryToken> tokens = tokenizer.tokenize("SELECT ?i ?p WHERE { Type(?i, VintageYear), Property(?i, ?p, \">?, oha \\\"\") }");
		for(QueryToken t : tokens) {
			System.out.println(t.toString());
		}
	}
}
