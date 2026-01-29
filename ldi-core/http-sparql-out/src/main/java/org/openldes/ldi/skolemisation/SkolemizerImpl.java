package org.openldes.ldi.skolemisation;

import org.openldes.ldi.SkolemisationTransformer;
import org.apache.jena.rdf.model.Model;

public class SkolemizerImpl implements Skolemizer {
	private final SkolemisationTransformer skolemisationTransformer;

	public SkolemizerImpl(SkolemisationTransformer skolemisationTransformer) {
		this.skolemisationTransformer = skolemisationTransformer;
	}

	@Override
	public Model skolemize(Model model) {
		return skolemisationTransformer.transform(model);
	}
}
