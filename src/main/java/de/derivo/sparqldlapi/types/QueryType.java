// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.types;

/**
 * All possible query types.
 * 
 * @author Mario Volke
 */
public enum QueryType 
{
	SELECT("select"), SELECT_DISTINCT("select distinct"), ASK("ask"),
	
	UNKNOWN;
	
	private final String syntax;
	
	private QueryType()
	{
		this(null);
	}
	
	private QueryType(String syntax)
	{
		this.syntax = syntax;
	}
	
	public static QueryType fromString(String str)
	{
		for(QueryType value : values()) {
			if(value.syntax != null && value.syntax.equalsIgnoreCase(str)) {
				return value;
			}
		}
		
		return QueryType.UNKNOWN;
	}
}
