package org.openldes.ldi.valuobjects.valueproperties;

import org.openldes.ldi.services.NgsiLdDateParser;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.openldes.ldi.config.NgsiV2ToLdMapping.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DateTimeValue extends ObjectValue {

	@JsonCreator
	public DateTimeValue(@JsonProperty(NGSI_V2_KEY_VALUE) String value,
			@JsonProperty(NGSI_V2_KEY_TYPE) String type) {
		this(value);
	}

	@JsonCreator
	public DateTimeValue(String value) {
		super(NgsiLdDateParser.normaliseDate(value));
		this.type = NGSI_LD_ATTRIBUTE_TYPE_DATETIME;
	}
}
