package org.openldes.ldio.pipeline.creation;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.springframework.context.ApplicationEventPublisher;

import static org.openldes.ldio.pipeline.status.PipelineStatusTrigger.START;

public class DummyIn extends LdioInput {
	private int counter = 0;

	public DummyIn(ComponentExecutor executor, LdiAdapter adapter, ApplicationEventPublisher applicationEventPublisher) {
		super(executor, adapter, LdioObserver.register("DummyIn", "test", null), applicationEventPublisher);
		this.updateStatus(START);
	}

	public void sendData() {
		String quad = "_:b0 <http://schema.org/integer> \"" + counter++
		              + "\"^^<http://www.w3.org/2001/XMLSchema#integer> .";
		processInput(quad, "application/n-quads");
	}

	@Override
	public void shutdown() {
		// No implementation needed for this test class
	}

	@Override
	protected void resume() {
		// No implementation needed for this test class
	}

	@Override
	protected void pause() {
		// No implementation needed for this test class
	}
}
