package org.openldes.ldio;

import org.openldes.ldi.types.LdiOutput;
import org.apache.jena.rdf.model.Model;

public class LdioNoopOut implements LdiOutput {
	public static final String NAME = "Ldio:NoopOut";

	@Override
	public void accept(Model linkedDataModel) {
		// No Operation
	}
}
