package org.openldes.ldio.event;

import org.openldes.ldio.LdioHttpInProcess;

public record HttpInPipelineCreatedEvent(String pipelineName, LdioHttpInProcess ldioHttpInProcess) {
}
