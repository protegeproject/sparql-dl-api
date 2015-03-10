// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.impl.QueryAtomGroupImpl;
import de.derivo.sparqldlapi.impl.QueryImpl;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * A jUnit 4.0 test class to test the implementation of Query
 * 
 * @author Mario Volke
 */
public class QueryTest 
{
	@Test
	public void testIsEmpty() 
	{
		QueryImpl query = new QueryImpl(QueryType.ASK);
		assertTrue(query.isEmpty());
		query.addAtomGroup(new QueryAtomGroupImpl());
		assertFalse(query.isEmpty());
	}
	
	@Test
	public void testGetType() 
	{
		Query query = new QueryImpl(QueryType.ASK);
		assertEquals(query.getType(), QueryType.ASK);
	}
	
	@Test
	public void testIsResultVariable() 
	{
		QueryImpl query = new QueryImpl(QueryType.SELECT);
		QueryArgument arg = new QueryArgument(QueryArgumentType.VAR, "x");
		
		assertFalse(query.isResultVar(arg));
		
		query.addResultVar(arg);
		assertTrue(query.isResultVar(arg));
	}
	
	@Test
	public void testNumResultVars() 
	{
		QueryImpl query = new QueryImpl(QueryType.SELECT);
		QueryArgument arg = new QueryArgument(QueryArgumentType.VAR, "x");
		
		assertEquals(query.numResultVars(), 0);
		
		query.addResultVar(arg);
		assertEquals(query.numResultVars(), 1);
		
		// no duplicate entries
		query.addResultVar(arg);
		assertEquals(query.numResultVars(), 1);
		
		// no other entries than variables
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		query.addResultVar(arg2);
		assertEquals(query.numResultVars(), 1);
	}
	
	@Test
	public void testGetResultVars() 
	{
		QueryImpl query = new QueryImpl(QueryType.SELECT);
		QueryArgument arg = new QueryArgument(QueryArgumentType.VAR, "x");
		query.addResultVar(arg);
		
		// no duplicate entries
		query.addResultVar(arg);
		
		// no other entries than variables
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		query.addResultVar(arg2);
		
		Set<QueryArgument> resultVars = new HashSet<QueryArgument>();
		resultVars.add(arg);
		assertEquals(query.getResultVars(), resultVars);
	}
	
	@Test
	public void testGetAtomGroups() 
	{
		QueryImpl query = new QueryImpl(QueryType.SELECT);
		QueryAtomGroup group = new QueryAtomGroupImpl();
		query.addAtomGroup(group);
		
		List<QueryAtomGroup> groups = new LinkedList<QueryAtomGroup>();
		groups.add(group);
		assertEquals(query.getAtomGroups(), groups);
	}
	
	
	@Test
	public void testIsAsk() 
	{
		Query query = new QueryImpl(QueryType.SELECT);
		assertFalse(query.isAsk());
		Query query2 = new QueryImpl(QueryType.ASK);
		assertTrue(query2.isAsk());
	}
	
	@Test
	public void testIsSelect() 
	{
		Query query = new QueryImpl(QueryType.SELECT);
		assertTrue(query.isSelect());
		Query query2 = new QueryImpl(QueryType.ASK);
		assertFalse(query2.isSelect());
	}
	
	@Test
	public void testIsSelectDistinct() 
	{
		Query query = new QueryImpl(QueryType.SELECT_DISTINCT);
		assertTrue(query.isSelectDistinct());
		Query query2 = new QueryImpl(QueryType.ASK);
		assertFalse(query2.isSelectDistinct());
	}
}
