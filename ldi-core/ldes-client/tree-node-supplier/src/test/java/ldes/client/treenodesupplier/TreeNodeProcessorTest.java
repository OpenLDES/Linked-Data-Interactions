package ldes.client.treenodesupplier;

import ldes.client.treenodesupplier.domain.valueobject.ClientStatus;
import ldes.client.treenodesupplier.domain.valueobject.LdesClientRepositories;
import ldes.client.treenodesupplier.domain.valueobject.LdesMetaData;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TreeNodeProcessorTest {
	private static final String ENTRY = "https://example.com/entry";
	private static final String OVERVIEW = "https://example.com/overview";
	private static final String ROOT = "https://example.com/root";
	private static final String CHILD = "https://example.com/child";
	private static final String MEMBER = "https://example.com/member/overview";

	@Test
	void givenResolvedOverview_whenReadMember_thenFetchAdvertisedRootAndChildOnce() {
		final List<String> requestedUrls = new ArrayList<>();
		final RequestExecutor requestExecutor = request -> {
			requestedUrls.add(request.getUrl());
			return switch (request.getUrl()) {
				case ROOT -> okResponse(request, rootWithChild());
				case CHILD -> okResponse(request, childWithMember());
				default -> throw new AssertionError("Unexpected request: " + request.getUrl());
			};
		};
		final Response overviewResponse = okResponse(
				new GetRequest(OVERVIEW, RequestHeaders.empty()),
				"@prefix tree: <https://w3id.org/tree#> . <> tree:view <root> .");
		SingleUseResponseRegistry.capture(requestExecutor, OVERVIEW, overviewResponse);
		SingleUseResponseRegistry.resolve(requestExecutor, OVERVIEW, ROOT);
		final TreeNodeProcessor processor = processor(OVERVIEW, requestExecutor);

		processor.init();
		final SuppliedMember member = processor.getMember();

		assertThat(member.getId()).isEqualTo(MEMBER);
		assertThat(requestedUrls).containsExactly(ROOT, CHILD);
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, OVERVIEW)).contains(overviewResponse);
	}

	@Test
	void givenRedirectedRootResponse_whenReadMember_thenReuseResponseWithoutAnotherRequest() {
		final RequestExecutor requestExecutor = mock(RequestExecutor.class);
		final Response rootResponse = okResponse(
				new GetRequest(ROOT, RequestHeaders.empty()),
				rootWithMember());
		SingleUseResponseRegistry.capture(requestExecutor, ROOT, rootResponse);
		SingleUseResponseRegistry.resolve(requestExecutor, ENTRY, ROOT);
		final TreeNodeProcessor processor = processor(ENTRY, requestExecutor);

		processor.init();
		final SuppliedMember member = processor.getMember();

		assertThat(member.getId()).isEqualTo(MEMBER);
		assertThat(SingleUseResponseRegistry.consume(requestExecutor, ROOT)).isEmpty();
		verifyNoInteractions(requestExecutor);
	}

	private static TreeNodeProcessor processor(String entrypoint, RequestExecutor requestExecutor) {
		final LdesClientRepositories repositories = LdesClientRepositories.memoryBased();
		final Consumer<ClientStatus> statusConsumer = ignored -> { };
		return new TreeNodeProcessor(
				new LdesMetaData(List.of(entrypoint), Lang.TTL),
				repositories,
				requestExecutor,
				new TimestampFromCurrentTimeExtractor(),
				statusConsumer);
	}

	private static Response okResponse(Request request, String body) {
		return new Response(
				request,
				List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")),
				HttpStatus.SC_OK,
				body);
	}

	private static String rootWithChild() {
		return """
				@prefix tree: <https://w3id.org/tree#> .
				<https://example.com/overview> tree:view <> .
				<> tree:relation [ tree:node <../child> ], [ tree:node <../child> ] .
				""";
	}

	private static String childWithMember() {
		return """
				@prefix ex: <https://example.com/vocab/> .
				@prefix tree: <https://w3id.org/tree#> .
				<https://example.com/overview> tree:member <https://example.com/member/overview> .
				<https://example.com/member/overview> a ex:Event; ex:value "overview" .
				""";
	}

	private static String rootWithMember() {
		return """
				@prefix ex: <https://example.com/vocab/> .
				@prefix tree: <https://w3id.org/tree#> .
				<https://example.com/stream> tree:view <>;
					tree:member <https://example.com/member/overview> .
				<https://example.com/member/overview> ex:value "one" .
				""";
	}
}
