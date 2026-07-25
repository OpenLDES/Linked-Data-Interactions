package ldes.client.treenodefetcher;

import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeRequest;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeResponse;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.apache.jena.riot.RDFLanguages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeNodeFetcherTest {

	@Test
	void should_ReturnEmptyImmutableResponse_when_TreeNodeIsGone() {
		final TreeNodeFetcher treeNodeFetcher = new TreeNodeFetcher(
				request -> new Response(
						new GetRequest(request.getUrl(), RequestHeaders.empty()),
						List.of(),
						410,
						(String) null),
				new TimestampFromCurrentTimeExtractor());

		final TreeNodeResponse response = treeNodeFetcher.fetchTreeNode(new TreeNodeRequest(
				"https://example.com/gone",
				RDFLanguages.TURTLE,
				null));

		assertThat(response.getMembers()).isEmpty();
		assertThat(response.getRelations()).isEmpty();
		assertThat(response.getMutabilityStatus().isMutable()).isFalse();
	}

	@Test
	void should_ReturnEtagAndImmutableResponse_when_RdfDeclaresNodeImmutable() {
		final String body = """
				@prefix ldes: <https://w3id.org/ldes#> .
				@prefix tree: <https://w3id.org/tree#> .

				<> ldes:immutable true ;
				    tree:relation
				        [ tree:node <rdf-immutable> ],
				        [ tree:node <header-immutable> ] .
				""";
		final TreeNodeFetcher treeNodeFetcher = new TreeNodeFetcher(
				request -> new Response(
						new GetRequest(request.getUrl(), RequestHeaders.empty()),
						List.of(
								new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/turtle"),
								new BasicHeader(HttpHeaders.ETAG, "\"root-v1\"")),
						HttpStatus.SC_OK,
						body),
				new TimestampFromCurrentTimeExtractor());

		final TreeNodeResponse response = treeNodeFetcher.fetchTreeNode(new TreeNodeRequest(
				"https://example.com/root",
				RDFLanguages.TURTLE,
				null));

		assertThat(response.getEtag()).contains("\"root-v1\"");
		assertThat(response.getMutabilityStatus().isMutable()).isFalse();
		assertThat(response.getRelations()).containsExactly(
				"https://example.com/rdf-immutable",
				"https://example.com/header-immutable");
	}
}
