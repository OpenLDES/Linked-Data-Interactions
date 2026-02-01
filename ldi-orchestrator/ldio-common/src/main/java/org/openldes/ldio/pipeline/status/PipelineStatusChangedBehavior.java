package org.openldes.ldio.pipeline.status;

import org.openldes.ldi.types.LdiComponent;

/**
 * Behavior interface that can be implemented for each pipeline status change
 */
@FunctionalInterface
public interface PipelineStatusChangedBehavior<T extends LdiComponent> {
	void applyNewStatus(T ldioComponent);
}
