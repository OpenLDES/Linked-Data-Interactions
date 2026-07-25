package ldes.client.treenodesupplier.domain.entities;

import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.time.LocalDateTime;
import java.util.Objects;

import com.sun.istack.NotNull;

public class MemberRecord implements Comparable<MemberRecord> {
	private final String memberId;
	private final LocalDateTime createdAt;
	private final Dataset dataset;
	private final Model model;

	public MemberRecord(String memberId, Model model, LocalDateTime createdAt) {
		this.memberId = memberId;
		this.dataset = DatasetFactory.create(model);
		this.model = model;
		this.createdAt = createdAt;
	}

	public MemberRecord(String memberId, Dataset dataset, LocalDateTime createdAt) {
		this.memberId = memberId;
		this.dataset = dataset;
		this.model = flatten(dataset);
		this.createdAt = createdAt;
	}

	public String getMemberId() {
		return memberId;
	}

	public SuppliedMember createSuppliedMember() {
		return new SuppliedMember(memberId, dataset);
	}

	public Model getModel() {
		return model;
	}

	public Dataset getDataset() {
		return dataset;
	}

	private static Model flatten(Dataset dataset) {
		final Model flattenedModel = ModelFactory.createDefaultModel()
				.add(dataset.getDefaultModel());
		dataset.listNames().forEachRemaining(name -> flattenedModel.add(dataset.getNamedModel(name)));
		return flattenedModel;
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
