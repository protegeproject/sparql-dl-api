// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryBinding;
import de.derivo.sparqldlapi.QueryResult;

/**
 * Concrete implementation of the QueryResult interface.
 * 
 * @author Mario Volke
 */
public class QueryResultImpl implements QueryResult
{
	private List<QueryBindingImpl> bindings;
	private boolean ask;
	private Query query;
	
	public QueryResultImpl(Query query)
	{
		bindings = new ArrayList<QueryBindingImpl>();
		ask = true;
		this.query = query;
	}
	
	/**
	 * Get the query that belongs to this result.
	 * 
	 * @return
	 */
	public Query getQuery()
	{
		return query;
	}
	
	/**
	 * Add a binding to the result set.
	 * 
	 * @param binding
	 */
	public void add(QueryBindingImpl binding)
	{
		ask = true;
		bindings.add(binding);
	}
	
	/**
	 * Set whether the query has a solution or not.
	 * 
	 * @param s
	 */
	public void setAsk(boolean s)
	{
		ask = s;
	}
	
	/**
	 * Ask if the query had a solution.
	 * This is the only result you get if the query was of type ASK.
	 * This could also be true if the result set is empty.
	 * 
	 * @return True if the query had a solution.
	 */
	public boolean ask()
	{
		return ask;
	}
	
	/**
	 * An iterator over the result set.
	 * 
	 * @return
	 */
	public Iterator<QueryBinding> iterator() 
	{
		List<QueryBinding> iBindings = new ArrayList<QueryBinding>();
		for(QueryBindingImpl b : bindings) {
			iBindings.add(b);
		}
		return iBindings.iterator();
	}
	
	public List<QueryBindingImpl> getBindings() 
	{
		return bindings;
	}	
	
	/**
	 * Get the size of the result set.
	 * 
	 * @return The size of the result set.
	 */
	public int size()
	{
		return bindings.size();
	}
	
	/**
	 * Returns the QueryBinding at the specified position of the result.
	 *  
	 * @param index
	 * @return The QueryBinding at the specified position. 
	 */
	public QueryBinding get(int index)
	{
		return (QueryBinding)bindings.get(index);
	}
	
	/**
	 * Check whether the result set is empty.
	 * 
	 * @return True if the result set is empty.
	 */
	public boolean isEmpty()
	{
		return bindings.isEmpty();
	}
	
	/**
	 * Output query results as JDOM XML document containing the standard 
	 * SPARQL query results XML format (http://www.w3.org/TR/rdf-sparql-XMLres/).
	 * Supports both: Variable binding results and Boolean results.
	 * 
	 * @return A JDOM XML document.
	 */
	public Document toXML()
	{
		Element sparql = new Element("sparql");
		sparql.setNamespace(Namespace.getNamespace("http://www.w3.org/2005/sparql-results#"));
		
		// generate head
		Element head = new Element("head");
		if(!bindings.isEmpty()) {
			QueryBinding top = bindings.get(0);
			for(QueryArgument arg : top.getBoundArgs()) {
				if(arg.isVar()) {
					Element var = new Element("variable");
					var.setAttribute("name", arg.getValueAsString());
					head.addContent(var);
				}
			}
		}
		sparql.addContent(head);
		
		if(query.isAsk()) {
			Element booleanElement = new Element("boolean");
			if(ask) {
				booleanElement.setText("true");
			}
			else {
				booleanElement.setText("false");
			}
			sparql.addContent(booleanElement);
		}
		else {
			// otherwise generate results
			Element results = new Element("results");
			for(QueryBinding binding : bindings) {
				Element result = new Element("result");
				for(QueryArgument key : binding.getBoundArgs()) {
					if(key.isVar()) {
						Element b = new Element("binding");
						b.setAttribute("name", key.getValueAsString());
						QueryArgument value = binding.get(key);
						switch(value.getType()) {
						case URI:
							Element uri = new Element("uri");
							uri.setText(value.getValueAsIRI().toString());
							b.addContent(uri);
							break;
						case LITERAL:
							Element literal = new Element("literal");
							literal.setText(value.getValueAsLiteral().getLiteral());
							b.addContent(literal);
							break;
						case BNODE:
							Element bnode = new Element("bnode");
							bnode.setText(value.getValueAsBNode().getID().toString());
							b.addContent(bnode);
							break;
						default:
						}
						result.addContent(b);
					}
				}
				results.addContent(result);
			}
			sparql.addContent(results);
		}
		
		return new Document(sparql);
	}
	
	/**
	 * Output query results in JSON format as standardized in http://www.w3.org/TR/rdf-sparql-json-res/.
	 * Supports both: Variable binding results and Boolean results.
	 * 
	 * @return The JSON result as string.
	 */
	public String toJSON()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("{\n");
		
		// generate head
		sb.append("\t\"head\": {\n");
		if(!bindings.isEmpty()) {
			sb.append("\t\t\"vars\": [\n");
			QueryBinding top = bindings.get(0);
			boolean first = true;
			for(QueryArgument arg : top.getBoundArgs()) {
				if(arg.isVar()) {
					if(first) {
						first = false;
					}
					else {
						sb.append(",\n");
					}
					sb.append("\t\t\t\"");
					sb.append(arg.getValueAsString());
					sb.append("\"");
				}
			}
			sb.append("\n\t\t]\n");
		}
		sb.append("\t},\n");
		
		if(query.isAsk()) {
			if(ask) {
				sb.append("\t\"boolean\": true\n");
			}
			else {
				sb.append("\t\"boolean\": false\n");
			}
		}
		else {
			// otherwise generate results
			sb.append("\t\"results\": {\n");
			sb.append("\t\t\"bindings\": [\n");
			boolean firstBinding = true;
			for(QueryBinding binding : bindings) {
				if(firstBinding) {
					firstBinding = false;
				}
				else {
					sb.append(",\n");
				}
				sb.append("\t\t\t{\n");
				boolean firstVar = true;
				for(QueryArgument key : binding.getBoundArgs()) {
					if(key.isVar()) {
						if(firstVar) {
							firstVar = false;
						}
						else {
							sb.append(",\n");
						}
						sb.append("\t\t\t\t\"");
						sb.append(key.getValueAsString());
						sb.append("\": {\n");
						QueryArgument value = binding.get(key);
						switch(value.getType()) {
						case URI:
							sb.append("\t\t\t\t\t\"type\": \"uri\",\n");
							sb.append("\t\t\t\t\t\"value\": \"");
							sb.append(value.getValueAsIRI().toString().replaceAll("\"", "\\\\\""));
							sb.append("\"\n");
							break;
						case LITERAL:
							sb.append("\t\t\t\t\t\"type\": \"literal\",\n");
							sb.append("\t\t\t\t\t\"value\": \"");
							sb.append(value.getValueAsLiteral().getLiteral().replaceAll("\"", "\\\\\""));
							sb.append("\"\n");
							break;
						case BNODE:
							sb.append("\t\t\t\t\t\"type\": \"bnode\",\n");
							sb.append("\t\t\t\t\t\"value\": \"");
							sb.append(value.getValueAsBNode().getID().toString().replaceAll("\"", "\\\\\""));
							sb.append("\"\n");
							break;
						default:
						}
						sb.append("\t\t\t\t}");
					}
				}
				sb.append("\n\t\t\t}");
			}
			sb.append("\n\t\t]\n\t}\n");
		}
		sb.append("}\n");
		
		return sb.toString();
	}
	
	/**
	 * Use this method for debugging purposes.
	 * This is no standard format like SPARQL-XML or JSON.
	 *
	 * @return A nicely formatted string containing the results and bindings.
	 */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		for(QueryBinding binding : bindings) {
			Set<QueryArgument> keys = binding.getBoundArgs();
			boolean first = true;
			for(QueryArgument key : keys) {
				if(first) {
					first = false;
				}
				else {
					sb.append(',');
					sb.append(' ');
				}
				sb.append(key.toString());
				sb.append(' ');
				sb.append('=');
				sb.append(' ');
				sb.append(binding.get(key).toString());
			}
			sb.append('\n');
		}
		return sb.toString();
	}
}
