package org.openldes.ldi.requestexecutor.executor;

/**
 * Marker for request executors that already have a retry policy, including explicitly disabled retries.
 */
public interface RetryableRequestExecutor extends RequestExecutor {
}
