package ldes.client.eventstreamproperties.valueobjects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class ViewSpecification implements StartingNodeSpecification {
	public static final String LDES = "https://w3id.org/ldes#";
	public static final Property LDES_EVENT_STREAM = createProperty(LDES, "EventStream");
	public static final String RDF_SYNTAX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public static final Property RDF_SYNTAX_TYPE = createProperty(RDF_SYNTAX, "type");
	public static final Property TREE_SHAPE = createProperty("https://w3id.org/tree#", "shape");
	public static final Property TREE_VIEW = createProperty("https://w3id.org/tree#", "view");
	public static final Property TREE_VIEW_DESCRIPTION = createProperty("https://w3id.org/tree#", "viewDescription");
	public static final Property LDES_VERSION_OF_PATH = createProperty(LDES, "versionOfPath");
	public static final Property LDES_TIMESTAMP_PATH = createProperty(LDES, "timestampPath");
	public static final Property LDES_SEQUENCE_PATH = createProperty(LDES, "sequencePath");
	public static final Property LDES_TRANSACTION_FINALIZED_PATH = createProperty(LDES, "transactionFinalizedPath");
	public static final Property LDES_VERSION_TIMESTAMP_PATH = createProperty(LDES, "versionTimestampPath");
	public static final Property LDES_VERSION_SEQUENCE_PATH = createProperty(LDES, "versionSequencePath");
	public static final Property LDES_POLLING_INTERVAL = createProperty(LDES, "pollingInterval");
	public static final Property LDES_RETENTION_POLICY = createProperty(LDES, "retentionPolicy");

	private final Model model;
	private final Dataset dataset;

	public ViewSpecification(Model model) {
		this(DatasetFactory.create(model));
	}

	public ViewSpecification(Dataset dataset) {
		this.dataset = dataset;
		this.model = dataset.getDefaultModel();
	}

	@Override
	public EventStreamProperties extractEventStreamProperties() {
		final Resource subject = extractEventStream(model).orElseThrow();
		final String rootNode = Optional.ofNullable(subject.getPropertyResourceValue(TREE_VIEW))
				.filter(RDFNode::isURIResource)
				.map(RDFNode::asResource)
				.map(Resource::getURI)
				.orElse(null);
		final List<String> viewDescriptions = resources(rootNode, TREE_VIEW_DESCRIPTION);
		return new EventStreamProperties(
				subject.getURI(),
				rootNode,
				resourceUri(subject, LDES_VERSION_OF_PATH).orElse(null),
				resourceUri(subject, LDES_TIMESTAMP_PATH).orElse(null),
				propertyPath(subject, LDES_SEQUENCE_PATH),
				resourceUri(subject, LDES_TRANSACTION_FINALIZED_PATH).orElse(null),
				resourceUri(subject, LDES_VERSION_TIMESTAMP_PATH).orElse(null),
				resourceUri(subject, LDES_VERSION_SEQUENCE_PATH).orElse(null),
				integerValue(subject, LDES_POLLING_INTERVAL).orElse(null),
				resources(subject, TREE_SHAPE),
				viewDescriptions,
				retentionPolicies(rootNode, viewDescriptions),
				dataset
		);
	}

	public static boolean isViewSpecification(Model model) {
		return extractEventStream(model).isPresent();
	}

	private static Optional<Resource> extractEventStream(Model model) {
		final Optional<Resource> typedEventStream = model.listSubjectsWithProperty(RDF_SYNTAX_TYPE, LDES_EVENT_STREAM)
				.nextOptional();
		if (typedEventStream.isPresent()) {
			return typedEventStream;
		}
		return model.listSubjectsWithProperty(TREE_VIEW).nextOptional();
	}

	private static Optional<String> resourceUri(Resource subject, Property property) {
		return Optional.ofNullable(subject.getPropertyResourceValue(property))
				.filter(RDFNode::isURIResource)
				.map(RDFNode::asResource)
				.map(Resource::getURI);
	}

	private List<String> propertyPath(Resource subject, Property property) {
		return Optional.ofNullable(subject.getProperty(property))
				.map(Statement::getObject)
				.map(this::propertyPath)
				.orElse(List.of());
	}

	private List<String> propertyPath(RDFNode pathNode) {
		if (pathNode.isURIResource()) {
			return List.of(pathNode.asResource().getURI());
		}
		if (!pathNode.isResource()) {
			return List.of();
		}

		final List<String> path = new ArrayList<>();
		RDFNode current = pathNode;
		while (current.isResource() && !current.asResource().equals(RDF.nil)) {
			final Resource listNode = current.asResource();
			final RDFNode first = Optional.ofNullable(listNode.getProperty(RDF.first))
					.map(Statement::getObject)
					.orElse(null);
			if (first == null || !first.isURIResource()) {
				return List.of();
			}
			path.add(first.asResource().getURI());
			current = Optional.ofNullable(listNode.getProperty(RDF.rest))
					.map(Statement::getObject)
					.orElse(RDF.nil);
		}
		return path;
	}

	private static Optional<Integer> integerValue(Resource subject, Property property) {
		return Optional.ofNullable(subject.getProperty(property))
				.map(Statement::getObject)
				.filter(RDFNode::isLiteral)
				.map(RDFNode::asLiteral)
				.map(Literal::getInt);
	}

	private List<String> resources(Resource subject, Property property) {
		return model.listObjectsOfProperty(subject, property)
				.toList()
				.stream()
				.filter(RDFNode::isURIResource)
				.map(RDFNode::asResource)
				.map(Resource::getURI)
				.distinct()
				.sorted()
				.toList();
	}

	private List<String> resources(String subject, Property property) {
		if (subject == null) {
			return List.of();
		}
		return resources(model.createResource(subject), property);
	}

	private List<String> retentionPolicies(String rootNode, List<String> viewDescriptions) {
		return Stream.concat(
						resources(rootNode, LDES_RETENTION_POLICY).stream(),
						viewDescriptions.stream()
								.flatMap(viewDescription -> resources(viewDescription, LDES_RETENTION_POLICY).stream()))
				.distinct()
				.sorted()
				.toList();
	}
}
