package org.openldes.ldio;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldio.converter.ValueConverter;
import org.openldes.ldio.repository.DbRepository;
import org.openldes.ldio.repository.StatementCreationService;
import org.openldes.ldio.sparqlselect.SparqlSelectService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class LdioRdbOutTest {

  public static final String TABLE_NAME = "TEST_TABLE_NAME";
  public static final String SPARQL_SELECT_QUERY = """
      PREFIX ns: <https://test.org/ns#>
      PREFIX prov: <http://www.w3.org/ns/prov#>
      
      SELECT
                      ?SensorId ?GeneratedAtTime
      WHERE {
           ?sensor ns:SensorId ?SensorId .
           OPTIONAL { ?sensor prov:generatedAtTime ?GeneratedAtTime }
      }
      """;
  public static final String EXPECTED_INSERT_STATEMENT = "INSERT INTO TEST_TABLE_NAME (SensorId, GeneratedAtTime) VALUES (?, ?)";
  public static final OffsetDateTime EXPECTED_DATE_TIME = OffsetDateTime.parse(
      "2024-12-18T13:00:40.575Z");
  public static final String EXPECTED_SENSOR_ID = "123456";
  private LdioRdbOut sut;
  @Mock
  private JdbcTemplate jdbcTemplateMock;
  @Spy
  private TransactionTemplate transactionTemplate;
  @Mock
  private PlatformTransactionManager transactionManagerMock;

  @BeforeEach
  void setUp() {
    SparqlSelectService sparqlSelectService = new SparqlSelectService(new ValueConverter());
    transactionTemplate.setTransactionManager(transactionManagerMock);
    StatementCreationService statementCreationService = new StatementCreationService();
    DbRepository dbRepository = new DbRepository(jdbcTemplateMock, transactionTemplate,
        statementCreationService, TABLE_NAME, true);
    sut = new LdioRdbOut(null,
        dbRepository,
        sparqlSelectService,
        TABLE_NAME,
        SPARQL_SELECT_QUERY,
        true);
  }

  @Test
  void given_emptyModel_when_accept_then_noRowsAreInserted() {
    Model model = ModelFactory.createDefaultModel();
    sut.accept(model);
    verify(transactionTemplate, never()).execute(any());
  }

  @Test
  void given_modelWithOneRow_when_accept_then_executeIsExecuted() {
    RDFParser parser = RDFParser.create().source("one_sensor.ttl").build();
    Model model = parser.toModel();
    when(jdbcTemplateMock.update(eq(EXPECTED_INSERT_STATEMENT), eq(EXPECTED_SENSOR_ID),
        eq(EXPECTED_DATE_TIME))).thenReturn(1);
    sut.accept(model);
    verify(jdbcTemplateMock, times(1)).update(eq(EXPECTED_INSERT_STATEMENT), eq(EXPECTED_SENSOR_ID),
        eq(EXPECTED_DATE_TIME));
  }


  @Test
  void given_modelWithOneColumn_when_accept_then_updateIsExecuted() {
    RDFParser parser = RDFParser.create().source("one_sensor_with_one_column.ttl").build();
    Model model = parser.toModel();
    when(transactionManagerMock.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    when(jdbcTemplateMock.update(eq(EXPECTED_INSERT_STATEMENT), eq(EXPECTED_SENSOR_ID), eq(null))).thenReturn(1);
    sut.accept(model);
    verify(jdbcTemplateMock, times(1)).update(eq(EXPECTED_INSERT_STATEMENT), eq(EXPECTED_SENSOR_ID),
        eq(null));
  }


  @Test
  void given_modelWithTwoRows_when_accept_then_batchUpdateIsExecuted() {
    RDFParser parser = RDFParser.create().source("two_sensors.ttl").build();
    Model model = parser.toModel();

    when(jdbcTemplateMock.batchUpdate(eq(EXPECTED_INSERT_STATEMENT), anyList())).thenReturn(
        new int[]{1, 1});
    sut.accept(model);
    verify(jdbcTemplateMock, times(1)).batchUpdate(eq(EXPECTED_INSERT_STATEMENT), anyList());
  }
}
