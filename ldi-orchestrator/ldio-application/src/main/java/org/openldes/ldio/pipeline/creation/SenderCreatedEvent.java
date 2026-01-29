package org.openldes.ldio.pipeline.creation;

import org.openldes.ldio.pipeline.creation.model.LdioSender;

public record SenderCreatedEvent(String pipelineName, LdioSender ldioSender) {
}
