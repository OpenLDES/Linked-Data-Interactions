package org.openldes.ldio.config;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioKafkaIn;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioKafkaIn.NAME;

@Configuration
public class LdioKafkaInAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioKafkaInConfigurator ldioConfigurator(ObservationRegistry observationRegistry) {
		return new LdioKafkaInConfigurator(observationRegistry);
	}

	public static class LdioKafkaInConfigurator implements LdioInputConfigurator {
		private final ObservationRegistry observationRegistry;

		public LdioKafkaInConfigurator(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
		}

		@Override
		public LdioInput configure(LdiAdapter adapter, ComponentExecutor executor, ApplicationEventPublisher applicationEventPublisher, ComponentProperties config) {
			final String pipelineName = config.getPipelineName();
			final LdioObserver ldioObserver = LdioObserver.register(NAME, pipelineName, observationRegistry);
			return new LdioKafkaIn(executor, adapter, ldioObserver, applicationEventPublisher, config);
		}

		@Override
		public boolean isAdapterRequired() {
			return true;
		}
	}
}
