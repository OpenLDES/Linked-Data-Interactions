package org.openldes.ldio.config;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.ArchiveFileCrawler;
import org.openldes.ldio.LdioArchiveFileIn;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import io.micrometer.observation.ObservationRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class LdioArchiveFileInAutoConfig {
	public static final String ARCHIVE_ROOT_DIR_PROP = "archive-root-dir";
	public static final String SOURCE_FORMAT = "source-format";

	@SuppressWarnings("java:S6830")
	@Bean(LdioArchiveFileIn.NAME)
	public LdioInputConfigurator ldiArchiveFileInConfigurator(ObservationRegistry observationRegistry) {
		return new LdioInputConfigurator() {
			@Override
			public LdioInput configure(LdiAdapter adapter,
									   ComponentExecutor executor,
									   ApplicationEventPublisher applicationEventPublisher,
									   ComponentProperties config) {
				ArchiveFileCrawler archiveFileCrawler = new ArchiveFileCrawler(getArchiveDirectoryPath(config));
				Lang hintLang = getSourceFormat(config);
				String pipelineName = config.getPipelineName();

				return new LdioArchiveFileIn(pipelineName, executor, observationRegistry, applicationEventPublisher, archiveFileCrawler, hintLang);
			}

			@Override
			public boolean isAdapterRequired() {
				return false;
			}

			private Path getArchiveDirectoryPath(ComponentProperties config) {
				String directory = config.getProperty(ARCHIVE_ROOT_DIR_PROP);
				return Paths.get(directory);
			}

			private Lang getSourceFormat(ComponentProperties config) {
				return config.getOptionalProperty(SOURCE_FORMAT)
						.map(RDFLanguages::nameToLang)
						.orElse(null);
			}

		};
	}

}
