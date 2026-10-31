package org.openldes.ldio;

import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.config.LdioHttpSparqlOutAutoConfig;
import org.openldes.ldio.config.LdioHttpSparqlOutProperties;
import org.openldes.ldio.exceptions.HttpSparqlOutException;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = LdioHttpSparqlOutContinueOnFailTest.PORT)
class LdioHttpSparqlOutContinueOnFailTest {
	static final int PORT = 12122;
	private static final String PIPELINE_NAME = "test-pipeline";
	private static final String PATH = "/sparql";
	private static final String ENDPOINT = "http://localhost:%d%s".formatted(PORT, PATH);

	private final LdioHttpSparqlOutAutoConfig.LdioHttpSparqlOutConfigurator configurator =
			new LdioHttpSparqlOutAutoConfig().ldiHttpSparqlOutConfigurator();
	private Model model;

	@BeforeEach
	void setUp() {
		model = ModelFactory.createDefaultModel();
		model.add(ResourceFactory.createResource("http://example.org/o/1"),
				ResourceFactory.createProperty("http://purl.org/dc/terms/title"),
				"product 1");
	}

	@Test
	void when_HostAnswersSuccess_and_ContinueOnFailFalse_then_NoException() {
		stubFor(post(PATH).willReturn(ok()));

		assertThatNoException().isThrownBy(() -> output(Map.of(LdioHttpSparqlOutProperties.CONTINUE_ON_FAIL, "false")).accept(model));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_HostAnswersServerErrorAfterRetries_and_ContinueOnFailFalse_then_HttpSparqlOutExceptionIsThrown() {
		stubFor(post(PATH).willReturn(serverError().withBody("boom")));
		final LdiOutput sparqlOut = output(Map.of(
				LdioHttpSparqlOutProperties.CONTINUE_ON_FAIL, "false",
				"retries.enabled", "true",
				"retries.max", "2"));

		assertThatThrownBy(() -> sparqlOut.accept(model))
				.isInstanceOf(HttpSparqlOutException.class)
				.hasMessageContaining(ENDPOINT)
				.hasMessageContaining("500")
				.hasMessageContaining("boom")
				.satisfies(e -> {
					final HttpSparqlOutException exception = (HttpSparqlOutException) e;
					assertThat(exception.getEndpoint()).isEqualTo(ENDPOINT);
					assertThat(exception.getHttpStatusCode()).isEqualTo(500);
					assertThat(exception.getResponseBody()).isEqualTo("boom");
				});
		verify(2, postRequestedFor(urlEqualTo(PATH)));
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 404})
	void when_HostAnswersClientError_and_ContinueOnFailFalse_then_HttpSparqlOutExceptionIsThrownWithoutRetry(int status) {
		stubFor(post(PATH).willReturn(aResponse().withStatus(status)));
		final LdiOutput sparqlOut = output(Map.of(LdioHttpSparqlOutProperties.CONTINUE_ON_FAIL, "false"));

		assertThatThrownBy(() -> sparqlOut.accept(model))
				.isInstanceOf(HttpSparqlOutException.class)
				.hasMessageContaining(String.valueOf(status));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_HostAnswersError_and_ContinueOnFailNotSet_then_FailureIsOnlyLogged() {
		stubFor(post(PATH).willReturn(serverError()));
		final LdiOutput sparqlOut = output(Map.of());

		assertThatNoException().isThrownBy(() -> sparqlOut.accept(model));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_HostAnswersError_and_ContinueOnFailTrue_then_FailureIsOnlyLogged() {
		stubFor(post(PATH).willReturn(aResponse().withStatus(400)));
		final LdiOutput sparqlOut = output(Map.of(LdioHttpSparqlOutProperties.CONTINUE_ON_FAIL, "true"));

		assertThatNoException().isThrownBy(() -> sparqlOut.accept(model));
	}

	@Test
	void when_ModelIsEmpty_then_NothingIsSent() {
		final LdiOutput sparqlOut = output(Map.of(LdioHttpSparqlOutProperties.CONTINUE_ON_FAIL, "false"));

		assertThatNoException().isThrownBy(() -> sparqlOut.accept(ModelFactory.createDefaultModel()));
		verify(0, postRequestedFor(urlEqualTo(PATH)));
	}

	private LdiOutput output(Map<String, String> extraConfig) {
		final Map<String, String> config = new HashMap<>(extraConfig);
		config.put(LdioHttpSparqlOutProperties.ENDPOINT, ENDPOINT);
		config.putIfAbsent("retries.enabled", "false");
		return configurator.configure(new ComponentProperties(PIPELINE_NAME, LdioHttpSparqlOut.NAME, config));
	}
}
