package org.openldes.ldi.requestexecutor.services;

import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.RetryableRequestExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestExecutorDecoratorTest {
	@Test
	void givenRetryableExecutor_whenApplyDefaultPolicy_thenReturnSameExecutor() {
		final RetryableRequestExecutor requestExecutor = mock(RetryableRequestExecutor.class);

		final RequestExecutor decorated = RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor);

		assertThat(decorated).isSameAs(requestExecutor);
	}

	@Test
	void givenPlainExecutor_whenApplyDefaultPolicy_thenReturnRetryableExecutor() {
		final RequestExecutor requestExecutor = mock(RequestExecutor.class);

		final RequestExecutor decorated = RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor);

		assertThat(decorated)
				.isNotSameAs(requestExecutor)
				.isInstanceOf(RetryableRequestExecutor.class);
	}
}
