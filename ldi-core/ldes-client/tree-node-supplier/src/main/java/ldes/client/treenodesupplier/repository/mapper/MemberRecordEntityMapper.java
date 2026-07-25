package ldes.client.treenodesupplier.repository.mapper;

import org.openldes.ldi.entities.MemberRecordEntity;
import ldes.client.treenodesupplier.domain.entities.MemberRecord;
import org.apache.jena.query.Dataset;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class MemberRecordEntityMapper {
	private MemberRecordEntityMapper() {
	}

	public static MemberRecordEntity fromMemberRecord(MemberRecord treeMember) {
		final Dataset dataset = treeMember.getDataset();
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		RDFWriter.source(dataset).lang(Lang.RDFPROTO).output(stream);
		final byte[] bytes = stream.toByteArray();
		return new MemberRecordEntity(treeMember.getMemberId(), treeMember.getCreatedAt(), bytes);
	}

	public static MemberRecord toMemberRecord(MemberRecordEntity memberRecordEntity) {
		final byte[] bytes = memberRecordEntity.getModelAsBytes();
		final Dataset dataset = RDFParser.source(new ByteArrayInputStream(bytes)).lang(Lang.RDFPROTO).toDataset();
		return new MemberRecord(memberRecordEntity.getMemberId(), dataset, memberRecordEntity.getCreatedAt());
	}
}
