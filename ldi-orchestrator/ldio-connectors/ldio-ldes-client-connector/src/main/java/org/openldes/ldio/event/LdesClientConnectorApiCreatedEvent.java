package org.openldes.ldio.event;

import org.openldes.ldio.LdioLdesClientConnectorApi;

public record LdesClientConnectorApiCreatedEvent(String pipelineName,
                                                 LdioLdesClientConnectorApi ldesClientConnectorApi) {
}
