package org.openldes.ldio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openldes.ldio.PostgresDbContainerExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.jdbc.Sql;


@ExtendWith(PostgresDbContainerExtension.class)
@Import(PostgresDbRepositoryIntegrationTest.TestConfig.class)
class PostgresDbRepositoryIntegrationTest extends AbstractDbRepositoryIntegrationTest {

  @Override
  protected void assertDateTimeEquals(OffsetDateTime actual, OffsetDateTime expected) {
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Sql("/db/postgres/sensor-schema.sql")
  void given_emptyDatabase_when_insertSensor_then_sensorGetsInserted_postgres() {
    super.given_emptyDatabase_when_insertSensor_then_sensorGetsInserted();
  }

  @Test
  @Sql("/db/postgres/sensor-schema.sql")
  @Sql("/db/postgres/sensor-unique-constraint.sql")
  @Sql("/db/postgres/sensor-values.sql")
  void given_uniqueConstraintAndOneSensorInDatabase_when_insertSameSensor_then_noDuplicates_postgres() {
    this.given_uniqueConstraintAndOneSensorInDatabase_when_insertSameSensor_then_noDuplicates();
  }

  @Test
  @Sql("/db/postgres/hindrance-schema.sql")
  void given_emptyDatabase_when_insertHindrance_then_hindranceGetsInserted_postgres() {
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
