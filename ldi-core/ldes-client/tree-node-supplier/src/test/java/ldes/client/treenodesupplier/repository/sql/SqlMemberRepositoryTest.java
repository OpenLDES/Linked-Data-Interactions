package ldes.client.treenodesupplier.repository.sql;

import ldes.client.treenodesupplier.domain.entities.MemberRecord;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.HibernateUtil;
import org.openldes.ldi.sqlite.SqliteProperties;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SqlMemberRepositoryTest {
	private static final LocalDateTime FIRST_CREATION = LocalDateTime.of(2024, 2, 7, 0, 0, 0);
	private static final int BATCH_SIZE = 3;

	private EntityManager entityManager;
	private SqlMemberRepository memberRepository;

	@BeforeEach
	void setUp() {
		final SqliteProperties sqliteProperties =
				new SqliteProperties("target", UUID.randomUUID().toString(), false);
		entityManager = HibernateUtil.createEntityManagerFromProperties(sqliteProperties.getProperties());
		memberRepository = new SqlMemberRepository(entityManager, BATCH_SIZE);
	}

	@AfterEach
	void tearDown() {
		if (entityManager.isOpen()) {
			entityManager.close();
		}
	}

	@Test
	void when_BatchSizeIsBelowOne_Then_TheRepositoryIsRejected() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new SqlMemberRepository(entityManager, 0))
				.withMessageContaining("at least one member");
	}

	@Test
	void when_TheRepositoryIsEmpty_Then_NoMemberIsSupplied() {
		assertThat(memberRepository.getTreeMember()).isEmpty();
	}

	@Test
	@DisplayName("Members are supplied ordered by creation, not in the order they were saved")
	void when_MembersAreSavedOutOfOrder_Then_TheyAreSuppliedOrderedByCreation() {
		memberRepository.getTreeMember();
		save(member("urn:member:second", 1), member("urn:member:first", 0));

		assertThat(supplyAll()).containsExactly("urn:member:first", "urn:member:second");
	}

	@Test
	@DisplayName("Saved members are supplied without being read back out of the store")
	void when_MembersWereJustSaved_Then_TheyAreSuppliedFromMemory() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1));

		// Reading from the store would parse the member again and so lose the
		// dataset identity that saving it kept.
		final MemberRecord supplied = memberRepository.getTreeMember().orElseThrow();
		assertThat(supplied.getMemberId()).isEqualTo("urn:member:1");
		assertThat(supplied.getDataset()).isSameAs(memberRepository.getTreeMember().orElseThrow().getDataset());
	}

	@Test
	void when_AMemberIsDeleted_Then_ItIsNotSuppliedAgain() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1));

		memberRepository.deleteMember(memberRepository.getTreeMember().orElseThrow());

		assertThat(memberRepository.getTreeMember().orElseThrow().getMemberId()).isEqualTo("urn:member:2");
	}

	@Test
	@DisplayName("More members than one batch are supplied, reading the store again as needed")
	void when_MoreMembersThanABatchAreSaved_Then_AllOfThemAreSupplied() {
		// Saving before the first read leaves every member in the store only, so
		// the batched reads are what supplies them.
		save(member("urn:member:1", 0), member("urn:member:2", 1), member("urn:member:3", 2),
				member("urn:member:4", 3), member("urn:member:5", 4), member("urn:member:6", 5),
				member("urn:member:7", 6));

		assertThat(supplyAll()).containsExactly("urn:member:1", "urn:member:2", "urn:member:3",
				"urn:member:4", "urn:member:5", "urn:member:6", "urn:member:7");
	}

	@Test
	@DisplayName("A resumed run supplies the members its predecessor had not supplied yet")
	void when_ARepositoryIsRecreated_Then_TheUnsuppliedMembersAreReadFromTheStore() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1));
		memberRepository.deleteMember(memberRepository.getTreeMember().orElseThrow());
		memberRepository.flush();

		final SqlMemberRepository resumed = new SqlMemberRepository(entityManager, BATCH_SIZE);

		assertThat(resumed.getTreeMember().orElseThrow().getMemberId()).isEqualTo("urn:member:2");
	}

	@Test
	@DisplayName("A supplied member whose removal was never flushed is supplied again")
	void when_ARemovalIsNotFlushed_Then_AResumedRunSuppliesThatMemberAgain() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1), member("urn:member:3", 2),
				member("urn:member:4", 3));
		// Deleting one of four members neither fills the batch nor empties the
		// view, so the removal is still only held in memory.
		memberRepository.deleteMember(memberRepository.getTreeMember().orElseThrow());

		final SqlMemberRepository resumed = new SqlMemberRepository(entityManager, BATCH_SIZE);

		assertThat(resumed.getTreeMember().orElseThrow().getMemberId()).isEqualTo("urn:member:1");
	}

	@Test
	@DisplayName("Removals are flushed once a batch is full, without waiting for a flush")
	void when_ABatchOfMembersIsSupplied_Then_TheirRemovalIsMadeDurable() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1), member("urn:member:3", 2),
				member("urn:member:4", 3));
		for (int supplied = 0; supplied < BATCH_SIZE; supplied++) {
			memberRepository.deleteMember(memberRepository.getTreeMember().orElseThrow());
		}

		final SqlMemberRepository resumed = new SqlMemberRepository(entityManager, BATCH_SIZE);

		assertThat(resumed.getTreeMember().orElseThrow().getMemberId()).isEqualTo("urn:member:4");
	}

	@Test
	@DisplayName("Destroying the state drops the in-memory view rather than keeping a stale one")
	void when_TheStateIsDestroyed_Then_TheStoreIsReadAgain() {
		memberRepository.getTreeMember();
		save(member("urn:member:1", 0), member("urn:member:2", 1));
		memberRepository.deleteMember(memberRepository.getTreeMember().orElseThrow());

		memberRepository.destroyState();

		// Destroying the state discards both the view and the removals it still
		// held, so what the store has is what gets supplied. The rows themselves
		// go when the store is dropped, which is the caller's decision.
		assertThat(supplyAll()).containsExactly("urn:member:1", "urn:member:2");
	}

	private List<String> supplyAll() {
		final List<String> suppliedMemberIds = new ArrayList<>();
		for (Optional<MemberRecord> member = memberRepository.getTreeMember();
		     member.isPresent();
		     member = memberRepository.getTreeMember()) {
			suppliedMemberIds.add(member.get().getMemberId());
			memberRepository.deleteMember(member.get());
		}
		return suppliedMemberIds;
	}

	private void save(MemberRecord... members) {
		memberRepository.saveTreeMembers(Stream.of(members));
	}

	private static MemberRecord member(String memberId, int creationOffsetInSeconds) {
		return new MemberRecord(memberId, ModelFactory.createDefaultModel(),
				FIRST_CREATION.plusSeconds(creationOffsetInSeconds));
	}
}
