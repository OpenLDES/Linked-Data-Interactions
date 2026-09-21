package org.openldes.ldi.rdf;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetHolderTest {
	private static final String EX = "http://example.org/";

	@Test
	void createsAUnionModelFromDefaultAndNamedGraphs() {
		final var dataset = DatasetFactory.create();
		dataset.getDefaultModel().add(
				dataset.getDefaultModel().createResource(EX + "default"),
				dataset.getDefaultModel().createProperty(EX + "value"),
				"default");
		final var namedModel = ModelFactory.createDefaultModel();
		namedModel.add(
				namedModel.createResource(EX + "named"),
				namedModel.createProperty(EX + "value"),
				"named");
		dataset.addNamedModel(EX + "graph", namedModel);

		final var model = new DatasetHolder(dataset).getModel();

		assertThat(model.containsResource(model.createResource(EX + "default"))).isTrue();
		assertThat(model.containsResource(model.createResource(EX + "named"))).isTrue();
	}

	@Test
	void reusesAProvidedModel() {
		final var model = ModelFactory.createDefaultModel();

		assertThat(new DatasetHolder(model).getModel()).isSameAs(model);
	}
}
