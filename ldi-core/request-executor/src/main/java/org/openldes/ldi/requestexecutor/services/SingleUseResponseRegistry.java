package org.openldes.ldi.requestexecutor.services;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Shares already fetched successful responses between client setup steps that
 * use the same request executor. Entries are consumed at most once.
 */
public final class SingleUseResponseRegistry {
	private static final Map<RequestExecutor, Map<String, Response>> RESPONSES_BY_EXECUTOR = new WeakHashMap<>();

	private SingleUseResponseRegistry() {
	}

	public static synchronized void capture(RequestExecutor owner, Response response) {
		if (owner == null || response == null || !response.isOk()) {
			return;
		}

		RESPONSES_BY_EXECUTOR
				.computeIfAbsent(owner, ignored -> new HashMap<>())
				.put(response.getRequestedUrl(), response);
	}

	public static synchronized Optional<Response> consume(RequestExecutor owner, Request request) {
		if (owner == null || request == null) {
			return Optional.empty();
		}

		final Map<String, Response> responsesByUrl = RESPONSES_BY_EXECUTOR.get(owner);
		if (responsesByUrl == null) {
			return Optional.empty();
		}

		final Response response = responsesByUrl.remove(request.getUrl());
		if (responsesByUrl.isEmpty()) {
			RESPONSES_BY_EXECUTOR.remove(owner);
		}
		return Optional.ofNullable(response);
	}
}
