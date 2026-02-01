package org.openldes.ldi.requestexecutor.exceptions;

public class HttpRequestException extends RuntimeException {
	public HttpRequestException(Exception e) {
		super(e);
	}
}
