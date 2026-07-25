package org.openldes.ldi.rdf.parser;

import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.WebContent;

import java.io.ByteArrayInputStream;

/**
 * Parses an HTTP RDF representation according to its response Content-Type.
 */
public final class RdfResponseParser {

	public static final String ACCEPT_HEADER = WebContent.defaultRDFAcceptHeader;

	private RdfResponseParser() {
	}

	/**
	 * Parse an RDF representation as a dataset so named graphs are preserved.
	 * The configured language is used only when the response omits Content-Type.
	 */
	public static Dataset parseDataset(byte[] body, String contentType, String baseIri, Lang fallbackLang) {
		final Lang responseLang = responseLang(contentType, fallbackLang);
		return RDFParser.source(new ByteArrayInputStream(body))
				.lang(responseLang)
				.base(baseIri)
				.toDataset();
	}

	/**
	 * Parse the default graph of an RDF representation for graph-only consumers.
	 */
	public static Model parseModel(byte[] body, String contentType, String baseIri, Lang fallbackLang) {
		return parseDataset(body, contentType, baseIri, fallbackLang).getDefaultModel();
	}

	private static Lang responseLang(String contentType, Lang fallbackLang) {
		if (contentType == null || contentType.isBlank()) {
			if (fallbackLang == null) {
				throw new RiotException("The RDF response has no Content-Type and no fallback language was configured");
			}
			return fallbackLang;
		}

		final Lang contentTypeLang = RDFLanguages.contentTypeToLang(ContentType.create(contentType));
		if (contentTypeLang == null) {
			throw new RiotException("Unsupported RDF response Content-Type: " + contentType);
		}
		return contentTypeLang;
	}
}
