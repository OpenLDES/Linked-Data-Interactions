package org.openldes.ldio.config;

import org.openldes.ldi.SkolemisationTransformer;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.apache.jena.rdf.model.Model;

public class LdioSkolemisationTransformer extends LdioTransformer {
	public static final String NAME = "Ldio:SkolemisationTransformer";
	private final SkolemisationTransformer skolemisationTransformer;

	public LdioSkolemisationTransformer(SkolemisationTransformer skolemisationTransformer) {
		this.skolemisationTransformer = skolemisationTransformer;
	}

	@Override
	public void apply(Model model) {
		this.next(skolemisationTransformer.transform(model));
	}
}
