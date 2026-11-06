package org.openldes.ldi.requestexecutor.domain.valueobjects.executorsupplier.retry;

import org.openldes.ldi.requestexecutor.executor.retry.HttpStatusRetryPredicate;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpStatusRetryPredicateTest {

	@Test
	void should_ReturnTrue_when_ResponseIsNull() {
		assertTrue(new HttpStatusRetryPredicate(List.of()).test(null));
	}

	@Test
	void should_ReturnTrue_when_ResponseStatusIsRetryableServerError() {
		for (int status : List.of(500, 502, 503, 504)) {
			Response response = new Response(null, List.of(), status, (String) null);
			assertTrue(new HttpStatusRetryPredicate(List.of()).test(response),
					"expected status " + status + " to be retried");
		}
	}

	@Test
	void should_ReturnFalse_when_ResponseStatusIsTerminalServerError() {
		for (int status : List.of(501, 505)) {
			Response response = new Response(null, List.of(), status, (String) null);
			assertFalse(new HttpStatusRetryPredicate(List.of()).test(response),
					"expected status " + status + " to be terminal");
		}
	}

	@Test
	void should_ReturnTrue_when_ResponseStatusIsTooManyRequests() {
		Response response = new Response(null, List.of(), 429, (String) null);
		assertTrue(new HttpStatusRetryPredicate(List.of()).test(response));
	}

	@Test
	void should_ReturnTrue_when_ResponseStatusIsLdesRetryableClientError() {
		Response response408 = new Response(null, List.of(), 408, (String) null);
		Response response425 = new Response(null, List.of(), 425, (String) null);

		assertTrue(new HttpStatusRetryPredicate(List.of()).test(response408));
		assertTrue(new HttpStatusRetryPredicate(List.of()).test(response425));
	}

	@Test
	void should_ReturnTrue_when_ResponseStatusIsIncludedInStatusesToRetry() {
		int customStatusThatShouldTriggerRetry = 418;
		Response response = new Response(null, List.of(), customStatusThatShouldTriggerRetry, (String) null);
		assertTrue(new HttpStatusRetryPredicate(List.of(customStatusThatShouldTriggerRetry)).test(response));
	}

	@Test
	void should_ReturnFalse_when_StatusIsValid() {
		Response response = new Response(null, List.of(), 200, (String) null);
		assertFalse(new HttpStatusRetryPredicate(List.of()).test(response));
	}

	@Test
	void should_ReturnFalse_when_StatusIsTerminalClientError() {
		Response response = new Response(null, List.of(), 404, (String) null);
		assertFalse(new HttpStatusRetryPredicate(List.of()).test(response));
	}
}
