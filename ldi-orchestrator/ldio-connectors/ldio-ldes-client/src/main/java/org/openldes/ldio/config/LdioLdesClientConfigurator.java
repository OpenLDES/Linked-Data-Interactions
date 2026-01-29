package org.openldes.ldio.config;

import org.openldes.ldi.requestexecutor.services.RequestExecutorFactory;
import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldi.types.LdiAdapter;
import org.openldes.ldio.LdioLdesClient;
import org.openldes.ldio.LdioLdesClientProperties;
import org.openldes.ldio.management.status.ClientStatusConsumer;
import org.openldes.ldio.management.status.ClientStatusService;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioInputConfigurator;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.requestexecutor.LdioRequestExecutorSupplier;
import io.micrometer.observation.ObservationRegistry;
import ldes.client.eventstreamproperties.EventStreamPropertiesFetcher;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import org.springframework.context.ApplicationEventPublisher;

public class LdioLdesClientConfigurator implements LdioInputConfigurator {
	private final ClientStatusService clientStatusService;
	private final ObservationRegistry observationRegistry;
	private final LdioRequestExecutorSupplier requestExecutorSupplier;

	public LdioLdesClientConfigurator(ClientStatusService clientStatusService, ObservationRegistry observationRegistry) {
		this.clientStatusService = clientStatusService;
		this.observationRegistry = observationRegistry;
		requestExecutorSupplier = new LdioRequestExecutorSupplier(new RequestExecutorFactory(false));
	}

	@Override
	public LdioInput configure(LdiAdapter adapter, ComponentExecutor componentExecutor,
	                           ApplicationEventPublisher applicationEventPublisher,
	                           ComponentProperties properties) {
		final String pipelineName = properties.getPipelineName();
		final LdioLdesClientProperties ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(properties);
		final var requestExecutor = requestExecutorSupplier.getRequestExecutor(properties);
		final EventStreamPropertiesFetcher eventStreamPropertiesFetcher = new EventStreamPropertiesFetcher(requestExecutor);
		final var clientStatusConsumer = new ClientStatusConsumer(pipelineName, clientStatusService);
		final MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, eventStreamPropertiesFetcher, requestExecutor, clientStatusConsumer).getMemberSupplier();
		final boolean keepState = ldioLdesClientProperties.isKeepStateEnabled();
		final LdioObserver ldioObserver = LdioObserver.register(LdioLdesClient.NAME, pipelineName, observationRegistry);
		final var ldesClient = new LdioLdesClient(componentExecutor, ldioObserver, memberSupplier, applicationEventPublisher, keepState, clientStatusConsumer);
		ldesClient.start();
		return ldesClient;
	}

	@Override
	public boolean isAdapterRequired() {
		return false;
	}
}
