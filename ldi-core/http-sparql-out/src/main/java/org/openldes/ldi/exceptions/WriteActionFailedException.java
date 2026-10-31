package org.openldes.ldi.exceptions;

public class WriteActionFailedException extends RuntimeException {
	private final String endpoint;
	private final int httpStatusCode;
	private final String responseBody;

	public WriteActionFailedException(String message) {
		super(message);
		this.endpoint = null;
		this.httpStatusCode = -1;
		this.responseBody = null;
	}

	/**
	 * The SPARQL host answered the update with a non-2xx status.
	 */
	public WriteActionFailedException(String endpoint, int httpStatusCode, String responseBody) {
		super("Failed to post model. The request url was %s. The http response obtained from the server has code %s and body \"%s\"."
				.formatted(endpoint, httpStatusCode, responseBody));
		this.endpoint = endpoint;
		this.httpStatusCode = httpStatusCode;
		this.responseBody = responseBody;
	}

	public String getEndpoint() {
		return endpoint;
	}

	/**
	 * @return the status the SPARQL host answered with, or -1 when the exception was created from a message only
	 */
	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}
}
