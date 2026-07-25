package ldes.client.treenodefetcher.domain.entities;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.time.LocalDateTime;

public class TreeMember {
	private final String memberId;
	private final LocalDateTime createdAt;
	private final Dataset dataset;
	private final Model model;

	public TreeMember(String memberId, LocalDateTime createdAt, Model model) {
		this.memberId = memberId;
		this.createdAt = createdAt;
		this.dataset = DatasetFactory.create(model);
		this.model = model;
	}

	public TreeMember(String memberId, LocalDateTime createdAt, Dataset dataset) {
		this.memberId = memberId;
		this.createdAt = createdAt;
		this.dataset = dataset;
		this.model = flatten(dataset);
	}

	public String getMemberId() {
		return memberId;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
