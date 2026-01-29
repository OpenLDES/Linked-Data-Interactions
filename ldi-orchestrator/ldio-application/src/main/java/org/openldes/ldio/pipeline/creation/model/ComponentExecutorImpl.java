package org.openldes.ldio.pipeline.creation.model;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldio.pipeline.creation.LdioTransformer;
import org.apache.jena.rdf.model.Model;

public class ComponentExecutorImpl implements ComponentExecutor {

	private final LdioTransformer ldiTransformerPipeline;

	public ComponentExecutorImpl(LdioTransformer ldiTransformerPipeline) {
		this.ldiTransformerPipeline = ldiTransformerPipeline;
	}

	@Override
	public void transformLinkedData(final Model linkedDataModel) {
		ldiTransformerPipeline.apply(linkedDataModel);
	}
}
