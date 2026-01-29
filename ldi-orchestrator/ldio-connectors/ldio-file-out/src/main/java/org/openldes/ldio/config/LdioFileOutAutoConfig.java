package org.openldes.ldio.config;

import org.openldes.ldi.timestampextractor.TimestampExtractor;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;
import org.openldes.ldi.timestampextractor.TimestampFromPathExtractor;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioFileOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.apache.jena.rdf.model.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

import static org.openldes.ldio.LdioFileOut.NAME;

@Configuration
public class LdioFileOutAutoConfig {

	public static final String ARCHIVE_ROOT_DIR_PROP = "archive-root-dir";
	public static final String TIMESTAMP_PATH_PROP = "timestamp-path";

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldiFileOutConfigurator() {
		return new LdioOutputConfigurator() {
			@Override
			public LdiComponent configure(ComponentProperties properties) {
				final String archiveDirectory = properties.getProperty(ARCHIVE_ROOT_DIR_PROP);
				final TimestampExtractor timestampExtractor = properties.getOptionalProperty(TIMESTAMP_PATH_PROP)
						.map(ResourceFactory::createProperty)
						.map(TimestampFromPathExtractor::new)
						.map(TimestampExtractor.class::cast)
						.orElseGet(TimestampFromCurrentTimeExtractor::new);

				return new LdioFileOut(timestampExtractor, removeTrailingSlash(archiveDirectory));
			}

			private String removeTrailingSlash(String path) {
				int indexLastChar = path.length() - 1;
				String substring = path.substring(indexLastChar);
				if (File.separator.equals(substring)) {
					return path.substring(0, indexLastChar);
				} else {
					return path;
				}
			}
		};
	}

}
