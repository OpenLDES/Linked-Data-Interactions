package ldes.client.treenodefetcher.domain.valueobjects;

import org.openldes.ldi.timestampextractor.TimestampExtractor;
import ldes.client.treenodefetcher.domain.entities.TreeMember;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.rdf.model.*;
import org.openldes.ldi.rdf.DatasetHolder;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static ldes.client.treenodefetcher.domain.valueobjects.Constants.*;

/**
 * Wrapper around the RDF response to more easily extract the required information from that response
 */
public class ModelResponse {
	private final TimestampExtractor timestampExtractor;
	private final Dataset dataset;
	private final Model model;
	private final String treeNodeIri;

	public ModelResponse(Model model, TimestampExtractor timestampExtractor) {
		this(DatasetFactory.create(model), timestampExtractor, null);
	}

	public ModelResponse(Dataset dataset, TimestampExtractor timestampExtractor) {
		this(dataset, timestampExtractor, null);
	}

	public ModelResponse(Dataset dataset, TimestampExtractor timestampExtractor, String treeNodeIri) {
		this.dataset = dataset;
		this.model = dataset.getDefaultModel();
		this.timestampExtractor = timestampExtractor;
		this.treeNodeIri = treeNodeIri;
	}

	/**
	 * @return the IRIs this tree node relates to, in the order they are found.
	 * Relations declared by another resource in the same response are not
	 * included, and a relation naming several targets contributes all of them.
	 */
	public List<String> getRelations() {
		final List<String> relations = relationTargets(extractRelations()).toList();
		if (!relations.isEmpty()) {
			return relations;
		}
		return selectedTreeViews().stream()
				.flatMap(treeView -> relationTargets(extractRelations(treeView)))
				.distinct()
				.toList();
	}

	private Stream<String> relationTargets(Stream<Statement> relationStatements) {
		return relationStatements
				.map(Statement::getObject)
				.filter(RDFNode::isResource)
				.map(RDFNode::asResource)
				.flatMap(relation -> statements(relation.listProperties(W3ID_TREE_NODE)))
				.map(Statement::getObject)
				.filter(RDFNode::isURIResource)
				.map(node -> node.asResource().getURI())
				.distinct();
	}

	public List<TreeMember> getMembers() {
		return extractMembers()
				.map(Statement::getResource)
				.distinct()
				.map(this::processMember)
				.toList();
	}

	public boolean isImmutable() {
		return statements(model.listStatements(ANY_RESOURCE, W3ID_LDES_IMMUTABLE, (RDFNode) null))
				.map(Statement::getObject)
				.filter(RDFNode::isLiteral)
				.map(RDFNode::asLiteral)
				.anyMatch(Literal::getBoolean);
	}

	private Stream<Statement> extractMembers() {
		final List<Resource> selectedEventStreams = selectedEventStreams();
		if (!selectedEventStreams.isEmpty()) {
			return selectedEventStreams.stream()
					.flatMap(eventStream -> statements(model.listStatements(
							eventStream, W3ID_TREE_MEMBER, ANY_RESOURCE)));
		}
		return statements(model.listStatements(ANY_RESOURCE, W3ID_TREE_MEMBER, ANY_RESOURCE));
	}

	private List<Resource> selectedEventStreams() {
		if (treeNodeIri == null) {
			return List.of();
		}
		return statements(model.listStatements(
				ANY_RESOURCE,
				W3ID_TREE_VIEW,
				model.createResource(treeNodeIri)))
				.map(Statement::getSubject)
				.distinct()
				.toList();
	}

	private TreeMember processMember(Resource member) {
		final Dataset memberDataset = extractMemberDataset(member);
		final Model memberModel = DatasetHolder.flatten(memberDataset);
		final LocalDateTime createdAt = timestampExtractor.extractTimestampWithSubject(
				memberModel.createResource(member.toString()),
				memberModel);
		return new TreeMember(member.toString(), createdAt, memberDataset, memberModel);
	}

	/**
	 * Extracts one member as a dataset.
	 * <p>
	 * Starting at the member, the default-graph subject star is copied and every
	 * blank node it reaches is expanded in turn, so connected blank-node
	 * structures are included and cycles terminate. A node that also labels a
	 * named graph contributes that graph under the same label, which covers both
	 * a member IRI naming its own graph and a blank node reached from the member
	 * naming one. Blank nodes found inside such a graph are expanded as well.
	 */
	private Dataset extractMemberDataset(Resource member) {
		final DatasetGraph source = dataset.asDatasetGraph();
		final DatasetGraph target = DatasetGraphFactory.create();
		final Deque<Node> pending = new ArrayDeque<>();
		final Set<Node> visited = new HashSet<>();
		pending.add(member.asNode());

		while (!pending.isEmpty()) {
			final Node node = pending.remove();
			if (!visited.add(node)) {
				continue;
			}
			copySubjectStar(source, target, node, pending);
			copyLabelledGraph(source, target, node, pending);
		}
		return DatasetFactory.wrap(target);
	}

	private void copySubjectStar(DatasetGraph source, DatasetGraph target, Node node, Deque<Node> pending) {
		final Iterator<Triple> triples = source.getDefaultGraph().find(node, Node.ANY, Node.ANY);
		while (triples.hasNext()) {
			final Triple triple = triples.next();
			target.getDefaultGraph().add(triple);
			enqueueBlankNode(triple.getObject(), pending);
		}
	}

	private void copyLabelledGraph(DatasetGraph source, DatasetGraph target, Node node, Deque<Node> pending) {
		if (!source.containsGraph(node)) {
			return;
		}
		final Iterator<Triple> triples = source.getGraph(node).find();
		while (triples.hasNext()) {
			final Triple triple = triples.next();
			target.add(new Quad(node, triple));
			enqueueBlankNode(triple.getObject(), pending);
		}
	}

	private void enqueueBlankNode(Node node, Deque<Node> pending) {
		if (node.isBlank()) {
			pending.add(node);
		}
	}

	private Stream<Statement> extractRelations() {
		if (treeNodeIri == null) {
			return statements(model.listStatements(ANY_RESOURCE, W3ID_TREE_RELATION, ANY_RESOURCE));
		}
		return extractRelations(model.createResource(treeNodeIri));
	}

	private Stream<Statement> extractRelations(Resource treeNode) {
		return statements(model.listStatements(treeNode, W3ID_TREE_RELATION, ANY_RESOURCE));
	}

	private List<Resource> selectedTreeViews() {
		return statements(model.listStatements(ANY_RESOURCE, W3ID_TREE_VIEW, ANY_RESOURCE))
				.map(Statement::getObject)
				.filter(RDFNode::isURIResource)
				.map(RDFNode::asResource)
				.distinct()
				.toList();
	}

	private Stream<Statement> statements(StmtIterator iterator) {
		return Stream.iterate(iterator, Iterator::hasNext, UnaryOperator.identity())
				.map(Iterator::next);
	}
}
