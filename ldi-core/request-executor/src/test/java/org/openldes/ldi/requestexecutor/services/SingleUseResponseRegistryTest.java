package org.openldes.ldi.requestexecutor.services;

import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SingleUseResponseRegistryTest {
	@Test
	void abandonedCapturesAreBoundedForALiveOwner() {
		final RequestExecutor owner = mock(RequestExecutor.class);
		final Response response = successfulResponse();
		for (int i = 0; i < 129; i++) {
			SingleUseResponseRegistry.capture(owner, "url" + i, response);
		}
		assertThat(SingleUseResponseRegistry.consume(owner, "url0")).isEmpty();
		assertThat(SingleUseResponseRegistry.consume(owner, "url128")).contains(response);
	}

	@Test
	void capturedResponseCanOnlyBeConsumedOnce() {
		final RequestExecutor owner = mock(RequestExecutor.class);
		final Response response = successfulResponse();
		final String url = "https://example.com/root";

		SingleUseResponseRegistry.capture(owner, url, response);

		assertThat(SingleUseResponseRegistry.consume(owner, url)).contains(response);
		assertThat(SingleUseResponseRegistry.consume(owner, url)).isEmpty();
	}

	@Test
	void resolvedUrlCanOnlyBeConsumedOnce() {
		final RequestExecutor owner = mock(RequestExecutor.class);
		final String entrypoint = "https://example.com/overview";
		final String root = "https://example.com/root";

		SingleUseResponseRegistry.resolve(owner, entrypoint, root);

		assertThat(SingleUseResponseRegistry.consumeResolvedUrl(owner, entrypoint)).contains(root);
		assertThat(SingleUseResponseRegistry.consumeResolvedUrl(owner, entrypoint)).isEmpty();
	}

	private static Response successfulResponse() {
		final Response response = mock(Response.class);
		when(response.isOk()).thenReturn(true);
		return response;
	}
}
