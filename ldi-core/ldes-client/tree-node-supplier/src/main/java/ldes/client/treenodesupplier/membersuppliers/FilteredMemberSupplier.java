package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.filters.MemberFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This is a decorator for the {@link MemberSupplier} which makes it possible filter out some members before
 * supplying them
 */
public class FilteredMemberSupplier extends MemberSupplierDecorator {
	private static final Logger log = LoggerFactory.getLogger(FilteredMemberSupplier.class);
	private final MemberFilter filter;
	private final AtomicBoolean destroyed = new AtomicBoolean(false);
	private final Thread shutdownHook;

	public FilteredMemberSupplier(MemberSupplier memberSupplier, MemberFilter filter) {
		super(memberSupplier);
		this.filter = filter;
		this.shutdownHook = new Thread(this::destroyState);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	/**
	 * Extended method that will return the first member that gets through the provided filter. If it gets through it,
	 * then the member will first be saved before it is returned
	 *
	 * @return the first member that gets through the filter
	 */
	@Override
	public SuppliedMember get() {
		SuppliedMember member = super.get();
		while (!filter.saveMemberIfAllowed(member)) {
			log.debug("Member {} has been ignored by the {}", member.getId(), filter.getClass().getSimpleName());
			member = super.get();
		}
		return member;
	}

	@Override
	public void destroyState() {
		if (!destroyed.compareAndSet(false, true)) {
			return;
		}
		removeShutdownHook();
		super.destroyState();
		filter.destroyState();
	}

	private void removeShutdownHook() {
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException ignored) {
			// JVM shutdown is already in progress.
		}
	}
}
