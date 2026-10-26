package org.openldes.ldi.requestexecutor.executor.retry;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import io.github.resilience4j.core.IntervalBiFunction;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.Either;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Supplier;

import static org.apache.commons.lang3.Validate.notNull;

/**
 * Wrapper to deal with the retry intervals when an http response execution has failed
 */
public class BasicIntervalFunctionDecorator implements IntervalBiFunction<Response> {

	private final Logger log = LoggerFactory.getLogger(BasicIntervalFunctionDecorator.class);

	private final IntervalFunction intervalFunction;

	public BasicIntervalFunctionDecorator(IntervalFunction intervalFunction) {
		this.intervalFunction = notNull(intervalFunction);
	}

	@Override
	public Long apply(Integer attempt, Either<Throwable, Response> eitherResponse) {
		final Response response = eitherResponse.isRight() ? eitherResponse.get() : null;
		final Throwable failure = eitherResponse.isLeft() ? eitherResponse.getLeft() : null;
		log.atWarn().log(getRetryLogSupplier(attempt, response, failure));

		return getRetryAfterHeader(response)
				.map(RetryAfter::getMillisUntilRetry)
				.orElseGet(() -> intervalFunction.apply(attempt));
	}

	private Supplier<String> getRetryLogSupplier(Integer attempt, Response response, Throwable failure) {
		if (response != null) {
			return () -> "Retrying request (attempt #%d) to %s after receiving response code %s"
					.formatted(attempt, response.getRequestedUrl(), response.getHttpStatus());
		} else if (failure != null) {
			return () -> getUrl(failure)
					.map(url -> "Retrying request (attempt #%d) to %s after %s".formatted(attempt, url, describe(failure)))
					.orElseGet(() -> "Retrying request (attempt #%d) after %s".formatted(attempt, describe(failure)));
		} else {
			return () -> "Retrying request (attempt #%d) after null response".formatted(attempt);
		}
	}

	private Optional<String> getUrl(Throwable failure) {
		return failure instanceof HttpRequestException httpRequestException
				? httpRequestException.getUrl()
				: Optional.empty();
	}

	/**
	 * An {@link HttpRequestException} only wraps the I/O error, so the cause is the interesting part,
	 * e.g. "java.net.SocketTimeoutException: Read timed out".
	 */
	private String describe(Throwable failure) {
		final Throwable cause = failure instanceof HttpRequestException && failure.getCause() != null
				? failure.getCause()
				: failure;
		return cause.getMessage() == null ? cause.getClass().getName() : cause.getClass().getName() + ": " + cause.getMessage();
	}

	private Optional<RetryAfter> getRetryAfterHeader(Response nullableResponse) {
		return Optional.ofNullable(nullableResponse)
				.flatMap(response -> response.getFirstHeaderValue(HttpHeaders.RETRY_AFTER))
				.map(RetryAfter::from);
	}

}
