package org.openldes.ldio.config;

import org.openldes.ldio.LdioGeoJsonToWkt;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioGeoJsonToWkt.NAME;

@Configuration
public class LdioGeoJsonToWktAutoConfig {

	@Bean(NAME)
	public LdioTransformerConfigurator geoJsonToWktConfigurator() {
        return new LdioGeoJsonToWktConfigurator();
	}

    public static class LdioGeoJsonToWktConfigurator implements LdioTransformerConfigurator {
        public static final String TRANSFORM_TO_RDF_WKT = "transform-to-rdf+wkt-enabled";

        @Override
        public LdioTransformer configure(ComponentProperties config) {
            boolean transformToRdfWkt = config.getOptionalBoolean(TRANSFORM_TO_RDF_WKT).orElse(false);
            return new LdioGeoJsonToWkt(transformToRdfWkt);
        }
    }

}
