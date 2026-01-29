package org.openldes.ldi.processors;

import org.openldes.ldi.HibernateUtil;
import org.openldes.ldi.processors.config.PersistenceProperties;
import org.openldes.ldi.processors.services.NiFiDBCPDataSource;
import org.openldes.ldi.repositories.HashedStateMemberRepository;
import org.openldes.ldi.repositories.inmemory.InMemoryHashedStateMemberRepository;
import org.openldes.ldi.repositories.sql.SqlHashedStateMemberRepository;
import org.openldes.ldi.valueobjects.StatePersistenceStrategy;
import org.apache.nifi.dbcp.DBCPService;
import org.apache.nifi.processor.ProcessContext;

import static org.openldes.ldi.processors.config.PersistenceProperties.DBCP_SERVICE;
import static org.openldes.ldi.processors.config.PersistenceProperties.getStatePersistenceStrategy;

public class HashedMemberRepositoryFactory {
	private HashedMemberRepositoryFactory() {
	}

	public static HashedStateMemberRepository getRepository(ProcessContext context) {
		StatePersistenceStrategy state = getStatePersistenceStrategy(context);

		return switch (state) {
			case POSTGRES, SQLITE -> {
				final DBCPService dbcpService = context.getProperty(DBCP_SERVICE).asControllerService(DBCPService.class);
				final NiFiDBCPDataSource dataSource = new NiFiDBCPDataSource(dbcpService);
				final boolean keepState = PersistenceProperties.stateKept(context);

				var entityManager = HibernateUtil.createEntityManagerFromDatasource(dataSource, keepState, state);

				yield new SqlHashedStateMemberRepository(entityManager);
			}
			case MEMORY -> new InMemoryHashedStateMemberRepository();
		};
	}
}
