package org.openldes.ldio.requestexecutor;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.RequestExecutorFactory;
import org.openldes.ldi.requestexecutor.valueobjects.AuthStrategy;
import org.openldes.ldi.requestexecutor.valueobjects.HttpTimeouts;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.openldes.ldio.requestexecutor.RequestExecutorProperties.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LdioRequestExecutorSupplierTest {
	private final String pipelineName = "pName";
	private final String componentName = "cName";
	@Mock
	private RequestExecutorFactory requestExecutorFactory;
	@InjectMocks
	private LdioRequestExecutorSupplier requestExecutorSupplier;

	@Test
	void shouldReturnRetryExecutorWithDefaults_whenNoProperties() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName);
		RequestExecutorDecorator requestExecutorDecorator = mock(RequestExecutorDecorator.class);
		when(requestExecutorDecorator.with((Retry) any())).thenReturn(requestExecutorDecorator);
		when(requestExecutorDecorator.with((RateLimiter) any())).thenReturn(requestExecutorDecorator);
		RequestExecutor retryRequestExecutor = mock(RequestExecutor.class);
		when(requestExecutorDecorator.get()).thenReturn(retryRequestExecutor);

		try (MockedStatic<RequestExecutorDecorator> utilities = Mockito.mockStatic(RequestExecutorDecorator.class)) {
			utilities.when(() -> RequestExecutorDecorator.decorate(any())).thenReturn(requestExecutorDecorator);
			RequestExecutor result = requestExecutorSupplier.getRequestExecutor(properties);
			assertEquals(result, retryRequestExecutor);
			verify(requestExecutorDecorator).with((Retry) any());
			verify(requestExecutorDecorator).with((RateLimiter) null);
		}
	}

	/**
	 * Required for test purposes. The mock compares on equals which is not implemented in BasicHeader.
	 */
	static class BasicHeaderWithEquals extends BasicHeader {

		public BasicHeaderWithEquals(String name, String value) {
			super(name, value);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Header header)) return false;
			return Objects.equals(getName(), header.getName()) && Objects.equals(getValue(), header.getValue());
		}

		@Override
		public int hashCode() {
			return Objects.hash(getName(), getValue());
		}

	}


	@Test
	void shouldReturnRetryExecutorWithConfiguredProperties_whenPropertiesConfigured() {
		String maxRetries = "10";
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(
				MAX_RETRIES, maxRetries,
				STATUSES_TO_RETRY, "400,404",
				AUTH_TYPE, AuthStrategy.API_KEY.name(),
				API_KEY_HEADER, "key-header",
				API_KEY, "key",
				HTTP_HEADERS + ".0.key", "header-key",
				HTTP_HEADERS + ".0.value", "header-value",
				HTTP_HEADERS + ".1.key", "other-header-key",
				HTTP_HEADERS + ".1.value", "other-header-value"));
		RequestExecutor requestExecutor = mock(RequestExecutor.class);
		List<Header> expectedHeaders =
				List.of(
						new BasicHeaderWithEquals("header-key", "header-value"),
						new BasicHeaderWithEquals("other-header-key", "other-header-value"),
						new BasicHeaderWithEquals("key-header", "key")
				);
		when(requestExecutorFactory.createNoAuthExecutor(expectedHeaders, HttpTimeouts.defaults())).thenReturn(requestExecutor);
		RequestExecutorDecorator requestExecutorDecorator = mock(RequestExecutorDecorator.class);
		when(requestExecutorDecorator.with((Retry) any())).thenReturn(requestExecutorDecorator);
		when(requestExecutorDecorator.with((RateLimiter) any())).thenReturn(requestExecutorDecorator);
		RequestExecutor retryRequestExecutor = mock(RequestExecutor.class);
		when(requestExecutorDecorator.get()).thenReturn(retryRequestExecutor);

		try (MockedStatic<RequestExecutorDecorator> utilities = Mockito.mockStatic(RequestExecutorDecorator.class)) {
			utilities.when(() -> RequestExecutorDecorator.decorate(any())).thenReturn(requestExecutorDecorator);
			RequestExecutor result = requestExecutorSupplier.getRequestExecutor(properties);
			assertEquals(result, retryRequestExecutor);
			verify(requestExecutorDecorator).with((Retry) any());
			verify(requestExecutorDecorator).with((RateLimiter) null);
		}
	}

	@Test
	void shouldReturnRateLimitExecutorWithConfiguredProperties_whenPropertiesConfigured() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(
				RATE_LIMIT_ENABLED, "true",
				RATE_LIMIT_LIMIT, "100"));
		RequestExecutorDecorator requestExecutorDecorator = mock(RequestExecutorDecorator.class);
		when(requestExecutorDecorator.with((Retry) any())).thenReturn(requestExecutorDecorator);
		when(requestExecutorDecorator.with((RateLimiter) any())).thenReturn(requestExecutorDecorator);
		RequestExecutor retryRequestExecutor = mock(RequestExecutor.class);
		when(requestExecutorDecorator.get()).thenReturn(retryRequestExecutor);

		try (MockedStatic<RequestExecutorDecorator> utilities = Mockito.mockStatic(RequestExecutorDecorator.class)) {
			utilities.when(() -> RequestExecutorDecorator.decorate(any())).thenReturn(requestExecutorDecorator);
			RequestExecutor result = requestExecutorSupplier.getRequestExecutor(properties);
			assertEquals(result, retryRequestExecutor);
			verify(requestExecutorDecorator).with((Retry) any());
			verify(requestExecutorDecorator).with((RateLimiter) any());
		}
	}

	@Test
	void shouldReturnNonRetryExecutorWithConfiguredProperties_whenPropertiesConfigured() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(
				RETRIES_ENABLED, "false",
				AUTH_TYPE, AuthStrategy.OAUTH2_CLIENT_CREDENTIALS.name(),
				CLIENT_ID, "client",
				CLIENT_SECRET, "secret",
				TOKEN_ENDPOINT, "token"));
		RequestExecutor requestExecutor = mock(RequestExecutor.class);
		when(requestExecutorFactory.createClientCredentialsExecutor(List.of(), "client", "secret", "token", null, HttpTimeouts.defaults()))
				.thenReturn(requestExecutor);

		RequestExecutor result = requestExecutorSupplier.getRequestExecutor(properties);

		assertEquals(requestExecutor, result);
	}

	@Test
	void shouldPassConfiguredTimeouts_whenTimeoutPropertiesConfigured() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(
				RETRIES_ENABLED, "false",
				TIMEOUT_CONNECT, "PT5S",
				TIMEOUT_CONNECTION_REQUEST, "PT10S",
				TIMEOUT_SOCKET, "PT2M"));
		HttpTimeouts expectedTimeouts = new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofMinutes(2));
		RequestExecutor requestExecutor = mock(RequestExecutor.class);
		when(requestExecutorFactory.createNoAuthExecutor(List.of(), expectedTimeouts)).thenReturn(requestExecutor);

		requestExecutorSupplier.getRequestExecutor(properties);

		verify(requestExecutorFactory).createNoAuthExecutor(List.of(), expectedTimeouts);
	}

	@Test
	void shouldUseDefaultForTimeoutsThatAreNotConfigured() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(
				RETRIES_ENABLED, "false",
				TIMEOUT_SOCKET, "PT10M"));
		HttpTimeouts expectedTimeouts = new HttpTimeouts(HttpTimeouts.DEFAULT_CONNECT_TIMEOUT,
				HttpTimeouts.DEFAULT_CONNECTION_REQUEST_TIMEOUT, Duration.ofMinutes(10));
		RequestExecutor requestExecutor = mock(RequestExecutor.class);
		when(requestExecutorFactory.createNoAuthExecutor(List.of(), expectedTimeouts)).thenReturn(requestExecutor);

		requestExecutorSupplier.getRequestExecutor(properties);

		verify(requestExecutorFactory).createNoAuthExecutor(List.of(), expectedTimeouts);
	}

	@Test
	void shouldThrowException_whenTimeoutIsNotAnIsoDuration() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(TIMEOUT_SOCKET, "60000"));
		assertThrows(IllegalArgumentException.class,
				() -> requestExecutorSupplier.getRequestExecutor(properties));
	}

	@Test
	void shouldThrowException_whenTimeoutIsNotPositive() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(TIMEOUT_SOCKET, "PT0S"));
		assertThrows(IllegalArgumentException.class,
				() -> requestExecutorSupplier.getRequestExecutor(properties));
	}

	@Test
	void shouldThrowException_whenAuthTypeNotSupported() {
		ComponentProperties properties = new ComponentProperties(pipelineName, componentName, Map.of(AUTH_TYPE, "fantasy"));
		assertThrows(UnsupportedOperationException.class,
				() -> requestExecutorSupplier.getRequestExecutor(properties));
	}
}
