// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.exceptions;

import de.derivo.sparqldlapi.QueryToken;

public class QueryParserException extends Exception 
{
	private static final long serialVersionUID = 1L;

	QueryToken token;
	
	public QueryParserException(String message)
	{
		super(message);
		token = null;
	}
	
	public QueryParserException(String message, QueryToken token)
	{
		super(message);
		this.token = token;
	}
	
	public String toString()
	{
		String s = this.getMessage();
		if(token != null) {
			s += " (near \"" + token.getToken() + "\", pos: " + token.getPos() + ", col: " + token.getCol() + ", row: " + token.getRow() + ")";
		}
		return s;
	}
}
