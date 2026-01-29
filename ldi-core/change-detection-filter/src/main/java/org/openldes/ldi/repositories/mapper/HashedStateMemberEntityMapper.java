package org.openldes.ldi.repositories.mapper;

import org.openldes.ldi.entities.HashedStateMember;
import org.openldes.ldi.entities.HashedStateMemberEntity;

public class HashedStateMemberEntityMapper {
	private HashedStateMemberEntityMapper() {
	}

	/**
	 * Maps a HashedStateMember from a domain object to an entity object
	 *
	 * @param hashedStateMember the domain object
	 * @return the entity object
	 */
	public static HashedStateMemberEntity fromHashedStateMember(HashedStateMember hashedStateMember) {
		return new HashedStateMemberEntity(hashedStateMember.memberId(), hashedStateMember.memberHash());
	}
}
