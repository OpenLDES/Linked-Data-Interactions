package org.openldes.ldio.pipeline.creation.model;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.types.LdiOutput;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openldes.ldio.pipeline.PipelineConfig.PIPELINE_NAME;

class LdioSenderTest {
	private static final String PIPELINE = "sender-test";
	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final Model model = ModelFactory.createDefaultModel();

	@BeforeEach
	void setUp() {
		Metrics.addRegistry(registry);
	}

	@AfterEach
	void tearDown() {
		Metrics.removeRegistry(registry);
	}

	@Test
	void when_OutputSucceeds_then_DataOutIsCounted() {
		new LdioSender(PIPELINE, List.of(m -> { })).apply(model);

		assertEquals(1.0, dataOut());
	}

	@Test
	void when_OutputThrows_then_ExceptionPropagates_and_DataOutIsNotCounted() {
		final LdiOutput failing = m -> {
			throw new IllegalStateException("target rejected the member");
		};
		final LdioSender sender = new LdioSender(PIPELINE + "-failing", List.of(failing));

		assertThrows(IllegalStateException.class, () -> sender.apply(model));
		assertEquals(0.0, registry.counter("ldio_data_out", PIPELINE_NAME, PIPELINE + "-failing").count());
	}

	private double dataOut() {
		return registry.counter("ldio_data_out", PIPELINE_NAME, PIPELINE).count();
	}
}
