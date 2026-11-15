package org.openldes.ldi.requestexecutor.executor.noauth;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.RequestExecutorSupplier;
import org.openldes.ldi.requestexecutor.valueobjects.HttpTimeouts;
import org.apache.http.Header;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;

import java.util.Collection;

import static org.apache.commons.lang3.Validate.notNull;

public class DefaultConfig implements RequestExecutorSupplier {

	private final Collection<Header> headers;
	private final boolean enableRedirectHandling;
	private final HttpTimeouts timeouts;

	public DefaultConfig(Collection<Header> headers, boolean enableRedirectHandling) {
		this(headers, enableRedirectHandling, HttpTimeouts.defaults());
	}

	public DefaultConfig(Collection<Header> headers, boolean enableRedirectHandling, HttpTimeouts timeouts) {
		this.headers = headers;
		this.enableRedirectHandling = enableRedirectHandling;
		this.timeouts = notNull(timeouts);
	}

	/**
	 *
	 * @return instance of DefaultRequestExecutor
	 */
	@Override
	public RequestExecutor createRequestExecutor() {
		final RequestConfig requestConfig = timeouts.applyTo(RequestConfig.custom()).build();
		final HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig);
		if (!enableRedirectHandling) {
			httpClientBuilder.disableRedirectHandling();
		}

		return new DefaultRequestExecutor(httpClientBuilder.setDefaultHeaders(headers).build());
	}

}
