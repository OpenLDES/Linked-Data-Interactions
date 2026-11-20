package org.openldes.ldi.rdf.parser;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads remote JSON-LD documents, such as an {@code @context} a response refers
 * to, and keeps each one so repeated parsing does not refetch it.
 * <p>
 * An LDES traversal parses many pages that share one {@code @context}, and a
 * page may be parsed more than once, so an uncached loader turns a single
 * context into one request per parse. Cached documents are held for the
 * lifetime of the process, which treats a remote context as stable; that is the
 * assumption a published context is meant to satisfy.
 */
public final class CachingJsonLdDocumentLoader implements DocumentLoader {

	private static final int MAX_CACHED_DOCUMENTS = 64;

	private final DocumentLoader delegate;
	private final Map<CacheKey, Document> documents = new ConcurrentHashMap<>();

	public CachingJsonLdDocumentLoader(DocumentLoader delegate) {
		this.delegate = Objects.requireNonNull(delegate);
	}

	@Override
	public Document loadDocument(URI uri, DocumentLoaderOptions options) throws JsonLdError {
		final CacheKey key = new CacheKey(uri, options);
		final Document cached = documents.get(key);
		if (cached != null) {
			return cached;
		}

		final Document document = delegate.loadDocument(uri, options);
		if (document != null) {
			// A traversal refers to a handful of contexts, so the bound is only
			// there to stop an unexpected variety of IRIs from growing the map.
			if (documents.size() >= MAX_CACHED_DOCUMENTS) {
				documents.clear();
			}
			documents.put(key, document);
		}
		return document;
	}

	private record CacheKey(URI uri, DocumentLoaderOptions options) {
	}
}
