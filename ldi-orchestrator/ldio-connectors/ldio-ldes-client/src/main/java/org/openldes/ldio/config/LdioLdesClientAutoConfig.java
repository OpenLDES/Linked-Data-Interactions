package org.openldes.ldio.config;

import org.openldes.ldio.LdioLdesClient;
import org.openldes.ldio.management.status.ClientStatusService;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioLdesClientAutoConfig {
	@SuppressWarnings("java:S6830")
	@Bean(LdioLdesClient.NAME)
	public LdioInputConfigurator ldioConfigurator(ClientStatusService clientStatusService, ObservationRegistry observationRegistry) {
		return new LdioLdesClientConfigurator(clientStatusService, observationRegistry);
	}

}
