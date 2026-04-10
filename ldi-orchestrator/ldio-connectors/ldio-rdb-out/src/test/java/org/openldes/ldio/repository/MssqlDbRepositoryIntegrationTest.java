package org.openldes.ldio.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openldes.ldio.MssqlDbContainerExtension;
import org.openldes.ldio.dto.DataModelDTO;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.jdbc.Sql;


@ExtendWith(MssqlDbContainerExtension.class)
@Import(MssqlDbRepositoryIntegrationTest.TestConfig.class)
class MssqlDbRepositoryIntegrationTest extends AbstractDbRepositoryIntegrationTest {

  @Override
  protected void assertDateTimeEquals(OffsetDateTime actual, OffsetDateTime expected) {
    assertThat(actual.toInstant()).isEqualTo(expected.toInstant());
  }

//  @Override
//  protected void executeDuplicateInsert(DbRepository dbRepository, DataModelDTO dataModelDTO) {
//    assertThatThrownBy(() -> dbRepository.execute(dataModelDTO))
//        .isInstanceOf(DuplicateKeyException.class);
//  }

  @Test
  @Sql("/db/mssql/sensor-schema.sql")
  void given_emptyDatabase_when_insertSensor_then_sensorGetsInserted_mssqldb() {
    super.given_emptyDatabase_when_insertSensor_then_sensorGetsInserted();
  }

  @Test
  @Sql("/db/mssql/sensor-schema.sql")
  @Sql("/db/mssql/sensor-unique-constraint.sql")
  @Sql("/db/mssql/sensor-values.sql")
  void given_uniqueConstraintAndOneSensorInDatabase_when_insertSameSensor_then_noDuplicates_mssqldb() {
    this.given_uniqueConstraintAndOneSensorInDatabase_when_insertSameSensor_then_noDuplicates();
  }

  @Test
  @Sql("/db/mssql/hindrance-schema.sql")
  void given_emptyDatabase_when_insertHindrance_then_hindranceGetsInserted_mssqdb() {
    this.given_emptyDatabase_when_insertHindrance_then_hindranceGetsInserted();
  }

  @TestConfiguration
  static class TestConfig {

    @Bean
    StatementCreationService statementCreationService() {
      return new StatementCreationService();
    }
  }
}
