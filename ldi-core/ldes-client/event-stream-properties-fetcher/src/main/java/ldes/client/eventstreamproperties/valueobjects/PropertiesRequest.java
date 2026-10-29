package ldes.client.eventstreamproperties.valueobjects;

import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeader;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.apache.http.HttpHeaders;
import org.apache.jena.riot.Lang;

import java.util.List;

import static org.openldes.ldi.rdf.parser.RdfResponseParser.ACCEPT_HEADER;

public record PropertiesRequest(String url, Lang lang) {

	public Request createRequest() {
		RequestHeaders requestHeaders = new RequestHeaders(List.of(
				new RequestHeader(HttpHeaders.ACCEPT, ACCEPT_HEADER)
		));
		return new GetRequest(url, requestHeaders);
	}

	public PropertiesRequest withUrl(String url) {
		return new PropertiesRequest(url, lang);
	}

	@Override
	public String toString() {
		return "PropertiesRequest{url='%s', lang=%s}".formatted(url, lang);
	}
}
