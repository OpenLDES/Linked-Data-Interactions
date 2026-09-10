package ldes.client.eventstreamproperties;

import ldes.client.eventstreamproperties.services.StartingNodeSpecificationFactory;
import ldes.client.eventstreamproperties.valueobjects.EventStreamProperties;
import ldes.client.eventstreamproperties.valueobjects.PropertiesRequest;
import ldes.client.eventstreamproperties.valueobjects.StartingNodeSpecification;
import org.apache.http.HttpHeaders;
import org.openldes.ldi.rdf.parser.RdfResponseParser;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

public class EventStreamPropertiesFetcher {
	private final RequestExecutor responseReuseOwner;
	private final RequestExecutor requestExecutor;

	public EventStreamPropertiesFetcher(RequestExecutor requestExecutor) {
		this.responseReuseOwner = requestExecutor;
		this.requestExecutor = RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor);
	}

	public EventStreamProperties fetchEventStreamProperties(PropertiesRequest request) {
		PropertiesResponse propertiesResponse = executePropertiesRequest(request, request.url());
		PropertiesResponse entrypointResponse = propertiesResponse;
		if (propertiesResponse.properties().needsEventStreamFollowUp()) {
			propertiesResponse = executePropertiesRequest(
					request.withUrl(propertiesResponse.properties().getUri()),
					request.url());
		}

		prepareTraversalResponse(request.url(), entrypointResponse, propertiesResponse);
		return propertiesResponse.properties();
	}

	private PropertiesResponse executePropertiesRequest(PropertiesRequest request, String discoveryUrl) {
		final Response response = requestExecutor.execute(request.createRequest());

		if (response.isOk()) {
			final EventStreamProperties properties = response.getBody()
					.map(body -> RdfResponseParser.parseDataset(
							body,
							response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE).orElse(null),
							request.url(),
							request.lang()))
					.map(dataset -> StartingNodeSpecificationFactory.fromDataset(
							dataset, request.url(), discoveryUrl))
					.map(StartingNodeSpecification::extractEventStreamProperties)
					.orElseThrow(() -> new IllegalStateException("Event stream properties response has no body."));
			return new PropertiesResponse(properties, request.url(), response);
		}

		if (response.isRedirect()) {
			final String redirectLocation = response.getRedirectLocation()
					.orElseThrow(() -> new IllegalStateException("No Location header in redirect response."));
			return executePropertiesRequest(request.withUrl(redirectLocation), discoveryUrl);
		}

		throw new UnsupportedOperationException(
				"Cannot handle response " + response.getHttpStatus() + " of EventStreamPropertiesRequest " + request);
	}

	private void prepareTraversalResponse(String entrypoint, PropertiesResponse entrypointResponse, PropertiesResponse propertiesResponse) {
		final String rootNode = propertiesResponse.properties().getRootNode();
		final boolean followedEntrypointForMetadata = !entrypointResponse.responseUrl().equals(propertiesResponse.responseUrl());
		SingleUseResponseRegistry.resolve(responseReuseOwner, entrypoint,
				followedEntrypointForMetadata && rootNode != null && rootNode.equals(propertiesResponse.responseUrl())
						? entrypointResponse.responseUrl()
						: rootNode);
		if (followedEntrypointForMetadata) {
			SingleUseResponseRegistry.capture(
					responseReuseOwner,
					entrypointResponse.responseUrl(),
					entrypointResponse.response());
		}
		if (rootNode != null && rootNode.equals(propertiesResponse.responseUrl())) {
			SingleUseResponseRegistry.capture(
					responseReuseOwner,
					propertiesResponse.responseUrl(),
					propertiesResponse.response());
		}
	}

	private record PropertiesResponse(EventStreamProperties properties, String responseUrl, Response response) {
	}
}
