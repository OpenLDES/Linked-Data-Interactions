package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Emits one finite synchronization run in ascending LDES order.
 * <p>
 * Ordering follows the LDES chronological tuple: {@code ldes:timestampPath}
 * first when present, then {@code ldes:sequencePath} when present.
 */
public class OrderedMemberSupplier implements MemberSupplier {
	private final MemberSupplier delegate;
	private final MemberOrdering ordering;
	private Iterator<MemberOrdering.OrderedMember> orderedMembers;

	public OrderedMemberSupplier(MemberSupplier delegate, List<String> sequencePath, String transactionFinalizedPath) {
		this(
				delegate,
				Optional.empty(),
				sequencePath == null || sequencePath.isEmpty()
						? Optional.empty()
						: Optional.of(MemberOrdering.pathFromUris(sequencePath)),
				Optional.empty(),
				Optional.ofNullable(transactionFinalizedPath).map(ResourceFactory::createProperty),
				Optional.empty());
	}

	public OrderedMemberSupplier(
			MemberSupplier delegate,
			Optional<RDFNode> timestampPath,
			Optional<RDFNode> sequencePath,
			Optional<RDFNode> transactionPath,
			Optional<RDFNode> transactionFinalizedPath,
			Optional<RDFNode> transactionFinalizedObject) {
		this.delegate = delegate;
		this.ordering = new MemberOrdering(
				timestampPath,
				sequencePath,
				transactionPath,
				transactionFinalizedPath,
				transactionFinalizedObject);
	}

	@Override
	public SuppliedMember get() {
		if (orderedMembers == null) {
			orderedMembers = drainAndOrder();
		}
		if (!orderedMembers.hasNext()) {
			throw new EndOfLdesException("No ordered members left to emit.");
		}
		return orderedMembers.next().member();
	}

	@Override
	public void destroyState() {
		delegate.destroyState();
	}

	@Override
	public void init() {
		delegate.init();
	}

	private Iterator<MemberOrdering.OrderedMember> drainAndOrder() {
		final List<MemberOrdering.OrderedMember> members = new ArrayList<>();
		try {
			while (true) {
				members.add(ordering.order(delegate.get()));
			}
		} catch (EndOfLdesException completed) {
			return members.stream().sorted().iterator();
		}
	}
}
