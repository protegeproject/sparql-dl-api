// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import org.junit.Test;

import de.derivo.sparqldlapi.QueryToken;

/**
 * A jUnit 4.0 test class to test QueryToken
 * 
 * @author Mario Volke
 */
public class QueryTokenTest 
{
	@Test
	public void testGetToken() 
	{
		QueryToken token = new QueryToken("token", 1, 2, 3);
		assertEquals(token.getToken(), "token");
	}
	
	@Test
	public void testGetPos() 
	{
		QueryToken token = new QueryToken("token", 1, 2, 3);
		assertEquals(token.getPos(), 1);
	}
	
	@Test
	public void testGetCol() 
	{
		QueryToken token = new QueryToken("token", 1, 2, 3);
		assertEquals(token.getCol(), 2);
	}
	
	@Test
	public void testGetRow() 
	{
		QueryToken token = new QueryToken("token", 1, 2, 3);
		assertEquals(token.getRow(), 3);
	}
	
	@Test
	public void testEqualsTrue() 
	{
		QueryToken token1 = new QueryToken("token", 1, 2, 3);
		QueryToken token2 = new QueryToken("token", 1, 2, 3);
		assertTrue(token1.equals(token2));
	}
	
	@Test
	public void testEqualsFalse() 
	{
		QueryToken token1 = new QueryToken("token1", 1, 2, 3);
		QueryToken token2 = new QueryToken("token2", 1, 2, 3);
		assertFalse(token1.equals(token2));
	}
	
	
	@Test
	public void testHashCodeEqualsTrue() 
	{
		QueryToken token1 = new QueryToken("token", 1, 2, 3);
		QueryToken token2 = new QueryToken("token", 1, 2, 3);
		assertEquals(token1.hashCode(), token2.hashCode());
	}
	
	@Test
	public void testHashCodeEqualsFalse() 
	{
		QueryToken token1 = new QueryToken("token1", 1, 2, 3);
		QueryToken token2 = new QueryToken("token2", 1, 2, 3);
		assertFalse(token1.hashCode() == token2.hashCode());
	}
}
