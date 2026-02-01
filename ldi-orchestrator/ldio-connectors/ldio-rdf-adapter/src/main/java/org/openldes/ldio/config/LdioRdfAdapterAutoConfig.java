package org.openldes.ldio.config;

import org.openldes.ldi.RdfAdapter;
import org.openldes.ldi.rdf.parser.JenaContextProvider;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioRdfAdapterAutoConfig {

	public static final String MAX_JSONLD_CACHE_CAPACITY = "max-jsonld-cache-capacity";

	@SuppressWarnings("java:S6830")
	@Bean("Ldio:RdfAdapter")
	public LdioAdapterConfigurator ldioAdapterConfigurator() {
		return new LdioRdfConfigurator();
	}

	public static class LdioRdfConfigurator implements LdioAdapterConfigurator {

		@Override
		public LdiAdapter configure(ComponentProperties config) {
			final int maxCacheCapacity = config.getOptionalInteger(MAX_JSONLD_CACHE_CAPACITY).orElse(100);
			final var context = JenaContextProvider.create().withMaxJsonLdCacheCapacity(maxCacheCapacity).getContext();
			return new RdfAdapter(context);
		}

	}
}
