package ldes.client.treenodesupplier.repository.sql;

import org.openldes.ldi.StatelessQueryExecutor;
import org.openldes.ldi.entities.MemberRecordEntity;
import ldes.client.treenodesupplier.domain.entities.MemberRecord;
import ldes.client.treenodesupplier.repository.MemberRepository;
import ldes.client.treenodesupplier.repository.mapper.MemberRecordEntityMapper;
import org.hibernate.Session;
import org.hibernate.StatelessSession;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.stream.Stream;

/**
 * Keeps the unsupplied members in a SQL store so a traversal can resume, and
 * serves them from memory while the process runs.
 * <p>
 * Members are written to the store as they are received and read back from it
 * only when the in-memory view does not already hold them, which it does for
 * the whole of an uninterrupted traversal. That avoids serialising a member to
 * the store and parsing it straight back out again. A resumed run starts with
 * an empty view and therefore reads from the store.
 * <p>
 * Supplied members are removed in batches rather than one statement at a time.
 * A batch that has not been flushed when the process dies leaves those members
 * in the store, so a later run supplies them again; {@link #flush()} makes the
 * removals durable and is called on an orderly shutdown. Supplying a member
 * twice is the failure mode this trades for; members are never dropped.
 * <p>
 * The in-memory view assumes this repository is the only writer of its state,
 * which is what a per-client state store is.
 */
public class SqlMemberRepository implements MemberRepository {
	/**
	 * How many members are read per store query, and how many supplied members
	 * are removed per statement. A size of one restores a store query and a
	 * removal per member.
	 */
	public static final int DEFAULT_BATCH_SIZE = 100;

	private final EntityManager entityManager;
	private final int batchSize;
	/** Unsupplied members, ordered by creation like the store query is. */
	private final Queue<MemberRecord> unsuppliedMembers = new PriorityQueue<>();
	private final List<String> suppliedMemberIds = new ArrayList<>();
	/** Whether {@link #unsuppliedMembers} holds every member the store still has. */
	private boolean holdsEveryStoredMember;

	public SqlMemberRepository(EntityManager entityManager) {
		this(entityManager, DEFAULT_BATCH_SIZE);
	}

	public SqlMemberRepository(EntityManager entityManager, int batchSize) {
		if (batchSize < 1) {
			throw new IllegalArgumentException("A batch size of at least one member is required, but got " + batchSize);
		}
		this.entityManager = entityManager;
		this.batchSize = batchSize;
	}

	@Override
	public Optional<MemberRecord> getTreeMember() {
		if (unsuppliedMembers.isEmpty() && !holdsEveryStoredMember) {
			readBatchFromStore();
		}
		return Optional.ofNullable(unsuppliedMembers.peek());
	}

	@Override
	public void deleteMember(MemberRecord member) {
		removeFromView(member);
		suppliedMemberIds.add(member.getMemberId());
		if (suppliedMemberIds.size() >= batchSize || unsuppliedMembers.isEmpty()) {
			flush();
		}
	}

	@Override
	public void saveTreeMembers(Stream<MemberRecord> treeMemberStream) {
		final List<MemberRecord> newMembers = treeMemberStream.toList();
		if (newMembers.isEmpty()) {
			return;
		}
		entityManager.getTransaction().begin();
		newMembers.stream().map(MemberRecordEntityMapper::fromMemberRecord).forEach(entityManager::persist);
		entityManager.getTransaction().commit();
		if (holdsEveryStoredMember) {
			// The view still holds everything the store has, so these members can
			// be supplied without reading them back.
			unsuppliedMembers.addAll(newMembers);
		}
	}

	@Override
	public void flush() {
		if (suppliedMemberIds.isEmpty() || !entityManager.isOpen()) {
			// A shutdown hook can reach this after the caller closed the entity
			// manager. The removals then stay outstanding and those members are
			// supplied again, which is the same outcome as an abrupt stop.
			return;
		}
		final List<String> memberIds = List.copyOf(suppliedMemberIds);
		suppliedMemberIds.clear();
		executeStatelessQuery(session -> session
				.createNamedQuery("Member.deleteByMemberIds")
				.setParameter("memberIds", memberIds)
				.executeUpdate());
	}

	@Override
	public void destroyState() {
		unsuppliedMembers.clear();
		suppliedMemberIds.clear();
		holdsEveryStoredMember = false;
		if (entityManager.isOpen()) {
			entityManager.clear();
		}
	}

	private void readBatchFromStore() {
		// Members awaiting removal would otherwise be read back and supplied again.
		flush();
		final List<MemberRecord> storedMembers = entityManager
				.createNamedQuery("Member.getAllOrderedByCreation", MemberRecordEntity.class)
				.setMaxResults(batchSize)
				.getResultStream()
				.map(MemberRecordEntityMapper::toMemberRecord)
				.toList();
		unsuppliedMembers.addAll(storedMembers);
		holdsEveryStoredMember = storedMembers.size() < batchSize;
	}

	private void removeFromView(MemberRecord member) {
		final MemberRecord next = unsuppliedMembers.peek();
		if (next != null && next.getMemberId().equals(member.getMemberId())) {
			unsuppliedMembers.remove();
		} else {
			unsuppliedMembers.removeIf(unsupplied -> unsupplied.getMemberId().equals(member.getMemberId()));
		}
	}

	private int executeStatelessQuery(StatelessQueryExecutor queryExecutor) {
		final Session session = entityManager.unwrap(Session.class);
		return session.doReturningWork(connection -> {
			try (final StatelessSession statelessSession = session.getSessionFactory().openStatelessSession(connection)) {
				return queryExecutor.execute(statelessSession);
			}
		});
	}
}
