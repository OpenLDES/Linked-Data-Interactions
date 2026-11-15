package org.openldes.ldio.exceptions;

/**
 * Thrown by Ldio:HttpOut when the target still answers with a non-2xx status after the configured retries,
 * and the output is configured with continueOnFail: false.
 */
public class HttpOutException extends RuntimeException {

	private final String endpoint;
	private final int httpStatusCode;
	private final String responseBody;

	public HttpOutException(String endpoint, int httpStatusCode, String responseBody) {
		this.endpoint = endpoint;
		this.httpStatusCode = httpStatusCode;
		this.responseBody = responseBody;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public int getHttpStatusCode() {
		return httpStatusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}

	@Override
	public String getMessage() {
		return "Failed to post model to " + endpoint + ". Response has status code: " + httpStatusCode
		       + " and body: \"" + responseBody + "\"";
	}
}
