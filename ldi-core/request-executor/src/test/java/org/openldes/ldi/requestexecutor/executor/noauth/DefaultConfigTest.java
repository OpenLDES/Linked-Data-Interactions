package org.openldes.ldi.requestexecutor.executor.noauth;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DefaultConfigTest {

	@Test
	void shouldFailWithSocketTimeout_whenServerNeverAnswers() throws Exception {
		final HttpTimeouts timeouts = new HttpTimeouts(Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofMillis(500));
		final RequestExecutor executor = new DefaultConfig(List.of(), true, timeouts).createRequestExecutor();

		try (HangingServer server = new HangingServer()) {
			final GetRequest request = new GetRequest(server.url("/sparql"), RequestHeaders.empty());

			assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
					assertThatThrownBy(() -> executor.execute(request))
							.isInstanceOf(HttpRequestException.class)
							.hasRootCauseInstanceOf(SocketTimeoutException.class)
							.extracting(e -> ((HttpRequestException) e).getUrl())
							.isEqualTo(Optional.of(server.url("/sparql"))));
		}
	}

}
