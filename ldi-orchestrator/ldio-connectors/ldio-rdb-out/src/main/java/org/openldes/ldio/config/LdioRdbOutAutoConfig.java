package org.openldes.ldio.config;

import static org.openldes.ldio.LdioRdbOut.NAME;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioRdbOut;
import org.openldes.ldio.converter.ValueConverter;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.repository.DbRepository;
import org.openldes.ldio.repository.StatementCreationService;
import org.openldes.ldio.sparqlselect.SparqlSelectService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class LdioRdbOutAutoConfig {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final Log logger = LogFactory.getLog(getClass());

  public LdioRdbOutAutoConfig(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
  }

  @SuppressWarnings("java:S6830")
  @Bean(NAME)
  public LdioOutputConfigurator ldioConfigurator() {
    logger.debug("Creating Ldio Configurator");
    return new LdioRdbOutConfigurator(jdbcTemplate, transactionTemplate);
  }

  public static class LdioRdbOutConfigurator implements LdioOutputConfigurator {

    public static final String PROPERTY_TABLE_NAME = "table-name";
    public static final String PROPERTY_SPARQL_SELECT_QUERY = "sparql-select-query";
    public static final String PROPERTY_IGNORE_DUPLICATE_KEY_EXCEPTION = "ignore-duplicate-key-exception";
    private final Log logger = LogFactory.getLog(getClass());
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public LdioRdbOutConfigurator(JdbcTemplate jdbcTemplate,
        TransactionTemplate transactionTemplate) {
      this.jdbcTemplate = jdbcTemplate;
      this.transactionTemplate = transactionTemplate;
    }

    @Override
    public LdiComponent configure(ComponentProperties properties) {
      if (properties.getProperty(PROPERTY_TABLE_NAME) == null
          || properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY) == null) {
        throw new IllegalArgumentException(
            "The configuration for '%s' and '%s' is missing".formatted(PROPERTY_TABLE_NAME,
                PROPERTY_SPARQL_SELECT_QUERY));
      }

      // SPARQL Select Service
      ValueConverter valueConverter = new ValueConverter();
      SparqlSelectService sparqlSelectService = new SparqlSelectService(valueConverter);
      // DB Repository
      String tableName = properties.getProperty(PROPERTY_TABLE_NAME);
      String sparqlSelectQuery = properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY);
      Boolean ignoreDuplicateKeyException = properties.getOptionalBoolean(
          PROPERTY_IGNORE_DUPLICATE_KEY_EXCEPTION).orElse(false);
      StatementCreationService statementCreationService = new StatementCreationService();
      DbRepository dbRepository = new DbRepository(jdbcTemplate, transactionTemplate, statementCreationService,
          tableName, ignoreDuplicateKeyException);
      LdioRdbOut ldioRdbOut = new LdioRdbOut(properties, dbRepository, sparqlSelectService,
          tableName, sparqlSelectQuery, ignoreDuplicateKeyException);
      logger.debug("Created LdioRdbOut: " + ldioRdbOut);
      return ldioRdbOut;
    }
  }
}
