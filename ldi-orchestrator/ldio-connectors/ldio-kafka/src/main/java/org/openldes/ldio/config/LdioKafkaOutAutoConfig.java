package org.openldes.ldio.config;

import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioKafkaOut.NAME;

@Configuration
public class LdioKafkaOutAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldiKafkaOutConfigurator() {
		return new LdioKafkaOutProcessorConfigurator();
	}

}
