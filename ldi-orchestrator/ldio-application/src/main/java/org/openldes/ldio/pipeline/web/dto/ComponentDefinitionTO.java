package org.openldes.ldio.pipeline.web.dto;

import org.openldes.ldio.pipeline.creation.valueobjects.ComponentDefinition;
import org.openldes.ldio.pipeline.web.FlattenDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.Map;

public record ComponentDefinitionTO(String name,
                                    @JsonDeserialize(using = FlattenDeserializer.class) Map<String, String> config) {
	public ComponentDefinitionTO(String name, Map<String, String> config) {
		this.name = name;
		this.config = config == null ? Map.of() : config;
	}

	ComponentDefinition toComponentDefinition(String pipelineName) {
		return new ComponentDefinition(pipelineName, name, config == null ? Map.of() : config);
	}

}
