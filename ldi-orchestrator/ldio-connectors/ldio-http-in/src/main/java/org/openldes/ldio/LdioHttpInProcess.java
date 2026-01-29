package org.openldes.ldio;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.springframework.context.ApplicationEventPublisher;

public class LdioHttpInProcess extends LdioInput {
	public static final String NAME = "Ldio:HttpIn";
	private boolean isPaused = false;

	public LdioHttpInProcess(ComponentExecutor executor, LdiAdapter adapter,
							 LdioObserver ldioObserver, ApplicationEventPublisher applicationEventPublisher) {
		super(executor, adapter, ldioObserver, applicationEventPublisher);
	}

	@Override
	protected void resume() {
		this.isPaused = false;
	}

	@Override
	protected void pause() {
		this.isPaused = true;
	}

	public boolean isPaused() {
		return isPaused;
	}

	@Override
	public void shutdown() {
		// Not implementable for push based
	}
}
