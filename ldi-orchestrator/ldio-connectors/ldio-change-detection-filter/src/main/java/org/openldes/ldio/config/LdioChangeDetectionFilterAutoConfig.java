package org.openldes.ldio.config;

import org.openldes.ldi.ChangeDetectionFilter;
import org.openldes.ldi.repositories.HashedStateMemberRepository;
import org.openldes.ldio.LdioChangeDetectionFilter;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformerConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.openldes.ldio.LdioChangeDetectionFilter.NAME;

@Configuration
public class LdioChangeDetectionFilterAutoConfig {

	@SuppressWarnings("java:S6830")
	@Bean(NAME)
	public LdioTransformerConfigurator ldioChangeDetectionFilterConfigurator() {
		return new LdioChangeDetectionFilterConfigurator();
	}

	public static class LdioChangeDetectionFilterConfigurator implements LdioTransformerConfigurator {
		@Override
		public LdioTransformer configure(ComponentProperties properties) {
			final HashedStateMemberRepository repository = HashedStateMemberRepositoryFactory.getHashedStateMemberRepository(properties);
			final ChangeDetectionFilter changeDetectionFilter = new ChangeDetectionFilter(repository);
			return new LdioChangeDetectionFilter(changeDetectionFilter);
		}
	}
}
