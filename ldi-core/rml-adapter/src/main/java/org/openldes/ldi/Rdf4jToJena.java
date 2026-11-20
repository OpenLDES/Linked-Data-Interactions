package org.openldes.ldi;

import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.sparql.core.Quad;
import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;

/** Converts CARML's RDF4J statements without depending on its older Jena API. */
final class Rdf4jToJena {
    private Rdf4jToJena() {
    }

    static Quad toQuad(Statement statement) {
        Node graph = statement.getContext() == null
                ? Quad.defaultGraphNodeGenerated : toNode(statement.getContext());
        return Quad.create(graph, toNode(statement.getSubject()),
                toNode(statement.getPredicate()), toNode(statement.getObject()));
    }

    private static Node toNode(Value value) {
        if (value instanceof IRI iri) {
            return NodeFactory.createURI(iri.stringValue());
        }
        if (value instanceof BNode blank) {
            return NodeFactory.createBlankNode(blank.getID());
        }
        if (value instanceof Literal literal) {
            return literal.getLanguage()
                    .map(language -> NodeFactory.createLiteralLang(literal.getLabel(), language))
                    .orElseGet(() -> NodeFactory.createLiteralDT(literal.getLabel(),
                            TypeMapper.getInstance().getSafeTypeByName(literal.getDatatype().stringValue())));
        }
        throw new IllegalArgumentException("Unsupported RDF4J value: " + value);
    }
}
