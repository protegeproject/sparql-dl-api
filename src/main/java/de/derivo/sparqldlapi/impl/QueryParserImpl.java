// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtom;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.QueryParser;
import de.derivo.sparqldlapi.QueryToken;
import de.derivo.sparqldlapi.exceptions.QueryParserException;
import de.derivo.sparqldlapi.impl.QueryImpl;
import de.derivo.sparqldlapi.types.QueryAtomType;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * Concrete implementation of the QueryParser interface.
 * 
 * @author Mario Volke
 */
public class QueryParserImpl implements QueryParser
{
	private List<QueryToken> tokens;
	private int pos;
	private QueryImpl query;
	private QueryAtomGroupImpl currentAtomGroup;
	private QueryAtomType currentAtomType;
	private List<QueryArgument> currentArgs;
	private Map<String, String> prefixes;
	
	public QueryParserImpl() 
	{}
	
	private void reset()
	{
		pos = 0;
		currentAtomType = null;
		currentArgs = null;
		tokens = null;
		query = null;
		
		// add standard prefixes
		prefixes = new HashMap<String, String>();
		prefixes.put("rdf:", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		prefixes.put("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
		prefixes.put("xsd:", "http://www.w3.org/2001/XMLSchema#");
		prefixes.put("fn:", "http://www.w3.org/2005/xpath-functions#");
		prefixes.put("owl:", "http://www.w3.org/2002/07/owl#");
	}
	
	public Query parse(List<QueryToken> tokens)
		throws QueryParserException
	{
		reset();
		
		this.tokens = tokens;
		
		parsePrefixes();
		parseQueryHead();
		
		return query;
	}
	
	private void parsePrefixes()
		throws QueryParserException
	{
		while(tokens.get(pos).getToken().equalsIgnoreCase("PREFIX")) {
			
			if(tokens.size() < pos + 3) {
				throw new QueryParserException("PREFIX syntax error.", tokens.get(pos));
			}
		
			String uri = tokens.get(pos + 2).getToken();
			if(!isURI(uri)) {
				throw new QueryParserException("PREFIX syntax error.", tokens.get(pos + 2));
			}
			
			prefixes.put(tokens.get(pos + 1).getToken(), uri.substring(1, uri.length() - 1));
			
			pos += 3;
		}
	}
	
	private void parseQueryHead()
		throws QueryParserException
	{
		QueryToken typeToken = tokens.get(pos);
		QueryToken nextToken = tokens.get(pos + 1);
		QueryType type;
		if(typeToken.getToken().equalsIgnoreCase("select") && nextToken.getToken().equalsIgnoreCase("distinct")) {
			type = QueryType.SELECT_DISTINCT;
			pos++;
		}
		else {
			type = QueryType.fromString(typeToken.getToken());
		}
		
		pos++;
		
		switch(type) {
		case SELECT:
			query = new QueryImpl(QueryType.SELECT);
			parseSelect();
			break;
		case SELECT_DISTINCT:
			query = new QueryImpl(QueryType.SELECT_DISTINCT);
			parseSelect();
			break;
		case ASK:
			query = new QueryImpl(QueryType.ASK);
			parseAsk();
			break;
		default:
			throw new QueryParserException("Unknown query type.", typeToken);	
		}
	}
	
	private void parseSelect()
		throws QueryParserException
	{	
		parseResultVars();
		
		// if result var list is empty here then
		// we parsed "*" and we have to fetch all variables after parsing all atoms.
		boolean fetchResultVars = false;
		if(query.numResultVars() == 0) {
			fetchResultVars = true;
		}
		
		// parse first where group
		parseWhere();
		
		// parse optional union groups
		parseOrWheres();
		
		if(fetchResultVars) {
			for(QueryAtomGroup group : query.getAtomGroups()) {
				for(QueryAtom atom : group.getAtoms()) {
					for(QueryArgument arg : atom.getArguments()) {
						if(arg.isVar()) {
							query.addResultVar(arg);
						}
					}
				}
			}
		}
	}
	
	private void parseResultVars()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		
		if("*".equals(token.getToken())) {
			pos++;
			return;
		}
		
		while(isVar(token.getToken())){
			query.addResultVar(QueryArgument.newVar(token.getToken().substring(1)));
			
			pos++;
			token = tokens.get(pos);
		};
		
		if(query.numResultVars() == 0) {
			throw new QueryParserException("Minimum one variable in result list is needed.", token);
		}
	}
	
	private void parseAsk()
		throws QueryParserException
	{	
		parseWhere();
	}
	
	private void parseWhere()
		throws QueryParserException
	{
		currentAtomGroup = new QueryAtomGroupImpl();
		
		parseOptionalWhere();
		parseGroupBegin();
		parseAtoms();
		parseGroupEnd();
		
		query.addAtomGroup(currentAtomGroup);
		currentAtomGroup = null;
	}
	
	private void parseOptionalWhere()
	{
		QueryToken where = tokens.get(pos);
		if("where".equalsIgnoreCase(where.getToken())) {
			pos++;
		}
	}
	
	private void parseGroupBegin()
		throws QueryParserException
	{	
		QueryToken grp = tokens.get(pos);
		if(!"{".equals(grp.getToken())) {
			throw new QueryParserException("Character \"{\" awaited in SPARQL-DL query.", grp);
		}
		pos++;
	}
	
	private void parseGroupEnd()
		throws QueryParserException
	{
		QueryToken grp = tokens.get(pos);
		if(!"}".equals(grp.getToken())) {
			throw new QueryParserException("Character \"}\" awaited in SPARQL-DL query.", grp);
		}
		pos++;
	}
	
	private void parseOrWheres()
		throws QueryParserException
	{
		while(pos < tokens.size()) {
			QueryToken or = tokens.get(pos);
			if(!"or".equalsIgnoreCase(or.getToken())) {
				break;
			}
			
			pos++;
			
			QueryToken where = tokens.get(pos);
			if(!"where".equalsIgnoreCase(where.getToken())) {
				throw new QueryParserException("\"WHERE\" awaited in SPARQL-DL query.", where);
			}
			
			pos++;
			
			currentAtomGroup = new QueryAtomGroupImpl();
			
			parseGroupBegin();
			parseAtoms();
			parseGroupEnd();
			
			// consume group
			query.addAtomGroup(currentAtomGroup);
			currentAtomGroup = null;
		}
	}
	
	private void parseAtoms()
		throws QueryParserException
	{
		// there could also be no atom at all
		// of course this doesn't make sense
		if("}".equals(tokens.get(pos).getToken())) {
			return;
		}
		
		while(pos < tokens.size()) {
			parseAtom();
			
			QueryToken delim = tokens.get(pos);
			if("}".equals(delim.getToken())) {
				break;
			}
			
			parseCommaDelim();
		}
	}
	
	private void parseCommaDelim()
		throws QueryParserException
	{
		QueryToken delim = tokens.get(pos);
		if(!",".equals(delim.getToken())) {
			throw new QueryParserException("Character \",\" awaited in SPARQL-DL query.", delim);
		}
		pos++;
	}
	
	private void parseAtom()
		throws QueryParserException
	{
		QueryToken atomNameToken = tokens.get(pos);
		String atomName = atomNameToken.getToken();
		pos++;
		
		currentAtomType = QueryAtomType.fromString(atomName);
		currentArgs = new ArrayList<QueryArgument>();
		
		switch(currentAtomType) {
		case TYPE:
		case DIRECT_TYPE:
			parseParamsListOpen();
			parseVariableBlankURI();
			parseCommaDelim();
			parseVariableURI();
			parseParamsListClose();
			break;
		case PROPERTY_VALUE:
		case ANNOTATION:
			parseParamsListOpen();
			parseVariableBlankURI();
			parseCommaDelim();
			parseVariableURI();
			parseCommaDelim();
			parseVariableBlankURILiteral();
			parseParamsListClose();
			break;
		case SAME_AS:
		case DIFFERENT_FROM:
			parseParamsListOpen();
			parseVariableBlankURI();
			parseCommaDelim();
			parseVariableBlankURI();
			parseParamsListClose();
			break;
		case EQUIVALENT_CLASS:
		case SUB_CLASS_OF:
		case DISJOINT_WITH:
		case COMPLEMENT_OF:
		case EQUIVALENT_PROPERTY:
		case SUB_PROPERTY_OF:
		case INVERSE_OF:
		case STRICT_SUB_CLASS_OF:
		case DIRECT_SUB_CLASS_OF:
		case STRICT_SUB_PROPERTY_OF:
		case DIRECT_SUB_PROPERTY_OF:
			parseParamsListOpen();
			parseVariableURI();
			parseCommaDelim();
			parseVariableURI();
			parseParamsListClose();
			break;
		case CLASS:
		case INDIVIDUAL:
		case PROPERTY:
		case OBJECT_PROPERTY:
		case DATA_PROPERTY:
		case FUNCTIONAL:
		case INVERSE_FUNCTIONAL:
		case TRANSITIVE:
		case SYMMETRIC:
		case REFLEXIVE:
		case IRREFLEXIVE:
			parseParamsListOpen();
			parseVariableURI();
			parseParamsListClose();
			break;
		default:
			throw new QueryParserException("Unknown atom in SPARQL-DL query.", atomNameToken);	
		}
		
		// consume atom
		currentAtomGroup.addAtom(new QueryAtom(currentAtomType, currentArgs));
		currentAtomType = null;
		currentArgs = null;
	}
	
	private void parseParamsListOpen()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		pos++;

		if(!"(".equals(token.getToken())) {
			throw new QueryParserException("Character \"(\" awaited in SPARQL-DL query.", token);
		}
	}
	
	private void parseParamsListClose()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		pos++;
			
		if(!")".equals(token.getToken())) {
			throw new QueryParserException("Character \")\" awaited in SPARQL-DL query.", token);
		}
	}
	
	private void parseVariableBlankURI()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		String tokenString = token.getToken();
		if(isPrefixURI(tokenString, prefixes)) {
			appendPrefixURI(tokenString);
		}
		else if(isBnode(tokenString)) {
			appendBnode(tokenString);
		}
		else if(isVar(tokenString)) {
			appendVar(tokenString);
		}
		else if(isURI(tokenString)) {
			appendURI(tokenString);
		}
		else {
			throw new QueryParserException("Variable, blank node or URI awaited as parameter in SPARQL-DL query.", token);
		}
		pos++;
	}
	
	private void parseVariableURI()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		String tokenString = token.getToken();
		if(isPrefixURI(tokenString, prefixes)) {
			appendPrefixURI(tokenString);
		}
		else if(isVar(tokenString)) {
			appendVar(tokenString);
		}
		else if(isURI(tokenString)) {
			appendURI(tokenString);
		}
		else {
			throw new QueryParserException("Variable or URI awaited as parameter in SPARQL-DL query.", token);
		}
		pos++;
	}
	
	private void parseVariableBlankURILiteral()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		String tokenString = token.getToken();
		if(isPrefixURI(tokenString, prefixes)) {
			appendPrefixURI(tokenString);
		}
		else if(isBnode(tokenString)) {
			appendLiteral(tokenString);
		}
		else if(isVar(tokenString)) {
			appendVar(tokenString);
		}
		else if(isURI(tokenString)) {
			appendURI(tokenString);
		}
		else if(isLiteral(tokenString)) {
			appendLiteral(tokenString);
		}
		else {
			throw new QueryParserException("Variable, blank node, URI or literal awaited as parameter in SPARQL-DL query.", token);
		}
		pos++;
	}
	
	@SuppressWarnings("unused")
	private void parseURI()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		String tokenString = token.getToken();
		if(isPrefixURI(tokenString, prefixes)) {
			appendPrefixURI(tokenString);
		}
		else if(isURI(tokenString)) {
			appendURI(tokenString);
		}
		else {
			throw new QueryParserException("URI awaited as parameter in SPARQL-DL query.", token);
		}
		pos++;
	}
	
	@SuppressWarnings("unused")
	private void parseURILiteral()
		throws QueryParserException
	{
		QueryToken token = tokens.get(pos);
		String tokenString = token.getToken();
		if(isPrefixURI(tokenString, prefixes)) {
			appendPrefixURI(tokenString);
		}
		else if(isURI(tokenString)) {
			appendURI(tokenString);
		}
		else if(isLiteral(tokenString)) {
			appendLiteral(tokenString);
		}
		else {
			throw new QueryParserException("URI or literal awaited as parameter in SPARQL-DL query.", token);
		}
		pos++;
	}
	
	private void appendURI(String s) 
	{
		currentArgs.add(QueryArgument.newURI(s.substring(1, s.length() - 1)));
	}
	
	private void appendPrefixURI(String s)
	{
		currentArgs.add(QueryArgument.newURI(uriWithPrefix(s)));
	}

	private void appendBnode(String s) 
	{
		currentArgs.add(QueryArgument.newBnode(s));
	}
	
	private void appendLiteral(String s) 
	{
		currentArgs.add(QueryArgument.newLiteral(s.substring(1, s.length() - 1)));
	}
	
	private void appendVar(String s) 
	{
		currentArgs.add(QueryArgument.newVar(s.substring(1)));
	}
	
	private String uriWithPrefix(String s)
	{
		for(String p : prefixes.keySet()) {
			if(s.startsWith(p)) {
				return prefixes.get(p) + s.substring(p.length());
			}
		}
		
		return s;
	}
	
	/* static helpers */
	
	private static boolean isLiteral(String s)
	{
		return (s.length() >= 2 && s.charAt(0) == '\"' && s.charAt(s.length() - 1) == '\"');
	}
	
	private static boolean isBnode(String s)
	{
		return (s.length() > 2 && s.charAt(0) == '_' && s.charAt(1) == ':');
	}
	
	private static boolean isVar(String s)
	{
		return (s.length() > 1 && (s.charAt(0) == '?' || s.charAt(0) == '$'));
	}
	
	private static boolean isURI(String s)
	{
		return (s.length() >= 2 && s.charAt(0) == '<' && s.charAt(s.length() - 1) == '>');
	}
	
	private static boolean isPrefixURI(String s, Map<String, String> prefixes)
	{
		for(String p : prefixes.keySet()) {
			if(s.startsWith(p)) {
				return true;
			}
		}
		
		return false;
	}
}
