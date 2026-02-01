package org.openldes.ldio.config;

import org.openldes.ldi.rdf.formatter.LdiRdfWriter;
import org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioAmqpOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.core.JmsTemplate;

import static org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties.RDF_WRITER;
import static org.openldes.ldio.config.AmqpConfig.*;

@Configuration
public class LdioAmqpOutAutoConfig {

    @SuppressWarnings("java:S6830")
    @Bean(LdioAmqpOut.NAME)
    public LdioJmsOutConfigurator ldioConfigurator() {
        return new LdioJmsOutConfigurator();
    }

    public static class LdioJmsOutConfigurator implements LdioOutputConfigurator {

        @Override
        public LdiComponent configure(ComponentProperties config) {
            final var pipelineName = config.getPipelineName();
            final var remoteUrl = new RemoteUrlExtractor(config).getRemoteUrl();
            final var connectionFactory =
                    new JmsConnectionFactory(config.getProperty(USERNAME), config.getProperty(PASSWORD), remoteUrl);
            final var jmsTemplate = new JmsTemplate(connectionFactory);
            final var rdfWriter = LdiRdfWriter.getRdfWriter(
                    new LdiRdfWriterProperties(config.extractNestedProperties(RDF_WRITER).getConfig())
            );
            return new LdioAmqpOut(config.getProperty(QUEUE), jmsTemplate, pipelineName, rdfWriter);
        }

    }
}
