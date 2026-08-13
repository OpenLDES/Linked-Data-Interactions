package ldes.client.treenodesupplier;

import ldes.client.treenodesupplier.domain.valueobject.EndOfLdesException;
import ldes.client.treenodesupplier.domain.valueobject.LdesMetaData;
import ldes.client.treenodesupplier.membersuppliers.StreamingOrderedMemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.StreamingOrderedMemberSupplier.OrderingConfiguration;
import ldes.client.treenodesupplier.repository.inmemory.InMemoryMemberIdRepository;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.services.SingleUseResponseRegistry;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.Response;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamingOrderedMemberSupplierTest {
	private static final String EX = "http://example.org/";
	private static final String ROOT = EX + "root";
	private static final String LATER = EX + "later";

	@Test
	void reusesTheRootResponseCapturedDuringEventStreamDiscovery() {
		final String rootBody = """
				@prefix tree: <https://w3id.org/tree#> .
				@prefix ex: <http://example.org/> .

				ex:collection tree:view <%s> ;
					tree:member ex:one .

				ex:one ex:sequence 1 .
				""".formatted(ROOT);
		final LdesMetaData metadata = new LdesMetaData(List.of(ROOT), Lang.TURTLE);
		final InMemoryRequestExecutor requestExecutor = new InMemoryRequestExecutor(Map.of());
		final Request rootRequest = metadata.createRequest(ROOT).createRequest();
		SingleUseResponseRegistry.capture(
				requestExecutor,
				ROOT,
				new Response(
						rootRequest,
						List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")),
						HttpStatus.SC_OK,
						rootBody.getBytes(StandardCharsets.UTF_8)));
		final StreamingOrderedMemberSupplier supplier = new StreamingOrderedMemberSupplier(
				metadata,
				requestExecutor,
				new InMemoryMemberIdRepository(),
				true,
				ordering(Optional.empty(), Optional.of(createProperty(EX + "sequence"))));

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(requestExecutor.requestedUrls()).isEmpty();
	}

	@Test
	void emitsBeforeFetchingBoundedLaterFrontierWhenSafe() {
		final InMemoryRequestExecutor requestExecutor = new InMemoryRequestExecutor(Map.of(
				ROOT, """
						@prefix tree: <https://w3id.org/tree#> .
						@prefix ex: <http://example.org/> .

						ex:collection tree:view <%s> ;
							tree:member ex:one .

						ex:one ex:sequence 1 .

						<%s> tree:relation [
							a tree:GreaterThanRelation ;
							tree:path ex:sequence ;
							tree:value 1 ;
							tree:node <%s>
						] .
						""".formatted(ROOT, ROOT, LATER),
				LATER, """
						@prefix tree: <https://w3id.org/tree#> .
						@prefix ex: <http://example.org/> .

						ex:collection tree:view <%s> ;
							tree:member ex:two .

						ex:two ex:sequence 2 .
						""".formatted(LATER)));
		final StreamingOrderedMemberSupplier supplier = new StreamingOrderedMemberSupplier(
				new LdesMetaData(List.of(ROOT), Lang.TURTLE),
				requestExecutor,
				new InMemoryMemberIdRepository(),
				true,
				ordering(Optional.empty(), Optional.of(createProperty(EX + "sequence"))));

		assertThat(supplier.get().getId()).isEqualTo(EX + "one");
		assertThat(requestExecutor.requestedUrls()).containsExactly(ROOT);

		assertThat(supplier.get().getId()).isEqualTo(EX + "two");
		assertThat(requestExecutor.requestedUrls()).containsExactly(ROOT, LATER);
		assertThatThrownBy(supplier::get).isInstanceOf(EndOfLdesException.class);
	}

	@Test
	void doesNotUseSecondaryPathBoundsForEarlyEmission() {
		final InMemoryRequestExecutor requestExecutor = new InMemoryRequestExecutor(Map.of(
				ROOT, """
						@prefix tree: <https://w3id.org/tree#> .
						@prefix ex: <http://example.org/> .
						@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

						ex:collection tree:view <%s> ;
							tree:member ex:late .

						ex:late ex:time "2026-01-02T00:00:00Z"^^xsd:dateTime ;
							ex:sequence 1 .

						<%s> tree:relation [
							a tree:GreaterThanRelation ;
							tree:path ex:sequence ;
							tree:value 1 ;
							tree:node <%s>
						] .
						""".formatted(ROOT, ROOT, LATER),
				LATER, """
						@prefix tree: <https://w3id.org/tree#> .
						@prefix ex: <http://example.org/> .
						@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

						ex:collection tree:view <%s> ;
							tree:member ex:early .

						ex:early ex:time "2026-01-01T00:00:00Z"^^xsd:dateTime ;
							ex:sequence 2 .
						""".formatted(LATER)));
		final StreamingOrderedMemberSupplier supplier = new StreamingOrderedMemberSupplier(
				new LdesMetaData(List.of(ROOT), Lang.TURTLE),
				requestExecutor,
				new InMemoryMemberIdRepository(),
				true,
				ordering(
						Optional.of(createProperty(EX + "time")),
						Optional.of(createProperty(EX + "sequence"))));

		assertThat(supplier.get().getId()).isEqualTo(EX + "early");
		assertThat(requestExecutor.requestedUrls()).containsExactly(ROOT, LATER);
		assertThat(supplier.get().getId()).isEqualTo(EX + "late");
	}

	@Test
	void comparesTypedStringsLexicallyEvenWhenTheyLookNumeric() {
		final InMemoryRequestExecutor requestExecutor = new InMemoryRequestExecutor(Map.of(
				ROOT, """
						@prefix tree: <https://w3id.org/tree#> .
						@prefix ex: <http://example.org/> .
						@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

						ex:collection tree:view <%s> ;
							tree:member ex:a-two, ex:z-ten .

						ex:a-two ex:sequence "2"^^xsd:string .
						ex:z-ten ex:sequence "10"^^xsd:string .
						""".formatted(ROOT)));
		final StreamingOrderedMemberSupplier supplier = new StreamingOrderedMemberSupplier(
				new LdesMetaData(List.of(ROOT), Lang.TURTLE),
				requestExecutor,
				new InMemoryMemberIdRepository(),
				true,
				ordering(Optional.empty(), Optional.of(createProperty(EX + "sequence"))));

		assertThat(supplier.get().getId()).isEqualTo(EX + "z-ten");
		assertThat(supplier.get().getId()).isEqualTo(EX + "a-two");
	}

	private static OrderingConfiguration ordering(
			Optional<org.apache.jena.rdf.model.RDFNode> timestampPath,
			Optional<org.apache.jena.rdf.model.RDFNode> sequencePath) {
		return new OrderingConfiguration(
				ROOT,
				timestampPath,
				sequencePath,
				Optional.empty(),
				Optional.empty(),
				Optional.empty());
	}

	private static final class InMemoryRequestExecutor implements RequestExecutor {
		private final Map<String, String> bodies;
		private final List<String> requestedUrls = new ArrayList<>();

		private InMemoryRequestExecutor(Map<String, String> bodies) {
			this.bodies = new LinkedHashMap<>(bodies);
		}

		@Override
		public Response execute(Request request) {
			requestedUrls.add(request.getUrl());
			final String body = bodies.get(request.getUrl());
			if (body == null) {
				return new Response(request, List.of(), HttpStatus.SC_NOT_FOUND, (byte[]) null);
			}
			return new Response(
					request,
					List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")),
					HttpStatus.SC_OK,
					body.getBytes(StandardCharsets.UTF_8));
		}

		private List<String> requestedUrls() {
			return List.copyOf(requestedUrls);
		}
	}
}
