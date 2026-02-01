package org.openldes.ldio.config;

import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.LdiAzureBlobOut;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdiAzureBlobOut.NAME;

@Configuration
public class LdioAzureBlobOutAutoConfig {
	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioOutputConfigurator ldioConfigurator() {
		return new LdioAzureBlobOutConfigurator();
	}

	public static class LdioAzureBlobOutConfigurator implements LdioOutputConfigurator {
		public static final String PROPERTY_LANG = "lang";
		public static final String PROPERTY_STORAGE_ACCOUNT_NAME = "storage-account-name";
		public static final String PROPERTY_CONNECTION_STRING = "connection-string";
		public static final String PROPERTY_BLOB_CONTAINER = "blob-container";
		public static final String PROPERTY_JSON_CONTEXT_URI = "json-context-uri";
		private static final String DEFAULT_OUTPUT_LANG = "n-quads";
		public static final String DEFAULT_JSON_CONTEXT_URI = "";

		@Override
		public LdiOutput configure(ComponentProperties properties) {
			String outputLanguage = properties.getOptionalProperty(PROPERTY_LANG)
					.orElse(DEFAULT_OUTPUT_LANG);
			String storageAccountName = properties.getProperty(PROPERTY_STORAGE_ACCOUNT_NAME);
			String connectionString = properties.getProperty(PROPERTY_CONNECTION_STRING);
			String blobContainer = properties.getProperty(PROPERTY_BLOB_CONTAINER);
			String jsonContextURI = properties.getOptionalProperty(PROPERTY_JSON_CONTEXT_URI)
					.orElse(DEFAULT_JSON_CONTEXT_URI);
			return new LdiAzureBlobOut(outputLanguage, storageAccountName, connectionString, blobContainer,
					jsonContextURI);
		}
	}
}
