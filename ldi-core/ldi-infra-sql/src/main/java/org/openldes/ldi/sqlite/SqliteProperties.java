package org.openldes.ldi.sqlite;

import org.openldes.ldi.HibernateProperties;

import java.util.Map;

public class SqliteProperties implements HibernateProperties {
	public static final String DATABASE_DIRECTORY = ".";
	public static final String DIALECT = "org.sqlite.hibernate.dialect.SQLiteDialect";
	/**
	 * Write-ahead logging with <code>synchronous=NORMAL</code>, so a commit does
	 * not wait for an fsync. SQLite's defaults fsync on every commit, which costs
	 * a disk round trip per supplied member once the state is on a real disk.
	 * <p>
	 * With this combination an operating-system crash or power loss can lose the
	 * most recently committed transactions, but it cannot corrupt the database.
	 * Losing them means a resumed traversal supplies those members again, which
	 * is the same outcome as a client that stops before its state is written.
	 */
	public static final String PRAGMAS = "journal_mode=WAL&synchronous=NORMAL";
	private final String databaseDirectory;
	private final String instanceName;
	private final boolean keepState;

	public SqliteProperties(String databaseDirectory, String instanceName, boolean keepState) {
		this.databaseDirectory = databaseDirectory;
		this.instanceName = instanceName;
		this.keepState = keepState;
	}

	public SqliteProperties(String instanceName, boolean keepState) {
		this(DATABASE_DIRECTORY, instanceName, keepState);
	}

	public String getInstanceName() {
		return instanceName;
	}

	public String getDatabaseDirectory() {
		return databaseDirectory;
	}

	public String getDatabaseName() {
		return instanceName + ".db";
	}

	@Override
	public Map<String, String> getProperties() {
		return Map.of("javax.persistence.jdbc.url",
				"jdbc:sqlite:./%s/%s?%s".formatted(databaseDirectory, getDatabaseName(), PRAGMAS),
				HIBERNATE_DIALECT, DIALECT,
				"javax.persistence.jdbc.driver", "org.sqlite.JDBC",
				"hibernate.connection.provider_class", "com.zaxxer.hikari.hibernate.HikariConnectionProvider",
				HIBERNATE_HBM_2_DDL_AUTO, keepState ? UPDATE : CREATE_DROP);
	}
}
