// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.examples;

import java.io.IOException;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryEngine;
import de.derivo.sparqldlapi.QueryResult;
import de.derivo.sparqldlapi.exceptions.QueryEngineException;
import de.derivo.sparqldlapi.exceptions.QueryParserException;

import org.jdom.output.XMLOutputter;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;


/**
 * This example shows how to export a query result as a standard SPARQL-XML-result or JSON.
 * We use the OWL wine ontology for demonstration and the built-in StructuralReasoner as sample
 * reasoning system.
 * In case you use any other reasoning engine make sure you have the respective jars within your
 * classpath (note that you have to provide the resp. ReasonerFactory in this case).
 *
 * @author Mario Volke
 */
public class Example_XML_JSON
{	
	private static QueryEngine engine;

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		try {
			// Create an ontology manager in the usual way.
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

            // Load the wine ontology from the web.
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(IRI.create("http://www.w3.org/TR/owl-guide/wine.rdf"));

            // Create an instance of an OWL API reasoner (we use the OWL API built-in StructuralReasoner for the purpose of demonstration here)
            StructuralReasonerFactory factory = new StructuralReasonerFactory();
			OWLReasoner reasoner = factory.createReasoner(ont);
            // Optionally let the reasoner compute the most relevant inferences in advance
			reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS,InferenceType.OBJECT_PROPERTY_ASSERTIONS);

			// Create an instance of the SPARQL-DL query engine
			engine = QueryEngine.create(manager, reasoner);
			
			processQuery(
                "PREFIX wine: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#>\n" +
				"SELECT * WHERE {\n" +
				    "SubClassOf(wine:PinotBlanc, ?x),\n" +
				    "SubClassOf(?x, wine:Wine)\n" +
				"}"
			);
			
			processQuery(
                "PREFIX wine: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#>\n" +
				"ASK {\n" +
				    "SubClassOf(wine:PinotBlanc, wine:Wine)\n" +
				"}"
			);
        }
        catch(UnsupportedOperationException exception) {
            System.out.println("Unsupported reasoner operation.");
        }
        catch(OWLOntologyCreationException e) {
            System.out.println("Could not load the ontology: " + e.getMessage());
        }
	}
	
	public static void processQuery(String q)
	{
		try {
			long startTime = System.currentTimeMillis();
			
			// Create the SPARQL-DL query
			Query query = Query.create(q);
			
			System.out.println("Excecute query:");
			System.out.println(q);
			System.out.println("-------------------------------------------------");
			
			// Execute the query and generate the result set
			QueryResult result = engine.execute(query);
			
			// print as XML
			try {
				XMLOutputter out = new XMLOutputter();
				out.output(result.toXML(), System.out);
			} 
			catch(IOException e) {
				// ok, this should not happen
			}
			
			System.out.println("-------------------------------------------------");
			
			// print as JSON
			System.out.print(result.toJSON());
			
			System.out.println("-------------------------------------------------");
			System.out.println("Size of result set: " + result.size());
			System.out.println("Finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + "s\n");
		}
        catch(QueryParserException e) {
        	System.out.println("Query parser error: " + e);
        }
        catch(QueryEngineException e) {
        	System.out.println("Query engine error: " + e);
        }
	}
}
