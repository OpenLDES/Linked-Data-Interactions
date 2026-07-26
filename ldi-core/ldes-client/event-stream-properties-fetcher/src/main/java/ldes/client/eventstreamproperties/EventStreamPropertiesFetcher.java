package ldes.client.eventstreamproperties;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.RetryableRequestExecutor;
import org.openldes.ldi.requestexecutor.executor.retry.RetryConfig;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.rdf.parser.RdfResponseParser;
import ldes.client.eventstreamproperties.services.StartingNodeSpecificationFactory;
import ldes.client.eventstreamproperties.valueobjects.EventStreamProperties;
import ldes.client.eventstreamproperties.valueobjects.PropertiesRequest;
import ldes.client.eventstreamproperties.valueobjects.StartingNodeSpecification;
import org.apache.http.HttpHeaders;

import java.util.List;

public class EventStreamPropertiesFetcher {
	private final RequestExecutor responseReuseOwner;
	private final RequestExecutor requestExecutor;

	public EventStreamPropertiesFetcher(RequestExecutor requestExecutor) {
		this.responseReuseOwner = requestExecutor;
		this.requestExecutor = withDefaultRetryPolicy(requestExecutor);
	}

	public EventStreamProperties fetchEventStreamProperties(PropertiesRequest request) {
		final EventStreamProperties eventStreamProperties = executePropertiesRequest(request);
		SingleUseResponseRegistry.resolve(responseReuseOwner, request.url(), eventStreamProperties.getRootNode());

		if(!eventStreamProperties.needsEventStreamFollowUp()) {
			return eventStreamProperties;
		}

		final EventStreamProperties followedEventStreamProperties = executePropertiesRequest(request.withUrl(eventStreamProperties.getUri()));
		SingleUseResponseRegistry.resolve(responseReuseOwner, request.url(), followedEventStreamProperties.getRootNode());
		return followedEventStreamProperties;

	}

	private EventStreamProperties executePropertiesRequest(PropertiesRequest request) {
		final Response response = requestExecutor.execute(request.createRequest());

		if(response.isOk()) {
			SingleUseResponseRegistry.capture(responseReuseOwner, request.url(), response);
			return response.getBody()
					.map(body -> RdfResponseParser.parseDataset(
							body,
							response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE).orElse(null),
							request.url(),
							request.lang()))
					.map(dataset -> StartingNodeSpecificationFactory.fromDataset(dataset, request.url()))
					.map(StartingNodeSpecification::extractEventStreamProperties)
					.orElseThrow();
		}

		if(response.isRedirect()) {
			final String redirectLocation = response.getRedirectLocation()
					.orElseThrow(() -> new IllegalStateException("No Location Header in redirect."));
			final EventStreamProperties properties = executePropertiesRequest(request.withUrl(redirectLocation));
			SingleUseResponseRegistry.alias(responseReuseOwner, redirectLocation, request.url());
			return properties;
		}

		throw new UnsupportedOperationException(
				"Cannot handle response " + response.getHttpStatus() + " of EventStreamPropertiesRequest " + request);
	}

	private static RequestExecutor withDefaultRetryPolicy(RequestExecutor requestExecutor) {
		if (requestExecutor instanceof RetryableRequestExecutor) {
			return requestExecutor;
		}

		return RequestExecutorDecorator
				.decorate(requestExecutor)
				.with(RetryConfig.of(RetryConfig.DEFAULT_MAX_ATTEMPTS, List.of()).getRetry())
				.get();
	}

}
