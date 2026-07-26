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
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.openldes.ldi.rdf.parser.RdfResponseParser;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
	private static final String SHACL = "http://www.w3.org/ns/shacl#";
	private static final Property TREE_RELATION = ResourceFactory.createProperty(TREE, "relation");
	private static final Property TREE_NODE = ResourceFactory.createProperty(TREE, "node");
	private static final Property TREE_PATH = ResourceFactory.createProperty(TREE, "path");
	private static final Property TREE_VALUE = ResourceFactory.createProperty(TREE, "value");
	private static final Property SH_ALTERNATIVE_PATH = ResourceFactory.createProperty(SHACL, "alternativePath");
	private static final Property SH_INVERSE_PATH = ResourceFactory.createProperty(SHACL, "inversePath");
	private static final Property SH_ZERO_OR_ONE_PATH = ResourceFactory.createProperty(SHACL, "zeroOrOnePath");
	private static final Property SH_ZERO_OR_MORE_PATH = ResourceFactory.createProperty(SHACL, "zeroOrMorePath");
	private static final Property SH_ONE_OR_MORE_PATH = ResourceFactory.createProperty(SHACL, "oneOrMorePath");
	private static final String TREE_GREATER_THAN = TREE + "GreaterThanRelation";
	private static final String TREE_GREATER_THAN_OR_EQUAL_TO = TREE + "GreaterThanOrEqualToRelation";
	private static final String TREE_LESS_THAN = TREE + "LessThanRelation";
	private static final String TREE_LESS_THAN_OR_EQUAL_TO = TREE + "LessThanOrEqualToRelation";
	private static final RDFNode DEFAULT_TRANSACTION_FINALIZED_OBJECT = ResourceFactory.createTypedLiteral(true);

	private final LdesMetaData metadata;
	private final RequestExecutor requestExecutor;
	private final MemberIdRepository memberIdRepository;
	private final boolean keepState;
	private final List<RDFNode> orderPaths;
	private final RDFNode transactionPath;
	private final RDFNode transactionFinalizedPath;
	private final RDFNode transactionFinalizedObject;
	private final PriorityQueue<FrontierNode> frontier = new PriorityQueue<>();
	private final PriorityQueue<OrderedMember> members = new PriorityQueue<>();
	private final Set<String> visitedNodes = new HashSet<>();

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
		this.metadata = metadata;
		this.requestExecutor = requestExecutor;
		this.memberIdRepository = memberIdRepository;
		this.keepState = keepState;
		this.orderPaths = new ArrayList<>();
		timestampPath.ifPresent(orderPaths::add);
		sequencePath.ifPresent(orderPaths::add);
		if (orderPaths.isEmpty()) {
			throw new IllegalArgumentException("Ordered traversal requires ldes:timestampPath and/or ldes:sequencePath");
		}
		this.transactionPath = transactionPath.orElse(null);
		this.transactionFinalizedPath = transactionFinalizedPath.orElse(null);
		this.transactionFinalizedObject = transactionFinalizedObject.orElse(DEFAULT_TRANSACTION_FINALIZED_OBJECT);
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
				members.add(new OrderedMember(
						suppliedMember,
						orderValues(suppliedMember),
						transactionId(suppliedMember).orElse(null),
						isTransactionFinalizer(suppliedMember)));
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
		final Response response = requestExecutor.execute(request.createRequest());
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
		if (path == null || value == null || !matchesPrimaryOrderingPath(path)) {
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
			return FrontierBound.lowerBound(new OrderValues(List.of(SequenceValue.of(value))), false);
		}
		if (types.contains(TREE_GREATER_THAN_OR_EQUAL_TO)) {
			return FrontierBound.lowerBound(new OrderValues(List.of(SequenceValue.of(value))), true);
		}
		if (types.contains(TREE_LESS_THAN) || types.contains(TREE_LESS_THAN_OR_EQUAL_TO)) {
			return FrontierBound.unboundedBound();
		}
		return FrontierBound.unboundedBound();
	}

	private boolean matchesPrimaryOrderingPath(RDFNode path) {
		return !orderPaths.isEmpty() && rdfPathEquals(orderPaths.getFirst(), path);
	}

	private static boolean rdfPathEquals(RDFNode left, RDFNode right) {
		if (left == null || right == null) {
			return left == right;
		}
		if (left.isURIResource() || right.isURIResource()) {
			return left.equals(right);
		}
		return left.toString().equals(right.toString());
	}

	private OrderValues orderValues(SuppliedMember member) {
		final Model model = member.getModel();
		final Resource subject = model.createResource(member.getId());
		final List<SequenceValue> values = orderPaths.stream()
				.map(path -> evaluateRequiredPath(model, subject, path, member.getId()))
				.map(SequenceValue::of)
				.toList();
		return new OrderValues(values);
	}

	private Optional<RDFNode> transactionId(SuppliedMember member) {
		if (transactionPath == null) {
			return Optional.empty();
		}
		final Model model = member.getModel();
		return evaluatePath(model, model.createResource(member.getId()), transactionPath).stream().findFirst();
	}

	private boolean isTransactionFinalizer(SuppliedMember member) {
		if (transactionFinalizedPath == null) {
			return false;
		}
		final Model model = member.getModel();
		return evaluatePath(model, model.createResource(member.getId()), transactionFinalizedPath)
				.stream()
				.anyMatch(value -> rdfTermEquals(value, transactionFinalizedObject));
	}

	private static RDFNode evaluateRequiredPath(Model model, Resource subject, RDFNode path, String memberId) {
		final List<RDFNode> values = evaluatePath(model, subject, path);
		if (values.isEmpty()) {
			throw new IllegalArgumentException("Member " + memberId + " has no value for configured ordered traversal path");
		}
		return values.get(0);
	}

	private static List<RDFNode> evaluatePath(Model model, RDFNode start, RDFNode path) {
		if (path.isURIResource()) {
			if (!start.isResource()) {
				return List.of();
			}
			return model.listObjectsOfProperty(start.asResource(), ResourceFactory.createProperty(path.asResource().getURI()))
					.toList();
		}
		if (!path.isResource()) {
			return List.of();
		}

		final Resource pathResource = path.asResource();
		final RDFNode inversePath = Optional.ofNullable(pathResource.getProperty(SH_INVERSE_PATH))
				.map(statement -> statement.getObject())
				.orElse(null);
		if (inversePath != null) {
			return evaluateInversePath(model, start, inversePath);
		}

		final RDFNode alternativePath = Optional.ofNullable(pathResource.getProperty(SH_ALTERNATIVE_PATH))
				.map(statement -> statement.getObject())
				.orElse(null);
		if (alternativePath != null) {
			return rdfList(alternativePath).stream()
					.flatMap(option -> evaluatePath(model, start, option).stream())
					.toList();
		}

		final RDFNode zeroOrOnePath = Optional.ofNullable(pathResource.getProperty(SH_ZERO_OR_ONE_PATH))
				.map(statement -> statement.getObject())
				.orElse(null);
		if (zeroOrOnePath != null) {
			final List<RDFNode> values = new ArrayList<>();
			values.add(start);
			values.addAll(evaluatePath(model, start, zeroOrOnePath));
			return distinct(values);
		}

		final RDFNode zeroOrMorePath = Optional.ofNullable(pathResource.getProperty(SH_ZERO_OR_MORE_PATH))
				.map(statement -> statement.getObject())
				.orElse(null);
		if (zeroOrMorePath != null) {
			final List<RDFNode> values = new ArrayList<>();
			values.add(start);
			values.addAll(evaluateOneOrMorePath(model, start, zeroOrMorePath));
			return distinct(values);
		}

		final RDFNode oneOrMorePath = Optional.ofNullable(pathResource.getProperty(SH_ONE_OR_MORE_PATH))
				.map(statement -> statement.getObject())
				.orElse(null);
		if (oneOrMorePath != null) {
			return evaluateOneOrMorePath(model, start, oneOrMorePath);
		}

		final List<RDFNode> sequence = rdfList(path);
		if (!sequence.isEmpty()) {
			List<RDFNode> current = List.of(start);
			for (RDFNode step : sequence) {
				current = current.stream()
						.flatMap(node -> evaluatePath(model, node, step).stream())
						.toList();
			}
			return current;
		}

		return List.of();
	}

	private static List<RDFNode> evaluateOneOrMorePath(Model model, RDFNode start, RDFNode path) {
		final List<RDFNode> values = new ArrayList<>();
		final Set<RDFNode> visited = new HashSet<>();
		List<RDFNode> frontier = evaluatePath(model, start, path);
		while (!frontier.isEmpty()) {
			final List<RDFNode> nextFrontier = new ArrayList<>();
			for (RDFNode value : frontier) {
				if (visited.add(value)) {
					values.add(value);
					nextFrontier.addAll(evaluatePath(model, value, path));
				}
			}
			frontier = nextFrontier;
		}
		return values;
	}

	private static List<RDFNode> distinct(List<RDFNode> values) {
		final List<RDFNode> distinctValues = new ArrayList<>();
		final Set<RDFNode> seen = new HashSet<>();
		for (RDFNode value : values) {
			if (seen.add(value)) {
				distinctValues.add(value);
			}
		}
		return distinctValues;
	}

	private static List<RDFNode> evaluateInversePath(Model model, RDFNode start, RDFNode inversePath) {
		if (!inversePath.isURIResource()) {
			return List.of();
		}
		final Property property = ResourceFactory.createProperty(inversePath.asResource().getURI());
		return model.listSubjectsWithProperty(property, start)
				.toList()
				.stream()
				.map(RDFNode.class::cast)
				.toList();
	}

	private static List<RDFNode> rdfList(RDFNode listRoot) {
		if (!listRoot.isResource() || listRoot.asResource().equals(RDF.nil)) {
			return List.of();
		}

		final List<RDFNode> values = new ArrayList<>();
		RDFNode current = listRoot;
		while (current.isResource() && !current.asResource().equals(RDF.nil)) {
			final Resource listNode = current.asResource();
			final RDFNode first = Optional.ofNullable(listNode.getProperty(RDF.first))
					.map(statement -> statement.getObject())
					.orElse(null);
			if (first == null) {
				return List.of();
			}
			values.add(first);
			current = Optional.ofNullable(listNode.getProperty(RDF.rest))
					.map(statement -> statement.getObject())
					.orElse(RDF.nil);
		}
		return values;
	}

	private static boolean rdfTermEquals(RDFNode left, RDFNode right) {
		if (left == null || right == null) {
			return left == right;
		}
		if (left.isLiteral() && right.isLiteral()) {
			final Literal leftLiteral = left.asLiteral();
			final Literal rightLiteral = right.asLiteral();
			return leftLiteral.sameValueAs(rightLiteral) ||
					leftLiteral.getLexicalForm().equals(rightLiteral.getLexicalForm());
		}
		return left.equals(right);
	}

	private record FetchResult(String url, Dataset dataset, List<TreeMember> members) {
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

	private record FrontierBound(OrderValues value, boolean inclusive, boolean unbounded) implements Comparable<FrontierBound> {
		private static FrontierBound unboundedBound() {
			return new FrontierBound(null, true, true);
		}

		private static FrontierBound lowerBound(OrderValues value, boolean inclusive) {
			return new FrontierBound(value, inclusive, false);
		}

		private boolean isAfter(OrderValues memberValue) {
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

	private record OrderedMember(
			SuppliedMember member,
			OrderValues orderValues,
			RDFNode transactionId,
			boolean transactionFinalizer) implements Comparable<OrderedMember> {
		@Override
		public int compareTo(OrderedMember other) {
			final int orderComparison = orderValues.compareTo(other.orderValues);
			if (orderComparison != 0) {
				return orderComparison;
			}
			final int finalizerComparison = Boolean.compare(transactionFinalizer, other.transactionFinalizer);
			if (finalizerComparison != 0) {
				return finalizerComparison;
			}
			final int transactionComparison = Optional.ofNullable(transactionId).map(RDFNode::toString).orElse("")
					.compareTo(Optional.ofNullable(other.transactionId).map(RDFNode::toString).orElse(""));
			if (transactionComparison != 0) {
				return transactionComparison;
			}
			return member.getId().compareTo(other.member.getId());
		}
	}

	private record OrderValues(List<SequenceValue> values) implements Comparable<OrderValues> {
		@Override
		public int compareTo(OrderValues other) {
			for (int index = 0; index < Math.min(values.size(), other.values.size()); index++) {
				final int comparison = values.get(index).compareTo(other.values.get(index));
				if (comparison != 0) {
					return comparison;
				}
			}
			return Integer.compare(values.size(), other.values.size());
		}
	}

	private record SequenceValue(BigDecimal number, OffsetDateTime dateTime, String lexical) implements Comparable<SequenceValue> {
		private static SequenceValue of(RDFNode node) {
			if (!node.isLiteral()) {
				return new SequenceValue(null, null, node.toString());
			}

			final Literal literal = node.asLiteral();
			final Object value = literal.getValue();
			if (value instanceof Number number) {
				return new SequenceValue(new BigDecimal(number.toString()), null, null);
			}
			if (XSDDatatype.XSDdateTime.getURI().equals(literal.getDatatypeURI())) {
				try {
					return new SequenceValue(null, OffsetDateTime.parse(literal.getLexicalForm()), null);
				} catch (RuntimeException ignored) {
					// Fall through to lexical comparison.
				}
			}

			return new SequenceValue(null, null, literal.getLexicalForm());
		}

		@Override
		public int compareTo(SequenceValue other) {
			if (number != null && other.number != null) {
				return number.compareTo(other.number);
			}
			if (dateTime != null && other.dateTime != null) {
				return dateTime.compareTo(other.dateTime);
			}
			return asString().compareTo(other.asString());
		}

		private String asString() {
			if (number != null) {
				return number.toPlainString();
			}
			if (dateTime != null) {
				return dateTime.toString();
			}
			return lexical;
		}
	}
}
