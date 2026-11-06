package ldes.client.treenodesupplier.membersuppliers;

import ldes.client.treenodesupplier.TreeNodeProcessor;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base implementation of the {@link MemberSupplier}
 */
public class MemberSupplierImpl implements MemberSupplier {

	private final TreeNodeProcessor treeNodeProcessor;
	private final boolean keepState;
	private final AtomicBoolean destroyed = new AtomicBoolean(false);
	private final Thread shutdownHook;

	public MemberSupplierImpl(TreeNodeProcessor treeNodeProcessor, boolean keepState) {
		this.treeNodeProcessor = treeNodeProcessor;
		this.keepState = keepState;
		this.shutdownHook = new Thread(this::destroyState);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	/**
	 * Base implementation that returns the fetched TreeNode member
	 *
	 * @return The plain fetched TreeNode member
	 */
	@Override
	public SuppliedMember get() {
		return treeNodeProcessor.getMember();
	}

	@Override
	public void destroyState() {
		if (!destroyed.compareAndSet(false, true)) {
			return;
		}
		removeShutdownHook();
		if (!keepState && treeNodeProcessor != null) {
			treeNodeProcessor.destroyState();
		}
	}

	private void removeShutdownHook() {
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException ignored) {
			// JVM shutdown is already in progress.
		}
	}

	@Override
	public void init() {
		treeNodeProcessor.init();
	}
}
