package org.openldes.ldio.config;

import org.openldes.ldi.NgsiV2ToLdAdapter;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioNgsiv2ToLdAdapterAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean("Ldio:NgsiV2ToLdAdapter")
	public LdioAdapterConfigurator ldioAdapterConfigurator() {
		return new LdioSparqlConstructProcessorConfigurator();
	}

	public static class LdioSparqlConstructProcessorConfigurator implements LdioAdapterConfigurator {
		@Override
		public LdiAdapter configure(ComponentProperties config) {
			String dataIdentifier = config.getProperty("data-identifier");
			String coreContext = config.getProperty("core-context");
			String ldContext = config.getOptionalProperty("ld-context").orElse(null);
			return new NgsiV2ToLdAdapter(dataIdentifier, coreContext, ldContext);
		}
	}
}
