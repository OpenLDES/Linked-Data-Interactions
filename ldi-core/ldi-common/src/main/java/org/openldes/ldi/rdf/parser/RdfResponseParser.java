package org.openldes.ldi.rdf.parser;

import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.loader.SchemeRouter;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.lang.LangJSONLD11;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.util.Context;

import java.io.ByteArrayInputStream;

/**
 * Parses an HTTP RDF representation according to its response Content-Type.
 */
public final class RdfResponseParser {

	public static final String ACCEPT_HEADER = WebContent.defaultRDFAcceptHeader;

	/**
	 * Parser context that reuses already fetched remote JSON-LD documents, so a
	 * shared {@code @context} is requested once instead of once per parse.
	 */
	private static final Context JSONLD_CONTEXT = jsonLdContext();

	private RdfResponseParser() {
	}

	private static Context jsonLdContext() {
		final JsonLdOptions options = new JsonLdOptions();
		options.setDocumentLoader(
				new CachingJsonLdDocumentLoader(SchemeRouter.defaultInstance()));
		final Context context = new Context();
		context.set(LangJSONLD11.JSONLD_OPTIONS, options);
		return context;
	}

	/**
	 * Parse an RDF representation as a dataset so named graphs are preserved.
	 * The configured language is used only when the response omits Content-Type.
	 */
	public static Dataset parseDataset(byte[] body, String contentType, String baseIri, Lang fallbackLang) {
		return parser(body, contentType, baseIri, fallbackLang).toDataset();
	}

	/**
	 * Builds a parser for an RDF response, resolving the language from the
	 * response Content-Type and reusing already fetched remote JSON-LD
	 * documents. Use this instead of building a parser directly, so every parse
	 * of a response shares one remote-document cache.
	 */
	public static RDFParserBuilder parser(byte[] body, String contentType, String baseIri, Lang fallbackLang) {
		final Lang responseLang = responseLang(contentType, fallbackLang);
		final RDFParserBuilder parser = RDFParser.source(new ByteArrayInputStream(body))
				.lang(responseLang)
				.base(baseIri);
		if (RDFLanguages.JSONLD.equals(responseLang) || RDFLanguages.JSONLD11.equals(responseLang)) {
			parser.context(JSONLD_CONTEXT);
		}
		return parser;
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
