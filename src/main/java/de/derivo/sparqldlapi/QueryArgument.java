// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi;

import de.derivo.sparqldlapi.types.QueryArgumentType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;

/**
 * This class represents a query argument (e.g. an URI or a variable).
 *
 * @author Mario Volke
 */
public class QueryArgument {

    private Object value;

//	private String value;


    private QueryArgumentType type;

    public QueryArgument(IRI value) {
        this.type = QueryArgumentType.URI;
        this.value = value;
    }


    public QueryArgument(OWLLiteral value) {
        this.type = QueryArgumentType.LITERAL;
        this.value = value;
    }


    public QueryArgument(OWLAnonymousIndividual value) {
        this.type = QueryArgumentType.BNODE;
        this.value = value;
    }

    public QueryArgument(Var value) {
        this.type = QueryArgumentType.VAR;
        this.value = value;
    }

    /**
     * Factory method to create a QueryArgument instance with type URI by IRI.
     *
     * @param value
     * @return
     */
    public static QueryArgument newURI(IRI value) {
        return new QueryArgument(value);
    }

//	/**
//	 * Factory method to create a QueryArgument instance with type URI by string.
//	 *
//	 * @param value
//	 * @return
//	 */
//	public static QueryArgument newURI(String value)
//	{
//		return new QueryArgument(QueryArgumentType.URI, value);
//	}

    /**
     * Factory method to create a QueryArgument instance with type VAR by string.
     *
     * @param value
     * @return
     */
    public static QueryArgument newVar(Var value) {
        return new QueryArgument(value);
    }

    /**
     * Factory method to create a QueryArgument instance with type BNODE by string.
     *
     * @param value
     * @return
     */
    public static QueryArgument newBnode(OWLAnonymousIndividual value) {
        return new QueryArgument(value);
    }

    /**
     * Factory method to create a QueryArgument instance with type LITERAL by string.
     *
     * @return
     */
//	public static QueryArgument newLiteral(String value, String datatype, String lang)
    public static QueryArgument newLiteral(OWLLiteral literal) {
        return new QueryArgument(literal);
    }


//	/**
//	 * Get the value of the query argument.
//	 *
//	 * @return
//	 */
//	public Object getValue()
//	{
//		return value;
//	}

    public IRI getValueAsIRI() {
        return (IRI) value;
    }

    public OWLLiteral getValueAsLiteral() {
        return (OWLLiteral) value;
    }

    public Var getValueAsVar() {
        return (Var) value;
    }

    public OWLAnonymousIndividual getValueAsBNode() {
        return (OWLAnonymousIndividual) value;
    }

    public String getValueAsString() {
        if (value instanceof IRI) {
            return value.toString();
        }
        else if (value instanceof OWLLiteral) {
            return ((OWLLiteral) value).getLiteral();
        }
        else if (value instanceof OWLAnonymousIndividual) {
            return ((OWLAnonymousIndividual) value).getID().toString();
        }
        else if (value instanceof Var) {
            return ((Var) value).getName();
        }
        else {
            return value.toString();
        }
    }

    /**
     * Get the type of the query argument.
     *
     * @return
     */
    public QueryArgumentType getType() {
        return type;
    }

    /**
     * Check whether the query argument has a special type.
     *
     * @param type
     * @return True if the query argument has this type.
     */
    public boolean hasType(QueryArgumentType type) {
        return this.type == type;
    }

    /**
     * Check whether the query argument is an URI.
     *
     * @return True if the query argument is an URI.
     */
    public boolean isURI() {
        return hasType(QueryArgumentType.URI);
    }

    /**
     * Check whether the query argument is a variable.
     *
     * @return True if the query argument is a variable.
     */
    public boolean isVar() {
        return hasType(QueryArgumentType.VAR);
    }

    /**
     * Check whether the query argument is a literal.
     *
     * @return True if the query argument is a literal.
     */
    public boolean isLiteral() {
        return hasType(QueryArgumentType.LITERAL);
    }

    /**
     * Check whether the query argument is a bnode.
     *
     * @return True if the query argument is a bnode.
     */
    public boolean isBnode() {
        return hasType(QueryArgumentType.BNODE);
    }

    @Override
    public boolean equals(Object obj) {
        QueryArgument arg = (QueryArgument) obj;
        return value.equals(arg.value) && this.type == arg.type;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * type.hashCode() + hash;
        hash = 31 * value.hashCode() + hash;
        return hash;
    }

    /**
     * Print the query argument as string.
     * Output depends on the type of the argument.
     */
    public String toString() {
        StringBuffer sb;

        switch (type) {
            case LITERAL:
                sb = new StringBuffer();
                sb.append(value);
//			sb.append('"');
//			char c;
//			int pos = 0;
//			while(pos < value.length()) {
//				c = value.charAt(pos);
//				if(c == '"' || c == '\\') {
//					sb.append('\\');
//				}
//				sb.append(c);
//				pos++;
//			}
//			sb.append('"');
//			return sb.toString();
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
                return value.toString();
        }
    }
}
