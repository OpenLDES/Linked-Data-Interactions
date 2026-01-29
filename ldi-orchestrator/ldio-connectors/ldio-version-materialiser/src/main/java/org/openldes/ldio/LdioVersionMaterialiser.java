package org.openldes.ldio;

import org.openldes.ldi.VersionMaterialiser;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;

public class LdioVersionMaterialiser extends LdioTransformer {
	public static final String NAME = "Ldio:VersionMaterialiser";
	private final VersionMaterialiser versionMaterialiser;

	public LdioVersionMaterialiser(Property versionOfProperty, boolean restrictToMembers) {
		this.versionMaterialiser = new VersionMaterialiser(versionOfProperty, restrictToMembers);
	}

	@Override
	public void apply(Model model) {
		this.next(versionMaterialiser.transform(model));
	}
}
