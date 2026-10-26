package org.openldes.ldio;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldio.management.status.ClientStatusConsumer;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.pipeline.creation.events.PipelineShutdownEvent;
import org.openldes.ldio.pipeline.status.PipelineStatus;
import org.openldes.ldio.pipeline.status.PipelineStatusTrigger;
import org.openldes.ldio.pipeline.status.StatusChangeSource;
import org.openldes.ldio.pipeline.status.events.PipelineStatusEvent;
import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LdioLdesClientTest {

	@Mock
	private ComponentExecutor componentExecutor;

	@Mock
	private LdioObserver observer;

	@Mock
	private MemberSupplier supplier;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private ClientStatusConsumer clientStatusConsumer;

	private LdioLdesClient client;
	private final String pipelineName = "pipeline";

	@BeforeEach
	void setUp() {
		when(observer.getPipelineName()).thenReturn(pipelineName);
		client = new LdioLdesClient(
				componentExecutor,
				observer,
				supplier,
				eventPublisher,
				false,
				clientStatusConsumer);
	}

	@AfterEach
	void tearDown() {
		client.shutdown();
	}

	@Test
	void when_EndOfLdesException_And_AllDataProcessed_ShutdownPipeline() {
		when(observer.hasProcessedAllData()).thenReturn(false).thenReturn(true);
		when(supplier.get()).thenThrow(EndOfLdesException.class);

		client.start();

		await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
			verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.RUNNING, StatusChangeSource.MANUAL));
			verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.MANUAL));
			verify(eventPublisher).publishEvent(new PipelineShutdownEvent(pipelineName));
		});
	}

	@Test
	void when_RuntimeException_StopPipeline() {
		when(observer.hasProcessedAllData()).thenReturn(true);
		doThrow(RuntimeException.class).when(supplier).init();

		client.start();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.RUNNING, StatusChangeSource.MANUAL));
            verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.MANUAL));
        });

	}

	@Test
	void when_ProcessingAMemberFails_ThenPause_And_ProcessTheSameMemberAgainOnResume() {
		final SuppliedMember first = member("first");
		final SuppliedMember second = member("second");
		runProcessingThroughTheObserver();
		when(observer.hasProcessedAllData()).thenReturn(true);
		when(supplier.get()).thenReturn(first).thenReturn(second).thenThrow(EndOfLdesException.class);
		doThrow(new IllegalStateException("output failed")).doNothing()
				.when(componentExecutor).transformLinkedData(first.getModel());

		client.start();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.AUTO)));
		verify(supplier, times(1)).get();
		verify(componentExecutor, never()).transformLinkedData(second.getModel());

		client.updateStatus(PipelineStatusTrigger.RESUME);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(eventPublisher).publishEvent(new PipelineShutdownEvent(pipelineName)));
		final InOrder inOrder = inOrder(componentExecutor);
		inOrder.verify(componentExecutor, times(2)).transformLinkedData(first.getModel());
		inOrder.verify(componentExecutor).transformLinkedData(second.getModel());
		verify(supplier, times(3)).get();
	}

	@Test
	void when_AnOutputFailsWithAnIOError_ThenTheMemberIsNotSkipped() {
		final SuppliedMember first = member("first");
		final SuppliedMember second = member("second");
		runProcessingThroughTheObserver();
		when(observer.hasProcessedAllData()).thenReturn(true);
		when(supplier.get()).thenReturn(first).thenReturn(second).thenThrow(EndOfLdesException.class);
		doThrow(new HttpRequestException(new IOException("target unreachable"))).doNothing()
				.when(componentExecutor).transformLinkedData(first.getModel());

		client.start();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.AUTO)));
		client.updateStatus(PipelineStatusTrigger.RESUME);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(eventPublisher).publishEvent(new PipelineShutdownEvent(pipelineName)));
		verify(componentExecutor, times(2)).transformLinkedData(first.getModel());
		verify(componentExecutor).transformLinkedData(second.getModel());
		verify(supplier, times(3)).get();
	}

	@Test
	void when_TheLdesIsUnavailable_ThenPause_And_FetchAgainOnResume() {
		final SuppliedMember first = member("first");
		runProcessingThroughTheObserver();
		when(observer.hasProcessedAllData()).thenReturn(true);
		when(supplier.get())
				.thenThrow(new HttpRequestException(new IOException("LDES unreachable")))
				.thenReturn(first)
				.thenThrow(EndOfLdesException.class);

		client.start();

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
				verify(eventPublisher).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.AUTO)));
		verify(componentExecutor, never()).transformLinkedData(any());
		client.updateStatus(PipelineStatusTrigger.RESUME);

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(eventPublisher).publishEvent(new PipelineShutdownEvent(pipelineName)));
		verify(componentExecutor, times(1)).transformLinkedData(first.getModel());
		verify(supplier, times(3)).get();
	}

	@Test
	void when_NothingFails_ThenEveryMemberIsProcessedOnce_WithoutPausing() {
		final SuppliedMember first = member("first");
		final SuppliedMember second = member("second");
		runProcessingThroughTheObserver();
		when(observer.hasProcessedAllData()).thenReturn(true);
		when(supplier.get()).thenReturn(first).thenReturn(second).thenThrow(EndOfLdesException.class);

		client.start();

		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> verify(eventPublisher).publishEvent(new PipelineShutdownEvent(pipelineName)));
		verify(componentExecutor).transformLinkedData(first.getModel());
		verify(componentExecutor).transformLinkedData(second.getModel());
		verify(eventPublisher, never()).publishEvent(new PipelineStatusEvent(pipelineName, PipelineStatus.HALTED, StatusChangeSource.AUTO));
	}

	private void runProcessingThroughTheObserver() {
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(observer).observe(any(Runnable.class), anyString());
	}

	private static SuppliedMember member(String name) {
		final Model model = ModelFactory.createDefaultModel();
		model.add(model.createResource("http://example.org/" + name), model.createProperty("http://example.org/p"), name);
		return new SuppliedMember("http://example.org/" + name, model);
	}
}
