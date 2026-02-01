package org.openldes.ldio.config;

import org.openldes.ldi.JsonToLdAdapter;
import org.openldes.ldi.rdf.parser.JenaContextProvider;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioJsonToLdAdapterAutoConfig {
	public static final String NAME = "Ldio:JsonToLdAdapter";

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioAdapterConfigurator ldioJsonToLdAdapterConfigurator() {
		return new LdioJsonToLdConfigurator();
	}

	public static class LdioJsonToLdConfigurator implements LdioAdapterConfigurator {
		@Override
		public LdiComponent configure(ComponentProperties config) {
			String coreContext = config.getProperty("context");
			boolean forceContentType = config.getOptionalBoolean("force-content-type").orElse(false);

			final int maxCacheCapacity = config.getOptionalInteger("max-jsonld-cache-capacity").orElse(100);
			final var context = JenaContextProvider.create().withMaxJsonLdCacheCapacity(maxCacheCapacity).getContext();
			return new JsonToLdAdapter(coreContext, forceContentType, context);
		}
	}
}
