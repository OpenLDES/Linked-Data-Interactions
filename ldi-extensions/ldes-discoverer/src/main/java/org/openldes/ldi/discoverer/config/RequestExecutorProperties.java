package org.openldes.ldi.discoverer.config;

import org.openldes.ldi.discoverer.valueobjects.AuthenticationProperties;
import org.openldes.ldi.discoverer.valueobjects.Headers;
import org.openldes.ldi.discoverer.valueobjects.RateLimitProperties;
import org.openldes.ldi.discoverer.valueobjects.RetryProperties;
import org.openldes.ldi.requestexecutor.valueobjects.AuthStrategy;
import org.apache.http.Header;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Configuration
public class RequestExecutorProperties {
	private final AuthenticationProperties authProperties;
	private final RateLimitProperties rateLimitProperties;
	private final RetryProperties retryProperties;
	private final Headers headers;

	public RequestExecutorProperties(ApplicationArguments arguments) {
		this.authProperties = new AuthenticationProperties(arguments);
		this.rateLimitProperties = new RateLimitProperties(arguments);
		this.retryProperties = new RetryProperties(arguments);
		this.headers = new Headers(arguments);
	}

	public AuthStrategy getAuthStrategy() {
		return authProperties.getAuthStrategy();
	}

	public String getApiKeyHeader() {
		return authProperties.getApiKeyHeader();
	}

	public String getApiKey() {
		return authProperties.getApiKey();
	}

	public String getClientId() {
		return authProperties.getClientId();
	}

	public String getClientSecret() {
		return authProperties.getClientSecret();
	}

	public String getTokenEndpoint() {
		return authProperties.getTokenEndpoint();
	}

	public Optional<String> getAuthScope() {
		return authProperties.getAuthScope();
	}

	public boolean isRetryingDisabled() {
		return retryProperties.isRetryingDisabled();
	}

	public int getRetryLimit() {
		return retryProperties.getRetryLimit();
	}

	public List<Integer> getRetryStatuses() {
		return retryProperties.getRetryStatuses();
	}

	public Optional<Integer> getRateLimit() {
		return rateLimitProperties.getRateLimit();
	}

	public Duration getRateLimitPeriod() {
		return rateLimitProperties.getRateLimitPeriod();
	}

	public List<Header> getHeaders() {
		return headers.getHeaders();
	}
}
