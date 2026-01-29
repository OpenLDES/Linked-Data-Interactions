package org.openldes.ldi.requestexecutor.executor.noauth;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;

/**
 * Default implementation of the RequestExecutor and acts like a wrapper around the Apache HttpClient
 */
public class DefaultRequestExecutor implements RequestExecutor {

	private final HttpClient httpClient;

	public DefaultRequestExecutor(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public Response execute(Request request) {
		try {
			HttpUriRequest httpRequest = new DefaultRequest(request).getHttpRequest();
			return new DefaultResponse(request, httpClient.execute(httpRequest)).getResponse();
		} catch (IOException e) {
			throw new HttpRequestException(e);
		}
	}

}
