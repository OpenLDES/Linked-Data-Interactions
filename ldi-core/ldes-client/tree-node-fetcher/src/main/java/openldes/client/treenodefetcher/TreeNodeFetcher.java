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
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.system.StreamRDFBase;
import org.apache.jena.sparql.core.Quad;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ldes.client.treenodefetcher.domain.valueobjects.Constants.W3ID_TREE_NODE;
import static ldes.client.treenodefetcher.domain.valueobjects.Constants.W3ID_TREE_RELATION;

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
		return fetchTreeNode(treeNodeRequest, List.of());
	}

	private TreeNodeResponse fetchTreeNode(TreeNodeRequest treeNodeRequest, List<String> redirectHistory) {
		final Response response = requestExecutor.execute(treeNodeRequest.createRequest());

		if (response.isOk()) {
			return createOkResponse(treeNodeRequest, response);
		}

		if (response.isRedirect()) {
			return fetchRedirectedTreeNode(treeNodeRequest, response, redirectHistory);
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

	private TreeNodeResponse fetchRedirectedTreeNode(
			TreeNodeRequest treeNodeRequest,
			Response response,
			List<String> redirectHistory) {
		final String redirectLocation = response.getRedirectLocation()
				.orElseThrow(() -> new IllegalStateException("No Location Header in redirect."));
		if (redirectHistory.contains(redirectLocation) || treeNodeRequest.getTreeNodeUrl().equals(redirectLocation)) {
			throw new IllegalStateException("Infinite redirect loop.");
		}
		final List<String> updatedRedirectHistory = new ArrayList<>(redirectHistory);
		updatedRedirectHistory.add(treeNodeRequest.getTreeNodeUrl());
		return fetchTreeNode(treeNodeRequest.createRedirectedRequest(redirectLocation), updatedRedirectHistory);
	}

	private TreeNodeResponse createOkResponse(TreeNodeRequest treeNodeRequest, Response response) {
		final byte[] responseBody = response.getBody().orElseThrow();
		final String contentType = response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE).orElse(null);
		final Dataset dataset = RdfResponseParser.parseDataset(
				responseBody,
				contentType,
				treeNodeRequest.getTreeNodeUrl(),
				treeNodeRequest.getLang());
		final ModelResponse modelResponse = new ModelResponse(
				dataset,
				timestampExtractor,
				treeNodeRequest.getTreeNodeUrl());
		final MutabilityStatus mutabilityStatus = getMutabilityStatus(response, modelResponse);
		final List<String> relations = extractRelationsInDocumentOrder(
				responseBody,
				contentType,
				treeNodeRequest.getTreeNodeUrl(),
				treeNodeRequest.getLang());
		return new TreeNodeResponse(
				relations.isEmpty() ? modelResponse.getRelations() : relations,
				modelResponse.getMembers(),
				mutabilityStatus,
				getEtag(response),
				dataset,
				treeNodeRequest.getTreeNodeUrl());
	}

	private static TreeNodeResponse createNotModifiedResponse(Response response) {
		return new TreeNodeResponse(List.of(), List.of(), getMutabilityStatus(response), getEtag(response));
	}

	private static TreeNodeResponse createGoneResponse() {
		return new TreeNodeResponse(List.of(), List.of(), new MutabilityStatus(false, maxSupportedDateTime));
	}

	private static MutabilityStatus getMutabilityStatus(Response response, ModelResponse modelResponse) {
		if (modelResponse.isImmutable()) {
			return new MutabilityStatus(false, LocalDateTime.now(ZoneOffset.UTC));
		}
		return response.getFirstHeaderValue(HttpHeaders.CACHE_CONTROL)
				.map(MutabilityStatus::ofHeader)
				.orElseGet(() -> getEmptyCacheControlMutabilityStatus(modelResponse));
	}

	private static String getEtag(Response response) {
		return response.getFirstHeaderValue(HttpHeaders.ETAG).orElse(null);
	}

	private static List<String> extractRelationsInDocumentOrder(byte[] responseBody, String contentType, String baseIri, Lang fallbackLang) {
		final RelationOrderCollector collector = new RelationOrderCollector(baseIri);
		RdfResponseParser.parser(responseBody, contentType, baseIri, fallbackLang).parse(collector);
		return collector.getRelations();
	}

	/**
	 * Collects the relation targets of one tree node in document order.
	 * <p>
	 * Only relations of the tree node that was requested are collected, so
	 * relations that another resource in the same response declares are not
	 * traversed. A single relation may name more than one target, and every
	 * named target is collected.
	 */
	private static class RelationOrderCollector extends StreamRDFBase {
		private final Node treeNode;
		private final List<Node> relationNodes = new ArrayList<>();
		private final Map<Node, List<String>> treeNodesByRelation = new HashMap<>();

		private RelationOrderCollector(String treeNodeIri) {
			this.treeNode = NodeFactory.createURI(treeNodeIri);
		}

		@Override
		public void triple(Triple triple) {
			process(triple);
		}

		@Override
		public void quad(Quad quad) {
			process(quad.asTriple());
		}

		private void process(Triple triple) {
			if (W3ID_TREE_RELATION.asNode().equals(triple.getPredicate())
					&& treeNode.equals(triple.getSubject())) {
				relationNodes.add(triple.getObject());
			}
			if (W3ID_TREE_NODE.asNode().equals(triple.getPredicate()) && triple.getObject().isURI()) {
				treeNodesByRelation
						.computeIfAbsent(triple.getSubject(), relation -> new ArrayList<>())
						.add(triple.getObject().getURI());
			}
		}

		private List<String> getRelations() {
			return relationNodes.stream()
					.flatMap(relationNode -> targetsOf(relationNode).stream())
					.distinct()
					.toList();
		}

		private List<String> targetsOf(Node relationNode) {
			final List<String> targets = treeNodesByRelation.get(relationNode);
			if (targets != null) {
				return targets;
			}
			// A relation that names no tree:node but is itself an IRI is read as
			// naming that IRI, which is how earlier client versions behaved.
			return relationNode.isURI() ? List.of(relationNode.getURI()) : List.of();
		}
	}

	private static MutabilityStatus getMutabilityStatus(Response response) {
		return response.getFirstHeaderValue(HttpHeaders.CACHE_CONTROL)
				.map(MutabilityStatus::ofHeader)
				.orElseGet(MutabilityStatus::empty);
	}

	private static MutabilityStatus getEmptyCacheControlMutabilityStatus(ModelResponse modelResponse) {
		if (modelResponse.getMembers().isEmpty() && modelResponse.getRelations().isEmpty()) {
			return new MutabilityStatus(false, LocalDateTime.now(ZoneOffset.UTC));
		}
		return MutabilityStatus.empty();
	}
}
