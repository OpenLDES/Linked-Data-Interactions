package org.openldes.ldio.config;

import org.openldes.ldio.LdioVersionMaterialiser;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioVersionMaterialiser.NAME;

@Configuration
public class LdioVersionMaterialiserAutoConfig {
	@Bean(NAME)
	public LdioTransformerConfigurator ldioConfigurator() {
		return new LdioVersionMaterialiserTransformerConfigurator();
	}

	public static class LdioVersionMaterialiserTransformerConfigurator implements LdioTransformerConfigurator {

		@Override
		public LdioTransformer configure(ComponentProperties config) {
			Model initModel = ModelFactory.createDefaultModel();

			Property versionOfProperty = config.getOptionalProperty("versionOf-property")
					.map(initModel::createProperty)
					.orElse(initModel.createProperty("http://purl.org/dc/terms/isVersionOf"));
			boolean restrictToMembers = config.getOptionalBoolean("restrict-to-members").orElse(false);

			return new LdioVersionMaterialiser(versionOfProperty, restrictToMembers);
		}
	}
}
