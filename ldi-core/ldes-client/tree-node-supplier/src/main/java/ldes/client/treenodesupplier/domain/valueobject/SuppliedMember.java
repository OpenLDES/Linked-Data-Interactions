package ldes.client.treenodesupplier.domain.valueobject;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.openldes.ldi.rdf.DatasetHolder;

/**
 * Wrapper around the received RDF member dataset.
 */
public class SuppliedMember {

	private final String id;
	private final DatasetHolder rdf;

	public SuppliedMember(String id, Model model) {
		this.id = id;
		this.rdf = new DatasetHolder(model);
	}

	public SuppliedMember(String id, Dataset dataset) {
		this.id = id;
		this.rdf = new DatasetHolder(dataset);
	}

	public String getId() {
		return id;
	}

	public Model getModel() {
		return rdf.getModel();
	}

	public Dataset getDataset() {
		return rdf.getDataset();
	}
}
