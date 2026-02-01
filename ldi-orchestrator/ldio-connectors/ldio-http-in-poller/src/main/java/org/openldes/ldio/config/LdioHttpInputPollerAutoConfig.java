package org.openldes.ldio.config;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioHttpInputPoller;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.requestexecutor.LdioRequestExecutorSupplier;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioHttpInputPoller.NAME;

@Configuration
public class LdioHttpInputPollerAutoConfig {
	private static final LdioRequestExecutorSupplier ldioRequestExecutorSupplier = new LdioRequestExecutorSupplier();

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public HttpInputPollerConfigurator httpInputPollerConfigurator(ObservationRegistry observationRegistry) {
		return new HttpInputPollerConfigurator(observationRegistry);
	}

	public static class HttpInputPollerConfigurator implements LdioInputConfigurator {
		private final ObservationRegistry observationRegistry;

		public HttpInputPollerConfigurator(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
		}

		@Override
		public LdioHttpInputPoller configure(LdiAdapter adapter, ComponentExecutor executor,
											 ApplicationEventPublisher applicationEventPublisher, ComponentProperties properties) {
			final var ldioObserver = LdioObserver.register(NAME, properties.getPipelineName(), observationRegistry);
			final var requestExecutor = ldioRequestExecutorSupplier.getRequestExecutor(properties);
			final var ldioHttpInPollerProperties = LdioHttpInputPollerProperties.fromComponentProperties(properties);
			final var httpInputPoller = new LdioHttpInputPoller(executor, adapter, ldioObserver, requestExecutor, ldioHttpInPollerProperties, applicationEventPublisher);
			httpInputPoller.start();
			return httpInputPoller;
		}

		@Override
		public boolean isAdapterRequired() {
			return true;
		}
	}

}
