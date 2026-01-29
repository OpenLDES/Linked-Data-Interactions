package org.openldes.ldio.config;

import org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdiConsoleOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties.RDF_WRITER;
import static org.openldes.ldio.LdiConsoleOut.NAME;

@Configuration
public class LdioConsoleOutAutoConfig {
	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldioConfigurator() {
		return new LdioConsoleOutConfigurator();
	}

	public static class LdioConsoleOutConfigurator implements LdioOutputConfigurator {

		@Override
		public LdiComponent configure(ComponentProperties config) {
			LdiRdfWriterProperties writerProperties = new LdiRdfWriterProperties(
					config.extractNestedProperties(RDF_WRITER).getConfig());

			return new LdiConsoleOut(writerProperties);
		}
	}
}
