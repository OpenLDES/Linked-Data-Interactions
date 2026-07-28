package ldes.client.eventstreamproperties.valueobjects;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFParser;
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

	private static ViewSpecification specification(Model model, String entrypoint) {
		return new ViewSpecification(DatasetFactory.create(model), entrypoint);
	}
}
