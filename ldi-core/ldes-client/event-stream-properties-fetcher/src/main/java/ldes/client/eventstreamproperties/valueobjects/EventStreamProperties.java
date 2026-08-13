package ldes.client.eventstreamproperties.valueobjects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.RDFNode;

import java.util.List;

public final class EventStreamProperties {
	private final String uri;
	private final String rootNode;
	private final String versionOfPath;
	private final String timestampPath;
	private final List<String> sequencePath;
	private final String transactionPath;
	private final String transactionFinalizedPath;
	private final RDFNode transactionFinalizedObject;
	private final String versionTimestampPath;
	private final String versionSequencePath;
	private final Integer pollingInterval;
	private final List<String> shaclShapeUris;
	private final List<String> viewDescriptions;
	private final List<String> retentionPolicies;
	private final Dataset contextDataset;

	public EventStreamProperties(String uri) {
		this(builder(uri));
	}

	public EventStreamProperties(String uri, String versionOfPath, String timestampPath, String shaclShapeUri) {
		this(builder(uri)
				.versionOfPath(versionOfPath)
				.timestampPath(timestampPath)
				.shaclShapeUris(shaclShapeUri == null || shaclShapeUri.isBlank()
						? List.of()
						: List.of(shaclShapeUri)));
	}

	private EventStreamProperties(Builder builder) {
		this.uri = builder.uri;
		this.rootNode = builder.rootNode;
		this.versionOfPath = builder.versionOfPath;
		this.timestampPath = builder.timestampPath;
		this.sequencePath = List.copyOf(builder.sequencePath);
		this.transactionPath = builder.transactionPath;
		this.transactionFinalizedPath = builder.transactionFinalizedPath;
		this.transactionFinalizedObject = builder.transactionFinalizedObject;
		this.versionTimestampPath = builder.versionTimestampPath;
		this.versionSequencePath = builder.versionSequencePath;
		this.pollingInterval = builder.pollingInterval;
		this.shaclShapeUris = List.copyOf(builder.shaclShapeUris);
		this.viewDescriptions = List.copyOf(builder.viewDescriptions);
		this.retentionPolicies = List.copyOf(builder.retentionPolicies);
		this.contextDataset = builder.contextDataset;
	}

	public static Builder builder(String uri) {
		return new Builder(uri);
	}

	public String getUri() {
		return uri;
	}

	public String getRootNode() {
		return rootNode;
	}

	public String getVersionOfPath() {
		return versionOfPath;
	}

	public String getTimestampPath() {
		return timestampPath;
	}

	public List<String> getSequencePath() {
		return sequencePath;
	}

	public String getTransactionPath() {
		return transactionPath;
	}

	public String getTransactionFinalizedPath() {
		return transactionFinalizedPath;
	}

	public RDFNode getTransactionFinalizedObject() {
		return transactionFinalizedObject;
	}

	public String getVersionTimestampPath() {
		return versionTimestampPath;
	}

	public String getVersionSequencePath() {
		return versionSequencePath;
	}

	public Integer getPollingInterval() {
		return pollingInterval;
	}

	public String getShaclShapeUri() {
		return shaclShapeUris.isEmpty() ? "" : shaclShapeUris.get(0);
	}

	public List<String> getShaclShapeUris() {
		return shaclShapeUris;
	}

	public List<String> getViewDescriptions() {
		return viewDescriptions;
	}

	public List<String> getRetentionPolicies() {
		return retentionPolicies;
	}

	public Dataset getContextDataset() {
		return contextDataset;
	}

	public boolean containsRequiredProperties() {
		return timestampPath != null && versionOfPath != null;
	}

	public boolean needsEventStreamFollowUp() {
		return rootNode == null && !containsRequiredProperties();
	}

	public static final class Builder {
		private final String uri;
		private String rootNode;
		private String versionOfPath;
		private String timestampPath;
		private List<String> sequencePath = List.of();
		private String transactionPath;
		private String transactionFinalizedPath;
		private RDFNode transactionFinalizedObject;
		private String versionTimestampPath;
		private String versionSequencePath;
		private Integer pollingInterval;
		private List<String> shaclShapeUris = List.of();
		private List<String> viewDescriptions = List.of();
		private List<String> retentionPolicies = List.of();
		private Dataset contextDataset = DatasetFactory.create();

		private Builder(String uri) {
			this.uri = uri;
		}

		public Builder rootNode(String value) {
			rootNode = value;
			return this;
		}

		public Builder versionOfPath(String value) {
			versionOfPath = value;
			return this;
		}

		public Builder timestampPath(String value) {
			timestampPath = value;
			return this;
		}

		public Builder sequencePath(List<String> value) {
			sequencePath = value;
			return this;
		}

		public Builder transactionPath(String value) {
			transactionPath = value;
			return this;
		}

		public Builder transactionFinalizedPath(String value) {
			transactionFinalizedPath = value;
			return this;
		}

		public Builder transactionFinalizedObject(RDFNode value) {
			transactionFinalizedObject = value;
			return this;
		}

		public Builder versionTimestampPath(String value) {
			versionTimestampPath = value;
			return this;
		}

		public Builder versionSequencePath(String value) {
			versionSequencePath = value;
			return this;
		}

		public Builder pollingInterval(Integer value) {
			pollingInterval = value;
			return this;
		}

		public Builder shaclShapeUris(List<String> value) {
			shaclShapeUris = value;
			return this;
		}

		public Builder viewDescriptions(List<String> value) {
			viewDescriptions = value;
			return this;
		}

		public Builder retentionPolicies(List<String> value) {
			retentionPolicies = value;
			return this;
		}

		public Builder contextDataset(Dataset value) {
			contextDataset = value;
			return this;
		}

		public EventStreamProperties build() {
			return new EventStreamProperties(this);
		}
	}
}
