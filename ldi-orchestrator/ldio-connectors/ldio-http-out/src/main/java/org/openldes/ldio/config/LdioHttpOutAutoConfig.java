package org.openldes.ldio.config;

import org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioHttpOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.openldes.ldio.requestexecutor.LdioRequestExecutorSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldi.rdf.formatter.LdiRdfWriterProperties.RDF_WRITER;
import static org.openldes.ldio.LdioHttpOut.NAME;

@Configuration
public class LdioHttpOutAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldiHttpOutConfigurator() {
		return new LdioHttpOutConfigurator();
	}

	public static class LdioHttpOutConfigurator implements LdioOutputConfigurator {

		@Override
		public LdiComponent configure(ComponentProperties config) {
			final RequestExecutor requestExecutor = new LdioRequestExecutorSupplier().getRequestExecutor(config);

			String targetURL = config.getProperty("endpoint");

			return new LdioHttpOut(requestExecutor, targetURL,
					new LdiRdfWriterProperties(config.extractNestedProperties(RDF_WRITER).getConfig()));
		}
	}
}
