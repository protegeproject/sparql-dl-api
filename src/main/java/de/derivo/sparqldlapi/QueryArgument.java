// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import de.derivo.sparqldlapi.impl.LiteralTranslator;
import org.semanticweb.owlapi.model.IRI;

import de.derivo.sparqldlapi.types.QueryArgumentType;
import org.semanticweb.owlapi.model.OWLLiteral;

/**
 * This class represents a query argument (e.g. an URI or a variable).
 * 
 * @author Mario Volke
 */
public class QueryArgument
{
	private String value;
	private QueryArgumentType type;
	
	public QueryArgument(QueryArgumentType type, String value)
	{
		this.type = type;
		this.value = value;
	}
	
	/**
	 * Factory method to create a QueryArgument instance with type URI by IRI.
	 * 
	 * @param value
	 * @return
	 */
	public static QueryArgument newURI(IRI value)
	{
		return new QueryArgument(QueryArgumentType.URI, value.toString());
	}
	
	/**
	 * Factory method to create a QueryArgument instance with type URI by string.
	 * 
	 * @param value
	 * @return
	 */
	public static QueryArgument newURI(String value)
	{
		return new QueryArgument(QueryArgumentType.URI, value);
	}
	
	/**
	 * Factory method to create a QueryArgument instance with type VAR by string.
	 * 
	 * @param value
	 * @return
	 */
	public static QueryArgument newVar(String value)
	{
		return new QueryArgument(QueryArgumentType.VAR, value);
	}
	
	/**
	 * Factory method to create a QueryArgument instance with type BNODE by string.
	 * 
	 * @param value
	 * @return
	 */
	public static QueryArgument newBnode(String value)
	{
		return new QueryArgument(QueryArgumentType.BNODE, value);
	}
	
	/**
	 * Factory method to create a QueryArgument instance with type LITERAL by string.
	 * 
	 * @return
	 */
//	public static QueryArgument newLiteral(String value, String datatype, String lang)
	public static QueryArgument newLiteral(OWLLiteral literal, LiteralTranslator translator)
	{
        return translator.toQueryArgument(literal);
	}

	
	/**
	 * Get the value of the query argument.
	 * 
	 * @return
	 */
	public String getValue()
	{
		return value;
	}
	
	/**
	 * Get the type of the query argument.
	 * 
	 * @return
	 */
	public QueryArgumentType getType()
	{
		return type;
	}
	
	/**
	 * Check whether the query argument has a special type.
	 * 
	 * @param type
	 * @return True if the query argument has this type.
	 */
	public boolean hasType(QueryArgumentType type) 
	{
		return this.type == type;
	}
	
	/**
	 * Check whether the query argument is an URI.
	 * 
	 * @return True if the query argument is an URI.
	 */
	public boolean isURI()
	{
		return hasType(QueryArgumentType.URI);
	}
	
	/**
	 * Check whether the query argument is a variable.
	 * 
	 * @return True if the query argument is a variable.
	 */
	public boolean isVar()
	{
		return hasType(QueryArgumentType.VAR);
	}
	
	/**
	 * Check whether the query argument is a literal.
	 * 
	 * @return True if the query argument is a literal.
	 */
	public boolean isLiteral()
	{
		return hasType(QueryArgumentType.LITERAL);
	}
	
	/**
	 * Check whether the query argument is a bnode.
	 * 
	 * @return True if the query argument is a bnode.
	 */
	public boolean isBnode()
	{
		return hasType(QueryArgumentType.BNODE);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		QueryArgument arg = (QueryArgument)obj;
		return value.equals(arg.value) && type.equals(arg.type);
	}
	
	@Override
	public int hashCode()
	{
		int hash = 7;
		hash = 31 * type.hashCode() + hash;
		hash = 31 * value.hashCode() + hash;
		return hash;
	}
	
	/**
	 * Print the query argument as string.
	 * Output depends on the type of the argument.
	 */
	public String toString()
	{
		StringBuffer sb;
		
		switch(type) {
		case LITERAL:
			sb = new StringBuffer();
			sb.append('"');
			char c;
			int pos = 0;
			while(pos < value.length()) {
				c = value.charAt(pos);
				if(c == '"' || c == '\\') {
					sb.append('\\');
				}
				sb.append(c);
				pos++;
			}
			sb.append('"');
			return sb.toString();
		case VAR:
			sb = new StringBuffer();
			sb.append('?');
			sb.append(value);
			return sb.toString();
		case URI:
			sb = new StringBuffer();
			sb.append('<');
			sb.append(value);
			sb.append('>');
		default:
			return value;
		}
	}
}
