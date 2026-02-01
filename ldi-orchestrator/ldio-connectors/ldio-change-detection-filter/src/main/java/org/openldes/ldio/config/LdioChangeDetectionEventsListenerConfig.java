package org.openldes.ldio.config;

import org.openldes.ldio.LdioChangeDetectionFilter;
import org.openldes.ldio.pipeline.status.LdioPipelineEventsListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LdioChangeDetectionEventsListenerConfig {
	@Bean
	public LdioPipelineEventsListener<LdioChangeDetectionFilter> ldioPipelineEventsListener() {
		return new LdioPipelineEventsListener.Builder<LdioChangeDetectionFilter>()
				.withStopBehavior(LdioChangeDetectionFilter::shutdown)
				.build();
	}
}
