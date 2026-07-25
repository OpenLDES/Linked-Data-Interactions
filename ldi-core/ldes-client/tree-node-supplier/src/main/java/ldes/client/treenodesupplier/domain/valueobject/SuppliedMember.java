package ldes.client.treenodesupplier.domain.valueobject;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

/**
 * Wrapper around the received RDF member dataset.
 */
public class SuppliedMember {

	private final String id;
	private final Dataset dataset;
	private final Model model;

	public SuppliedMember(String id, Model model) {
		this.id = id;
		this.dataset = DatasetFactory.create(model);
		this.model = model;
	}

	public SuppliedMember(String id, Dataset dataset) {
		this.id = id;
		this.dataset = dataset;
		this.model = flatten(dataset);
	}

	public String getId() {
		return id;
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
}
