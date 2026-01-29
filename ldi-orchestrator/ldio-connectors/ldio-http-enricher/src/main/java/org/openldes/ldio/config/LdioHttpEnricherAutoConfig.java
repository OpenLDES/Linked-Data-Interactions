package org.openldes.ldio.config;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioHttpEnricher;
import org.openldes.ldio.RequestPropertyPathExtractors;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioAdapterConfigurator;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.openldes.ldio.requestexecutor.LdioRequestExecutorSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioHttpEnricher.NAME;
import static org.openldes.ldio.config.LdioHttpEnricherProperties.ADAPTER_CONFIG;
import static org.openldes.ldio.config.LdioHttpEnricherProperties.ADAPTER_NAME;

@Configuration
public class LdioHttpEnricherAutoConfig {

	@Bean(NAME)
	public LdioTransformerConfigurator ldioConfigurator(ConfigurableApplicationContext configContext) {
		return config -> {
			final LdiAdapter adapter = createAdapter(configContext, config);
			final RequestPropertyPathExtractors requestPropertyPaths = new PropertyPathExtractorConverter(config)
					.mapToPropertyPathExtractors();
			final RequestExecutor requestExecutor = new LdioRequestExecutorSupplier().getRequestExecutor(config);
			return new LdioHttpEnricher(adapter, requestExecutor, requestPropertyPaths);
		};
	}

	private LdiAdapter createAdapter(ConfigurableApplicationContext configContext, ComponentProperties config) {
		final String adapterBeanName = config.getProperty(ADAPTER_NAME);
		final LdioAdapterConfigurator ldioConfigurator = (LdioAdapterConfigurator) configContext
				.getBean(adapterBeanName);
		return (LdiAdapter) ldioConfigurator.configure(config.extractNestedProperties(ADAPTER_CONFIG));
	}

}
