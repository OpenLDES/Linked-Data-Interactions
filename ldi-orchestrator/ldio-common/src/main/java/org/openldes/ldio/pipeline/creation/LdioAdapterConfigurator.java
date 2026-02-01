package org.openldes.ldio.pipeline.creation;

import org.openldes.ldi.types.LdiAdapter;

/**
 * Interface to manage the configuration of the {@link LdiAdapter}
 * in LDIO. Implementations will typically be declared as a bean in a "Ldio[AdapterName]AutoConfig" class that will be
 * annotated as {@link org.springframework.context.annotation.Configuration}
 */
@FunctionalInterface
public interface LdioAdapterConfigurator extends LdioConfigurator {
}
