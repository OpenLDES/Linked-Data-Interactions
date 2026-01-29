package org.openldes.ldio.config;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioAmqpIn;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.valueobjects.LdioAmpqInProperties;
import io.micrometer.observation.ObservationRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.config.AmqpConfig.*;

@Configuration
public class LdioAmqpInAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(LdioAmqpIn.NAME)
	public LdioJmsInConfigurator ldioConfigurator(LdioAmqpInRegistrator ldioAmqpInRegistrator,
	                                              ObservationRegistry observationRegistry) {
		return new LdioJmsInConfigurator(ldioAmqpInRegistrator, observationRegistry);
	}

	public static class LdioJmsInConfigurator implements LdioInputConfigurator {

		private final LdioAmqpInRegistrator ldioAmqpInRegistrator;

		private final ObservationRegistry observationRegistry;

		public LdioJmsInConfigurator(LdioAmqpInRegistrator ldioAmqpInRegistrator, ObservationRegistry observationRegistry) {
			this.ldioAmqpInRegistrator = ldioAmqpInRegistrator;
			this.observationRegistry = observationRegistry;
		}

		@Override
		public LdioInput configure(LdiAdapter adapter, ComponentExecutor executor, ApplicationEventPublisher applicationEventPublisher, ComponentProperties config) {
			final String pipelineName = config.getPipelineName();
			final String remoteUrl = new RemoteUrlExtractor(config).getRemoteUrl();
			final JmsConfig jmsConfig = new JmsConfig(config.getProperty(USERNAME), config.getProperty(PASSWORD),
					remoteUrl, config.getProperty(QUEUE));
			final LdioAmpqInProperties properties = new LdioAmpqInProperties(pipelineName, getContentType(config), jmsConfig);
			final LdioObserver ldioObserver = LdioObserver.register(LdioAmqpIn.NAME, pipelineName, observationRegistry);
			return new LdioAmqpIn(executor, adapter, ldioObserver, ldioAmqpInRegistrator, properties, applicationEventPublisher);
		}

		@Override
		public boolean isAdapterRequired() {
			return true;
		}

		private String getContentType(ComponentProperties config) {
			return config
					.getOptionalProperty(CONTENT_TYPE)
					.map(RDFLanguages::contentTypeToLang)
					.map(Lang::getHeaderString)
					.orElse(Lang.NQUADS.getHeaderString());
		}

	}
}
