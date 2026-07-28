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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class ViewSpecification implements StartingNodeSpecification {
	public static final String LDES = "https://w3id.org/ldes#";
	public static final Property LDES_EVENT_STREAM = createProperty(LDES, "EventStream");
	public static final String RDF_SYNTAX = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public static final Property RDF_SYNTAX_TYPE = createProperty(RDF_SYNTAX, "type");
	public static final String TREE = "https://w3id.org/tree#";
	public static final Property TREE_SHAPE = createProperty(TREE, "shape");
	public static final Property TREE_VIEW = createProperty(TREE, "view");
	public static final Property TREE_VIEW_DESCRIPTION = createProperty(TREE, "viewDescription");
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
	private final String requestUrl;

	public ViewSpecification(Model model) {
		this(DatasetFactory.create(model));
	}

	public ViewSpecification(Dataset dataset) {
		this(dataset, null);
	}

	public ViewSpecification(Dataset dataset, String requestUrl) {
		this.dataset = dataset;
		this.model = dataset.getDefaultModel();
		this.requestUrl = requestUrl;
	}

	@Override
	public EventStreamProperties extractEventStreamProperties() {
		final Resource subject = extractEventStream().orElseThrow();
		final String eventStreamUri = requireUri(subject, "event stream");
		final String rootNode = extractRootNode(subject).orElse(eventStreamUri);
		final List<String> viewDescriptions = resources(rootNode, TREE_VIEW_DESCRIPTION);
		return new EventStreamProperties(
				eventStreamUri,
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

	public static boolean isViewSpecificationCandidate(Model model) {
		return model.contains(null, RDF_SYNTAX_TYPE, LDES_EVENT_STREAM) || model.contains(null, TREE_VIEW);
	}

	private static Optional<Resource> extractEventStream(Model model) {
		final List<Resource> typedEventStreams = typedEventStreams(model);
		return typedEventStreams.isEmpty()
				? selectSingle(model.listSubjectsWithProperty(TREE_VIEW).toList(), "subject with tree:view")
				: selectSingle(typedEventStreams, "ldes:EventStream subject");
	}

	private Optional<Resource> extractEventStream() {
		final List<Resource> candidates = eventStreamCandidates(model);
		final List<Resource> matchingCandidates = candidates.stream()
				.filter(this::containsMatchingView)
				.toList();
		return selectSingle(
				matchingCandidates.isEmpty() ? candidates : matchingCandidates,
				"discoverable event stream");
	}

	private static List<Resource> eventStreamCandidates(Model model) {
		final List<Resource> typedEventStreams = typedEventStreams(model);
		return typedEventStreams.isEmpty()
				? model.listSubjectsWithProperty(TREE_VIEW).toList()
				: typedEventStreams;
	}

	private static List<Resource> typedEventStreams(Model model) {
		return model.listSubjectsWithProperty(RDF_SYNTAX_TYPE, LDES_EVENT_STREAM).toList();
	}

	private static Optional<Resource> selectSingle(List<Resource> candidates, String description) {
		if (candidates.size() > 1) {
			throw new IllegalStateException(
					"Expected exactly one " + description + ", found " + candidates.size());
		}
		return candidates.stream().findFirst();
	}

	private boolean containsMatchingView(Resource candidate) {
		return resources(candidate, TREE_VIEW).stream().anyMatch(this::matchesRequestUrl);
	}

	private Optional<String> extractRootNode(Resource eventStream) {
		final List<String> viewTargets = resources(eventStream, TREE_VIEW);
		if (viewTargets.size() < 2) {
			return viewTargets.stream().findFirst();
		}

		final List<String> matchingViews = viewTargets.stream()
				.filter(this::matchesRequestUrl)
				.toList();
		if (matchingViews.size() != 1) {
			throw new IllegalStateException(
					"Expected exactly one tree:view target matching the entrypoint, found " + matchingViews.size());
		}
		return Optional.of(matchingViews.getFirst());
	}

	private boolean matchesRequestUrl(String viewUrl) {
		if (requestUrl == null) {
			return false;
		}

		try {
			final URI request = URI.create(requestUrl).normalize();
			final URI view = URI.create(viewUrl).normalize();
			if (!Objects.equals(request.getScheme(), view.getScheme())
					|| !Objects.equals(request.getAuthority(), view.getAuthority())) {
				return false;
			}
			final String requestPath = Optional.ofNullable(request.getPath()).orElse("");
			final String viewPath = Optional.ofNullable(view.getPath()).orElse("");
			return Objects.equals(requestPath, viewPath)
					|| requestPath.startsWith(viewPath.endsWith("/") ? viewPath : viewPath + "/");
		} catch (IllegalArgumentException ignored) {
			return requestUrl.equals(viewUrl);
		}
	}

	private static String requireUri(Resource resource, String description) {
		if (!resource.isURIResource()) {
			throw new IllegalStateException("Expected " + description + " to be identified by an IRI");
		}
		return resource.getURI();
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
