package org.openldes.ldio;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObserveConfiguration {
	public static final String ERROR_TEMPLATE = "ERROR - when='{}', problem='{}'";

	@Bean
	public ObservationRegistry observationRegistry() {
		return ObservationRegistry.create();
	}
}
