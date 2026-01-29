package org.openldes.ldio.pipeline.creation.model;

import org.openldes.ldi.types.LdiAdapter;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

/**
 * Wrapper around any LdiAdapter for debugging purposes
 */
public class AdapterDebugger implements LdiAdapter {
	private final Logger log;
	private final LdiAdapter adapter;

	public AdapterDebugger(LdiAdapter adapter) {
		this.log = LoggerFactory.getLogger(adapter.getClass());
		this.adapter = adapter;
	}

	@Override
	public Stream<Model> apply(Content content) {
		log.atDebug().log("Starting point: {}", content);

		return adapter.apply(content);
	}
}
