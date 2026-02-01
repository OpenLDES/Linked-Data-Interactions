package org.openldes.ldio.config.config;

import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldio.config.LdioSkolemisationTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioSkolemisationTransformerAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(LdioSkolemisationTransformer.NAME)
	public LdioTransformerConfigurator ldioConfigurator() {
		return new LdioSkolemisationTransformerConfigurator();
	}

	public static class LdioSkolemisationTransformerConfigurator implements LdioTransformerConfigurator {
		@Override
		public LdioTransformer configure(ComponentProperties config) {
			String skolemDomain = config.getProperty("skolem-domain");
			return new LdioSkolemisationTransformer(new SkolemisationTransformer(skolemDomain));
		}
	}
}
