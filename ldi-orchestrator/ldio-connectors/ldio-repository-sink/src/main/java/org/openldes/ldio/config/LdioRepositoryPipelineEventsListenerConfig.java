package org.openldes.ldio.config;

import org.openldes.ldio.LdioRepositorySink;
import org.openldes.ldio.pipeline.status.LdioPipelineEventsListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioRepositoryPipelineEventsListenerConfig {
	@Bean
	public LdioPipelineEventsListener<LdioRepositorySink> ldioRepositoryMaterialiserLdioPipelineEventsListener() {
		return new LdioPipelineEventsListener.Builder<LdioRepositorySink>()
				.withStartBehavior(LdioRepositorySink::start)
				.withResumeBehavior(LdioRepositorySink::start)
				.withStopBehavior(LdioRepositorySink::shutdown)
				.withPauseBehavior(materialiser -> {
					materialiser.sendToSink();
					materialiser.shutdown();
				})
				.build();
	}
}
