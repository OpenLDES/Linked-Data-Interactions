package ldes.client.treenodesupplier.repository.mapper;

import ldes.client.treenodesupplier.domain.entities.MemberRecord;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.util.IsoMatcher;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberRecordEntityMapperTest {

	@Test
	void preservesNamedGraphs() {
		final Dataset dataset = RDFParser.fromString("""
				<https://example.com/member> <https://example.com/default> "default" .
				<https://example.com/member> <https://example.com/named> "named" <https://example.com/member> .
				""")
				.lang(Lang.NQUADS)
				.toDataset();
		final LocalDateTime createdAt = LocalDateTime.of(2026, 7, 25, 12, 0);
		final MemberRecord member = new MemberRecord(
				"https://example.com/member",
				dataset,
				createdAt);

		final MemberRecord restored = MemberRecordEntityMapper.toMemberRecord(
				MemberRecordEntityMapper.fromMemberRecord(member));

		assertEquals(member.getMemberId(), restored.getMemberId());
		assertEquals(createdAt, restored.getCreatedAt());
		assertTrue(IsoMatcher.isomorphic(
				dataset.asDatasetGraph(),
				restored.getDataset().asDatasetGraph()));
	}
}
