package ldes.client.startingtreenode;

import ldes.client.startingtreenode.domain.valueobjects.RedirectHistory;
import ldes.client.startingtreenode.domain.valueobjects.StartingNodeRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StartingTreeNodeFinderTest {

	@Test
	void keepsCurrentNodeWhenOneOfMultipleViewsTargetsIt() {
		final String root = "https://example.com/root";
		final String body = """
				@prefix tree: <https://w3id.org/tree#> .
				<https://example.com/selected-stream> tree:view <https://example.com/root> .
				<https://example.com/other-stream> tree:view <https://example.com/other-root> .
				""";
		final RequestExecutor requestExecutor = request -> new Response(
				request,
				List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")),
				HttpStatus.SC_OK,
				body);

		final var startingNode = new StartingTreeNodeFinder(requestExecutor)
				.determineStartingTreeNode(new StartingNodeRequest(
						root,
						Lang.TURTLE,
						new RedirectHistory()));

		assertThat(startingNode.getUrl()).isEqualTo(root);
	}
}
