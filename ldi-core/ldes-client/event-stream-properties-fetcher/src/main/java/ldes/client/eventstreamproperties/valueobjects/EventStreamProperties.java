package ldes.client.eventstreamproperties.valueobjects;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;

import java.util.List;

public final class EventStreamProperties {
	private final String uri;
	private final String rootNode;
	private final String versionOfPath;
	private final String timestampPath;
	private final String versionTimestampPath;
	private final String versionSequencePath;
	private final Integer pollingInterval;
	private final List<String> shaclShapeUris;
	private final List<String> viewDescriptions;
	private final List<String> retentionPolicies;
	private final Dataset contextDataset;

	public EventStreamProperties(String uri) {
		this(uri, null, null, null);
	}

	public EventStreamProperties(String uri, String versionOfPath, String timestampPath, String shaclShapeUri) {
		this(
				uri,
				null,
				versionOfPath,
				timestampPath,
				null,
				null,
				null,
				shaclShapeUri == null || shaclShapeUri.isBlank() ? List.of() : List.of(shaclShapeUri),
				List.of(),
				List.of(),
				DatasetFactory.create());
	}

	public EventStreamProperties(
			String uri,
			String rootNode,
			String versionOfPath,
			String timestampPath,
			String versionTimestampPath,
			String versionSequencePath,
			Integer pollingInterval,
			List<String> shaclShapeUris,
			List<String> viewDescriptions,
			List<String> retentionPolicies,
			Dataset contextDataset) {
		this.uri = uri;
		this.rootNode = rootNode;
		this.versionOfPath = versionOfPath;
		this.timestampPath = timestampPath;
		this.versionTimestampPath = versionTimestampPath;
		this.versionSequencePath = versionSequencePath;
		this.pollingInterval = pollingInterval;
		this.shaclShapeUris = List.copyOf(shaclShapeUris);
		this.viewDescriptions = List.copyOf(viewDescriptions);
		this.retentionPolicies = List.copyOf(retentionPolicies);
		this.contextDataset = contextDataset;
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
}
