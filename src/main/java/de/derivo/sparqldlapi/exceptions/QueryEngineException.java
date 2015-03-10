// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.exceptions;

public class QueryEngineException extends Exception 
{
	private static final long serialVersionUID = 1L;
	
	public QueryEngineException(String message)
	{
		super(message);
	}
}
