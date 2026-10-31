package org.openldes.ldio.exceptions;

import org.openldes.ldi.exceptions.WriteActionFailedException;

/**
 * Thrown by Ldio:HttpSparqlOut when the SPARQL host still answers with a non-2xx status after the configured retries,
 * and the output is configured with continueOnFail: false.
 */
public class HttpSparqlOutException extends RuntimeException {

	private final String endpoint;
	private final int httpStatusCode;
	private final String responseBody;

	public HttpSparqlOutException(WriteActionFailedException cause) {
		super(cause);
		this.endpoint = cause.getEndpoint();
		this.httpStatusCode = cause.getHttpStatusCode();
		this.responseBody = cause.getResponseBody();
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
		return "Failed to write model to " + endpoint + ". Response has status code: " + httpStatusCode
		       + " and body: \"" + responseBody + "\"";
	}
}
