package ldes.client.eventstreamproperties.services;

import ldes.client.eventstreamproperties.valueobjects.StartingNodeSpecification;
import ldes.client.eventstreamproperties.valueobjects.TreeNodeSpecification;
import ldes.client.eventstreamproperties.valueobjects.ViewSpecification;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;

public class StartingNodeSpecificationFactory {
	private StartingNodeSpecificationFactory() {
	}

	public static StartingNodeSpecification fromModel(Model model) {
		return fromDataset(org.apache.jena.query.DatasetFactory.create(model));
	}

	public static StartingNodeSpecification fromDataset(Dataset dataset) {
		final Model model = dataset.getDefaultModel();
		if (TreeNodeSpecification.isTreeNode(model)) {
			return new TreeNodeSpecification(model);
		}
		if (ViewSpecification.isViewSpecification(model)) {
			return new ViewSpecification(dataset);
		}
		throw new IllegalStateException("The provided starting node must contain either a dcterms:isPartOf property or the ldes:versionOfPath and ldes:timestampPath properties");
	}

}
