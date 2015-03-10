// Copyright (c) 2011. This source code is available under the terms of the GNU Lesser General Public License (LGPL)
// Author: Mario Volke <volke@derivo.de>
// derivo GmbH, James-Franck-Ring, 89081 Ulm 

package de.derivo.sparqldlapi.examples;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import de.derivo.sparqldlapi.Query;
import de.derivo.sparqldlapi.QueryEngine;
import de.derivo.sparqldlapi.QueryResult;
import de.derivo.sparqldlapi.exceptions.QueryEngineException;
import de.derivo.sparqldlapi.exceptions.QueryParserException;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/**
 * This examples show some extra queries and most notably the "OR WHERE" clause which
 * is useful to concatenate two conjunctive queries.
 * We use the OWL wine ontology for demonstration and the built-in StructuralReasoner as sample
 * reasoning system.
 * In case you use any other reasoning engine make sure you have the respective jars within your
 * classpath (note that you have to provide the resp. ReasonerFactory in this case).
 *
 * @author Mario Volke
 * @author Thorsten Liebig
 */
public class Example_Extended
{
	private static QueryEngine engine;

	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		try {
			// Create our ontology manager in the usual way.
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

			// Load a copy of the wine ontology.  We'll load the ontology from the web.
            OWLOntology ont = manager.loadOntologyFromOntologyDocument(IRI.create("http://www.w3.org/TR/owl-guide/wine.rdf"));

			// Create an instance of an OWL API reasoner (we use the OWL API built-in StructuralReasoner for the purpose of demonstration here)
            StructuralReasonerFactory factory = new StructuralReasonerFactory();
			OWLReasoner reasoner = factory.createReasoner(ont);
            // Optionally let the reasoner compute the most relevant inferences in advance
			reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS,InferenceType.OBJECT_PROPERTY_ASSERTIONS);

			// Create an instance of the SPARQL-DL query engine
			engine = QueryEngine.create(manager, reasoner);
			
            // Some queries which demonstrate more sophisticated language constructs of SPARQL-DL

            // The empty ASK is true by default
            processQuery(
				"ASK {}"
			);

            // The response to an empty SELECT is an empty response
            processQuery(
				"SELECT * WHERE {}"
			);

            // There can't be an instance of owl:Nothing. Therefore this query has no solutions.
            processQuery(
                "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
				"SELECT * WHERE { Type(?x,owl:Nothing) }"
			);

            // A complicated way to retrieve all individuals. Note that the WHERE keyword is optional.
            processQuery(
                "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
				"SELECT DISTINCT ?x { Type(?x,?y), ComplementOf(owl:Nothing,?y) }"
			);

            // All wines which are OffDry
            processQuery(
                "PREFIX wine: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#>\n" +
                "PREFIX food: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/food#>\n" +
				"SELECT DISTINCT ?w WHERE { PropertyValue(?w, wine:hasWineDescriptor, food:OffDry) }"
			);

            // A query returning pairs of results, namely all sources and fillers of yearValue
            processQuery(
                "PREFIX wine: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#>\n" +
				"SELECT DISTINCT ?w ?g WHERE { PropertyValue(?w, wine:yearValue, ?g)" +
				"}"
			);

            // The most specific types of wines of all wineries
            processQuery(
                "PREFIX wine: <http://www.w3.org/TR/2003/PR-owl-guide-20031209/wine#>\n" +
				"SELECT DISTINCT ?x ?y WHERE {\n" +
                    "Type(?x, wine:Winery), \n" +
                    "DirectType(?z, ?y), \n" +
                    "PropertyValue(?x, wine:producesWine, ?z)" +
				"}"
			);

            // All entities which are either object properties or classes
			processQuery(
				"SELECT ?i WHERE {" +
				    "ObjectProperty(?i) " +
				"} OR WHERE {" +
                    "Class(?i)" +
                "}"
			);

            // Equivalent query to the one above
            processQuery(
				"SELECT * WHERE {" +
				    "ObjectProperty(?i) " +
				"} OR WHERE {" +
                    "Class(?j)" +
                "}"
			);

        }
        catch(UnsupportedOperationException exception) {
            System.out.println("Unsupported reasoner operation.");
        }
        catch(OWLOntologyCreationException e) {
            System.out.println("Could not load the pizza ontology: " + e.getMessage());
        }
	}
	
	public static void processQuery(String q)
	{
		try {
			long startTime = System.currentTimeMillis();
			
			// Create a SPARQL-DL query
			Query query = Query.create(q);
			
			System.out.println("Excecute query:");
			System.out.println(q);
			System.out.println("-------------------------------------------------");
			
			// Execute the query and generate the result set
			QueryResult result = engine.execute(query);
			if(query.isAsk()) {
				System.out.print("Result: ");
				if(result.ask()) {
					System.out.println("yes");
				}
				else {
					System.out.println("no");
				}
			}
			else {
				if(!result.ask()) {
					System.out.println("Query has no solution.\n");
				}
				else {
					System.out.println("Results:");
					System.out.print(result);
					System.out.println("-------------------------------------------------");
					System.out.println("Size of result set: " + result.size());
				}
			}

			System.out.println("-------------------------------------------------");
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
