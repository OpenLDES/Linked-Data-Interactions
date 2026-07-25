package org.openldes.ldi.requestexecutor.executor.retry;

import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class HttpStatusRetryPredicate implements Predicate<Response> {

	public static final int HTTP_REQUEST_TIMEOUT = 408;
	public static final int HTTP_TOO_EARLY = 425; // not included in apache HttpStatus
	public static final int HTTP_TOO_MANY_REQUESTS = 429; // not included in apache HttpStatus
	private static final List<Integer> DEFAULT_STATUSES_TO_RETRY = List.of(
			HTTP_REQUEST_TIMEOUT,
			HTTP_TOO_EARLY,
			HTTP_TOO_MANY_REQUESTS);

	private final List<Integer> statusesToRetry;

	public HttpStatusRetryPredicate(List<Integer> statusesToRetry) {
		this.statusesToRetry = Objects.requireNonNullElse(statusesToRetry, new ArrayList<>());
	}

	@Override
	public boolean test(Response response) {
		return response == null
				|| response.getHttpStatus() >= 500
				|| response.hasStatus(DEFAULT_STATUSES_TO_RETRY)
				|| response.hasStatus(statusesToRetry);
	}

}
