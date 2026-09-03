package org.openldes.ldi.requestexecutor.domain.valueobjects.executorsupplier.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.retry.RetryAfter;

class RetryAfterTest {

  @Nested
  class From {

    @Test
    void should_parseNumber_when_inputIsNumber() {
      long millisUntilRetry = RetryAfter.from("25").getMillisUntilRetry();

      // allow 5s margin for code to run
      assertTrue(millisUntilRetry > 20000 && millisUntilRetry <= 25000);
    }

    @Test
    void should_parseDate_when_inputIsDate() {
      // IMF-fixdate, the only http date format that must be produced by senders
      // https://www.rfc-editor.org/rfc/rfc9110#http.date
      DateTimeFormatter formatter = DateTimeFormatter
          .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
      String httpDate = LocalDateTime.now(ZoneOffset.UTC).plusSeconds(25).format(formatter);

      long millisUntilRetry = RetryAfter.from(httpDate).getMillisUntilRetry();

      // allow 5s margin for code to run
      assertTrue(millisUntilRetry > 20000 && millisUntilRetry <= 25000);
    }
  }

  @Nested
  class GetMillisUntilRetry {

    @Test
    void should_returnPositive_when_retryHeaderIsInThePast() {
      long millisUntilRetry = new RetryAfter(LocalDateTime.MIN).getMillisUntilRetry();

      assertEquals(1L, millisUntilRetry);
    }

    @Test
    void should_returnMillisBetweenNow_and_retryHeader() {
      long millisUntilRetry = new RetryAfter(
          LocalDateTime.now().plusSeconds(25)).getMillisUntilRetry();

      // allow 5s margin for code to run
      assertTrue(millisUntilRetry > 20000 && millisUntilRetry <= 25000);
    }
  }
}
