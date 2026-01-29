package org.openldes.ldi.repositories.inmemory;

import org.openldes.ldi.entities.HashedStateMember;
import org.openldes.ldi.repositories.HashedStateMemberRepository;

import java.util.HashMap;
import java.util.Map;

public class InMemoryHashedStateMemberRepository implements HashedStateMemberRepository {
	private final Map<String, HashedStateMember> members;

	public InMemoryHashedStateMemberRepository() {
		members = new HashMap<>();
	}

	@Override
	public boolean containsHashedStateMember(HashedStateMember hashedStateMember) {
		final HashedStateMember member = members.get(hashedStateMember.memberId());
		return member != null && member.memberHash().equals(hashedStateMember.memberHash());
	}

	@Override
	public void saveHashedStateMember(HashedStateMember hashedStateMember) {
		members.put(hashedStateMember.memberId(), hashedStateMember);
	}

	@Override
	public void close() {
		members.clear();
	}
}
