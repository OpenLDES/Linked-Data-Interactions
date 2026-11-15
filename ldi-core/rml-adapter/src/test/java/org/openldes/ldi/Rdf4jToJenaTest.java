package org.openldes.ldi;

import org.apache.jena.graph.NodeFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Rdf4jToJenaTest {
    private final SimpleValueFactory values = SimpleValueFactory.getInstance();

    @Test
    void preservesTypedLiteralLexicalFormAndDefaultGraph() {
        var statement = values.createStatement(values.createIRI("urn:subject"),
                values.createIRI("urn:predicate"),
                values.createLiteral("007", values.createIRI("http://www.w3.org/2001/XMLSchema#integer")));

        var quad = Rdf4jToJena.toQuad(statement);

        assertTrue(quad.isDefaultGraph());
        assertEquals(NodeFactory.createURI("urn:subject"), quad.getSubject());
        assertEquals(NodeFactory.createURI("urn:predicate"), quad.getPredicate());
        assertEquals("007", quad.getObject().getLiteralLexicalForm());
        assertEquals("http://www.w3.org/2001/XMLSchema#integer", quad.getObject().getLiteralDatatypeURI());
    }

    @Test
    void preservesLanguageAndBlankNodesInNamedGraphs() {
        var statement = values.createStatement(values.createBNode("subject"),
                values.createIRI("urn:predicate"), values.createLiteral("bonjour", "fr"),
                values.createBNode("graph"));

        var quad = Rdf4jToJena.toQuad(statement);

        assertEquals(NodeFactory.createBlankNode("subject"), quad.getSubject());
        assertEquals(NodeFactory.createBlankNode("graph"), quad.getGraph());
        assertEquals(NodeFactory.createLiteralLang("bonjour", "fr"), quad.getObject());
    }

    @Test
    void preservesUnknownDatatypesAndIriGraphNames() {
        var statement = values.createStatement(values.createIRI("urn:subject"),
                values.createIRI("urn:predicate"), values.createLiteral("value", values.createIRI("urn:datatype")),
                values.createIRI("urn:graph"));

        var quad = Rdf4jToJena.toQuad(statement);

        assertEquals(NodeFactory.createURI("urn:graph"), quad.getGraph());
        assertEquals("value", quad.getObject().getLiteralLexicalForm());
        assertEquals("urn:datatype", quad.getObject().getLiteralDatatypeURI());
    }
}
