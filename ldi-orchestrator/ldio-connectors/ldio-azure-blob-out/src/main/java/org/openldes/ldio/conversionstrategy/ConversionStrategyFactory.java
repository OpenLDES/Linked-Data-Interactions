package org.openldes.ldio.conversionstrategy;

import org.openldes.ldio.exception.UnsupportedLangException;
import org.apache.jena.riot.Lang;

import static org.apache.jena.riot.RDFLanguages.nameToLang;

public class ConversionStrategyFactory {

	public ConversionStrategy getConversionStrategy(String outputLanguage, String jsonContextURI) {
		if (outputLanguage.equals("json")) {
			return new JsonConversionStrategy(jsonContextURI);
		}
		Lang lang = nameToLang(outputLanguage);
		if (lang == null) {
			throw new UnsupportedLangException(outputLanguage);
		}
		return new RDFSerializationConversionStrategy(lang);
	}
}
