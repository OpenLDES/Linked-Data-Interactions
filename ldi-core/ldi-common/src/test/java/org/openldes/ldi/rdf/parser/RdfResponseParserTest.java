package org.openldes.ldi.rdf.parser;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RiotException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdfResponseParserTest {

	private static final String SUBJECT = "https://example.com/member";
	private static final String PREDICATE = "https://example.com/value";

	@Test
	void acceptHeaderAdvertisesAllRequiredLdesSerializations() {
		for (String mediaType : List.of(
				"application/n-quads",
				"application/n-triples",
				"application/trig",
				"text/turtle",
				"application/ld+json")) {
			assertTrue(RdfResponseParser.ACCEPT_HEADER.contains(mediaType));
		}
	}

	@Test
	void responseContentTypeOverridesConfiguredFallback() {
		final Dataset dataset = RdfResponseParser.parseDataset(
				("<%s> <%s> \"value\" .".formatted(SUBJECT, PREDICATE)).getBytes(StandardCharsets.UTF_8),
				"application/n-triples; charset=UTF-8",
				"https://example.com/",
				Lang.JSONLD);

		assertTrue(dataset.getDefaultModel().contains(
				dataset.getDefaultModel().createResource(SUBJECT),
				dataset.getDefaultModel().createProperty(PREDICATE),
				"value"));
	}

	@Test
	void nQuadsNamedGraphsArePreserved() {
		final String graph = "https://example.com/graph";
		final Dataset dataset = RdfResponseParser.parseDataset(
				("<%s> <%s> \"value\" <%s> .".formatted(SUBJECT, PREDICATE, graph))
						.getBytes(StandardCharsets.UTF_8),
				"application/n-quads",
				"https://example.com/",
				Lang.TURTLE);

		final var graphNames = dataset.listNames();
		assertEquals(graph, graphNames.next());
		assertFalse(graphNames.hasNext());
		assertTrue(dataset.getNamedModel(graph).contains(
				dataset.getNamedModel(graph).createResource(SUBJECT),
				dataset.getNamedModel(graph).createProperty(PREDICATE),
				"value"));
	}

	@Test
	void configuredLanguageIsUsedWhenContentTypeIsMissing() {
		final Dataset dataset = RdfResponseParser.parseDataset(
				"<member> <value> \"value\" .".getBytes(StandardCharsets.UTF_8),
				null,
				"https://example.com/",
				Lang.TURTLE);

		assertTrue(dataset.getDefaultModel().contains(
				dataset.getDefaultModel().createResource(SUBJECT),
				dataset.getDefaultModel().createProperty(PREDICATE),
				"value"));
	}

	@Test
	void unsupportedContentTypeIsRejected() {
		assertThrows(RiotException.class, () -> RdfResponseParser.parseDataset(
				new byte[0],
				"text/html",
				"https://example.com/",
				Lang.TURTLE));
	}
}
