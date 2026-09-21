package ldes.client.treenodesupplier.repository.sql;

import org.openldes.ldi.entities.TreeNodeRecordEntity;
import ldes.client.treenodesupplier.domain.entities.TreeNodeRecord;
import ldes.client.treenodesupplier.domain.valueobject.TreeNodeStatus;
import ldes.client.treenodesupplier.repository.TreeNodeRecordRepository;
import ldes.client.treenodesupplier.repository.mapper.TreeNodeRecordEntityMapper;

import javax.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SqlTreeNodeRepository implements TreeNodeRecordRepository {
	private final EntityManager entityManager;

	public SqlTreeNodeRepository(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public void saveTreeNodeRecord(TreeNodeRecord treeNodeRecord) {
		TreeNodeRecordEntity memberRecordEntity = TreeNodeRecordEntityMapper.fromTreeNodeRecord(treeNodeRecord);
		entityManager.getTransaction().begin();
		TreeNodeRecordEntity storedTreeNodeRecord = entityManager.find(TreeNodeRecordEntity.class, treeNodeRecord.getTreeNodeUrl());
		if (storedTreeNodeRecord == null) {
			entityManager.persist(memberRecordEntity);
		} else {
			storedTreeNodeRecord.setTreeNodeStatus(memberRecordEntity.getTreeNodeStatus());
			storedTreeNodeRecord.setEarliestNextVisit(memberRecordEntity.getEarliestNextVisit());
			updateMembers(storedTreeNodeRecord, memberRecordEntity.getMembers());
			storedTreeNodeRecord.setEtag(memberRecordEntity.getEtag());
		}
		entityManager.getTransaction().commit();
	}

	/**
	 * Brings the stored member ids in line with the given ones by changing the
	 * collection in place.
	 * <p>
	 * Replacing the collection makes Hibernate delete every stored id and insert
	 * all of them again, which a fragment that keeps receiving members pays on
	 * every visit. Adding only the ids that are new leaves the stored rows alone.
	 * Ids are only ever added or, when the fragment is fully processed, all
	 * dropped, so a rewrite is needed solely in the latter case.
	 */
	private static void updateMembers(TreeNodeRecordEntity storedTreeNodeRecord, List<String> memberIds) {
		final List<String> storedMemberIds = storedTreeNodeRecord.getMembers();
		if (storedMemberIds == null) {
			storedTreeNodeRecord.setMembers(memberIds);
			return;
		}
		final Set<String> members = new HashSet<>(memberIds);
		if (!members.containsAll(storedMemberIds)) {
			storedMemberIds.clear();
			storedMemberIds.addAll(memberIds);
			return;
		}
		final Set<String> storedMembers = new HashSet<>(storedMemberIds);
		memberIds.stream().filter(memberId -> !storedMembers.contains(memberId)).forEach(storedMemberIds::add);
	}

	@Override
	public boolean existsById(String treeNodeId) {
		return entityManager
				.createNamedQuery("TreeNode.getById", TreeNodeRecordEntity.class)
				.setParameter("id", treeNodeId)
				.setMaxResults(1)
				.getResultStream()
				.findFirst()
				.isPresent();
	}

	@Override
	public Optional<TreeNodeRecord> findById(String treeNodeId) {
		return entityManager
				.createNamedQuery("TreeNode.getById", TreeNodeRecordEntity.class)
				.setParameter("id", treeNodeId)
				.setMaxResults(1)
				.getResultStream()
				.findFirst()
				.map(TreeNodeRecordEntityMapper::toTreeNode);
	}

	@Override
	public List<TreeNodeRecord> findAll() {
		return entityManager
				.createNamedQuery("TreeNode.getAll", TreeNodeRecordEntity.class)
				.getResultStream()
				.map(TreeNodeRecordEntityMapper::toTreeNode)
				.toList();
	}

	@Override
	public Optional<TreeNodeRecord> getTreeNodeRecordWithStatusAndEarliestNextVisit(TreeNodeStatus treeNodeStatus) {
		return entityManager
				.createNamedQuery("TreeNode.getByStatusAndDate", TreeNodeRecordEntity.class)
				.setParameter("treeNodeStatus", treeNodeStatus.name())
				.setMaxResults(1)
				.getResultStream()
				.findFirst()
				.map(TreeNodeRecordEntityMapper::toTreeNode);
	}

	@Override
	public boolean existsByIdAndStatus(String treeNodeId, TreeNodeStatus treeNodeStatus) {
		return entityManager
				.createNamedQuery("TreeNode.getByIdAndStatus", TreeNodeRecordEntity.class)
				.setParameter("id", treeNodeId)
				.setParameter("treeNodeStatus", treeNodeStatus.name())
				.setMaxResults(1)
				.getResultStream()
				.findFirst()
				.isPresent();
	}

	@Override
	public void destroyState() {
		if (entityManager.isOpen()) {
			entityManager.close();
		}
	}

	@Override
	public boolean containsTreeNodeRecords() {
		return entityManager
				.createNamedQuery("TreeNode.getAll", TreeNodeRecordEntity.class)
				.setMaxResults(1)
				.getResultStream()
				.findFirst()
				.isPresent();
	}

	@Override
	public void resetContext() {
		entityManager.clear();
	}

}
