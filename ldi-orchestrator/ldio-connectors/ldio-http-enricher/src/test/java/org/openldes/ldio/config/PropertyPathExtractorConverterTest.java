package org.openldes.ldio.config;

import org.openldes.ldio.RequestPropertyPathExtractors;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.openldes.ldio.LdioHttpEnricher.NAME;
import static org.openldes.ldio.config.LdioHttpEnricherProperties.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PropertyPathExtractorConverterTest {

	@Test
	void mapToPropertyPathExtractors() {
		String url = "http://example.org/url";
		String header = "http://example.org/header";
		String body = "http://example.org/body";
		String httpMethod = "http://example.org/http-method";
		final Map<String, String> propertyMap = Map.of(
				URL_PROPERTY_PATH, url,
				HEADER_PROPERTY_PATH, header,
				BODY_PROPERTY_PATH, body,
				HTTP_METHOD_PROPERTY_PATH, httpMethod);
		final Model model = ModelFactory.createDefaultModel();
		model.add(ResourceFactory.createResource(), model.createProperty(url), url);
		model.add(ResourceFactory.createResource(), model.createProperty(header), header);
		model.add(ResourceFactory.createResource(), model.createProperty(body), body);
		model.add(ResourceFactory.createResource(), model.createProperty(httpMethod), httpMethod);

		RequestPropertyPathExtractors result = new PropertyPathExtractorConverter(new ComponentProperties("pipelineName", NAME, propertyMap))
				.mapToPropertyPathExtractors();

		assertEquals(url, result.urlPropertyPathExtractor().getProperties(model).get(0).toString());
		assertEquals(header, result.headerPropertyPathExtractor().getProperties(model).get(0).toString());
		assertEquals(body, result.bodyPropertyPathExtractor().getProperties(model).get(0).toString());
		assertEquals(httpMethod, result.httpMethodPropertyPathExtractor().getProperties(model).get(0).toString());
	}

}