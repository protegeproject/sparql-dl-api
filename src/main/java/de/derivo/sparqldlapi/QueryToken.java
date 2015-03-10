// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

/**
 * The SPARQL-DL query token.
 * 
 * This tokenizer is heavily influenced by org.coode.manchesterowlsyntax.ManchesterOWLSyntaxTokenizer
 * of the OWL-API.
 * 
 * @author Mario Volke
 */
public class QueryToken 
{
	private String token;
	private int pos;
	private int col;
	private int row;
		
	public QueryToken(String token, int pos, int col, int row)
	{
		this.token = token;
		this.pos = pos;
		this.col = col;
		this.row = row;
	}
		
	public String getToken()
	{
		return token;
	}
		
	public int getPos()
	{
		return pos;
	}
		
	public int getCol()
	{
		return col;		
	}
		
	public int getRow()
	{
		return row;
	}
		
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(token);
		sb.append(" [");
		sb.append(pos);
		sb.append(", ");
		sb.append(col);
		sb.append(", ");
		sb.append(row);
		sb.append("]");
		return sb.toString();
	}
	
	@Override
	public boolean equals(Object obj)
	{
		QueryToken t = (QueryToken)obj;
		return token.equals(t.token) && pos == t.pos && col == t.col && row == t.row;
	}
	
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 31 * token.hashCode() + hash;
		hash = 31 * pos + hash;
		hash = 31 * col + hash;
		hash = 31 * row + hash;
		return hash;
	}
}
