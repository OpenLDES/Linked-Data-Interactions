package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodefetcher.domain.entities.TreeMember;
import ldes.client.treenodefetcher.domain.valueobjects.ModelResponse;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeRequest;
import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.LdesMetaData;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.repository.MemberIdRepository;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.openldes.ldi.rdf.parser.RdfResponseParser;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Ordered LDES member supplier that emits incrementally using a priority
 * frontier of TREE relation targets.
 * <p>
 * The supplier buffers discovered members by their LDES order key and fetches
 * relation targets by the earliest order key they can still contain. A member
 * is emitted when its order key is before the lower bound of every still-open
 * frontier node.
 */
public class StreamingOrderedMemberSupplier implements MemberSupplier {
	private static final String TREE = "https://w3id.org/tree#";
	private static final Property TREE_RELATION = ResourceFactory.createProperty(TREE, "relation");
	private static final Property TREE_NODE = ResourceFactory.createProperty(TREE, "node");
	private static final Property TREE_PATH = ResourceFactory.createProperty(TREE, "path");
	private static final Property TREE_VALUE = ResourceFactory.createProperty(TREE, "value");
	private static final String TREE_GREATER_THAN = TREE + "GreaterThanRelation";
	private static final String TREE_GREATER_THAN_OR_EQUAL_TO = TREE + "GreaterThanOrEqualToRelation";
	private static final String TREE_LESS_THAN = TREE + "LessThanRelation";
	private static final String TREE_LESS_THAN_OR_EQUAL_TO = TREE + "LessThanOrEqualToRelation";

	private final LdesMetaData metadata;
	private final RequestExecutor responseReuseOwner;
	private final RequestExecutor requestExecutor;
	private final MemberIdRepository memberIdRepository;
	private final boolean keepState;
	private final MemberOrdering ordering;
	private final PriorityQueue<FrontierNode> frontier = new PriorityQueue<>();
	private final PriorityQueue<MemberOrdering.OrderedMember> members = new PriorityQueue<>();
	private final Set<String> visitedNodes = new HashSet<>();

	/**
	 * Compatibility entry point for adapters compiled against the original API.
	 * New callers should group the ordering settings in {@link OrderingConfiguration}.
	 */
	@SuppressWarnings("java:S107")
	public StreamingOrderedMemberSupplier(
			LdesMetaData metadata,
			RequestExecutor requestExecutor,
			MemberIdRepository memberIdRepository,
			boolean keepState,
			String rootNode,
			Optional<RDFNode> timestampPath,
			Optional<RDFNode> sequencePath,
			Optional<RDFNode> transactionPath,
			Optional<RDFNode> transactionFinalizedPath,
			Optional<RDFNode> transactionFinalizedObject) {
		this(
				metadata,
				requestExecutor,
				memberIdRepository,
				keepState,
				new OrderingConfiguration(
						rootNode,
						timestampPath,
						sequencePath,
						transactionPath,
						transactionFinalizedPath,
						transactionFinalizedObject));
	}

	public StreamingOrderedMemberSupplier(
			LdesMetaData metadata,
			RequestExecutor requestExecutor,
			MemberIdRepository memberIdRepository,
			boolean keepState,
			OrderingConfiguration ordering) {
		this.metadata = metadata;
		this.responseReuseOwner = requestExecutor;
		this.requestExecutor = RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor);
		this.memberIdRepository = memberIdRepository;
		this.keepState = keepState;
		this.ordering = new MemberOrdering(
				ordering.timestampPath(),
				ordering.sequencePath(),
				ordering.transactionPath(),
				ordering.transactionFinalizedPath(),
				ordering.transactionFinalizedObject());
		final String rootNode = ordering.rootNode();
		this.frontier.add(new FrontierNode(rootNode == null ? metadata.getStartingNodeUrl() : rootNode, FrontierBound.unboundedBound()));
	}

	@Override
	public void init() {
		// Frontier is initialized in the constructor.
	}

	@Override
	public SuppliedMember get() {
		while (true) {
			if (canEmitNextMember()) {
				return members.poll().member();
			}
			if (!frontier.isEmpty()) {
				processNextFrontierNode();
				continue;
			}
			if (!members.isEmpty()) {
				return members.poll().member();
			}
			throw new EndOfLdesException("No ordered members or frontier nodes left to process.");
		}
	}

	@Override
	public void destroyState() {
		if (!keepState) {
			memberIdRepository.destroyState();
		}
	}

	private boolean canEmitNextMember() {
		if (members.isEmpty()) {
			return false;
		}
		if (frontier.isEmpty()) {
			return true;
		}
		return frontier.peek().lowerBound().isAfter(members.peek().orderValues());
	}

	private void processNextFrontierNode() {
		final FrontierNode node = frontier.poll();
		if (!visitedNodes.add(node.url())) {
			return;
		}

		final FetchResult result = fetch(node.url(), List.of());
		for (TreeMember member : result.members()) {
			if (memberIdRepository.addMemberIdIfNotExists(member.getMemberId())) {
				final SuppliedMember suppliedMember = new SuppliedMember(member.getMemberId(), member.getDataset());
				members.add(ordering.order(suppliedMember));
			}
		}
		extractRelationBounds(result.dataset(), result.url()).forEach((url, bound) -> {
			if (!visitedNodes.contains(url)) {
				frontier.add(new FrontierNode(url, bound));
			}
		});
	}

	private FetchResult fetch(String url, List<String> redirectHistory) {
		final TreeNodeRequest request = metadata.createRequest(url);
		final Request httpRequest = request.createRequest();
		final Response response = SingleUseResponseRegistry.consume(responseReuseOwner, httpRequest)
				.orElseGet(() -> requestExecutor.execute(httpRequest));
		if (response.isRedirect()) {
			final String location = response.getRedirectLocation()
					.orElseThrow(() -> new IllegalStateException("No Location header in redirect."));
			if (redirectHistory.contains(location) || url.equals(location)) {
				throw new IllegalStateException("Infinite redirect loop.");
			}
			final List<String> updatedHistory = new ArrayList<>(redirectHistory);
			updatedHistory.add(url);
			return fetch(location, updatedHistory);
		}
		if (response.hasStatus(List.of(HttpStatus.SC_GONE)) || response.isNotModified()) {
			return new FetchResult(url, org.apache.jena.query.DatasetFactory.create(), List.of());
		}
		if (!response.isOk()) {
			throw new UnsupportedOperationException("Cannot handle response " + response.getHttpStatus() + " of TreeNodeRequest " + request);
		}

		final byte[] body = response.getBody().orElseThrow();
		final String contentType = response.getFirstHeaderValue(HttpHeaders.CONTENT_TYPE).orElse(null);
		final Dataset dataset = RdfResponseParser.parseDataset(body, contentType, url, metadata.getLang());
		final ModelResponse modelResponse = new ModelResponse(dataset, new TimestampFromCurrentTimeExtractor(), url);
		return new FetchResult(url, dataset, modelResponse.getMembers());
	}

	private Map<String, FrontierBound> extractRelationBounds(Dataset dataset, String currentUrl) {
		final Model model = dataset.getDefaultModel();
		final Map<String, List<Resource>> relationsByTarget = new HashMap<>();
		final Resource currentNode = model.createResource(currentUrl);
		final List<Resource> relationResources = model.listObjectsOfProperty(currentNode, TREE_RELATION)
				.toList()
				.stream()
				.filter(RDFNode::isResource)
				.map(RDFNode::asResource)
				.toList();
		for (Resource relation : relationResources) {
			final Resource target = Optional.ofNullable(relation.getPropertyResourceValue(TREE_NODE))
					.filter(RDFNode::isURIResource)
					.orElse(null);
			if (target != null) {
				relationsByTarget.computeIfAbsent(target.getURI(), ignored -> new ArrayList<>()).add(relation);
			}
		}

		final Map<String, FrontierBound> bounds = new HashMap<>();
		relationsByTarget.forEach((target, relations) -> bounds.put(target, combineBounds(relations)));
		return bounds;
	}

	private FrontierBound combineBounds(List<Resource> relations) {
		FrontierBound bound = FrontierBound.unboundedBound();
		for (Resource relation : relations) {
			final FrontierBound relationBound = relationBound(relation);
			if (relationBound.compareTo(bound) > 0) {
				bound = relationBound;
			}
		}
		return bound;
	}

	private FrontierBound relationBound(Resource relation) {
		final RDFNode path = Optional.ofNullable(relation.getProperty(TREE_PATH)).map(statement -> statement.getObject()).orElse(null);
		final RDFNode value = Optional.ofNullable(relation.getProperty(TREE_VALUE)).map(statement -> statement.getObject()).orElse(null);
		if (path == null || value == null || !ordering.matchesPrimaryPath(path)) {
			return FrontierBound.unboundedBound();
		}
		final List<String> types = relation.listProperties(RDF.type)
				.toList()
				.stream()
				.map(statement -> statement.getObject())
				.filter(RDFNode::isURIResource)
				.map(node -> node.asResource().getURI())
				.toList();
		if (types.contains(TREE_GREATER_THAN)) {
			return FrontierBound.lowerBound(MemberOrdering.OrderValues.of(value), false);
		}
		if (types.contains(TREE_GREATER_THAN_OR_EQUAL_TO)) {
			return FrontierBound.lowerBound(MemberOrdering.OrderValues.of(value), true);
		}
		if (types.contains(TREE_LESS_THAN) || types.contains(TREE_LESS_THAN_OR_EQUAL_TO)) {
			return FrontierBound.unboundedBound();
		}
		return FrontierBound.unboundedBound();
	}

	private record FetchResult(String url, Dataset dataset, List<TreeMember> members) {
	}

	public record OrderingConfiguration(
			String rootNode,
			Optional<RDFNode> timestampPath,
			Optional<RDFNode> sequencePath,
			Optional<RDFNode> transactionPath,
			Optional<RDFNode> transactionFinalizedPath,
			Optional<RDFNode> transactionFinalizedObject) {
	}

	private record FrontierNode(String url, FrontierBound lowerBound) implements Comparable<FrontierNode> {
		@Override
		public int compareTo(FrontierNode other) {
			final int boundComparison = lowerBound.compareTo(other.lowerBound);
			if (boundComparison != 0) {
				return boundComparison;
			}
			return url.compareTo(other.url);
		}
	}

	private record FrontierBound(MemberOrdering.OrderValues value, boolean inclusive, boolean unbounded)
			implements Comparable<FrontierBound> {
		private static FrontierBound unboundedBound() {
			return new FrontierBound(null, true, true);
		}

		private static FrontierBound lowerBound(MemberOrdering.OrderValues value, boolean inclusive) {
			return new FrontierBound(value, inclusive, false);
		}

		private boolean isAfter(MemberOrdering.OrderValues memberValue) {
			if (unbounded) {
				return false;
			}
			final int comparison = value.compareTo(memberValue);
			return inclusive ? comparison > 0 : comparison >= 0;
		}

		@Override
		public int compareTo(FrontierBound other) {
			if (unbounded && other.unbounded) {
				return 0;
			}
			if (unbounded) {
				return -1;
			}
			if (other.unbounded) {
				return 1;
			}
			final int valueComparison = value.compareTo(other.value);
			if (valueComparison != 0) {
				return valueComparison;
			}
			return Boolean.compare(other.inclusive, inclusive);
		}
	}

}
