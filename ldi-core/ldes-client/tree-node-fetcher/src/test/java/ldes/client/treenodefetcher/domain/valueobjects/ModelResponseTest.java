package ldes.client.treenodefetcher.domain.valueobjects;

import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.util.IsoMatcher;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.timestampextractor.TimestampFromCurrentTimeExtractor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelResponseTest {

	private static final String PAGE = "https://example.com/root";
	private static final String MEMBER = "https://example.com/member/selected";

	@Test
	void extractsOnlySelectedStreamMemberAcrossDefaultAndNamedGraphs() {
		final Dataset response = RDFParser.fromString("""
				@prefix ex: <https://example.com/vocab/> .
				@prefix tree: <https://w3id.org/tree#> .

				<https://example.com/stream> tree:view <https://example.com/root>;
				    tree:member <https://example.com/member/selected> .
				<https://example.com/other-stream> tree:view <https://example.com/other>;
				    tree:member <https://example.com/member/other> .

				<https://example.com/member/selected>
				    ex:value "default";
				    ex:detail _:a .
				_:a ex:next _:b .
				_:b ex:next _:a; ex:value "nested" .

				<https://example.com/unrelated> ex:value "not a member" .
				<https://example.com/member/other> ex:value "wrong stream" .

				<https://example.com/member/selected> {
				    <https://example.com/member/selected> ex:value "named graph" .
				    <https://example.com/payload-part> ex:value "all member graph quads" .
				}
				""")
				.lang(Lang.TRIG)
				.toDataset();
		final Dataset expected = RDFParser.fromString("""
				<https://example.com/member/selected> <https://example.com/vocab/value> "default" .
				<https://example.com/member/selected> <https://example.com/vocab/detail> _:first .
				_:first <https://example.com/vocab/next> _:second .
				_:second <https://example.com/vocab/next> _:first .
				_:second <https://example.com/vocab/value> "nested" .
				<https://example.com/member/selected> <https://example.com/vocab/value> "named graph" <https://example.com/member/selected> .
				<https://example.com/payload-part> <https://example.com/vocab/value> "all member graph quads" <https://example.com/member/selected> .
				""")
				.lang(Lang.NQUADS)
				.toDataset();

		final var members = new ModelResponse(
				response,
				new TimestampFromCurrentTimeExtractor(),
				PAGE)
				.getMembers();

		assertEquals(1, members.size());
		assertEquals(MEMBER, members.getFirst().getMemberId());
		assertTrue(IsoMatcher.isomorphic(
				expected.asDatasetGraph(),
				members.getFirst().getDataset().asDatasetGraph()));
	}
}
