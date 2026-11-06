package ldes.client.treenodesupplier;

import org.openldes.ldi.requestexecutor.exceptions.HttpRequestException;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.RequestExecutorDecorator;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.timestampextractor.TimestampExtractor;
import ldes.client.treenodefetcher.TreeNodeFetcher;
import ldes.client.treenodefetcher.domain.entities.TreeMember;
import ldes.client.treenodefetcher.domain.valueobjects.TreeNodeResponse;
import ldes.client.treenodesupplier.domain.entities.MemberRecord;
import ldes.client.treenodesupplier.domain.entities.TreeNodeRecord;
import ldes.client.treenodesupplier.domain.valueobject.*;
import ldes.client.treenodesupplier.repository.MemberRepository;
import ldes.client.treenodesupplier.repository.TreeNodeRecordRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;
import static ldes.client.treenodesupplier.domain.valueobject.ClientStatus.*;

public class TreeNodeProcessor {

	/**
	 * How many member ids are remembered by default to recognise a member that
	 * another tree node already presented. Holding every id would grow without
	 * bound over a synchronisation that never ends.
	 */
	public static final int DEFAULT_SEEN_MEMBER_IDS = 1_000_000;

	private final TreeNodeRecordRepository treeNodeRecordRepository;
	private final MemberRepository memberRepository;
	private final TreeNodeFetcher treeNodeFetcher;
	private final LdesMetaData ldesMetaData;
	private final RequestExecutor responseReuseOwner;
	private final SingleUseOkResponseCache requestExecutor;
	private final Consumer<ClientStatus> clientStatusConsumer;
	private final Set<String> explicitStartingNodes = ConcurrentHashMap.newKeySet();
	private final Set<String> seenMemberIds;
	private MemberRecord memberRecord;

	public TreeNodeProcessor(LdesMetaData ldesMetaData, LdesClientRepositories ldesClientRepositories,
	                         RequestExecutor requestExecutor, TimestampExtractor timestampExtractor,
	                         Consumer<ClientStatus> clientStatusConsumer) {
		this(ldesMetaData, ldesClientRepositories, requestExecutor, timestampExtractor, clientStatusConsumer,
				DEFAULT_SEEN_MEMBER_IDS);
	}

	/**
	 * @param rememberedMemberIds how many member ids are remembered to recognise a
	 *                            member that another tree node of the same stream
	 *                            already presented. A synchronising client runs
	 *                            without end, so the oldest ids are forgotten once
	 *                            this many are held; a member that reappears after
	 *                            that many others is supplied again. Use
	 *                            {@link ldes.client.treenodesupplier.filters.ExactlyOnceFilter}
	 *                            for a bound-free guarantee.
	 */
	public TreeNodeProcessor(LdesMetaData ldesMetaData, LdesClientRepositories ldesClientRepositories,
	                         RequestExecutor requestExecutor, TimestampExtractor timestampExtractor,
	                         Consumer<ClientStatus> clientStatusConsumer, int rememberedMemberIds) {
		this.seenMemberIds = boundedIdSet(rememberedMemberIds);
		this.treeNodeRecordRepository = ldesClientRepositories.treeNodeRecordRepository();
		this.memberRepository = ldesClientRepositories.memberRepository();
		this.responseReuseOwner = requestExecutor;
		this.requestExecutor = new SingleUseOkResponseCache(
				requestExecutor,
				RequestExecutorDecorator.withDefaultRetryPolicy(requestExecutor));
		this.clientStatusConsumer = clientStatusConsumer;
		this.treeNodeFetcher = new TreeNodeFetcher(this.requestExecutor, timestampExtractor);
		this.ldesMetaData = ldesMetaData;
		this.explicitStartingNodes.addAll(ldesMetaData.getStartingNodeUrls());
	}

	private static class SingleUseOkResponseCache implements RequestExecutor {
		private final RequestExecutor responseReuseOwner;
		private final RequestExecutor requestExecutor;
		private final Map<String, Response> okResponsesByUrl = new ConcurrentHashMap<>();
		private volatile boolean captureOkResponses;
		private volatile Response lastCapturedOkResponse;

		private SingleUseOkResponseCache(RequestExecutor responseReuseOwner, RequestExecutor requestExecutor) {
			this.responseReuseOwner = responseReuseOwner;
			this.requestExecutor = requestExecutor;
		}

		private void startCapturing() {
			captureOkResponses = true;
		}

		private void stopCapturing() {
			captureOkResponses = false;
		}

		private void alias(String sourceUrl, String targetUrl) {
			Optional.ofNullable(okResponsesByUrl.get(sourceUrl))
					.or(() -> Optional.ofNullable(lastCapturedOkResponse))
					.or(() -> SingleUseResponseRegistry.consume(responseReuseOwner, sourceUrl))
					.ifPresent(cachedResponse -> okResponsesByUrl.put(targetUrl, cachedResponse));
		}

		@Override
		public Response execute(Request request) {
			final Response cachedResponse = okResponsesByUrl.remove(request.getUrl());
			if (cachedResponse != null) {
				return cachedResponse;
			}

			final Response response = SingleUseResponseRegistry
					.consume(responseReuseOwner, request)
					.orElseGet(() -> requestExecutor.execute(request));
			if (captureOkResponses && response.isOk()) {
				okResponsesByUrl.put(request.getUrl(), response);
				lastCapturedOkResponse = response;
			}
			return response;
		}
	}

	public void init() {
		if (!treeNodeRecordRepository.containsTreeNodeRecords()) {
			initializeTreeNodeRecordRepository();
		}
	}

	public SuppliedMember getMember() {
		removeLastMember();

		Optional<MemberRecord> unprocessedTreeMember = memberRepository.getTreeMember();
		while (unprocessedTreeMember.isEmpty()) {
			processTreeNode();
			unprocessedTreeMember = memberRepository.getTreeMember();
		}
		MemberRecord treeMember = unprocessedTreeMember.get();
		SuppliedMember suppliedMember = treeMember.createSuppliedMember();
		memberRecord = treeMember;
		return suppliedMember;
	}

	private void processTreeNode() {
		TreeNodeRecord treeNodeRecord = getNextTreeNode();

		if (TreeNodeStatus.IMMUTABLE_WITH_UNPROCESSED_MEMBERS.equals(treeNodeRecord.getTreeNodeStatus())) {
			treeNodeRecord.markImmutableWithoutUnprocessedMembers();
			treeNodeRecordRepository.saveTreeNodeRecord(treeNodeRecord);
		} else {
			try {
				waitUntilNextVisit(treeNodeRecord);
				TreeNodeResponse treeNodeResponse = treeNodeFetcher
						.fetchTreeNode(ldesMetaData.createRequest(treeNodeRecord.getTreeNodeUrl(), treeNodeRecord.getEtag()));
				treeNodeRecord.updateStatus(treeNodeResponse.getMutabilityStatus());
				treeNodeResponse.getEtag().ifPresent(treeNodeRecord::updateEtag);
				saveNewRelations(treeNodeResponse);
				List<TreeMember> newMembers = getNewMembersFromResponse(treeNodeResponse, treeNodeRecord);
				saveNewMembers(newMembers);
				treeNodeRecord.addToReceived(newMembers.stream().map(TreeMember::getMemberId).toList());
				treeNodeRecordRepository.saveTreeNodeRecord(treeNodeRecord);
				treeNodeRecordRepository.resetContext();
			} catch (HttpRequestException e) {
				treeNodeRecordRepository.saveTreeNodeRecord(treeNodeRecord);
				throw e;
			}

		}
	}

	private void saveNewMembers(List<TreeMember> newMembers) {
		memberRepository.saveTreeMembers(newMembers
				.stream()
				.map(treeMember -> new MemberRecord(
						treeMember.getMemberId(),
						treeMember.getDataset(),
						treeMember.getCreatedAt())));
	}

	private List<TreeMember> getNewMembersFromResponse(TreeNodeResponse treeNodeResponse, TreeNodeRecord treeNodeRecord) {
		final boolean explicitStartingNode = explicitStartingNodes.contains(treeNodeRecord.getTreeNodeUrl());
		return treeNodeResponse
				.getMembers()
				.stream()
				.filter(member -> !treeNodeRecord.hasReceived(member.getMemberId()))
				.filter(member -> seenMemberIds.add(member.getMemberId()) || explicitStartingNode)
				.toList();
	}

	private void saveNewRelations(TreeNodeResponse treeNodeResponse) {
		treeNodeResponse.getRelations()
				.stream()
				.filter(treeNodeId -> !treeNodeRecordRepository.existsById(treeNodeId))
				.map(TreeNodeRecord::new)
				.forEach(treeNodeRecordRepository::saveTreeNodeRecord);
	}

	private TreeNodeRecord getNextTreeNode() {
		TreeNodeRecord treeNodeRecord = treeNodeRecordRepository
				.getTreeNodeRecordWithStatusAndEarliestNextVisit(TreeNodeStatus.IMMUTABLE_WITH_UNPROCESSED_MEMBERS)
				.or(() -> treeNodeRecordRepository.getTreeNodeRecordWithStatusAndEarliestNextVisit(TreeNodeStatus.NOT_VISITED))
				.or(() -> treeNodeRecordRepository.getTreeNodeRecordWithStatusAndEarliestNextVisit(TreeNodeStatus.MUTABLE_AND_ACTIVE))
				.orElseThrow(() -> {
					clientStatusConsumer.accept(COMPLETED);
					return new EndOfLdesException("No fragments to mutable or new fragments to process -> LDES ends.");
				});

		TreeNodeStatus treeNodeStatus = Objects.requireNonNull(treeNodeRecord.getTreeNodeStatus());
		if (treeNodeStatus == TreeNodeStatus.IMMUTABLE_WITH_UNPROCESSED_MEMBERS ||
				treeNodeStatus == TreeNodeStatus.IMMUTABLE_WITHOUT_UNPROCESSED_MEMBERS ||
				treeNodeStatus == TreeNodeStatus.NOT_VISITED) {
			clientStatusConsumer.accept(REPLICATING);
		} else if (treeNodeStatus == TreeNodeStatus.MUTABLE_AND_ACTIVE) {
			clientStatusConsumer.accept(SYNCHRONISING);
		}

		return treeNodeRecord;
	}

	private void waitUntilNextVisit(TreeNodeRecord treeNodeRecord) {
		try {
			LocalDateTime earliestNextVisit = treeNodeRecord.getEarliestNextVisit();
			if (earliestNextVisit.isAfter(LocalDateTime.now())) {
				long sleepDuration = LocalDateTime.now().until(earliestNextVisit, ChronoUnit.MILLIS);
				sleep(sleepDuration);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void initializeTreeNodeRecordRepository() {
		requestExecutor.startCapturing();
		try {
			ldesMetaData.getStartingNodeUrls()
					.stream()
					.map(startingNode -> {
						final Optional<String> resolvedStartingNode = SingleUseResponseRegistry.consumeResolvedUrl(responseReuseOwner, startingNode);
						if (resolvedStartingNode.isPresent()) {
							requestExecutor.alias(resolvedStartingNode.get(), resolvedStartingNode.get());
							StartingTreeNode start = new StartingTreeNode(resolvedStartingNode.get(), ldesMetaData.getLang());
							explicitStartingNodes.add(start.getStartingNodeUrl());
							return start;
						}
						final StartingTreeNode start = new StartingTreeNodeSupplier(requestExecutor)
								.getStart(startingNode, ldesMetaData.getLang());
						if (startingNode.equals(start.getStartingNodeUrl())) {
							requestExecutor.alias(startingNode, start.getStartingNodeUrl());
						}
						explicitStartingNodes.add(start.getStartingNodeUrl());
						return start;
					})
					.map(start -> new TreeNodeRecord(start.getStartingNodeUrl()))
					.forEach(treeNodeRecordRepository::saveTreeNodeRecord);
		} finally {
			requestExecutor.stopCapturing();
		}
	}

	private void removeLastMember() {
		if (memberRecord != null) {
			memberRepository.deleteMember(memberRecord);
		}
	}

	/**
	 * Makes the record of which members have already been supplied durable,
	 * keeping the state so a later run resumes where this one stopped.
	 */
	public void flushState() {
		memberRepository.flush();
	}

	public void destroyState() {
		memberRepository.destroyState();
		treeNodeRecordRepository.destroyState();
	}

	private static Set<String> boundedIdSet(int maximumSize) {
		if (maximumSize < 1) {
			throw new IllegalArgumentException(
					"At least one member id has to be remembered, but got " + maximumSize);
		}
		return Collections.synchronizedSet(Collections.newSetFromMap(new BoundedIdMap(maximumSize)));
	}

	/**
	 * Holds a bounded number of member ids, forgetting the oldest first.
	 */
	private static final class BoundedIdMap extends LinkedHashMap<String, Boolean> {
		private static final long serialVersionUID = 1L;
		private final int maximumSize;

		private BoundedIdMap(int maximumSize) {
			this.maximumSize = maximumSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
			return size() > maximumSize;
		}
	}
}
