// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Set;

import de.derivo.sparqldlapi.Var;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryBinding;
import de.derivo.sparqldlapi.impl.QueryBindingImpl;
import de.derivo.sparqldlapi.types.QueryArgumentType;

/**
 * A jUnit 4.0 test class to test the implementation of QueryBinding
 * 
 * @author Mario Volke
 */
public class QueryBindingTest 
{
	private QueryArgument varArg;
	private QueryArgument uriArg;
	private QueryArgument varArg2;
	private QueryArgument uriArg2;
	
	@Before
	public void setUp() 
	{
		varArg = new QueryArgument(new Var("x"));
		uriArg = new QueryArgument(new Var("test"));
		varArg2 = new QueryArgument(new Var("y"));
		uriArg2 = new QueryArgument(new Var("test2"));
	}
	
	@After
	public void tearDown()
	{
		varArg = null;
		uriArg = null;
		varArg2 = null;
		uriArg2 = null;
	}
	
	@Test
	public void testGet() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(varArg, uriArg);
		assertEquals(binding.get(varArg), uriArg);
	}
	
	@Test
	public void testGetNull() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(varArg, uriArg);
		assertTrue(binding.get(uriArg) == null);
	}
	
	@Test
	public void testGetBoundArgs() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(varArg, uriArg);
		Set<QueryArgument> boundArgs = new HashSet<QueryArgument>();
		boundArgs.add(varArg);
		assertEquals(binding.getBoundArgs(), boundArgs);
	}
	
	@Test
	public void testIsBound() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(varArg, uriArg);
		assertTrue(binding.isBound(varArg));
		assertFalse(binding.isBound(uriArg));
	}
	
	@Test
	public void testSize() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		assertTrue(binding.size() == 0);
		binding.set(varArg, uriArg);
		assertTrue(binding.size() == 1);
		// no duplicate entries
		binding.set(varArg, uriArg);
		assertTrue(binding.size() == 1);
		binding.set(varArg2, uriArg2);
		assertTrue(binding.size() == 2);
	}
	
	@Test
	public void testIsEmpty() 
	{
		QueryBindingImpl binding = new QueryBindingImpl();
		assertTrue(binding.isEmpty());
		binding.set(varArg, uriArg);
		assertFalse(binding.isEmpty());
	}
	
	@Test
	public void testEqualsTrue() 
	{
		QueryBindingImpl binding1 = new QueryBindingImpl();
		QueryBindingImpl binding2 = new QueryBindingImpl();
		binding1.set(varArg, uriArg);
		binding2.set(varArg, uriArg);
		assertTrue(((QueryBinding)binding1).equals((QueryBinding)binding2));
	}
	
	@Test
	public void testEqualsFalse() 
	{
		QueryBindingImpl binding1 = new QueryBindingImpl();
		QueryBindingImpl binding2 = new QueryBindingImpl();
		binding1.set(varArg, uriArg);
		binding2.set(varArg, uriArg2);
		assertFalse(((QueryBinding)binding1).equals((QueryBinding)binding2));
	}
	
	
	@Test
	public void testHashCodeEqualsTrue() 
	{
		QueryBindingImpl binding1 = new QueryBindingImpl();
		QueryBindingImpl binding2 = new QueryBindingImpl();
		binding1.set(varArg, uriArg);
		binding2.set(varArg, uriArg);
		assertEquals(((QueryBinding)binding1).hashCode(), ((QueryBinding)binding2).hashCode());
	}
	
	@Test
	public void testHashCodeEqualsFalse() 
	{
		QueryBindingImpl binding1 = new QueryBindingImpl();
		QueryBindingImpl binding2 = new QueryBindingImpl();
		binding1.set(varArg, uriArg);
		binding2.set(varArg, uriArg2);
		assertFalse(((QueryBinding)binding1).hashCode() == ((QueryBinding)binding2).hashCode());
	}
}
