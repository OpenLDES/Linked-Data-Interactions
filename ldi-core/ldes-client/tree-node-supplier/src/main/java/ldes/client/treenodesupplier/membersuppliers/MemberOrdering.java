package ldes.client.treenodesupplier.membersuppliers;

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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates LDES ordering metadata and creates comparable member order keys.
 */
final class MemberOrdering {
	private static final String SHACL = "http://www.w3.org/ns/shacl#";
	private static final Property SH_ALTERNATIVE_PATH = ResourceFactory.createProperty(SHACL, "alternativePath");
	private static final Property SH_INVERSE_PATH = ResourceFactory.createProperty(SHACL, "inversePath");
	private static final Property SH_ZERO_OR_ONE_PATH = ResourceFactory.createProperty(SHACL, "zeroOrOnePath");
	private static final Property SH_ZERO_OR_MORE_PATH = ResourceFactory.createProperty(SHACL, "zeroOrMorePath");
	private static final Property SH_ONE_OR_MORE_PATH = ResourceFactory.createProperty(SHACL, "oneOrMorePath");
	private static final RDFNode DEFAULT_TRANSACTION_FINALIZED_OBJECT = ResourceFactory.createTypedLiteral(true);

	private final List<RDFNode> orderPaths = new ArrayList<>();
	private final RDFNode transactionPath;
	private final RDFNode transactionFinalizedPath;
	private final RDFNode transactionFinalizedObject;

	MemberOrdering(
			Optional<RDFNode> timestampPath,
			Optional<RDFNode> sequencePath,
			Optional<RDFNode> transactionPath,
			Optional<RDFNode> transactionFinalizedPath,
			Optional<RDFNode> transactionFinalizedObject) {
		timestampPath.ifPresent(orderPaths::add);
		sequencePath.ifPresent(orderPaths::add);
		if (orderPaths.isEmpty()) {
			throw new IllegalArgumentException("Ordered traversal requires ldes:timestampPath and/or ldes:sequencePath");
		}
		this.transactionPath = transactionPath.orElse(null);
		this.transactionFinalizedPath = transactionFinalizedPath.orElse(null);
		this.transactionFinalizedObject = transactionFinalizedObject.orElse(DEFAULT_TRANSACTION_FINALIZED_OBJECT);
	}

	OrderedMember order(SuppliedMember member) {
		return new OrderedMember(
				member,
				orderValues(member),
				transactionId(member).orElse(null),
				isTransactionFinalizer(member));
	}

	boolean matchesPrimaryPath(RDFNode path) {
		return rdfPathEquals(orderPaths.getFirst(), path);
	}

	static RDFNode pathFromUris(List<String> uris) {
		if (uris.size() == 1) {
			return ResourceFactory.createProperty(uris.getFirst());
		}
		final Model model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel();
		return model.createList(uris.stream()
				.map(model::createProperty)
				.toArray(RDFNode[]::new));
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
		return values.getFirst();
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
		final RDFNode inversePath = object(pathResource, SH_INVERSE_PATH).orElse(null);
		if (inversePath != null) {
			return evaluateInversePath(model, start, inversePath);
		}

		final RDFNode alternativePath = object(pathResource, SH_ALTERNATIVE_PATH).orElse(null);
		if (alternativePath != null) {
			return rdfList(alternativePath).stream()
					.flatMap(option -> evaluatePath(model, start, option).stream())
					.toList();
		}

		final RDFNode zeroOrOnePath = object(pathResource, SH_ZERO_OR_ONE_PATH).orElse(null);
		if (zeroOrOnePath != null) {
			final List<RDFNode> values = new ArrayList<>();
			values.add(start);
			values.addAll(evaluatePath(model, start, zeroOrOnePath));
			return distinct(values);
		}

		final RDFNode zeroOrMorePath = object(pathResource, SH_ZERO_OR_MORE_PATH).orElse(null);
		if (zeroOrMorePath != null) {
			final List<RDFNode> values = new ArrayList<>();
			values.add(start);
			values.addAll(evaluateOneOrMorePath(model, start, zeroOrMorePath));
			return distinct(values);
		}

		final RDFNode oneOrMorePath = object(pathResource, SH_ONE_OR_MORE_PATH).orElse(null);
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

	private static Optional<RDFNode> object(Resource subject, Property property) {
		return Optional.ofNullable(subject.getProperty(property)).map(statement -> statement.getObject());
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
			final RDFNode first = object(listNode, RDF.first).orElse(null);
			if (first == null) {
				return List.of();
			}
			values.add(first);
			current = object(listNode, RDF.rest).orElse(RDF.nil);
		}
		return values;
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

	record OrderedMember(
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
			final int transactionComparison = transactionString(transactionId)
					.compareTo(transactionString(other.transactionId));
			return transactionComparison != 0
					? transactionComparison
					: member.getId().compareTo(other.member.getId());
		}

		private static String transactionString(RDFNode transactionId) {
			return Optional.ofNullable(transactionId).map(RDFNode::toString).orElse("");
		}
	}

	record OrderValues(List<SequenceValue> values) implements Comparable<OrderValues> {
		static OrderValues of(RDFNode value) {
			return new OrderValues(List.of(SequenceValue.of(value)));
		}

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

	private record SequenceValue(BigDecimal number, OffsetDateTime dateTime, String lexical)
			implements Comparable<SequenceValue> {
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
				} catch (DateTimeParseException ignored) {
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
			return dateTime != null ? dateTime.toString() : lexical;
		}
	}
}
