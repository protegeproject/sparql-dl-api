// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.List;

import de.derivo.sparqldlapi.Var;
import org.junit.Test;

import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.QueryAtom;
import de.derivo.sparqldlapi.impl.QueryBindingImpl;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import de.derivo.sparqldlapi.types.QueryAtomType;
import org.semanticweb.owlapi.model.IRI;

/**
 * A jUnit 4.0 test class to test QueryAtom
 * 
 * @author Mario Volke
 */
public class QueryAtomTest 
{
	@Test
	public void testIsBound() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		QueryAtom atom = new QueryAtom(QueryAtomType.CLASS, arg);
		assertTrue(atom.isBound());
		
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		QueryAtom atom2 = new QueryAtom(QueryAtomType.CLASS, arg2);
		assertFalse(atom2.isBound());
	}
	
	@Test
	public void testGetType() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		QueryAtom atom = new QueryAtom(QueryAtomType.CLASS, arg);
		assertEquals(atom.getType(), QueryAtomType.CLASS);
	}
	
	@Test
	public void testHasType() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		QueryAtom atom = new QueryAtom(QueryAtomType.CLASS, arg);
		assertTrue(atom.hasType(QueryAtomType.CLASS));
		assertFalse(atom.hasType(QueryAtomType.DATA_PROPERTY));
	}
	
	@Test
	public void testGetArguments() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		QueryAtom atom = new QueryAtom(QueryAtomType.CLASS, arg);
		
		List<QueryArgument> args = new LinkedList<QueryArgument>();
		args.add(arg);
		assertEquals(atom.getArguments(), args);
	}
	
	@Test
	public void testBind() 
	{
		QueryArgument arg = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(IRI.create("http://example.com"));
		QueryAtom atom = new QueryAtom(QueryAtomType.CLASS, arg);
		QueryBindingImpl binding = new QueryBindingImpl();
		binding.set(arg, arg2);
		QueryAtom boundAtom = new QueryAtom(QueryAtomType.CLASS, arg2);
		assertEquals(atom.bind(binding), boundAtom);
	}
	
	@Test
	public void testEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		QueryAtom atom1 = new QueryAtom(QueryAtomType.CLASS, arg1);
		QueryAtom atom2 = new QueryAtom(QueryAtomType.CLASS, arg2);
		assertTrue(atom1.equals(atom2));
	}
	
	@Test
	public void testEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		QueryAtom atom1 = new QueryAtom(QueryAtomType.CLASS, arg1);
		QueryAtom atom2 = new QueryAtom(QueryAtomType.DATA_PROPERTY, arg2);
		assertFalse(atom1.equals(atom2));
	}
	
	
	@Test
	public void testHashCodeEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		QueryAtom atom1 = new QueryAtom(QueryAtomType.CLASS, arg1);
		QueryAtom atom2 = new QueryAtom(QueryAtomType.CLASS, arg2);
		assertEquals(atom1.hashCode(), atom2.hashCode());
	}
	
	@Test
	public void testHashCodeEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		QueryAtom atom1 = new QueryAtom(QueryAtomType.CLASS, arg1);
		QueryAtom atom2 = new QueryAtom(QueryAtomType.DATA_PROPERTY, arg2);
		assertFalse(atom1.hashCode() == atom2.hashCode());
	}
}
