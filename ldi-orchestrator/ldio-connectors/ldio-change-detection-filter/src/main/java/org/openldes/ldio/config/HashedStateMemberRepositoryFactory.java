package org.openldes.ldio.config;

import org.openldes.ldi.HibernateUtil;
import org.openldes.ldi.postgres.PostgresProperties;
import org.openldes.ldi.repositories.HashedStateMemberRepository;
import org.openldes.ldi.repositories.inmemory.InMemoryHashedStateMemberRepository;
import org.openldes.ldi.repositories.sql.SqlHashedStateMemberRepository;
import org.openldes.ldi.sqlite.SqliteProperties;
import org.openldes.ldi.valueobjects.StatePersistenceStrategy;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.persistence.PersistenceProperties;

public class HashedStateMemberRepositoryFactory {
	public static final StatePersistenceStrategy DEFAULT_PERSISTENCE_STRATEGY = StatePersistenceStrategy.MEMORY;
	public static final boolean DEFAULT_KEEP_STATE = false;

	private HashedStateMemberRepositoryFactory() {
	}

	public static HashedStateMemberRepository getHashedStateMemberRepository(ComponentProperties properties) {
		final StatePersistenceStrategy persistenceStrategy = properties.getOptionalProperty(PersistenceProperties.STATE)
				.flatMap(StatePersistenceStrategy::from)
				.orElse(DEFAULT_PERSISTENCE_STRATEGY);

		return switch (persistenceStrategy) {
			case POSTGRES -> {
				var hibernateProperties = createPostgresProperties(properties);
				var entityManager = HibernateUtil.createEntityManagerFromProperties(hibernateProperties.getProperties());
				yield new SqlHashedStateMemberRepository(entityManager);
			}
			case SQLITE -> {
				var hibernateProperties = createSqliteProperties(properties);
				var entityManager = HibernateUtil.createEntityManagerFromProperties(hibernateProperties.getProperties());
				yield new SqlHashedStateMemberRepository(entityManager);
			}
			case MEMORY -> new InMemoryHashedStateMemberRepository();
		};
	}

	private static PostgresProperties createPostgresProperties(ComponentProperties properties) {
		String url = properties.getProperty(PersistenceProperties.POSTGRES_URL);
		String username = properties.getProperty(PersistenceProperties.POSTGRES_USERNAME);
		String password = properties.getProperty(PersistenceProperties.POSTGRES_PASSWORD);
		boolean keepState = properties.getOptionalBoolean(PersistenceProperties.KEEP_STATE)
				.orElse(DEFAULT_KEEP_STATE);
		return new PostgresProperties(url, username, password, keepState);
	}

	private static SqliteProperties createSqliteProperties(ComponentProperties properties) {
		final String pipelineName = properties.getPipelineName();
		final boolean keepState = properties.getOptionalBoolean(PersistenceProperties.KEEP_STATE).orElse(DEFAULT_KEEP_STATE);
		return properties.getOptionalProperty(PersistenceProperties.SQLITE_DIRECTORY)
				.map(directory -> new SqliteProperties(directory, pipelineName, keepState))
				.orElse(new SqliteProperties(pipelineName, keepState));
	}
}
