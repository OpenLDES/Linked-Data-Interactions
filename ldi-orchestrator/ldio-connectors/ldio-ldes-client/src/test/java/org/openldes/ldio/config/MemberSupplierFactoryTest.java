package org.openldes.ldio.config;

import org.openldes.ldio.LdioLdesClientProperties;
import org.openldes.ldio.management.status.ClientStatusConsumer;
import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.exception.ConfigPropertyMissingException;
import ldes.client.eventstreamproperties.EventStreamPropertiesFetcher;
import ldes.client.eventstreamproperties.valueobjects.EventStreamProperties;
import ldes.client.treenodesupplier.filters.LatestStateFilter;
import ldes.client.treenodesupplier.membersuppliers.FilteredMemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplierImpl;
import ldes.client.treenodesupplier.membersuppliers.StreamingOrderedMemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.VersionMaterialisedMemberSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.openldes.ldio.LdioLdesClientPropertyKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSupplierFactoryTest {

	private Map<String, String> defaultInputConfig;
	@Mock
	private ClientStatusConsumer statusConsumer;
	@Mock
	private EventStreamPropertiesFetcher fetcher;
	private LdioLdesClientProperties ldioLdesClientProperties;
	private EventStreamProperties eventStreamProperties;

	@BeforeEach
	void setUp() {
		defaultInputConfig = new HashMap<>();
		defaultInputConfig.put(URLS, "http://example.org");
		eventStreamProperties = new EventStreamProperties("http://localhost:8080/collection", "versionOf", "timestamp", "shaclUri");
	}

	@Test
	void when_VersionMaterialisationIsEnabled_then_VersionMaterialisedMemberSupplierIsReturned() {
		defaultInputConfig.put(USE_VERSION_MATERIALISATION, "true");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier).isInstanceOf(VersionMaterialisedMemberSupplier.class);
	}

	@Test
	void when_VersionMaterialisationAndOnlyOnceFilterAreNotEnabled_then_MemberSupplierImplIsReturned() {
		defaultInputConfig.put(USE_EXACTLY_ONCE_FILTER, "false");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier).isInstanceOf(MemberSupplierImpl.class);
	}

	@Test
	void when_OrderedTraversalIsEnabled_then_StreamingOrderedMemberSupplierIsReturned() {
		defaultInputConfig.put(ORDERED, "true");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier).isInstanceOf(StreamingOrderedMemberSupplier.class);
	}

	@Test
	void when_OrderedTraversalIsEnabledWithVersionMaterialisation_then_VersionMaterialisedStreamingSupplierIsReturned() {
		defaultInputConfig.put(ORDERED, "true");
		defaultInputConfig.put(USE_VERSION_MATERIALISATION, "true");
		defaultInputConfig.put(USE_LATEST_STATE_FILTER, "false");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier)
				.isInstanceOf(VersionMaterialisedMemberSupplier.class)
				.extracting("memberSupplier").isInstanceOf(StreamingOrderedMemberSupplier.class);
	}

	@Test
	void when_VersionMaterialisationIsNotEnabled_then_OnlyOnceMemberSupplierIsReturned() {
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier).isInstanceOf(FilteredMemberSupplier.class);
	}

	@Test
	void when_LatestStateFilterIsEnabled_then_returnVersionMaterialisedMemberSupplierWithLatestStateFilter() {
		defaultInputConfig.put(USE_VERSION_MATERIALISATION, "true");
		defaultInputConfig.put(USE_LATEST_STATE_FILTER, "true");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier)
				.isInstanceOf(VersionMaterialisedMemberSupplier.class)
				.extracting("memberSupplier").isInstanceOf(FilteredMemberSupplier.class)
				.extracting("filter").isInstanceOf(LatestStateFilter.class);
	}

	@Test
	void when_LatestStateFilterIsDisabled_then_returnVersionMaterialisedMemberSupplierImpl() {
		defaultInputConfig.put(USE_VERSION_MATERIALISATION, "true");
		defaultInputConfig.put(USE_LATEST_STATE_FILTER, "false");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier)
				.isInstanceOf(VersionMaterialisedMemberSupplier.class)
				.extracting("memberSupplier").isInstanceOf(MemberSupplierImpl.class);
	}

	@Test
	void when_LatestStateFilterIsEnabledWithoutMaterialisation_then_returnMemberSupplierImpl() {
		defaultInputConfig.put(USE_VERSION_MATERIALISATION, "false");
		defaultInputConfig.put(USE_EXACTLY_ONCE_FILTER, "false");
		defaultInputConfig.put(USE_LATEST_STATE_FILTER, "true");
		final var componentProperties = new ComponentProperties("pipelineName", "cName", defaultInputConfig);
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);
		when(fetcher.fetchEventStreamProperties(any())).thenReturn(eventStreamProperties);

		MemberSupplier memberSupplier = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer).getMemberSupplier();

		assertThat(memberSupplier).isInstanceOf(MemberSupplierImpl.class);
	}

	@Test
	void when_NoUrlsAreConfigured_then_ThrowException() {
		final String expectedErrorMessage = "Pipeline \"pipelineName\": \"cName\" : Missing value for property \"urls\" .";
		final var componentProperties = new ComponentProperties("pipelineName", "cName", Map.of("url", "http://localhost:8080/ldes"));
		ldioLdesClientProperties = LdioLdesClientProperties.fromComponentProperties(componentProperties);

		MemberSupplierFactory memberSupplierFactory = new MemberSupplierFactory(ldioLdesClientProperties, fetcher, null, statusConsumer);
		assertThatThrownBy(memberSupplierFactory::getMemberSupplier)
				.isInstanceOf(ConfigPropertyMissingException.class)
				.hasMessage(expectedErrorMessage);
	}
}
