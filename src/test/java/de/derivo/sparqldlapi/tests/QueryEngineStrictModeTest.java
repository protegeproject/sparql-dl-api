// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryEngine;
import de.derivo.sparqldlapi.exceptions.QueryEngineException;
import de.derivo.sparqldlapi.exceptions.QueryParserException;

/**
 * Test for exceptions thrown in QueryEngine strict mode
 * 
 * @author Mario Volke
 */
public class QueryEngineStrictModeTest 
{
	private static final String ONTOLOGY_PREFIX = "http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#";
	private static OWLOntologyManager manager;
	private static OWLReasoner reasoner;
	private QueryEngine engine;
	
	@BeforeClass
	public static void oneTimeSetUp()
	{
		try {
			// Create our ontology manager in the usual way.
			manager = OWLManager.createOWLOntologyManager();

			OWLOntology ont = manager.loadOntologyFromOntologyDocument(IRI.create("http://www.w3.org/TR/owl-guide/wine.rdf"));

			// We need to create an instance of Reasoner.
			StructuralReasonerFactory factory = new StructuralReasonerFactory();
			reasoner = factory.createReasoner(ont);
			reasoner.precomputeInferences();
        }
        catch(UnsupportedOperationException exception) {
            System.out.println("Unsupported reasoner operation.");
        }
        catch(OWLOntologyCreationException e) {
            System.out.println("Could not load the wine ontology: " + e.getMessage());
        }
	}
	
	@Before
	public void setUp()
	{
		engine = QueryEngine.create(manager, reasoner, true);
	}
	
	@After
	public void tearDown()
	{
		engine = null;
	}
	
	// Class atom
	
	@Test(expected = QueryEngineException.class)
	public void testClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Class(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testClassNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Class(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// Individual atom
	
	@Test(expected = QueryEngineException.class)
	public void testIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Individual(<http://example.com>) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testIndividualNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Individual(<" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// Type atom
	
	@Test(expected = QueryEngineException.class)
	public void testTypeClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Type(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testTypeNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Type(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testTypeIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Type(<http://example.com>, ?x) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testTypeNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Type(<" + ONTOLOGY_PREFIX + "Riesling>, ?x) }");
		engine.execute(query);
	}
	
	// DirectType atom
	
	@Test(expected = QueryEngineException.class)
	public void testDirectTypeClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectType(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectTypeNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectType(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectTypeIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectType(<http://example.com>, ?x) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testDirectTypeNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectType(<" + ONTOLOGY_PREFIX + "Riesling>, ?x) }");
		engine.execute(query);
	}
	
	// PropertyValue atom
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(<http://example.com>, ?x, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(<" + ONTOLOGY_PREFIX + "Riesling>, ?x, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValuePropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <http://example.com>, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <" + ONTOLOGY_PREFIX + "Riesling>, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueNotLiteral()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <" + ONTOLOGY_PREFIX + "yearValue>, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueNotUri()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <" + ONTOLOGY_PREFIX + "locatedIn>, \"literal\") }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueIndividualUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <" + ONTOLOGY_PREFIX + "locatedIn>, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyValueNotIndividual2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { PropertyValue(?x, <" + ONTOLOGY_PREFIX + "locatedIn>, <" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// SameAs atom
	
	@Test(expected = QueryEngineException.class)
	public void testSameAsIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SameAs(<http://example.com>, ?x) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testSameAsNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SameAs(<" + ONTOLOGY_PREFIX + "Riesling>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSameAsIndividualUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SameAs(?x, <http://example.com>) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testSameAsNotIndividual2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SameAs(?x, <" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// DifferentFrom atom
	
	@Test(expected = QueryEngineException.class)
	public void testDifferentFromIndividualUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DifferentFrom(<http://example.com>, ?x) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testDifferentFromNotIndividual()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DifferentFrom(<" + ONTOLOGY_PREFIX + "Riesling>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDifferentFromIndividualUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DifferentFrom(?x, <http://example.com>) }");
		engine.execute(query);
	}

	@Test(expected = QueryEngineException.class)
	public void testDifferentFromNotIndividual2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DifferentFrom(?x, <" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// SubClassOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testSubClassOfClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubClassOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubClassOfNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubClassOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubClassOfClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubClassOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubClassOfNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubClassOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// DirectSubClassOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubClassOfClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubClassOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubClassOfNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubClassOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubClassOfClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubClassOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubClassOfNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubClassOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// StrictSubClassOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubClassOfClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubClassOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubClassOfNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubClassOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubClassOfClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubClassOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubClassOfNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubClassOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// EquivalentClass atom
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentClassClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentClass(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentClassNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentClass(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentClassClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentClass(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentClassNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentClass(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// DisjointWith atom
	
	@Test(expected = QueryEngineException.class)
	public void testDisjointWithClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DisjointWith(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDisjointWithNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DisjointWith(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDisjointWithClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DisjointWith(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDisjointWithNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DisjointWith(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// ComplementOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testComplementOfClassUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ComplementOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testComplementOfNotClass()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ComplementOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testComplementOfClassUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ComplementOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testComplementOfNotClass2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ComplementOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// SubPropertyOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testSubPropertyOfPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubPropertyOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubPropertyOfNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubPropertyOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubPropertyOfPropertyUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubPropertyOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSubPropertyOfNotProperty2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { SubPropertyOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// DirectSubPropertyOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubPropertyOfPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubPropertyOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubPropertyOfNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubPropertyOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubPropertyOfPropertyUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubPropertyOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDirectSubPropertyOfNotProperty2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DirectSubPropertyOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// StrictSubPropertyOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubPropertyOfPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubPropertyOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubPropertyOfNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubPropertyOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubPropertyOfPropertyUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubPropertyOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testStrictSubPropertyOfNotProperty2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { StrictSubPropertyOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// EquivalentProperty atom
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentPropertyPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentProperty(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentPropertyNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentProperty(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentPropertyPropertyUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentProperty(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testEquivalentPropertyNotProperty2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { EquivalentProperty(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// InverseOf atom
	
	@Test(expected = QueryEngineException.class)
	public void testInverseOfPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseOf(<http://example.com>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testInverseOfNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseOf(<" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>, ?x) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testInverseOfPropertyUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseOf(?x, <http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testInverseOfNotProperty2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseOf(?x, <" + ONTOLOGY_PREFIX + "StonleighSauvignonBlanc>) }");
		engine.execute(query);
	}
	
	// ObjectProperty atom
	
	@Test(expected = QueryEngineException.class)
	public void testObjectPropertyPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ObjectProperty(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testObjectPropertyNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { ObjectProperty(<" + ONTOLOGY_PREFIX + "yearValue>) }");
		engine.execute(query);
	}
	
	// InverseFunctional atom
	
	@Test(expected = QueryEngineException.class)
	public void testInverseFunctionalPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseFunctional(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testInverseFunctionalNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { InverseFunctional(<" + ONTOLOGY_PREFIX + "yearValue>) }");
		engine.execute(query);
	}
	
	// Symmetric atom
	
	@Test(expected = QueryEngineException.class)
	public void testSymmetricPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Symmetric(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testSymmetricNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Symmetric(<" + ONTOLOGY_PREFIX + "yearValue>) }");
		engine.execute(query);
	}
	
	// Transitive atom
	
	@Test(expected = QueryEngineException.class)
	public void testTransitivePropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Transitive(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testTransitiveNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Transitive(<" + ONTOLOGY_PREFIX + "yearValue>) }");
		engine.execute(query);
	}
	
	// Irreflexive atom
	
	@Test(expected = QueryEngineException.class)
	public void testIrreflexivePropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Irreflexive(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testIrreflexiveNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Irreflexive(<" + ONTOLOGY_PREFIX + "yearValue>) }");
		engine.execute(query);
	}
	
	// DataProperty atom
	
	@Test(expected = QueryEngineException.class)
	public void testDataPropertyPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DataProperty(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testDataPropertyNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { DataProperty(<" + ONTOLOGY_PREFIX + "locatedIn>) }");
		engine.execute(query);
	}
	
	// Property atom
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Property(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testPropertyNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Property(<" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// Functional atom
	
	@Test(expected = QueryEngineException.class)
	public void testFunctionalPropertyUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Functional(<http://example.com>) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testFunctionalNotProperty()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Functional(<" + ONTOLOGY_PREFIX + "Riesling>) }");
		engine.execute(query);
	}
	
	// Annotation atom
	
	@Test(expected = QueryEngineException.class)
	public void testAnnotationUndeclared()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Annotation(<http://example.com>, ?x, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testAnnotationUndeclared2()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Annotation(?x, <http://example.com>, ?y) }");
		engine.execute(query);
	}
	
	@Test(expected = QueryEngineException.class)
	public void testAnnotationNotAnnotation()
		throws QueryParserException, QueryEngineException
	{
		Query query = Query.create("SELECT * WHERE { Annotation(?x, <" + ONTOLOGY_PREFIX + "Riesling>, ?y) }");
		engine.execute(query);
	}
}
