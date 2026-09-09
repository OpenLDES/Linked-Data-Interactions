package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodefetcher.TreeNodeFetcher;
import ldes.client.treenodefetcher.domain.entities.TreeMember;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeResponse;
import ldes.client.treenodesupplier.domain.entities.TreeNodeRecord;
import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.LdesMetaData;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.domain.valueobject.TreeNodeStatus;
import ldes.client.treenodesupplier.repository.MemberIdRepository;
import ldes.client.treenodesupplier.repository.TreeNodeRecordRepository;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
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
	private final TreeNodeFetcher treeNodeFetcher;
	private final MemberIdRepository memberIdRepository;
	private final TreeNodeRecordRepository treeNodeRecordRepository;
	private final boolean keepState;
	private final MemberOrdering ordering;
	private final PriorityQueue<FrontierNode> frontier = new PriorityQueue<>();
	private final PriorityQueue<MemberOrdering.OrderedMember> members = new PriorityQueue<>();
	private final Set<String> visitedNodes = new HashSet<>();

	/**
	 * Compatibility entry point that adds the tree node record repository to the
	 * flat ordering arguments, so a caller which has not moved to
	 * {@link OrderingConfiguration} can still resume from persisted node state.
	 */
	@SuppressWarnings("java:S107")
	public StreamingOrderedMemberSupplier(
			LdesMetaData metadata,
			RequestExecutor requestExecutor,
			MemberIdRepository memberIdRepository,
			TreeNodeRecordRepository treeNodeRecordRepository,
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
				treeNodeRecordRepository,
				keepState,
				new OrderingConfiguration(
						rootNode,
						timestampPath,
						sequencePath,
						transactionPath,
						transactionFinalizedPath,
						transactionFinalizedObject));
	}

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
		this(metadata, requestExecutor, memberIdRepository, null, keepState, ordering);
	}

	/**
	 * @param treeNodeRecordRepository keeps the cache validator and processed
	 *                                 state of each tree node, so a later
	 *                                 synchronization run revalidates a mutable
	 *                                 node instead of refetching it and skips an
	 *                                 immutable one entirely. May be
	 *                                 <code>null</code>, in which case every run
	 *                                 refetches every node it reaches.
	 */
	public StreamingOrderedMemberSupplier(
			LdesMetaData metadata,
			RequestExecutor requestExecutor,
			MemberIdRepository memberIdRepository,
			TreeNodeRecordRepository treeNodeRecordRepository,
			boolean keepState,
			OrderingConfiguration ordering) {
		this.metadata = metadata;
		this.treeNodeFetcher = new TreeNodeFetcher(
				new ResponseReusingRequestExecutor(requestExecutor),
				new TimestampFromCurrentTimeExtractor());
		this.memberIdRepository = memberIdRepository;
		this.treeNodeRecordRepository = treeNodeRecordRepository;
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
		if (treeNodeRecordRepository == null) {
			return;
		}
		// A previous run persisted every node it discovered. Reopening the ones
		// it did not finish restores the frontier without having to refetch the
		// nodes that led to them. Their bounds are not persisted, so they are
		// reopened as unbounded, which only makes emission more conservative.
		treeNodeRecordRepository.findAll().stream()
				.filter(record -> record.getTreeNodeStatus()
						!= TreeNodeStatus.IMMUTABLE_WITHOUT_UNPROCESSED_MEMBERS)
				.forEach(record -> frontier.add(
						new FrontierNode(record.getTreeNodeUrl(), FrontierBound.unboundedBound())));
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

		final TreeNodeRecord record = record(node.url());
		if (record.getTreeNodeStatus() == TreeNodeStatus.IMMUTABLE_WITHOUT_UNPROCESSED_MEMBERS) {
			// Fully read already and it cannot change, so there is nothing to
			// request. Its relation targets were reopened by init.
			return;
		}

		final TreeNodeResponse response = treeNodeFetcher
				.fetchTreeNode(metadata.createRequest(node.url(), record.getEtag()));
		bufferMembers(response);
		final String effectiveUrl = response.getEffectiveUrl().orElse(node.url());
		extractRelationBounds(response.getDataset(), effectiveUrl).forEach((url, bound) -> {
			if (!visitedNodes.contains(url)) {
				frontier.add(new FrontierNode(url, bound));
			}
			persistDiscoveredNode(url);
		});
		persistProcessedNode(record, response);
	}

	private void bufferMembers(TreeNodeResponse response) {
		for (TreeMember member : response.getMembers()) {
			if (memberIdRepository.addMemberIdIfNotExists(member.getMemberId())) {
				final SuppliedMember suppliedMember =
						new SuppliedMember(member.getMemberId(), member.getDataset());
				members.add(ordering.order(suppliedMember));
			}
		}
	}

	private TreeNodeRecord record(String url) {
		if (treeNodeRecordRepository == null) {
			return new TreeNodeRecord(url);
		}
		return treeNodeRecordRepository.findById(url).orElseGet(() -> new TreeNodeRecord(url));
	}

	private void persistDiscoveredNode(String url) {
		if (treeNodeRecordRepository != null && !treeNodeRecordRepository.existsById(url)) {
			treeNodeRecordRepository.saveTreeNodeRecord(new TreeNodeRecord(url));
		}
	}

	private void persistProcessedNode(TreeNodeRecord record, TreeNodeResponse response) {
		if (treeNodeRecordRepository == null) {
			return;
		}
		record.updateStatus(response.getMutabilityStatus());
		response.getEtag().ifPresent(record::updateEtag);
		if (!response.getMutabilityStatus().isMutable()) {
			// Ordered traversal takes every member of a node in one pass, so an
			// immutable node has nothing left to offer once it has been read.
			record.markImmutableWithoutUnprocessedMembers();
		}
		treeNodeRecordRepository.saveTreeNodeRecord(record);
		treeNodeRecordRepository.resetContext();
	}

	/**
	 * Lets the tree node fetcher consume a response that discovery already
	 * fetched, so starting a traversal does not repeat the root request.
	 */
	private static final class ResponseReusingRequestExecutor implements RequestExecutor {
		private final RequestExecutor responseReuseOwner;
		private final RequestExecutor requestExecutor;

		private ResponseReusingRequestExecutor(RequestExecutor requestExecutor) {
			this.responseReuseOwner = requestExecutor;
			this.requestExecutor = RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor);
		}

		@Override
		public Response execute(Request request) {
			return SingleUseResponseRegistry.consume(responseReuseOwner, request)
					.orElseGet(() -> requestExecutor.execute(request));
		}
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
			// One relation may name several targets; each of them inherits the
			// bounds of that relation.
			relation.listProperties(TREE_NODE)
					.toList()
					.stream()
					.map(Statement::getObject)
					.filter(RDFNode::isURIResource)
					.map(target -> target.asResource().getURI())
					.distinct()
					.forEach(target -> relationsByTarget
							.computeIfAbsent(target, ignored -> new ArrayList<>())
							.add(relation));
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
