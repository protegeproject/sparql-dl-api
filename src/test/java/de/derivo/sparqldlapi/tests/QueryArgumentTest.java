// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.types.QueryArgumentType;

/**
 * A jUnit 4.0 test class to test QueryArgument
 * 
 * @author Mario Volke
 */
public class QueryArgumentTest 
{
	@Test
	public void testGetValue() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		assertEquals(arg.getValue(), "http://example.com");
	}
	
	@Test
	public void testGetType() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		assertEquals(arg.getType(), QueryArgumentType.URI);
	}
	
	@Test
	public void testHasType() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		assertTrue(arg.hasType(QueryArgumentType.URI));
		assertFalse(arg.hasType(QueryArgumentType.VAR));
	}
	
	@Test
	public void testIsURI() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		assertTrue(arg.isURI());
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.VAR, "x");
		assertFalse(arg2.isURI());
	}
	
	@Test
	public void testIsVar() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.VAR, "x");
		assertTrue(arg.isVar());
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.URI, "http://example.com");
		assertFalse(arg2.isVar());
	}
	
	@Test
	public void testIsLiteral() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.LITERAL, "test");
		assertTrue(arg.isLiteral());
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.VAR, "x");
		assertFalse(arg2.isLiteral());
	}
	
	@Test
	public void testIsBnode() 
	{
		QueryArgument arg = new QueryArgument(QueryArgumentType.BNODE, "x");
		assertTrue(arg.isBnode());
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.VAR, "x");
		assertFalse(arg2.isBnode());
	}
	
	@Test
	public void testEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(QueryArgumentType.VAR, "x");
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.VAR, "x");
		assertTrue(arg1.equals(arg2));
	}
	
	@Test
	public void testEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(QueryArgumentType.VAR, "x");
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.LITERAL, "x");
		assertFalse(arg1.equals(arg2));
	}
	
	
	@Test
	public void testHashCodeEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(QueryArgumentType.VAR, "x");
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.VAR, "x");
		assertEquals(arg1.hashCode(), arg2.hashCode());
	}
	
	@Test
	public void testHashCodeEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(QueryArgumentType.VAR, "x");
		QueryArgument arg2 = new QueryArgument(QueryArgumentType.LITERAL, "x");
		assertFalse(arg1.hashCode() == arg2.hashCode());
	}
}
