package org.openldes.ldi.requestexecutor.executor.clientcredentials;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.requestexecutor.executor.HangingServer;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.HttpTimeouts;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ClientCredentialsConfigTest {

	@Test
	void shouldFailWithSocketTimeout_whenTokenEndpointNeverAnswers() throws Exception {
		final HttpTimeouts timeouts = new HttpTimeouts(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500));

		try (HangingServer server = new HangingServer()) {
			final RequestExecutor executor = new ClientCredentialsConfig(List.of(), "client", "secret",
					server.url("/token"), null, true, timeouts).createRequestExecutor();
			final GetRequest request = new GetRequest(server.url("/data"), RequestHeaders.empty());

			assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
					assertThatThrownBy(() -> executor.execute(request))
							.isInstanceOf(HttpRequestException.class)
							.hasRootCauseInstanceOf(SocketTimeoutException.class));
		}
	}

}
