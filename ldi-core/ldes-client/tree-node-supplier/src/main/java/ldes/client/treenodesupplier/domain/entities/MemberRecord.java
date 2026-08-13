package ldes.client.treenodesupplier.domain.entities;

import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldi.rdf.DatasetHolder;

import java.time.LocalDateTime;
import java.util.Objects;

import com.sun.istack.NotNull;

public class MemberRecord implements Comparable<MemberRecord> {
	private final String memberId;
	private final LocalDateTime createdAt;
	private final DatasetHolder rdf;

	public MemberRecord(String memberId, Model model, LocalDateTime createdAt) {
		this.memberId = memberId;
		this.rdf = new DatasetHolder(model);
		this.createdAt = createdAt;
	}

	public MemberRecord(String memberId, Dataset dataset, LocalDateTime createdAt) {
		this.memberId = memberId;
		this.rdf = new DatasetHolder(dataset);
		this.createdAt = createdAt;
	}

	public String getMemberId() {
		return memberId;
	}

	public SuppliedMember createSuppliedMember() {
		return new SuppliedMember(memberId, rdf.getDataset());
	}

	public Model getModel() {
		return rdf.getModel();
	}

	public Dataset getDataset() {
		return rdf.getDataset();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof MemberRecord that))
			return false;
		return Objects.equals(memberId, that.memberId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(memberId);
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	@Override
	public int compareTo(@NotNull MemberRecord member) {
		return getCreatedAt().compareTo(member.getCreatedAt());
	}
}
