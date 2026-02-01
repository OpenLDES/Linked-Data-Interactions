package org.openldes.ldio.config;

import org.openldes.ldi.extractor.EmptyPropertyExtractor;
import org.openldes.ldi.extractor.PropertyExtractor;
import org.openldes.ldi.extractor.PropertyPathExtractor;
import org.openldes.ldio.RequestPropertyPathExtractors;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;

import static org.openldes.ldio.config.LdioHttpEnricherProperties.*;

class PropertyPathExtractorConverter {

	private final ComponentProperties config;

	PropertyPathExtractorConverter(ComponentProperties config) {
		this.config = config;
	}

	RequestPropertyPathExtractors mapToPropertyPathExtractors() {
		final var urlPropertyPathExtractor = PropertyPathExtractor.from(config.getProperty(URL_PROPERTY_PATH));
		final var bodyPropertyPathExtractor = createPropertyPathExtractor(BODY_PROPERTY_PATH);
		final var headerPropertyPathExtractor = createPropertyPathExtractor(HEADER_PROPERTY_PATH);
		final var httpMethodPropertyPathExtractor = createPropertyPathExtractor(HTTP_METHOD_PROPERTY_PATH);
		return new RequestPropertyPathExtractors(
				urlPropertyPathExtractor,
				bodyPropertyPathExtractor,
				headerPropertyPathExtractor,
				httpMethodPropertyPathExtractor);
	}

	private PropertyExtractor createPropertyPathExtractor(String property) {
		return config
				.getOptionalProperty(property)
				.map(PropertyPathExtractor::from)
				.map(PropertyExtractor.class::cast)
				.orElse(new EmptyPropertyExtractor());
	}

}
