package org.openldes.ldi.requestexecutor.valueobjects;

import org.apache.http.client.config.RequestConfig;

import java.time.Duration;

import static org.apache.commons.lang3.Validate.isTrue;
import static org.apache.commons.lang3.Validate.notNull;

/**
 * Time limits applied to every request of an HTTP client.
 * <p>
 * Without these limits the Apache HttpClient waits forever: a server that accepts the connection but never sends a
 * response would block the calling thread indefinitely, which no retry or rate limiter can interrupt.
 *
 * @param connect           maximum time to establish the TCP connection
 * @param connectionRequest maximum time to wait for a free connection from the connection pool
 * @param socket            maximum time of inactivity between two data packets, e.g. while waiting for the response
 *                          headers of a slow server
 */
public record HttpTimeouts(Duration connect, Duration connectionRequest, Duration socket) {

	public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);
	public static final Duration DEFAULT_CONNECTION_REQUEST_TIMEOUT = Duration.ofMinutes(1);
	public static final Duration DEFAULT_SOCKET_TIMEOUT = Duration.ofMinutes(5);

	public HttpTimeouts {
		notNull(connect, "connect timeout must not be null");
		notNull(connectionRequest, "connection request timeout must not be null");
		notNull(socket, "socket timeout must not be null");
		isTrue(isValid(connect), "connect timeout must be positive and at most %d ms", Integer.MAX_VALUE);
		isTrue(isValid(connectionRequest), "connection request timeout must be positive and at most %d ms", Integer.MAX_VALUE);
		isTrue(isValid(socket), "socket timeout must be positive and at most %d ms", Integer.MAX_VALUE);
	}

	public static HttpTimeouts defaults() {
		return new HttpTimeouts(DEFAULT_CONNECT_TIMEOUT, DEFAULT_CONNECTION_REQUEST_TIMEOUT, DEFAULT_SOCKET_TIMEOUT);
	}

	/**
	 * @return a RequestConfig builder with the three timeouts set, to which other settings can be added
	 */
	public RequestConfig.Builder applyTo(RequestConfig.Builder builder) {
		return builder
				.setConnectTimeout((int) connect.toMillis())
				.setConnectionRequestTimeout((int) connectionRequest.toMillis())
				.setSocketTimeout((int) socket.toMillis());
	}

	private static boolean isValid(Duration duration) {
		return !duration.isNegative() && !duration.isZero() && duration.toMillis() <= Integer.MAX_VALUE;
	}

}
