package org.openldes.ldi.requestexecutor.executor.edc;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.edc.services.TokenService;
import org.openldes.ldi.requestexecutor.executor.edc.valueobjects.EdcUrlProxy;
import org.openldes.ldi.requestexecutor.valueobjects.*;

import java.util.List;

public class EdcRequestExecutor implements RequestExecutor {

	private final RequestExecutor requestExecutor;
	private final TokenService tokenService;
	private final EdcUrlProxy urlProxy;

	public EdcRequestExecutor(RequestExecutor requestExecutor, TokenService tokenService, EdcUrlProxy urlProxy) {
		this.requestExecutor = requestExecutor;
		this.tokenService = tokenService;
		this.urlProxy = urlProxy;
	}

	@Override
	public Response execute(Request request) {
		final Request edcRequest = createEdcRequest(request);
		var response = requestExecutor.execute(edcRequest);
		if (response.isForbidden()) {
			tokenService.invalidateToken();
			return execute(request);
		} else {
			return response;
		}
	}

	private Request createEdcRequest(Request request) {
		final var tokenHeader = tokenService.waitForTokenHeader();
		final var requestHeaders = new RequestHeaders(List.of(tokenHeader));
		final var url = urlProxy.proxy(request.getUrl());
		return request.with(url).with(requestHeaders);
	}

}
