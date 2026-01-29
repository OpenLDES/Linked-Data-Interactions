package org.openldes.ldio;

import org.openldes.ldi.HttpSparqlOut;
import org.openldes.ldi.exceptions.WriteActionFailedException;
import org.openldes.ldi.types.LdiOutput;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdioHttpSparqlOut implements LdiOutput {
	public static final String NAME = "Ldio:HttpSparqlOut";
	private static final Logger log = LoggerFactory.getLogger(LdioHttpSparqlOut.class);
	private final HttpSparqlOut httpSparqlOut;

	public LdioHttpSparqlOut(HttpSparqlOut httpSparqlOut) {
		this.httpSparqlOut = httpSparqlOut;
	}

	@Override
	public void accept(Model model) {
		try {
			httpSparqlOut.write(model);
		} catch (WriteActionFailedException e) {
			log.error(e.getMessage());
		}
	}
}
