package ldes.client.treenodefetcher.domain.valueobjects;

import org.openldes.ldi.timestampextractor.TimestampExtractor;
import ldes.client.treenodefetcher.domain.entities.TreeMember;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.*;

import java.time.LocalDateTime;
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

	public List<String> getRelations() {
		return extractRelations()
				.map(relationStatement -> relationStatement.getResource()
						.getProperty(W3ID_TREE_NODE).getResource().toString())
				.toList();
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
		final Model memberModel = flatten(memberDataset);
		final LocalDateTime createdAt = timestampExtractor.extractTimestampWithSubject(
				memberModel.createResource(member.toString()),
				memberModel);
		return new TreeMember(member.toString(), createdAt, memberDataset, memberModel);
	}

	private Dataset extractMemberDataset(Resource member) {
		final Dataset memberDataset = DatasetFactory.create();
		copySubjectStar(
				member.asNode(),
				model.getGraph(),
				memberDataset.getDefaultModel().getGraph(),
				new HashSet<>());

		if (member.isURIResource() && dataset.containsNamedModel(member.getURI())) {
			memberDataset.addNamedModel(
					member.getURI(),
					ModelFactory.createDefaultModel().add(dataset.getNamedModel(member.getURI())));
		}
		return memberDataset;
	}

	private void copySubjectStar(Node subject, org.apache.jena.graph.Graph source,
	                             org.apache.jena.graph.Graph target, Set<Node> visited) {
		if (!visited.add(subject)) {
			return;
		}
		final Iterator<Triple> triples = source.find(subject, Node.ANY, Node.ANY);
		while (triples.hasNext()) {
			final Triple triple = triples.next();
			target.add(triple);
			if (triple.getObject().isBlank()) {
				copySubjectStar(triple.getObject(), source, target, visited);
			}
		}
	}

	private static Model flatten(Dataset dataset) {
		final Model flattenedModel = ModelFactory.createDefaultModel()
				.add(dataset.getDefaultModel());
		dataset.listNames().forEachRemaining(name -> flattenedModel.add(dataset.getNamedModel(name)));
		return flattenedModel;
	}

	private Stream<Statement> extractRelations() {
		return statements(model.listStatements(ANY_RESOURCE, W3ID_TREE_RELATION, ANY_RESOURCE));
	}

	private Stream<Statement> statements(StmtIterator iterator) {
		return Stream.iterate(iterator, Iterator::hasNext, UnaryOperator.identity())
				.map(Iterator::next);
	}
}
