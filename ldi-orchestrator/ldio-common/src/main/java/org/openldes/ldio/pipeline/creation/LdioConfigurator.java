package org.openldes.ldio.pipeline.creation;

import org.openldes.ldi.types.LdiComponent;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;

/**
 * Base interface to configure all LDIO components except for the {@link LdioInput}
 */
@FunctionalInterface
public interface LdioConfigurator {
	/**
	 * Configures an LdiComponent based on the provided ComponentProperties
	 */
	LdiComponent configure(ComponentProperties properties);
}
