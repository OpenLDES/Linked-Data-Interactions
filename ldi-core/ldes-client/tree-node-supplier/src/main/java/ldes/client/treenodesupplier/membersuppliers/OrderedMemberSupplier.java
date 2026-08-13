package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Emits one finite synchronization run in ascending LDES order.
 * <p>
 * Ordering follows the LDES chronological tuple: {@code ldes:timestampPath}
 * first when present, then {@code ldes:sequencePath} when present. The supplier
 * supports common SHACL property-path expressions needed by LDES/TREE metadata:
 * direct predicate paths, RDF-list sequence paths, {@code sh:alternativePath},
 * and {@code sh:inversePath}.
 */
public class OrderedMemberSupplier implements MemberSupplier {
	private static final String SHACL = "http://www.w3.org/ns/shacl#";
	private static final Property SH_ALTERNATIVE_PATH = ResourceFactory.createProperty(SHACL, "alternativePath");
	private static final Property SH_INVERSE_PATH = ResourceFactory.createProperty(SHACL, "inversePath");
	private static final RDFNode DEFAULT_TRANSACTION_FINALIZED_OBJECT = ResourceFactory.createTypedLiteral(true);

	private final MemberSupplier delegate;
	private final List<RDFNode> orderPaths;
	private final RDFNode transactionPath;
	private final RDFNode transactionFinalizedPath;
	private final RDFNode transactionFinalizedObject;
	private Iterator<OrderedMember> orderedMembers;

	public OrderedMemberSupplier(MemberSupplier delegate, List<String> sequencePath, String transactionFinalizedPath) {
		this(
				delegate,
				Optional.empty(),
				sequencePath == null || sequencePath.isEmpty()
						? Optional.empty()
						: Optional.of(pathFromUris(sequencePath)),
				Optional.empty(),
				Optional.ofNullable(transactionFinalizedPath).map(ResourceFactory::createProperty),
				Optional.empty());
	}

	public OrderedMemberSupplier(
			MemberSupplier delegate,
			Optional<RDFNode> timestampPath,
			Optional<RDFNode> sequencePath,
			Optional<RDFNode> transactionPath,
			Optional<RDFNode> transactionFinalizedPath,
			Optional<RDFNode> transactionFinalizedObject) {
		this.delegate = delegate;
		this.orderPaths = new ArrayList<>();
		timestampPath.ifPresent(orderPaths::add);
		sequencePath.ifPresent(orderPaths::add);
		if (orderPaths.isEmpty()) {
			throw new IllegalArgumentException("Ordered traversal requires ldes:timestampPath and/or ldes:sequencePath");
		}
		this.transactionPath = transactionPath.orElse(null);
		this.transactionFinalizedPath = transactionFinalizedPath.orElse(null);
		this.transactionFinalizedObject = transactionFinalizedObject.orElse(DEFAULT_TRANSACTION_FINALIZED_OBJECT);
	}

	private static RDFNode pathFromUris(List<String> uris) {
		if (uris.size() == 1) {
			return ResourceFactory.createProperty(uris.get(0));
		}
		final Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
		return model.createList(uris.stream()
				.map(model::createProperty)
				.toArray(RDFNode[]::new));
	}

	@Override
	public SuppliedMember get() {
		if (orderedMembers == null) {
			orderedMembers = drainAndOrder();
		}
		if (!orderedMembers.hasNext()) {
			throw new EndOfLdesException("No ordered members left to emit.");
		}
		return orderedMembers.next().member();
	}

	@Override
	public void destroyState() {
		delegate.destroyState();
	}

	@Override
	public void init() {
		delegate.init();
	}

	private Iterator<OrderedMember> drainAndOrder() {
		final List<OrderedMember> members = new ArrayList<>();
		try {
			while (true) {
				final SuppliedMember member = delegate.get();
				members.add(new OrderedMember(
						member,
						orderValues(member),
						transactionId(member).orElse(null),
						isTransactionFinalizer(member)));
			}
		} catch (EndOfLdesException completed) {
			return orderedIterator(members);
		}
	}

	private Iterator<OrderedMember> orderedIterator(List<OrderedMember> members) {
		return members.stream()
				.sorted(Comparator
						.comparing(OrderedMember::orderValues, OrderValues::compareTo)
						.thenComparing(OrderedMember::transactionFinalizer)
						.thenComparing(orderedMember -> Optional.ofNullable(orderedMember.transactionId()).map(RDFNode::toString).orElse(""))
						.thenComparing(orderedMember -> orderedMember.member().getId()))
				.iterator();
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

	private record OrderedMember(
			SuppliedMember member,
			OrderValues orderValues,
			RDFNode transactionId,
			boolean transactionFinalizer) {
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
