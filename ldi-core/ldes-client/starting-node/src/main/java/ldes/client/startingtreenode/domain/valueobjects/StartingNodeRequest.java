package ldes.client.startingtreenode.domain.valueobjects;

import org.apache.jena.riot.Lang;

import static org.openldes.ldi.rdf.parser.RdfResponseParser.ACCEPT_HEADER;

/**
 * Contains the endpoint to connect to the server. This can be a collection,
 * view or subset.
 */
public class StartingNodeRequest {

	private final String url;
	private final Lang lang;
	private final RedirectHistory redirectHistory;

	public StartingNodeRequest(String url, Lang lang, RedirectHistory redirectHistory) {
		this.url = url;
		this.lang = lang;
		this.redirectHistory = redirectHistory;
	}

	public String acceptHeader() {
		return ACCEPT_HEADER;
	}

	public String url() {
		return url;
	}

	public Lang lang() {
		return lang;
	}

	public StartingNodeRequest createRedirectedEndpoint(final String location) {
		RedirectHistory updatedRedirectHistory = redirectHistory.addStartingNodeRequest(this);
		return new StartingNodeRequest(location, lang, updatedRedirectHistory);
	}
}
