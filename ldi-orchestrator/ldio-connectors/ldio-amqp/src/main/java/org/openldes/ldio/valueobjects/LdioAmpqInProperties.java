package org.openldes.ldio.valueobjects;

import org.openldes.ldio.config.JmsConfig;

public record LdioAmpqInProperties(String pipelineName, String defaultContentType, JmsConfig jmsConfig) {
}
