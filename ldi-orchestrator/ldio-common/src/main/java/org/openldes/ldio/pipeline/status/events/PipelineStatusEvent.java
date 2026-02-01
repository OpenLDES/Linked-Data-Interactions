package org.openldes.ldio.pipeline.status.events;

import org.openldes.ldio.pipeline.status.PipelineStatus;
import org.openldes.ldio.pipeline.status.StatusChangeSource;

public record PipelineStatusEvent(String pipelineId, PipelineStatus status, StatusChangeSource statusChangeSource) {
}
