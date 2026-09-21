package ldes.client.treenodefetcher.domain.entities;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldi.rdf.DatasetHolder;

import java.time.LocalDateTime;

public class TreeMember {
	private final String memberId;
	private final LocalDateTime createdAt;
	private final DatasetHolder rdf;

	public TreeMember(String memberId, LocalDateTime createdAt, Model model) {
		this.memberId = memberId;
		this.createdAt = createdAt;
		this.rdf = new DatasetHolder(model);
	}

	public TreeMember(String memberId, LocalDateTime createdAt, Dataset dataset) {
		this(memberId, createdAt, dataset, null);
	}

	public TreeMember(String memberId, LocalDateTime createdAt, Dataset dataset, Model model) {
		this.memberId = memberId;
		this.createdAt = createdAt;
		this.rdf = new DatasetHolder(dataset, model);
	}

	public String getMemberId() {
		return memberId;
	}

	public Model getModel() {
		return rdf.getModel();
	}

	public Dataset getDataset() {
		return rdf.getDataset();
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
