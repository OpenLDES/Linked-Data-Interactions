package org.openldes.ldio;

import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.config.LdioHttpOutAutoConfig;
import org.openldes.ldio.exceptions.HttpOutException;
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
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = LdioHttpOutTest.PORT)
class LdioHttpOutTest {
	static final int PORT = 12131;
	private static final String PIPELINE_NAME = "test-pipeline";
	private static final String PATH = "/collection";
	private static final String ENDPOINT = "http://localhost:%d%s".formatted(PORT, PATH);

	private final LdioHttpOutAutoConfig.LdioHttpOutConfigurator configurator =
			new LdioHttpOutAutoConfig.LdioHttpOutConfigurator();
	private Model model;

	@BeforeEach
	void setUp() {
		model = ModelFactory.createDefaultModel();
		model.add(ResourceFactory.createResource("http://example.org/v/1"),
				ResourceFactory.createProperty("http://purl.org/dc/terms/isVersionOf"),
				ResourceFactory.createResource("http://example.org/o/1"));
	}

	@Test
	void when_TargetAnswersSuccess_then_NoException() {
		stubFor(post(PATH).willReturn(created()));

		assertThatNoException().isThrownBy(() -> output(Map.of(LdioHttpOutAutoConfig.CONTINUE_ON_FAIL, "false")).accept(model));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_TargetAnswersServerErrorAfterRetries_and_ContinueOnFailFalse_then_HttpOutExceptionIsThrown() {
		stubFor(post(PATH).willReturn(serverError().withBody("boom")));
		final LdiOutput httpOut = output(Map.of(
				LdioHttpOutAutoConfig.CONTINUE_ON_FAIL, "false",
				"retries.enabled", "true",
				"retries.max", "2"));

		assertThatThrownBy(() -> httpOut.accept(model))
				.isInstanceOf(HttpOutException.class)
				.hasMessageContaining(ENDPOINT)
				.hasMessageContaining("500")
				.hasMessageContaining("boom");
		verify(2, postRequestedFor(urlEqualTo(PATH)));
	}

	@ParameterizedTest
	@ValueSource(ints = {400, 413})
	void when_TargetAnswersClientError_and_ContinueOnFailFalse_then_HttpOutExceptionIsThrownWithoutRetry(int status) {
		stubFor(post(PATH).willReturn(aResponse().withStatus(status)));
		final LdiOutput httpOut = output(Map.of(LdioHttpOutAutoConfig.CONTINUE_ON_FAIL, "false"));

		assertThatThrownBy(() -> httpOut.accept(model))
				.isInstanceOf(HttpOutException.class)
				.hasMessageContaining(String.valueOf(status));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_TargetAnswersError_and_ContinueOnFailNotSet_then_FailureIsOnlyLogged() {
		stubFor(post(PATH).willReturn(aResponse().withStatus(400)));
		final LdiOutput httpOut = output(Map.of());

		assertThatNoException().isThrownBy(() -> httpOut.accept(model));
		verify(1, postRequestedFor(urlEqualTo(PATH)));
	}

	@Test
	void when_TargetAnswersError_and_ContinueOnFailTrue_then_FailureIsOnlyLogged() {
		stubFor(post(PATH).willReturn(aResponse().withStatus(400)));
		final LdiOutput httpOut = output(Map.of(LdioHttpOutAutoConfig.CONTINUE_ON_FAIL, "true"));

		assertThatNoException().isThrownBy(() -> httpOut.accept(model));
	}

	@Test
	void when_ModelIsEmpty_then_NothingIsSent() {
		final LdiOutput httpOut = output(Map.of(LdioHttpOutAutoConfig.CONTINUE_ON_FAIL, "false"));

		assertThatNoException().isThrownBy(() -> httpOut.accept(ModelFactory.createDefaultModel()));
		verify(0, postRequestedFor(urlEqualTo(PATH)));
	}

	private LdiOutput output(Map<String, String> extraConfig) {
		final Map<String, String> config = new HashMap<>(extraConfig);
		config.put(LdioHttpOutAutoConfig.ENDPOINT, ENDPOINT);
		config.putIfAbsent("retries.enabled", "false");
		return (LdiOutput) configurator.configure(new ComponentProperties(PIPELINE_NAME, LdioHttpOut.NAME, config));
	}
}
