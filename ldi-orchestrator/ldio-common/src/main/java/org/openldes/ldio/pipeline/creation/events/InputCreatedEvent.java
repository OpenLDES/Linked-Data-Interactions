package org.openldes.ldio.pipeline.creation.events;

import org.openldes.ldio.pipeline.creation.LdioInput;

public record InputCreatedEvent(String pipelineName, LdioInput ldioInput) {
}
