package org.openldes.ldio.conversionstrategy.json;

import com.github.jsonldjava.core.DocumentLoader;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Model2JsonConverterTest {

	private static final String JSON_CONTEXT_URI = "https://essentialcomplexity.eu/gipod.jsonld";

	Model2JsonConverter model2JsonConverter = new Model2JsonConverter(JSON_CONTEXT_URI, offlineJsonLdOptions());

	// Injects gipod-context.jsonld (a local reconstruction covering the terms this fixture exercises)
	// instead of dereferencing JSON_CONTEXT_URI over the network, so this test doesn't depend on that host being up.
	private static JsonLdOptions offlineJsonLdOptions() {
		try {
			DocumentLoader documentLoader = new DocumentLoader();
			documentLoader.addInjectedDoc(JSON_CONTEXT_URI, readFile("gipod-context.jsonld"));
			JsonLdOptions options = new JsonLdOptions();
			options.setDocumentLoader(documentLoader);
			return options;
		} catch (JsonLdError | URISyntaxException | IOException e) {
			throw new IllegalStateException("Failed to load local JSON-LD context fixture", e);
		}
	}

	@Test
	void when_ModelIsConverted_then_JsonSerializedStringIsReturned() throws IOException, URISyntaxException {
		Model model = RDFParser.fromString(readFile("original.jsonld")).lang(Lang.JSONLD11).toModel();
		String expectedJson = readFile("expected.json");

		String actualJson = model2JsonConverter.modelToJSONLD(model);

		assertEquals(replaceAnonymousIdsAndLineSeparators(expectedJson),
				replaceAnonymousIdsAndLineSeparators(actualJson));

	}

	private static String replaceAnonymousIdsAndLineSeparators(String expectedJson) {
		return expectedJson
				.replaceAll("_:b\\d", "") // Anonymous Ids
				.replaceAll("\\n|\\r\\n", System.getProperty("line.separator")); // Line Separators
	}

	private static String readFile(String fileName)
			throws URISyntaxException, IOException {
		File file = new File(Objects.requireNonNull(Model2JsonConverterTest.class.getClassLoader().getResource(fileName)).toURI());
		return Files.lines(Paths.get(file.toURI())).collect(Collectors.joining("\n"));
	}

}