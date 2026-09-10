package ldes.client.eventstreamproperties;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import ldes.client.eventstreamproperties.valueobjects.EventStreamProperties;
import ldes.client.eventstreamproperties.valueobjects.PropertiesRequest;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.RequestExecutorFactory;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@WireMockTest(httpPort = EventStreamPropertiesFetcherTest.WIREMOCK_PORT)
class EventStreamPropertiesFetcherTest {
	public static final int WIREMOCK_PORT = 12121;
	private static final String BASE_URL = "http://localhost:" + WIREMOCK_PORT;
	private static final EventStreamProperties EXPECTED_PROPERTIES = new EventStreamProperties(
			BASE_URL + "/observations",
			"http://purl.org/dc/terms/isVersionOf",
			"http://www.w3.org/ns/prov#generatedAtTime",
			"");
	private RequestExecutor requestExecutor;
	private EventStreamPropertiesFetcher fetcher;

	@BeforeEach
	void setUp() {
		requestExecutor = new RequestExecutorFactory(false).createNoAuthExecutor();
		fetcher = new EventStreamPropertiesFetcher(requestExecutor);
	}

	@AfterEach
	void cleanResponseRegistry() {
		List.of(
				"/observations",
				"/observations-redirected",
				"/observations/by-page",
				"/observations/by-page?pageNumber=1",
				"/items",
				"/items/grouped?group=1",
				"/overview",
				"/root")
				.stream()
				.map(path -> BASE_URL + path)
				.forEach(url -> {
					SingleUseResponseRegistry.consume(requestExecutor, url);
					SingleUseResponseRegistry.consumeResolvedUrl(requestExecutor, url);
				});
	}

	@Test
	void givenEventStream_whenFetchProperties_thenReturnValidProperties() throws IOException, URISyntaxException {
		stubModel("/observations", "models/view.ttl");

		final EventStreamProperties properties = fetch("/observations");

		verify(getRequestedFor(urlEqualTo("/observations")));
		assertEventStreamProperties(properties);
	}

	@Test
	void givenRetryableStatus_whenFetchProperties_thenRetryAndReturnValidProperties()
			throws IOException, URISyntaxException {
		final byte[] responseBytes = readModel("models/view.ttl");
		stubFor(get("/observations")
				.inScenario("Retry event stream properties")
				.whenScenarioStateIs(STARTED)
				.willSetStateTo("Retryable status returned")
				.willReturn(aResponse().withStatus(408)));
		stubFor(get("/observations")
				.inScenario("Retry event stream properties")
				.whenScenarioStateIs("Retryable status returned")
				.willReturn(ok().withBody(responseBytes)));

		final EventStreamProperties properties = fetch("/observations");

		verify(2, getRequestedFor(urlEqualTo("/observations")));
		assertEventStreamProperties(properties);
	}

	@Test
	void givenRedirect_whenFetchProperties_thenResolveAndReuseRedirectedRoot() {
		stubFor(get("/observations").willReturn(temporaryRedirect("/observations-redirected")));
		stubFor(get("/observations-redirected").willReturn(ok().withBody("""
				@prefix ldes: <https://w3id.org/ldes#> .
				@prefix tree: <https://w3id.org/tree#> .
				@prefix dcterms: <http://purl.org/dc/terms/> .
				@prefix prov: <http://www.w3.org/ns/prov#> .
				<#stream> a ldes:EventStream;
					ldes:versionOfPath dcterms:isVersionOf;
					ldes:timestampPath prov:generatedAtTime;
					tree:view <> .
				""")));

		final EventStreamProperties properties = fetch("/observations");
		final String redirectedRoot = BASE_URL + "/observations-redirected";

		verify(getRequestedFor(urlEqualTo("/observations")));
		verify(getRequestedFor(urlEqualTo("/observations-redirected")));
		assertThat(properties.getRootNode()).isEqualTo(redirectedRoot);
		assertThat(SingleUseResponseRegistry.consumeResolvedUrl(requestExecutor, BASE_URL + "/observations"))
				.contains(redirectedRoot);
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, redirectedRoot)).isPresent();
	}

	@Test
	void givenMissingEventStream_whenFetchProperties_thenThrow() {
		stubFor(get("/observations").willReturn(notFound()));
		final PropertiesRequest request = request("/observations");

		assertThatThrownBy(() -> fetcher.fetchEventStreamProperties(request))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessage("Cannot handle response 404 of EventStreamPropertiesRequest %s", request);
		verify(getRequestedFor(urlEqualTo("/observations")));
	}

	@Test
	void givenView_whenFetchProperties_thenUseViewAsRoot() throws IOException, URISyntaxException {
		stubModel("/observations/by-page", "models/view.ttl");

		final EventStreamProperties properties = fetch("/observations/by-page");

		verify(getRequestedFor(urlEqualTo("/observations/by-page")));
		assertEventStreamProperties(properties);
		assertThat(properties.getRootNode()).isEqualTo(BASE_URL + "/observations/by-page");
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, properties.getRootNode())).isPresent();
	}

	@Test
	void givenFragment_whenFetchProperties_thenUseOriginalEntrypointToSelectView()
			throws IOException, URISyntaxException {
		stubModel("/observations/by-page?pageNumber=1", "models/treenode.ttl");
		stubModel("/observations", "models/eventstream.ttl");

		final EventStreamProperties properties = fetch("/observations/by-page?pageNumber=1");

		verify(getRequestedFor(urlEqualTo("/observations/by-page?pageNumber=1")));
		verify(getRequestedFor(urlEqualTo("/observations")));
		assertEventStreamProperties(properties);
		assertThat(properties.getRootNode()).isEqualTo(BASE_URL + "/observations/by-page");
	}

	@Test
	void givenFragmentAndEventStreamWithoutView_whenFetchProperties_thenReuseOriginalEntrypoint() {
		stubFor(get("/items/grouped?group=1").willReturn(ok().withBody("""
				@prefix tree: <https://w3id.org/tree#> .
				@prefix dcterms: <http://purl.org/dc/terms/> .
				<> a tree:Node ;
					dcterms:isPartOf <http://localhost:12121/items> .
				""")));
		stubFor(get("/items").willReturn(ok().withBody("""
				@prefix ldes: <https://w3id.org/ldes#> .
				@prefix dcterms: <http://purl.org/dc/terms/> .
				@prefix prov: <http://www.w3.org/ns/prov#> .
				<> a ldes:EventStream ;
					ldes:versionOfPath dcterms:isVersionOf ;
					ldes:timestampPath prov:generatedAtTime .
				""")));

		final EventStreamProperties properties = fetch("/items/grouped?group=1");

		assertThat(properties.getRootNode()).isEqualTo(BASE_URL + "/items");
		assertThat(SingleUseResponseRegistry.consumeResolvedUrl(requestExecutor, BASE_URL + "/items/grouped?group=1"))
				.contains(BASE_URL + "/items/grouped?group=1");
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, BASE_URL + "/items/grouped?group=1"))
				.isPresent();
	}

	@Test
	void givenOverviewWithOneView_whenFetchProperties_thenUseAdvertisedRoot() {
		stubFor(get("/overview").willReturn(ok().withBody("""
				@prefix tree: <https://w3id.org/tree#> .
				<> tree:view <root> .
				""")));

		final EventStreamProperties properties = fetch("/overview");

		assertThat(properties.getUri()).isEqualTo(BASE_URL + "/overview");
		assertThat(properties.getRootNode()).isEqualTo(BASE_URL + "/root");
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, BASE_URL + "/overview")).isEmpty();
		assertThat(SingleUseResponseRegistry.consumeResolvedUrl(requestExecutor, BASE_URL + "/overview"))
				.contains(BASE_URL + "/root");
	}

	@Test
	void givenOverviewWithMultipleViews_whenFetchProperties_thenThrowAmbiguityError() {
		stubFor(get("/overview").willReturn(ok().withBody("""
				@prefix tree: <https://w3id.org/tree#> .
				<> tree:view <root-a>, <root-b> .
				""")));

		assertThatThrownBy(() -> fetch("/overview"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Expected exactly one tree:view target matching the entrypoint, found 0");
	}

	private EventStreamProperties fetch(String path) {
		return fetcher.fetchEventStreamProperties(request(path));
	}

	private static PropertiesRequest request(String path) {
		return new PropertiesRequest(BASE_URL + path, Lang.TTL);
	}

	private static void stubModel(String path, String resourcePath) throws IOException, URISyntaxException {
		stubFor(get(path).willReturn(ok().withBody(readModel(resourcePath))));
	}

	private static byte[] readModel(String resourcePath) throws IOException, URISyntaxException {
		final URL resource = EventStreamPropertiesFetcherTest.class.getClassLoader().getResource(resourcePath);
		return Files.readAllBytes(Path.of(Objects.requireNonNull(resource).toURI()));
	}

	private static void assertEventStreamProperties(EventStreamProperties properties) {
		assertThat(properties.getUri()).isEqualTo(EXPECTED_PROPERTIES.getUri());
		assertThat(properties.getVersionOfPath()).isEqualTo(EXPECTED_PROPERTIES.getVersionOfPath());
		assertThat(properties.getTimestampPath()).isEqualTo(EXPECTED_PROPERTIES.getTimestampPath());
		assertThat(properties.getShaclShapeUri()).isEqualTo(EXPECTED_PROPERTIES.getShaclShapeUri());
	}
}
