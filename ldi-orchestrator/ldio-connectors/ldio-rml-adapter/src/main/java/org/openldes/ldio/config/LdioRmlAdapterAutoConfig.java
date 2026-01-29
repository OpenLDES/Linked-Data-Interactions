package org.openldes.ldio.config;

import org.openldes.ldi.RmlAdapter;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioRmlAdapterAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean("Ldio:RmlAdapter")
	public LdioAdapterConfigurator ldioAdapterConfigurator() {
		return new LdioRmlAdapterProcessorConfigurator();
	}

	public static class LdioRmlAdapterProcessorConfigurator implements LdioAdapterConfigurator {
		public static final String MAPPING = "mapping";

		@Override
		public LdiAdapter configure(ComponentProperties config) {
			String rmlMapping = config.getOptionalPropertyFromFile(MAPPING).orElse(config.getProperty(MAPPING));

			return new RmlAdapter(rmlMapping);
		}
	}
}
