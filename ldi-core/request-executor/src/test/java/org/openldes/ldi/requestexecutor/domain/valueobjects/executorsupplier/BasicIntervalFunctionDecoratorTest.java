package org.openldes.ldi.requestexecutor.domain.valueobjects.executorsupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.requestexecutor.executor.retry.BasicIntervalFunctionDecorator;
import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class BasicIntervalFunctionDecoratorTest {

  // A real request: with logback on the test classpath the retry message (which reads the url) is rendered.
  private static final GetRequest REQUEST = new GetRequest("http://host/x", RequestHeaders.empty());

  @Mock
  private IntervalFunction intervalFunction;

  @InjectMocks
  private BasicIntervalFunctionDecorator basicIntervalFunctionDecorator;

  private final Logger logger = (Logger) LoggerFactory.getLogger(BasicIntervalFunctionDecorator.class);
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(logs);
  }

  @Test
  void should_logUrlAndCause_when_retryFollowsAnHttpRequestException() {
    HttpRequestException failure = new HttpRequestException("http://host/sparql",
        new SocketTimeoutException("Read timed out"));

    basicIntervalFunctionDecorator.apply(1, Either.left(failure));

    assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
        "Retrying request (attempt #1) to http://host/sparql after java.net.SocketTimeoutException: Read timed out");
  }

  @Test
  void should_logCauseWithoutUrl_when_exceptionCarriesNoUrl() {
    HttpRequestException failure = new HttpRequestException(new IOException("Connection reset"));

    basicIntervalFunctionDecorator.apply(2, Either.left(failure));

    assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
        "Retrying request (attempt #2) after java.io.IOException: Connection reset");
  }

  @Test
  void should_logExceptionClass_when_otherExceptionWithoutMessage() {
    basicIntervalFunctionDecorator.apply(3, Either.left(new IllegalStateException()));

    assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
        "Retrying request (attempt #3) after java.lang.IllegalStateException");
  }

  @Test
  void should_keepResponseCodeMessage_when_retryFollowsAResponse() {
    Response response = new Response(REQUEST, List.of(),
        HttpStatus.SC_SERVICE_UNAVAILABLE, "body");

    basicIntervalFunctionDecorator.apply(1, Either.right(response));

    assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
        "Retrying request (attempt #1) to http://host/x after receiving response code 503");
  }

  @Test
  void should_keepNullResponseMessage_when_neitherResponseNorException() {
    basicIntervalFunctionDecorator.apply(1, Either.left(null));

    assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
        "Retrying request (attempt #1) after null response");
  }

  @Test
  void should_callIntervalFunction_when_EitherContainsNoResponse() {
    int attempt = 10;
    long interval = 25L;
    when(intervalFunction.apply(attempt)).thenReturn(interval);

    Long result = basicIntervalFunctionDecorator.apply(attempt, Either.left(null));

    assertThat(result).isEqualTo(interval);
  }

  @Test
  void should_callIntervalFunction_when_ResponseContainsNoRetryAfterHeader() {
    int attempt = 10;
    long interval = 50L;
    when(intervalFunction.apply(attempt)).thenReturn(interval);
    Response response = new Response(REQUEST, List.of(), HttpStatus.SC_SERVICE_UNAVAILABLE, "body");

    Long result = basicIntervalFunctionDecorator.apply(attempt, Either.right(response));

    assertThat(result).isEqualTo(interval);
  }

  @Test
  void should_returnMilliSecondsBasedOnRetryAfterHeader_when_retryAfterHeaderIsPresentWithSeconds() {
    BasicHeader basicHeader = new BasicHeader(HttpHeaders.RETRY_AFTER, "25");
    Response response = new Response(REQUEST, List.of(basicHeader), HttpStatus.SC_SERVICE_UNAVAILABLE,
        "body");

    Long result = basicIntervalFunctionDecorator.apply(5, Either.right(response));

    // we allow a margin of 5000ms for the code to be executed.
    assertThat(result)
        .isGreaterThan(20000L)
        .isLessThanOrEqualTo(25000L);
    verifyNoInteractions(intervalFunction);
  }

  @Test
  void should_returnMilliSecondsBasedOnRetryAfterHeader_when_retryAfterHeaderIsPresentWithDate() {
    Header basicHeader = new BasicHeader(HttpHeaders.RETRY_AFTER, getHttpDateString(25));
    Response response = new Response(REQUEST, List.of(basicHeader), HttpStatus.SC_SERVICE_UNAVAILABLE,
        "body");

    Long result = basicIntervalFunctionDecorator.apply(5, Either.right(response));

    // we allow a margin of 5000ms for the code to be executed.
    assertThat(result)
        .isGreaterThan(20000L)
        .isLessThanOrEqualTo(25000L);
    verifyNoInteractions(intervalFunction);
  }

  @SuppressWarnings("SameParameterValue")
  private String getHttpDateString(int secondsOffset) {
    // IMF-fixdate, the only http date format that must be produced by senders
    // https://www.rfc-editor.org/rfc/rfc9110#http.date
    DateTimeFormatter formatter = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    return LocalDateTime.now(ZoneOffset.UTC).plusSeconds(secondsOffset).format(formatter);
  }

}
