package ldes.client.treenodesupplier;

import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.SuppliedMember;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.OrderedMemberSupplier;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.ResourceFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderedMemberSupplierTest {
	private static final String EX = "http://example.org/";

	@Test
	void emitsMembersBySequenceWithTransactionFinalizerLastForEqualValues() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						member("three", 3, false),
						member("two-final", 2, true),
						member("two", 2, false),
						member("one", 1, false)),
				List.of(EX + "sequence"),
				EX + "done");

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(supplier.get().getId()).isEqualTo(EX + "two");
		assertThat(supplier.get().getId()).isEqualTo(EX + "two-final");
		assertThat(supplier.get().getId()).isEqualTo(EX + "three");
		assertThatThrownBy(supplier::get).isInstanceOf(EndOfLdesException.class);
	}

	@Test
	void supportsSequencePathsAcrossBlankNodes() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithNestedSequence("two", 2),
						memberWithNestedSequence("one", 1)),
				List.of(EX + "metadata", EX + "sequence"),
				null);

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(supplier.get().getId()).isEqualTo(EX + "two");
	}

	@Test
	void ordersByTimestampWhenNoSequencePathIsPresent() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithTimestamp("late", "2026-01-01T00:00:03Z"),
						memberWithTimestamp("early", "2026-01-01T00:00:01Z")),
				Optional.of(property(EX + "time")),
				Optional.empty(),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		assertThat(supplier.get().getId()).isEqualTo(EX + "early");
		assertThat(supplier.get().getId()).isEqualTo(EX + "late");
	}

	@Test
	void ordersBySequenceWithinEqualTimestamps() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithTimestampAndSequence("second", "2026-01-01T00:00:01Z", 2),
						memberWithTimestampAndSequence("first", "2026-01-01T00:00:01Z", 1)),
				Optional.of(property(EX + "time")),
				Optional.of(property(EX + "sequence")),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		assertThat(supplier.get().getId()).isEqualTo(EX + "first");
		assertThat(supplier.get().getId()).isEqualTo(EX + "second");
	}

	@Test
	void comparesTypedStringsLexicallyEvenWhenTheyLookNumeric() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithStringSequence("a-two", "2"),
						memberWithStringSequence("z-ten", "10")),
				Optional.empty(),
				Optional.of(property(EX + "sequence")),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		assertThat(supplier.get().getId()).isEqualTo(EX + "z-ten");
		assertThat(supplier.get().getId()).isEqualTo(EX + "a-two");
	}


	@Test
	void supportsAlternativePaths() {
		final var pathModel = ModelFactory.createDefaultModel();
		final var path = pathModel.createResource();
		path.addProperty(
				pathModel.createProperty("http://www.w3.org/ns/shacl#alternativePath"),
				pathModel.createList(new RDFNode[]{
						pathModel.createProperty(EX + "primarySequence"),
						pathModel.createProperty(EX + "fallbackSequence")
				}));
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithProperty("two", EX + "primarySequence", 2),
						memberWithProperty("one", EX + "fallbackSequence", 1)),
				Optional.empty(),
				Optional.of(path),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(supplier.get().getId()).isEqualTo(EX + "two");
	}

	@Test
	void supportsInversePaths() {
		final var pathModel = ModelFactory.createDefaultModel();
		final var path = pathModel.createResource()
				.addProperty(
						pathModel.createProperty("http://www.w3.org/ns/shacl#inversePath"),
						pathModel.createProperty(EX + "member"));
		final var model = ModelFactory.createDefaultModel();
		final var one = model.createResource(EX + "one");
		final var two = model.createResource(EX + "two");
		model.createResource(EX + "rank/2")
				.addProperty(model.createProperty(EX + "member"), two)
				.addLiteral(model.createProperty(EX + "sequence"), 2);
		model.createResource(EX + "rank/1")
				.addProperty(model.createProperty(EX + "member"), one)
				.addLiteral(model.createProperty(EX + "sequence"), 1);

		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						new SuppliedMember(two.getURI(), model),
						new SuppliedMember(one.getURI(), model)),
				Optional.empty(),
				Optional.of(pathModel.createList(new RDFNode[]{path, pathModel.createProperty(EX + "sequence")})),
				Optional.empty(),
				Optional.empty(),
				Optional.empty());

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(supplier.get().getId()).isEqualTo(EX + "two");
	}

	@Test
	void supportsTransactionFinalizedObject() {
		final MemberSupplier supplier = new OrderedMemberSupplier(
				new FiniteMemberSupplier(
						memberWithTransaction("final", "txn-1", "closed"),
						memberWithTransaction("open", "txn-1", "open")),
				Optional.empty(),
				Optional.of(property(EX + "sequence")),
				Optional.of(property(EX + "transaction")),
				Optional.of(property(EX + "state")),
				Optional.of(ResourceFactory.createPlainLiteral("closed")));

		assertThat(supplier.get().getId()).isEqualTo(EX + "open");
		assertThat(supplier.get().getId()).isEqualTo(EX + "final");
	}

	private static SuppliedMember member(String localName, int sequence, boolean done) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(EX + "sequence"), sequence);
		if (done) {
			subject.addLiteral(model.createProperty(EX + "done"), true);
		}
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithNestedSequence(String localName, int sequence) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		final var metadata = model.createResource();
		subject.addProperty(model.createProperty(EX + "metadata"), metadata);
		metadata.addLiteral(model.createProperty(EX + "sequence"), sequence);
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithTimestamp(String localName, String timestamp) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(EX + "time"), ResourceFactory.createTypedLiteral(timestamp, XSDDatatype.XSDdateTime));
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithTimestampAndSequence(String localName, String timestamp, int sequence) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(EX + "time"), ResourceFactory.createTypedLiteral(timestamp, XSDDatatype.XSDdateTime));
		subject.addLiteral(model.createProperty(EX + "sequence"), sequence);
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithProperty(String localName, String property, int value) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(property), value);
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithStringSequence(String localName, String value) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(EX + "sequence"), ResourceFactory.createTypedLiteral(value, XSDDatatype.XSDstring));
		return new SuppliedMember(subject.getURI(), model);
	}

	private static SuppliedMember memberWithTransaction(String localName, String transaction, String state) {
		final var model = ModelFactory.createDefaultModel();
		final var subject = model.createResource(EX + localName);
		subject.addLiteral(model.createProperty(EX + "sequence"), 1);
		subject.addLiteral(model.createProperty(EX + "transaction"), transaction);
		subject.addLiteral(model.createProperty(EX + "state"), state);
		return new SuppliedMember(subject.getURI(), model);
	}

	private static RDFNode property(String uri) {
		return ResourceFactory.createProperty(uri);
	}

	private static final class FiniteMemberSupplier implements MemberSupplier {
		private final Queue<SuppliedMember> members;

		private FiniteMemberSupplier(SuppliedMember... members) {
			this.members = new ArrayDeque<>(List.of(members));
		}

		@Override
		public SuppliedMember get() {
			final SuppliedMember member = members.poll();
			if (member == null) {
				throw new EndOfLdesException("complete");
			}
			return member;
		}

		@Override
		public void init() {
			// Do nothing
		}

		@Override
		public void destroyState() {
			// Do nothing
		}
	}
}
