package org.openldes.ldio.repository;


import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openldes.ldio.dto.DataModelDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class DbRepository {

  private final Log logger = LogFactory.getLog(getClass());
  private final JdbcTemplate jdbcTemplate;
  private final String tableName;
  private final Boolean ignoreDuplicateKeyException;
  private final StatementCreationService statementCreationService;
  private final TransactionTemplate transactionTemplate;

  private String insertStatement;

  public DbRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
      StatementCreationService statementCreationService,
      String tableName, Boolean ignoreDuplicateKeyException) {
    if (jdbcTemplate == null || statementCreationService == null || tableName == null) {
      throw new IllegalArgumentException("Argument must not be null");
    }
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
    this.statementCreationService = statementCreationService;
    this.tableName = tableName;
    this.ignoreDuplicateKeyException = ignoreDuplicateKeyException;
  }

  public int execute(DataModelDTO dataModelDTO) {
    if (dataModelDTO == null || dataModelDTO.getColumns() == null || dataModelDTO.getColumns()
        .columns().isEmpty()) {
      throw new IllegalArgumentException(
          "Invalid data model or no valid columns in the data model");
    }
    if (dataModelDTO.getData() == null || dataModelDTO.getData().values().isEmpty()) {
      return 0;
    }

    int insertCount = 0;
    if (insertStatement == null) {
      insertStatement = statementCreationService.createInsertStatement(tableName,
          dataModelDTO.getColumns().columns());
    }
    if (dataModelDTO.getData().values().size() == 1) {
      if (ignoreDuplicateKeyException) {
        insertStatement = insertStatement + " ON CONFLICT DO NOTHING";
        insertCount = transactionTemplate.execute(status -> {
          try {
            return jdbcTemplate.update(insertStatement,
                dataModelDTO.getData().values().getFirst().toArray());
          } catch (DuplicateKeyException duplicateKeyException) {
            logger.warn("Duplicate key found: " + duplicateKeyException.getMessage());
            return 0;
          }
        });
      } else {
        insertCount = jdbcTemplate.update(insertStatement,
            dataModelDTO.getData().values().getFirst().toArray());
      }
    } else {
      List<List<Object>> values = dataModelDTO.getData().values();
      List<Object[]> listOfObjects = values.stream().map(List::toArray).toList();
      if (ignoreDuplicateKeyException) {
        try {
          int[] inserts = jdbcTemplate.batchUpdate(insertStatement, listOfObjects);
          insertCount = Arrays.stream(inserts).reduce(Integer::sum).getAsInt();
        } catch (DuplicateKeyException duplicateKeyException) {
          logger.warn("Duplicate key found: " + duplicateKeyException.getMessage());
          return 0;
        }
      } else {
        int[] inserts = jdbcTemplate.batchUpdate(insertStatement, listOfObjects);
        insertCount = Arrays.stream(inserts).reduce(Integer::sum).getAsInt();
      }
    }
    return insertCount;
  }

  private boolean isDuplicateKeySqlException(SQLException sqlException) {
    if (sqlException == null) {
      return false;
    }

    if ("23505".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 2627
        || sqlException.getErrorCode() == 2601) {
      return true;
    }

    return isDuplicateKeySqlException(sqlException.getNextException());
  }
}
