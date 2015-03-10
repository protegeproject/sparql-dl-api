// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtom;
import de.derivo.sparqldlapi.QueryAtomGroup;
import de.derivo.sparqldlapi.QueryResult;
import de.derivo.sparqldlapi.impl.QueryAtomGroupImpl;
import de.derivo.sparqldlapi.impl.QueryBindingImpl;
import de.derivo.sparqldlapi.impl.QueryImpl;
import de.derivo.sparqldlapi.impl.QueryResultImpl;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import de.derivo.sparqldlapi.types.QueryAtomType;
import de.derivo.sparqldlapi.types.QueryType;

/**
 * A jUnit 4.0 test class to test the implementation of QueryResult
 * 
 * @author Mario Volke
 */
public class QueryResultTest 
{
	private Query query;
	private QueryAtomGroup group;
	private QueryAtom atom;
	private QueryArgument arg1, arg2, arg3;
	
	@Before
	public void setUp()
	{	
		arg1 = new QueryArgument(QueryArgumentType.VAR, "x");
		arg2 = new QueryArgument(QueryArgumentType.URI, "http://xmlns.com/foaf/0.1/name");
		arg3 = new QueryArgument(QueryArgumentType.LITERAL, "foobar");
		
		atom = new QueryAtom(QueryAtomType.PROPERTY_VALUE, arg1, arg2, arg3);
		
		QueryAtomGroupImpl groupImpl = new QueryAtomGroupImpl();
		groupImpl.addAtom(atom);
		group = groupImpl;
		
		QueryImpl queryImpl = new QueryImpl(QueryType.SELECT);
		queryImpl.addAtomGroup(group);
		queryImpl.addResultVar(arg1);
		query = queryImpl;
	}
	
	@After 
	public void tearDown()
	{
		arg1 = null;
		arg2 = null;
		arg3 = null;
		group = null;
		atom = null;
		query = null;
	}
	
	@Test
	public void testGetQuery() 
	{
		QueryResult result = new QueryResultImpl(query);
		assertSame(result.getQuery(), query);
	}
	
	@Test
	public void testSize() 
	{
		QueryResultImpl result = new QueryResultImpl(query);
		assertTrue(result.size() == 0);
		
		QueryArgument boundArg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(arg1, boundArg);
		result.add(binding);
		assertTrue(result.size() == 1);
	}
	
	@Test
	public void testGet() 
	{
		QueryResultImpl result = new QueryResultImpl(query);
		QueryArgument boundArg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(arg1, boundArg);
		result.add(binding);
		assertEquals(result.get(0), binding);
	}
	
	@Test(expected = IndexOutOfBoundsException.class)
	public void testGetWithException() 
	{
		QueryResult result = new QueryResultImpl(query);
		result.get(0);
	}
	
	@Test
	public void testIsEmpty() 
	{
		QueryResultImpl result = new QueryResultImpl(query);
		assertTrue(result.isEmpty());
		
		QueryArgument boundArg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(arg1, boundArg);
		result.add(binding);
		assertFalse(result.isEmpty());
	}
	
	@Test
	public void testAsk() 
	{
		QueryResultImpl result = new QueryResultImpl(query);
		result.setAsk(true);
		assertTrue(result.ask());
		result.setAsk(false);
		assertFalse(result.ask());
	}
}
