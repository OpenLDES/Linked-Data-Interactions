package org.openldes.ldio.config;

import java.sql.SQLException;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.LdioRdbOut;
import org.openldes.ldio.pipeline.creation.LdioOutputConfigurator;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.openldes.ldio.config.LdioRdbOutAutoConfig.LdioRdbOutConfigurator.PROPERTY_SPARQL_SELECT_QUERY;
import static org.openldes.ldio.config.LdioRdbOutAutoConfig.LdioRdbOutConfigurator.PROPERTY_TABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LdioRdbOutConfiguratorTest {

    public static final String TEST_TABLE = "test_table";
    public static final String DEFAULT_SPARQL_SELECT_QUERY = "SELECT * WHERE {?s ?p ?o}";

    @Test
    @SneakyThrows
    void given_jdbcTemplateAndTransactionTemplate_when_createLdioRdbOutAutoConfig_then_ldioConfiguratorBeanIsCreated() {
        JdbcTemplate jdbcTemplateMock = getJdbcTemplateMock();
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        LdioRdbOutAutoConfig config = new LdioRdbOutAutoConfig(jdbcTemplateMock, transactionTemplateMock);

        assertThat(config.ldioConfigurator()).isNotNull();
    }

    @Test
    void given_jdbcTemplateAndTransactionTemplate_when_createLdioRdbOutAutoConfig_then_ldioConfiguratorBeanIsInstanceOfCorrectType() {
        JdbcTemplate jdbcTemplateMock = Mockito.mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        LdioRdbOutAutoConfig config = new LdioRdbOutAutoConfig(jdbcTemplateMock, transactionTemplateMock);

        assertThat(config.ldioConfigurator()).isInstanceOf(LdioRdbOutAutoConfig.LdioRdbOutConfigurator.class);
    }

    @Test
    @SneakyThrows
    void given_jdbcTemplateAndTransactionTemplate_when_createLdioRdbOutAutoConfigWithProperties_then_ldioConfiguratorBeanHasUsedProperties() {
        JdbcTemplate jdbcTemplateMock = getJdbcTemplateMock();
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        ComponentProperties properties = Mockito.mock(ComponentProperties.class);

        when(properties.getProperty(PROPERTY_TABLE_NAME)).thenReturn(TEST_TABLE);
        when(properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY)).thenReturn(DEFAULT_SPARQL_SELECT_QUERY);

        LdioOutputConfigurator configurator = new LdioRdbOutAutoConfig.LdioRdbOutConfigurator(jdbcTemplateMock, transactionTemplateMock);
        LdiComponent component = configurator.configure(properties);

        assertNotNull(component);
        assertInstanceOf(LdioRdbOut.class, component);

        verify(properties, times(2)).getProperty(PROPERTY_TABLE_NAME);
        verify(properties, times(2)).getProperty(PROPERTY_SPARQL_SELECT_QUERY);
    }

    @Test
    void when_createLdioRdbOutAutoConfigWithNoTableName_then_throwsIllegalArgumentException() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        ComponentProperties properties = Mockito.mock(ComponentProperties.class);

        when(properties.getProperty(PROPERTY_TABLE_NAME)).thenReturn(null);
        when(properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY)).thenReturn(DEFAULT_SPARQL_SELECT_QUERY);

        LdioOutputConfigurator configurator = new LdioRdbOutAutoConfig.LdioRdbOutConfigurator(jdbcTemplate, transactionTemplateMock);

        assertThrows(IllegalArgumentException.class, () -> configurator.configure(properties));

        verify(properties, times(1)).getProperty(PROPERTY_TABLE_NAME);
    }

    @Test
    void when_createLdioRdbOutAutoConfigWithNoSparqlSelectQuery_then_throwsIllegalArgumentException() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        ComponentProperties properties = Mockito.mock(ComponentProperties.class);

        when(properties.getProperty(PROPERTY_TABLE_NAME)).thenReturn(TEST_TABLE);
        when(properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY)).thenReturn(null);

        LdioOutputConfigurator configurator = new LdioRdbOutAutoConfig.LdioRdbOutConfigurator(jdbcTemplate, transactionTemplateMock);

        assertThrows(IllegalArgumentException.class, () -> configurator.configure(properties));

        verify(properties, times(1)).getProperty(PROPERTY_TABLE_NAME);
        verify(properties, times(1)).getProperty(PROPERTY_SPARQL_SELECT_QUERY);
    }

    @Test
    @SneakyThrows
    void given_jdbcTemplate_when_createLdioRdbOutAutoConfigWithPropertiesAndIgnoreDuplicateKeyException_then_ldioConfiguratorBeanHasUsedProperties() {
        JdbcTemplate jdbcTemplateMock = getJdbcTemplateMock();
        TransactionTemplate transactionTemplateMock = Mockito.mock(TransactionTemplate.class);
        ComponentProperties properties = Mockito.mock(ComponentProperties.class);

        when(properties.getProperty(PROPERTY_TABLE_NAME)).thenReturn(TEST_TABLE);
        when(properties.getProperty(PROPERTY_SPARQL_SELECT_QUERY)).thenReturn(DEFAULT_SPARQL_SELECT_QUERY);
        when(properties.getOptionalBoolean("ignore-duplicate-key-exception")).thenReturn(java.util.Optional.of(true));

        LdioOutputConfigurator configurator = new LdioRdbOutAutoConfig.LdioRdbOutConfigurator(jdbcTemplateMock, transactionTemplateMock);
        LdiComponent component = configurator.configure(properties);

        assertNotNull(component);
        assertInstanceOf(LdioRdbOut.class, component);
        verify(properties, times(2)).getProperty(PROPERTY_TABLE_NAME);
        verify(properties, times(2)).getProperty(PROPERTY_SPARQL_SELECT_QUERY);
        verify(properties, times(1)).getOptionalBoolean("ignore-duplicate-key-exception");
    }

    private @NotNull JdbcTemplate getJdbcTemplateMock() throws SQLException {
        JdbcTemplate jdbcTemplateMock = Mockito.mock(JdbcTemplate.class);
        lenient().when(jdbcTemplateMock.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        lenient().when(jdbcTemplateMock.getDataSource().getConnection()).thenReturn(mock(java.sql.Connection.class));
        lenient().when(jdbcTemplateMock.getDataSource().getConnection().getMetaData()).thenReturn(mock(java.sql.DatabaseMetaData.class));
        lenient().when(jdbcTemplateMock.getDataSource().getConnection().getMetaData().getDatabaseProductName())
            .thenReturn("PostgreSQL");
        return jdbcTemplateMock;
    }
}
