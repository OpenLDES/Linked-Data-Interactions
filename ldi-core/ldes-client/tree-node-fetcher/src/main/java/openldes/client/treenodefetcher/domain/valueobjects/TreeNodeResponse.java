package ldes.client.treenodefetcher.domain.valueobjects;

import ldes.client.treenodefetcher.domain.entities.TreeMember;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;

import java.util.List;
import java.util.Optional;

/**
 * Wrapper around the RDF response that represents a fragment to more easily extract the required information
 * from that response
 */
public class TreeNodeResponse {
	private final List<String> relation;
	private final List<TreeMember> members;
	private final MutabilityStatus mutabilityStatus;
	private final String etag;
	private final Dataset dataset;
	private final String effectiveUrl;

	public TreeNodeResponse(List<String> relations, List<TreeMember> members,
							MutabilityStatus mutabilityStatus) {
		this(relations, members, mutabilityStatus, null);
	}

	public TreeNodeResponse(List<String> relations, List<TreeMember> members,
							MutabilityStatus mutabilityStatus, String etag) {
		this(relations, members, mutabilityStatus, etag, DatasetFactory.create(), null);
	}

	public TreeNodeResponse(List<String> relations, List<TreeMember> members,
							MutabilityStatus mutabilityStatus, String etag,
							Dataset dataset, String effectiveUrl) {
		this.relation = relations;
		this.members = members;
		this.mutabilityStatus = mutabilityStatus;
		this.etag = etag;
		this.dataset = dataset;
		this.effectiveUrl = effectiveUrl;
	}

	public List<String> getRelations() {
		return relation;
	}

	public List<TreeMember> getMembers() {
		return members;
	}

	public MutabilityStatus getMutabilityStatus() {
		return mutabilityStatus;
	}

	public Optional<String> getEtag() {
		return Optional.ofNullable(etag);
	}

	/**
	 * @return the parsed response, so a consumer that needs more than the
	 * relation targets, such as the bounds a relation declares, does not have to
	 * fetch and parse the tree node again. Empty for a response without a body.
	 */
	public Dataset getDataset() {
		return dataset;
	}

	/**
	 * @return the url the tree node was finally served from, after any redirect,
	 * which is the base the response content is relative to
	 */
	public Optional<String> getEffectiveUrl() {
		return Optional.ofNullable(effectiveUrl);
	}
}
