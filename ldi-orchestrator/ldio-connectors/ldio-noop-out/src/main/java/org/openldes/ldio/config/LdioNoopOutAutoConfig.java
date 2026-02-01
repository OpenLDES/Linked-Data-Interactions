package org.openldes.ldio.config;

import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioNoopOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioNoopOut.NAME;

@Configuration
public class LdioNoopOutAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldiHttpOutConfigurator() {
		return new LdioHttpOutConfigurator();
	}

	public static class LdioHttpOutConfigurator implements LdioOutputConfigurator {

		@Override
		public LdiComponent configure(ComponentProperties config) {
			return new LdioNoopOut();
		}
	}
}
