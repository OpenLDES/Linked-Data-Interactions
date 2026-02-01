package org.openldes.ldio.pipeline.initializer;

import org.openldes.ldio.pipeline.PipelineConfig;

import java.util.List;

public interface PipelineInitializer {
	String name();

	List<PipelineConfig> initPipelines();
}
