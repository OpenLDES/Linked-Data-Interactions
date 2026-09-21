package ldes.client.eventstreamproperties.valueobjects;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ViewSpecificationTest {
	private static final String EVENT_STREAM = "http://localhost:12121/observations";
	private static final String BY_PAGE = EVENT_STREAM + "/by-page";

	@ParameterizedTest
	@ValueSource(strings = {"models/view.ttl", "models/eventstream.ttl"})
	void testExtractEventStreamProperties(String fileUri) {
		final EventStreamProperties expectedProperties = new EventStreamProperties(
				EVENT_STREAM,
				"http://purl.org/dc/terms/isVersionOf",
				"http://www.w3.org/ns/prov#generatedAtTime",
				"");
		final Model model = RDFParser.source(fileUri).toModel();
		final String entrypoint = fileUri.endsWith("eventstream.ttl") ? BY_PAGE + "?pageNumber=1" : BY_PAGE;

		final EventStreamProperties properties = specification(model, entrypoint).extractEventStreamProperties();

		assertThat(properties.getUri()).isEqualTo(expectedProperties.getUri());
		assertThat(properties.getRootNode()).isEqualTo(BY_PAGE);
		assertThat(properties.getVersionOfPath()).isEqualTo(expectedProperties.getVersionOfPath());
		assertThat(properties.getTimestampPath()).isEqualTo(expectedProperties.getTimestampPath());
		assertThat(properties.getShaclShapeUri()).isEqualTo(expectedProperties.getShaclShapeUri());
	}

	@Test
	void extractsTransactionOrderingContext() {
		final Model model = RDFParser.fromString("""
				@prefix ex: <http://example.org/> .
				@prefix ldes: <https://w3id.org/ldes#> .
				@prefix tree: <https://w3id.org/tree#> .
				@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

				ex:stream a ldes:EventStream ;
					ldes:timestampPath ex:timestamp ;
					ldes:transactionPath ex:transaction ;
					ldes:transactionFinalizedPath ex:closed ;
					ldes:transactionFinalizedObject "done" ;
					tree:view ex:root .
				""").lang(Lang.TURTLE).toModel();

		final EventStreamProperties properties = specification(model, "http://example.org/root")
				.extractEventStreamProperties();

		assertThat(properties.getTransactionPath()).isEqualTo("http://example.org/transaction");
		assertThat(properties.getTransactionFinalizedPath()).isEqualTo("http://example.org/closed");
		assertThat(properties.getTransactionFinalizedObject().asLiteral().getString()).isEqualTo("done");
	}

	@Test
	void usesTheRequestUrlAsRootWhenNoViewIsAdvertised() {
		final String entrypoint = "http://localhost:10101/exampleData?generatedAtTime=2022-05-03T00:00:00.000Z";
		final Model model = RDFParser.fromString("""
				@prefix ldes: <https://w3id.org/ldes#> .
				@prefix prov: <http://www.w3.org/ns/prov#> .
				@prefix dcterms: <http://purl.org/dc/terms/> .

				<localhost:10101/exampleData> a ldes:EventStream ;
					ldes:timestampPath prov:generatedAtTime ;
					ldes:versionOfPath dcterms:isVersionOf .
				""").lang(Lang.TURTLE).toModel();

		final EventStreamProperties properties = specification(model, entrypoint)
				.extractEventStreamProperties();

		assertThat(properties.getUri()).isEqualTo("localhost:10101/exampleData");
		assertThat(properties.getRootNode()).isEqualTo(entrypoint);
	}

	private static ViewSpecification specification(Model model, String entrypoint) {
		return new ViewSpecification(DatasetFactory.create(model), entrypoint);
	}
}
