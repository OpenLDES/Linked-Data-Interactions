package org.openldes.ldi.requestexecutor.services;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Best-effort, single-use reuse of discovery responses. Weak owner identity and
 * bounded entries prevent abandoned setup responses retaining executors or an
 * unlimited number of response bodies. Eviction simply causes a normal fetch.
 */
public final class SingleUseResponseRegistry {
	private static final int MAX_OWNERS = 128;
	private static final int MAX_ENTRIES = 128;
	private static final List<OwnerCache> CACHES = new ArrayList<>();

	private SingleUseResponseRegistry() {
	}

	private static OwnerCache cache(RequestExecutor owner, boolean create) {
		CACHES.removeIf(cache -> cache.owner.get() == null
				|| (cache.responses.isEmpty() && cache.resolvedUrls.isEmpty()));
		for (OwnerCache cache : CACHES) {
			if (cache.owner.get() == owner) {
				return cache;
			}
		}
		if (!create) {
			return null;
		}
		if (CACHES.size() >= MAX_OWNERS) {
			CACHES.removeFirst();
		}
		final OwnerCache cache = new OwnerCache(owner);
		CACHES.add(cache);
		return cache;
	}

	private static <T> void put(Map<String, T> entries, String url, T value) {
		entries.put(url, value);
		if (entries.size() > MAX_ENTRIES) {
			entries.remove(entries.keySet().iterator().next());
		}
	}

	public static synchronized void capture(RequestExecutor owner, String url, Response response) {
		if (owner != null && url != null && response != null && response.isOk()) {
			put(cache(owner, true).responses, url, response);
		}
	}

	public static synchronized Optional<Response> consume(RequestExecutor owner, Request request) {
		return request == null ? Optional.empty() : consume(owner, request.getUrl());
	}

	public static synchronized Optional<Response> consume(RequestExecutor owner, String url) {
		final OwnerCache cache = owner == null ? null : cache(owner, false);
		return cache == null ? Optional.empty() : Optional.ofNullable(cache.responses.remove(url));
	}

	public static synchronized void resolve(RequestExecutor owner, String sourceUrl, String resolvedUrl) {
		if (owner != null && sourceUrl != null && resolvedUrl != null) {
			put(cache(owner, true).resolvedUrls, sourceUrl, resolvedUrl);
		}
	}

	public static synchronized Optional<String> consumeResolvedUrl(RequestExecutor owner, String sourceUrl) {
		final OwnerCache cache = owner == null ? null : cache(owner, false);
		return cache == null ? Optional.empty() : Optional.ofNullable(cache.resolvedUrls.remove(sourceUrl));
	}

	private static final class OwnerCache {
		private final WeakReference<RequestExecutor> owner;
		private final Map<String, Response> responses = new LinkedHashMap<>();
		private final Map<String, String> resolvedUrls = new LinkedHashMap<>();

		private OwnerCache(RequestExecutor owner) {
			this.owner = new WeakReference<>(owner);
		}
	}
}
