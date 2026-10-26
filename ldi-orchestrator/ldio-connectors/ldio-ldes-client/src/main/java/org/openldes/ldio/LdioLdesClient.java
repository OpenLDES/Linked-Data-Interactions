package org.openldes.ldio;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.services.ComponentExecutor;
import org.openldes.ldio.management.status.ClientStatusConsumer;
import org.openldes.ldio.pipeline.creation.LdioInput;
import org.openldes.ldio.pipeline.creation.LdioObserver;
import org.openldes.ldio.pipeline.creation.events.PipelineShutdownEvent;
import org.openldes.ldio.pipeline.status.PipelineStatusTrigger;
import org.openldes.ldio.pipeline.status.StatusChangeSource;
import ldes.client.treenodesupplier.domain.valueobject.ClientStatus;
import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class LdioLdesClient extends LdioInput {

	public static final String NAME = "Ldio:LdesClient";
	public static final String LDIO_SHUTDOWN_THREAD_NAME = "ldio-ldes-client-shutdown";

	private final Logger log = LoggerFactory.getLogger(LdioLdesClient.class);

	private final MemberSupplier memberSupplier;
	private boolean threadRunning = true;
	private final Supplier<Boolean> canGracefullyShutdownChecker;
	private boolean paused = false;
	private final boolean keepState;
	private final String pipelineName;
	private final ClientStatusConsumer clientStatusConsumer;

	public LdioLdesClient(ComponentExecutor componentExecutor,
	                      LdioObserver ldioObserver,
	                      MemberSupplier memberSupplier,
	                      ApplicationEventPublisher applicationEventPublisher,
	                      boolean keepState, ClientStatusConsumer clientStatusConsumer) {
		super(componentExecutor, null, ldioObserver, applicationEventPublisher);
		this.pipelineName = ldioObserver.getPipelineName();
		this.canGracefullyShutdownChecker = ldioObserver::hasProcessedAllData;
		this.memberSupplier = memberSupplier;
		this.keepState = keepState;
		this.clientStatusConsumer = clientStatusConsumer;
	}

	@Override
	public void start() {
		super.start();
		final ExecutorService executorService = newSingleThreadExecutor();
		executorService.submit(() -> {
			try {
				memberSupplier.init();
				this.run();
			} catch (RuntimeException e) {
				log.atWarn().log("HALTING pipeline because of an unhandled error");
				log.atError().log(e.getMessage());
				shutdownPipeline();
				throw e;
			}
		});
	}

	/**
	 * Fetching a member from the LDES and processing it (transformers and outputs) fail in different ways:
	 * <ul>
	 *     <li>The LDES is unavailable: the client pauses and fetches again once the pipeline is resumed.</li>
	 *     <li>Processing a member fails: the client pauses and keeps that member. Once the pipeline is resumed, the
	 *     same member is processed again before the next one is fetched. The member supplier only removes a member
	 *     from its state when the next one is requested, so the member is also delivered again after a restart.</li>
	 * </ul>
	 */
	private synchronized void run() {
		SuppliedMember failedMember = null;
		while (threadRunning) {
			checkPause();
			final SuppliedMember member;
			if (failedMember != null) {
				member = failedMember;
			} else {
				try {
					member = memberSupplier.get();
				} catch (EndOfLdesException e) {
					log.info("SHUTTING DOWN pipeline {} because end of LDES has been reached", pipelineName);
					shutdownPipeline();
					return;
				} catch (HttpRequestException e) {
					pauseOnError("LDES URL unavailable. Client paused: {}", e.getMessage());
					continue;
				} catch (Exception e) {
					pauseOnError("LdesClientRunner FAILURE: {}", e.getMessage());
					return;
				}
			}
			try {
				processModel(member.getModel());
				failedMember = null;
			} catch (Exception e) {
				failedMember = member;
				pauseOnError("Processing member {} failed. Client paused, the member is processed again when the pipeline is resumed: {}",
						member.getId(), e.getMessage());
			}
		}
	}

	private void pauseOnError(String message, Object... arguments) {
		updateStatus(PipelineStatusTrigger.HALT, StatusChangeSource.AUTO);
		clientStatusConsumer.accept(ClientStatus.ERROR);
		log.error(message, arguments);
	}

	private synchronized void checkPause() {
		while (paused) {
			try {
				this.wait();
			} catch (InterruptedException e) {
				log.error("Thread interrupted: {}", e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void shutdown() {
		shutdownPipeline();
		if (!keepState) {
			memberSupplier.destroyState();
		}
	}

	@Override
	protected synchronized void resume() {
		this.paused = false;
		this.notifyAll();
	}

	@Override
	protected void pause() {
		this.paused = true;
	}

	private void shutdownPipeline() {
		try {
			Thread.ofVirtual().start(this::shutdownPipelineThread).join();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void shutdownPipelineThread() {
		threadRunning = false;
		do {
			try {
				Thread.sleep(Duration.ofSeconds(1));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		} while (Boolean.FALSE.equals(canGracefullyShutdownChecker.get()));
		updateStatus(PipelineStatusTrigger.HALT);
		applicationEventPublisher.publishEvent(new PipelineShutdownEvent(pipelineName));
	}
}
