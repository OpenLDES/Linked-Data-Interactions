package org.openldes.ldi;

import org.openldes.ldi.types.LdiOneToOneTransformer;
import org.openldes.ldi.valueobjects.SkolemizedModel;
import org.apache.jena.rdf.model.Model;

public class SkolemisationTransformer implements LdiOneToOneTransformer {
	public static final String SKOLEM_URI = "/.well-known/genid/";
	private final String skolemUriTemplate;

	public SkolemisationTransformer(String skolemDomain) {
		this.skolemUriTemplate = skolemDomain + SKOLEM_URI + "%s";
	}

	@Override
	public Model transform(Model model) {
		return new SkolemizedModel(skolemUriTemplate, model).getModel();
	}
}
