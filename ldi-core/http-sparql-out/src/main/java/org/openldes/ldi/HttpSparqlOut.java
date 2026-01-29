package org.openldes.ldi;

import org.openldes.ldi.exceptions.WriteActionFailedException;
import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.valueobjects.PostRequest;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeader;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.openldes.ldi.skolemisation.Skolemizer;
import org.openldes.ldi.valueobjects.SparqlQuery;
import org.apache.http.HttpHeaders;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HttpSparqlOut {
	private static final Logger log = LoggerFactory.getLogger(HttpSparqlOut.class);
	private final String endpoint;
	private final SparqlQuery sparqlQuery;
	private final Skolemizer skolemizer;
	private final RequestExecutor requestExecutor;

	public HttpSparqlOut(String endpoint, SparqlQuery sparqlQuery, Skolemizer skolemizer, RequestExecutor requestExecutor) {
		this.endpoint = endpoint;
		this.sparqlQuery = sparqlQuery;
		this.skolemizer = skolemizer;
		this.requestExecutor = requestExecutor;
	}

	public void write(Model model) {
		if (model.isEmpty()) {
			return;
		}

		String query = sparqlQuery.getQueryForModel(skolemizer.skolemize(model));

		final PostRequest request = new PostRequest(endpoint, new RequestHeaders(List.of(
				new RequestHeader(HttpHeaders.CONTENT_TYPE, "application/sparql-update"),
				new RequestHeader(HttpHeaders.ACCEPT, "application/json"))), query);
		synchronized (requestExecutor) {
			Response response = requestExecutor.execute(request);
			if (response.isSuccess()) {
				log.debug("{} {} {}", request.getMethod(), request.getUrl(), response.getHttpStatus());
			} else {
				final String message = "Failed to post model. The request url was %s. The http response obtained from the server has code %s and body \"%s\"."
						.formatted(response.getRequestedUrl(), response.getHttpStatus(), response.getBodyAsString().orElse(null));
				throw new WriteActionFailedException(message);
			}
		}
	}
}
