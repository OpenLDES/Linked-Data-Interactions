package org.openldes.ldio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldio.dto.ColumnsDTO;
import org.openldes.ldio.dto.DataModelDTO;
import org.openldes.ldio.dto.ValuesDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DbRepositoryTest {

  public static final List<@NotNull String> COLUMNS = List.of("column1");
  public static final String TABLE_NAME = "tableName";
  public static final String INSERT_STATEMENT = "insert into " + TABLE_NAME;
  TransactionTemplate transactionTemplate;
  @Mock
  private JdbcTemplate jdbcTemplate;
  @Mock
  private PlatformTransactionManager transactionManagerMock;
  @Mock
  private StatementCreationService statementCreationService;

  @BeforeEach
  void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManagerMock);
  }

  @Test
  void when_createdWithNullValues_then_illegalArgumentExceptionIsThrown() {
    assertThatThrownBy(() -> new DbRepository(null, null, null, null, null)).isInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  void when_createdWithMandatoryFields_then_dbRepositoryIsCreated() {
    DbRepository dbRepository = new DbRepository(mock(JdbcTemplate.class),
        mock(TransactionTemplate.class), mock(StatementCreationService.class), TABLE_NAME, true);
    assertThat(dbRepository).isNotNull();
  }

  @Test
  void given_createdWithMandatoryFields_when_executeWithNullDataModelDTO_then_illegalArgumentExceptionIsThrown() {
    DbRepository dbRepository = new DbRepository(mock(JdbcTemplate.class),
        mock(TransactionTemplate.class), mock(StatementCreationService.class), TABLE_NAME, true);
    assertThatThrownBy(() -> dbRepository.execute(null)).isInstanceOf(
        IllegalArgumentException.class);
  }

  @Test
  void given_createdWithMandatoryFields_when_executeWithEmptyDataModelDTO_then_noRowsAreInserted() {
    DbRepository dbRepository = new DbRepository(mock(JdbcTemplate.class),
        mock(TransactionTemplate.class), mock(StatementCreationService.class), TABLE_NAME, true);
    DataModelDTO dataModelDTO = new DataModelDTO(new ColumnsDTO(COLUMNS));
    dataModelDTO.setData(new ValuesDTO(List.of()));
    assertThat(dbRepository.execute(dataModelDTO)).isEqualTo(0);
  }

  @Test
  void given_createdWithMandatoryFields_when_executeWithDataModelDTO_then_oneRowIsInserted() {
    when(statementCreationService.createInsertStatement(TABLE_NAME, COLUMNS)).thenReturn(INSERT_STATEMENT);
    DataModelDTO dataModelDTO = new DataModelDTO(new ColumnsDTO(COLUMNS));
    dataModelDTO.setData(new ValuesDTO(List.of(List.of("value"))));
    when(jdbcTemplate.update(INSERT_STATEMENT,
        dataModelDTO.getData().values().getFirst().toArray())).thenReturn(1);
    DbRepository dbRepository = new DbRepository(jdbcTemplate, transactionTemplate,
        statementCreationService, TABLE_NAME, true);
    assertThat(dbRepository.execute(dataModelDTO)).isEqualTo(1);
  }

  @Test
  void given_createdWithMandatoryFields_when_executeWithDataModelDTO_then_multipleRowsAreInserted() {
    when(statementCreationService.createInsertStatement(TABLE_NAME, COLUMNS)).thenReturn(
        INSERT_STATEMENT);
    DataModelDTO dataModelDTO = new DataModelDTO(new ColumnsDTO(COLUMNS));
    dataModelDTO.setData(new ValuesDTO(List.of(List.of("value1", "value2"))));
    when(jdbcTemplate.update(INSERT_STATEMENT,
        dataModelDTO.getData().values().getFirst().toArray())).thenReturn(2);
    DbRepository dbRepository = new DbRepository(jdbcTemplate, transactionTemplate,
        statementCreationService, TABLE_NAME, true);
    assertThat(dbRepository.execute(dataModelDTO)).isEqualTo(2);
  }
}
