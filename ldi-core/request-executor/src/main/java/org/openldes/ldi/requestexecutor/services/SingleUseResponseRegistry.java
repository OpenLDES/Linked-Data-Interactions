package org.openldes.ldi.requestexecutor.services;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shares already fetched successful responses between client setup steps that
 * use the same request executor. Entries are consumed at most once.
 */
public final class SingleUseResponseRegistry {
	private static final Map<RequestExecutor, Map<String, Response>> RESPONSES_BY_EXECUTOR = new IdentityHashMap<>();
	private static final Map<RequestExecutor, Map<String, String>> RESOLVED_URLS_BY_EXECUTOR = new IdentityHashMap<>();

	private SingleUseResponseRegistry() {
	}

	public static synchronized void capture(RequestExecutor owner, String url, Response response) {
		if (owner == null || url == null || response == null || !response.isOk()) {
			return;
		}

		RESPONSES_BY_EXECUTOR
				.computeIfAbsent(owner, ignored -> new HashMap<>())
				.put(url, response);
	}

	public static synchronized Optional<Response> consume(RequestExecutor owner, Request request) {
		if (owner == null || request == null) {
			return Optional.empty();
		}

		return consume(owner, request.getUrl());
	}

	public static synchronized Optional<Response> consume(RequestExecutor owner, String url) {
		if (owner == null || url == null) {
			return Optional.empty();
		}

		final Map<String, Response> responsesByUrl = RESPONSES_BY_EXECUTOR.get(owner);
		if (responsesByUrl == null) {
			return Optional.empty();
		}

		final Response response = responsesByUrl.remove(url);
		if (responsesByUrl.isEmpty()) {
			RESPONSES_BY_EXECUTOR.remove(owner);
		}
		return Optional.ofNullable(response);
	}

	public static synchronized void resolve(RequestExecutor owner, String sourceUrl, String resolvedUrl) {
		if (owner == null || sourceUrl == null || resolvedUrl == null) {
			return;
		}

		RESOLVED_URLS_BY_EXECUTOR
				.computeIfAbsent(owner, ignored -> new HashMap<>())
				.put(sourceUrl, resolvedUrl);
	}

	public static synchronized Optional<String> consumeResolvedUrl(RequestExecutor owner, String sourceUrl) {
		if (owner == null || sourceUrl == null) {
			return Optional.empty();
		}

		final Map<String, String> resolvedUrls = RESOLVED_URLS_BY_EXECUTOR.get(owner);
		if (resolvedUrls == null) {
			return Optional.empty();
		}

		final String resolvedUrl = resolvedUrls.remove(sourceUrl);
		if (resolvedUrls.isEmpty()) {
			RESOLVED_URLS_BY_EXECUTOR.remove(owner);
		}
		return Optional.ofNullable(resolvedUrl);
	}
}
