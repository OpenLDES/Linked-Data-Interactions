package ldes.client.treenodefetcher;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.rdf.parser.RdfResponseParser;
import org.openldes.ldi.timestampextractor.TimestampExtractor;
import ldes.client.treenodefetcher.domain.valueobjects.ModelResponse;
import ldes.client.treenodefetcher.domain.valueobjects.MutabilityStatus;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeRequest;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeResponse;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Responsible for fetching the next TreeNodes
 */
public class TreeNodeFetcher {

	private static final LocalDateTime maxSupportedDateTime = LocalDateTime.of(294276, 12, 31, 23, 59, 59);
	private final RequestExecutor requestExecutor;
	private final TimestampExtractor timestampExtractor;

	public TreeNodeFetcher(RequestExecutor requestExecutor, TimestampExtractor timestampExtractor) {
		this.requestExecutor = requestExecutor;
		this.timestampExtractor = timestampExtractor;
	}

	/**
	 * @param treeNodeRequest based on the relations found in the previous TreeNode
	 * @return the new TreeNode with all its information
	 */
	public TreeNodeResponse fetchTreeNode(TreeNodeRequest treeNodeRequest) {
		final Response response = requestExecutor.execute(treeNodeRequest.createRequest());

		if (response.isOk()) {
			return createOkResponse(treeNodeRequest, response);
		}

		if (response.isRedirect()) {
			return createRedirectResponse(response);
		}

		if (response.isNotModified()) {
			return createNotModifiedResponse(response);
		}

		if (response.hasStatus(List.of(HttpStatus.SC_GONE))) {
			return createGoneResponse();
		}

		throw new UnsupportedOperationException(
				"Cannot handle response " + response.getHttpStatus() + " of TreeNodeRequest " + treeNodeRequest);
	}

	private TreeNodeResponse createOkResponse(TreeNodeRequest treeNodeRequest, Response response) {
		final byte[] responseBody = response.getBody().orElseThrow();
		final String contentType = response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE).orElse(null);
		final ModelResponse modelResponse = new ModelResponse(
				RdfResponseParser.parseDataset(
						responseBody,
							contentType,
							treeNodeRequest.getTreeNodeUrl(),
							treeNodeRequest.getLang()),
				timestampExtractor,
				treeNodeRequest.getTreeNodeUrl());
		final MutabilityStatus mutabilityStatus = getMutabilityStatus(response, modelResponse);
		return new TreeNodeResponse(modelResponse.getRelations(), modelResponse.getMembers(), mutabilityStatus);
	}

	private static TreeNodeResponse createRedirectResponse(Response response) {
		return new TreeNodeResponse(
				List.of(response.getRedirectLocation()
						.orElseThrow(() -> new IllegalStateException("No Location Header in redirect."))),
				List.of(),
				new MutabilityStatus(false, maxSupportedDateTime));
	}

	private static TreeNodeResponse createNotModifiedResponse(Response response) {
		return new TreeNodeResponse(List.of(), List.of(), getMutabilityStatus(response));
	}

	private static TreeNodeResponse createGoneResponse() {
		return new TreeNodeResponse(List.of(), List.of(), new MutabilityStatus(false, maxSupportedDateTime));
	}

	private static MutabilityStatus getMutabilityStatus(Response response, ModelResponse modelResponse) {
		return response.getFirstHeaderValue(HttpHeaders.CACHE_CONTROL)
				.map(MutabilityStatus::ofHeader)
				.orElseGet(() -> getEmptyCacheControlMutabilityStatus(modelResponse));
	}

	private static MutabilityStatus getMutabilityStatus(Response response) {
		return response.getFirstHeaderValue(HttpHeaders.CACHE_CONTROL)
				.map(MutabilityStatus::ofHeader)
				.orElseGet(MutabilityStatus::empty);
	}

	private static MutabilityStatus getEmptyCacheControlMutabilityStatus(ModelResponse modelResponse) {
		if (modelResponse.getMembers().isEmpty() && modelResponse.getRelations().isEmpty()) {
			return new MutabilityStatus(false, LocalDateTime.now());
		}
		return MutabilityStatus.empty();
	}
}
