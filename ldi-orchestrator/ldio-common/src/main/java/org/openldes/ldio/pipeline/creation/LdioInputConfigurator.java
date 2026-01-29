package org.openldes.ldio.pipeline.creation;

import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Interface to manage the configuration of the {@link LdioInput}
 * in LDIO. Implementations will typically be declared as a bean in a "Ldio[OutputName]AutoConfig" class that will be
 * annotated as {@link org.springframework.context.annotation.Configuration}
 */
public interface LdioInputConfigurator {
	LdioInput configure(LdiAdapter adapter, ComponentExecutor executor, ApplicationEventPublisher eventPublisher, ComponentProperties properties);

	boolean isAdapterRequired();
}
