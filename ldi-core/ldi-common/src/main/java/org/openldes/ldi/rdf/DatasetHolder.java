package org.openldes.ldi.rdf;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import java.util.Objects;

/**
 * Keeps an RDF dataset together with its lazily-created union model.
 */
public final class DatasetHolder {
	private final Dataset dataset;
	private volatile Model model;

	public DatasetHolder(Model model) {
		this(DatasetFactory.create(Objects.requireNonNull(model)), model);
	}

	public DatasetHolder(Dataset dataset) {
		this(dataset, null);
	}

	public DatasetHolder(Dataset dataset, Model model) {
		this.dataset = Objects.requireNonNull(dataset);
		this.model = model;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public Model getModel() {
		Model currentModel = model;
		if (currentModel == null) {
			synchronized (this) {
				currentModel = model;
				if (currentModel == null) {
					currentModel = flatten(dataset);
					model = currentModel;
				}
			}
		}
		return currentModel;
	}

	public static Model flatten(Dataset dataset) {
		final Model flattenedModel = ModelFactory.createDefaultModel()
				.add(dataset.getDefaultModel());
		dataset.listNames().forEachRemaining(name -> flattenedModel.add(dataset.getNamedModel(name)));
		return flattenedModel;
	}
}
