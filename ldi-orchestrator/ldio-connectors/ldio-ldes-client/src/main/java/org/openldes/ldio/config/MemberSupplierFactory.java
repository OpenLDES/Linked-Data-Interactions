package org.openldes.ldio.config;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.timestampextractor.TimestampExtractor;
import org.openldes.ldi.timestampextractor.TimestampFromPathExtractor;
import org.openldes.ldio.LdioLdesClientProperties;
import org.openldes.ldio.config.wrappers.MemberSupplierWrappersBuilder;
import org.openldes.ldio.management.status.ClientStatusConsumer;
import ldes.client.eventstreamproperties.EventStreamPropertiesFetcher;
import ldes.client.eventstreamproperties.valueobjects.EventStreamProperties;
import ldes.client.eventstreamproperties.valueobjects.PropertiesRequest;
import ldes.client.eventstreamproperties.valueobjects.ViewSpecification;
import ldes.client.treenodesupplier.TreeNodeProcessor;
import ldes.client.treenodesupplier.domain.valueobject.LdesClientRepositories;
import ldes.client.treenodesupplier.domain.valueobject.LdesMetaData;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.MemberSupplierImpl;
import ldes.client.treenodesupplier.membersuppliers.StreamingOrderedMemberSupplier;
import ldes.client.treenodesupplier.membersuppliers.StreamingOrderedMemberSupplier.OrderingConfiguration;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;

public class MemberSupplierFactory {
	private static final Logger log = LoggerFactory.getLogger(MemberSupplierFactory.class);
	private final LdioLdesClientProperties clientProperties;
	private final RequestExecutor requestExecutor;
	private final ClientStatusConsumer clientStatusConsumer;
	private final EventStreamPropertiesFetcher eventStreamPropertiesFetcher;

	public MemberSupplierFactory(LdioLdesClientProperties clientProperties,
	                             EventStreamPropertiesFetcher eventStreamPropertiesFetcher,
	                             RequestExecutor requestExecutor,
	                             ClientStatusConsumer clientStatusConsumer ) {
		this.clientProperties = clientProperties;
		this.requestExecutor = requestExecutor;
		this.clientStatusConsumer = clientStatusConsumer;
		this.eventStreamPropertiesFetcher = eventStreamPropertiesFetcher;
	}

	public MemberSupplier getMemberSupplier() {
		log.info("Starting LdesClientRunner run setup");
		final EventStreamProperties eventStreamProperties = eventStreamPropertiesFetcher.fetchEventStreamProperties(new PropertiesRequest(clientProperties.getFirstUrl(), clientProperties.getSourceFormat()));
		final LdesClientRepositories ldesClientRepositories = LdesClientRepositoriesFactory.getLdesClientRepositories(clientProperties.getProperties());
		final LdesMetaData ldesMetaData = new LdesMetaData(clientProperties.getUrls(), clientProperties.getSourceFormat());
		MemberSupplier baseMemberSupplier = clientProperties.isOrderedEnabled()
				? getOrderedMemberSupplier(eventStreamProperties, ldesClientRepositories, ldesMetaData)
				: new MemberSupplierImpl(getTreeNodeProcessor(eventStreamProperties, ldesClientRepositories, ldesMetaData), clientProperties.isKeepStateEnabled());
		baseMemberSupplier = new MemberSupplierWrappersBuilder()
				.withEventStreamProperties(eventStreamProperties)
				.withLdioLdesClientProperties(clientProperties)
				.build()
				.wrapMemberSupplier(baseMemberSupplier);

		log.info("LdesClientRunner setup finished");
		return baseMemberSupplier;
	}

	private MemberSupplier getOrderedMemberSupplier(
			EventStreamProperties eventStreamProperties,
			LdesClientRepositories ldesClientRepositories,
			LdesMetaData ldesMetaData) {
		return new StreamingOrderedMemberSupplier(
				ldesMetaData,
				requestExecutor,
				ldesClientRepositories.memberIdRepository(),
				ldesClientRepositories.treeNodeRecordRepository(),
				clientProperties.isKeepStateEnabled(),
				new OrderingConfiguration(
						eventStreamProperties.getRootNode(),
						path(eventStreamProperties, ViewSpecification.LDES_TIMESTAMP_PATH, Optional.ofNullable(eventStreamProperties.getTimestampPath()).map(MemberSupplierFactory::property)),
						sequencePath(eventStreamProperties),
						path(eventStreamProperties, ViewSpecification.LDES_TRANSACTION_PATH, Optional.ofNullable(eventStreamProperties.getTransactionPath()).map(MemberSupplierFactory::property)),
						path(eventStreamProperties, ViewSpecification.LDES_TRANSACTION_FINALIZED_PATH, Optional.ofNullable(eventStreamProperties.getTransactionFinalizedPath()).map(MemberSupplierFactory::property)),
						Optional.ofNullable(eventStreamProperties.getTransactionFinalizedObject())));
	}

	private TreeNodeProcessor getTreeNodeProcessor(
			EventStreamProperties eventStreamProperties,
			LdesClientRepositories ldesClientRepositories,
			LdesMetaData ldesMetaData) {
		TimestampExtractor timestampExtractor = new TimestampFromPathExtractor(createProperty(eventStreamProperties.getTimestampPath()));
		return new TreeNodeProcessor(ldesMetaData, ldesClientRepositories, requestExecutor, timestampExtractor, clientStatusConsumer);
	}

	private Optional<RDFNode> sequencePath(EventStreamProperties eventStreamProperties) {
		final List<String> sequencePath = eventStreamProperties.getSequencePath();
		final Optional<RDFNode> fallback = switch (sequencePath.size()) {
			case 0 -> Optional.empty();
			case 1 -> Optional.of(property(sequencePath.get(0)));
			default -> {
				final Model model = eventStreamProperties.getContextDataset().getDefaultModel();
				yield Optional.of(model.createList(sequencePath.stream()
						.map(MemberSupplierFactory::property)
						.toArray(RDFNode[]::new)));
			}
		};
		return path(eventStreamProperties, ViewSpecification.LDES_SEQUENCE_PATH, fallback);
	}

	private Optional<RDFNode> path(EventStreamProperties eventStreamProperties, Property property, Optional<RDFNode> fallback) {
		final Model model = eventStreamProperties.getContextDataset().getDefaultModel();
		return Optional.ofNullable(model.createResource(eventStreamProperties.getUri()).getProperty(property))
				.map(statement -> statement.getObject())
				.or(() -> fallback);
	}

	private static RDFNode property(String uri) {
		return createProperty(uri);
	}

}
