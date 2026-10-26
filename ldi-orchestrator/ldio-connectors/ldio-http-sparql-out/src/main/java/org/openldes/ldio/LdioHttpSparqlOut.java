package org.openldes.ldio;

import org.openldes.ldi.HttpSparqlOut;
import org.openldes.ldi.exceptions.WriteActionFailedException;
import org.openldes.ldi.types.LdiOutput;
import org.openldes.ldio.exceptions.HttpSparqlOutException;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdioHttpSparqlOut implements LdiOutput {
	public static final String NAME = "Ldio:HttpSparqlOut";
	private static final Logger log = LoggerFactory.getLogger(LdioHttpSparqlOut.class);
	private final HttpSparqlOut httpSparqlOut;
	private final boolean continueOnFail;

	public LdioHttpSparqlOut(HttpSparqlOut httpSparqlOut) {
		this(httpSparqlOut, true);
	}

	/**
	 * @param continueOnFail when false, a non-2xx response of the SPARQL host that remains after the configured retries
	 *                       raises an {@link HttpSparqlOutException}, so the pipeline input can act on the failed write.
	 *                       When true, the failure is only logged.
	 */
	public LdioHttpSparqlOut(HttpSparqlOut httpSparqlOut, boolean continueOnFail) {
		this.httpSparqlOut = httpSparqlOut;
		this.continueOnFail = continueOnFail;
	}

	@Override
	public void accept(Model model) {
		try {
			httpSparqlOut.write(model);
		} catch (WriteActionFailedException e) {
			if (!continueOnFail) {
				throw new HttpSparqlOutException(e);
			}
			log.error(e.getMessage());
		}
	}
}
