package org.openldes.ldi.requestexecutor.exceptions;

import java.util.Optional;

public class HttpRequestException extends RuntimeException {
	private final String url;

	public HttpRequestException(Exception e) {
		this(null, e);
	}

	/**
	 * @param url the url of the request that failed, used in log messages (the message itself stays that of the cause)
	 * @param e   the cause
	 */
	public HttpRequestException(String url, Exception e) {
		super(e);
		this.url = url;
	}

	public Optional<String> getUrl() {
		return Optional.ofNullable(url);
	}
}
