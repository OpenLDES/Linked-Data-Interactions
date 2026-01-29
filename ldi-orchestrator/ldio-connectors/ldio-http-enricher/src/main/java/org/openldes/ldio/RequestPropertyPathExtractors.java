package org.openldes.ldio;

import org.openldes.ldi.extractor.PropertyExtractor;

public record RequestPropertyPathExtractors(PropertyExtractor urlPropertyPathExtractor,
                                            PropertyExtractor bodyPropertyPathExtractor,
                                            PropertyExtractor headerPropertyPathExtractor,
                                            PropertyExtractor httpMethodPropertyPathExtractor) {
}
