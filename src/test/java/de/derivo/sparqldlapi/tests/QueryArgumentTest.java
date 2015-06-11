// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import de.derivo.sparqldlapi.Var;
import org.junit.Test;

import de.derivo.sparqldlapi.QueryArgument;
import de.derivo.sparqldlapi.types.QueryArgumentType;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLLiteral;
import uk.ac.manchester.cs.owl.owlapi.OWLLiteralImpl;

/**
 * A jUnit 4.0 test class to test QueryArgument
 * 
 * @author Mario Volke
 */
@RunWith(MockitoJUnitRunner.class)
public class QueryArgumentTest 
{
	@Test
	public void testGetValue() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		assertEquals(arg.getValueAsIRI(), IRI.create("http://example.com"));
	}
	
	@Test
	public void testGetType() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		assertEquals(arg.getType(), QueryArgumentType.URI);
	}
	
	@Test
	public void testHasType() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		assertTrue(arg.hasType(QueryArgumentType.URI));
		assertFalse(arg.hasType(QueryArgumentType.VAR));
	}
	
	@Test
	public void testIsURI() 
	{
		QueryArgument arg = new QueryArgument(IRI.create("http://example.com"));
		assertTrue(arg.isURI());
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		assertFalse(arg2.isURI());
	}
	
	@Test
	public void testIsVar() 
	{
		QueryArgument arg = new QueryArgument(new Var("x"));
		assertTrue(arg.isVar());
		QueryArgument arg2 = new QueryArgument(IRI.create("http://example.com"));
		assertFalse(arg2.isVar());
	}
	
	@Test
	public void testIsLiteral() 
	{
		QueryArgument arg = new QueryArgument(mock(OWLLiteral.class));
		assertTrue(arg.isLiteral());
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		assertFalse(arg2.isLiteral());
	}
	
	@Test
	public void testIsBnode() 
	{
		QueryArgument arg = new QueryArgument(mock(OWLAnonymousIndividual.class));
		assertTrue(arg.isBnode());
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		assertFalse(arg2.isBnode());
	}
	
	@Test
	public void testEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		assertTrue(arg1.equals(arg2));
	}
	
	@Test
	public void testEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(mock(OWLLiteral.class));
		assertFalse(arg1.equals(arg2));
	}
	
	
	@Test
	public void testHashCodeEqualsTrue() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(new Var("x"));
		assertEquals(arg1.hashCode(), arg2.hashCode());
	}
	
	@Test
	public void testHashCodeEqualsFalse() 
	{
		QueryArgument arg1 = new QueryArgument(new Var("x"));
		QueryArgument arg2 = new QueryArgument(mock(OWLLiteral.class));
		assertFalse(arg1.hashCode() == arg2.hashCode());
	}
}
