package ldes.client.treenodesupplier.repository;

import ldes.client.treenodesupplier.domain.entities.MemberRecord;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * * Repository that keeps track of the processed members
 */
public interface MemberRepository {

	/**
	 * @return the first MemberRecord in the repository
	 */
	Optional<MemberRecord> getTreeMember();

	/**
	 * @param member MemberRecord to delete from the repository
	 */
	void deleteMember(MemberRecord member);

	/**
	 * @param treeMemberStream the stream of MemberRecords to save
	 */
	void saveTreeMembers(Stream<MemberRecord> treeMemberStream);

	/**
	 * Clean up the repository when it is not used anymore
	 */
	void destroyState();

	/**
	 * Makes the record of which members have already been supplied durable,
	 * without discarding the state.
	 * <p>
	 * An implementation may batch the removal of supplied members instead of
	 * removing each one on its own. Calling this before shutting down keeps a
	 * later run from supplying those members a second time; ending the process
	 * without it leaves them to be supplied again.
	 */
	default void flush() {
		// Implementations that remove every supplied member immediately have
		// nothing outstanding to make durable.
	}
}
