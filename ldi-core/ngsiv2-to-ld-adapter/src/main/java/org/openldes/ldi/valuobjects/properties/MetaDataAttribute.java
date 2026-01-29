package org.openldes.ldi.valuobjects.properties;

import org.openldes.ldi.services.NgsiLdDateParser;
import org.openldes.ldi.valuobjects.LinkedDataAttributeBase;
import org.openldes.ldi.valuobjects.valueproperties.ObjectValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.openldes.ldi.config.NgsiV2ToLdMapping.*;
import static java.util.Objects.isNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaDataAttribute extends LinkedDataAttributeBase {

	private String timestamp;
	private final ObjectValue unitCode;

	@JsonCreator
	public MetaDataAttribute(@JsonProperty(NGSI_V2_KEY_TIMESTAMP) String timestamp,
			@JsonProperty(NGSI_V2_KEY_UNIT_CODE) ObjectValue unitCode) {

		super();
		this.unitCode = unitCode;
		if (!isNull(timestamp) && !timestamp.isEmpty()) {
			this.timestamp = NgsiLdDateParser.normaliseDate(timestamp);
		}

	}

	public String getTimestamp() {
		return timestamp;
	}

	public ObjectValue getUnitCode() {
		return unitCode;
	}

	public Boolean hasTimestamp() {
		return !isNull(timestamp) && !timestamp.isEmpty();
	}

	public Boolean hasUnitCode() {
		return !isNull(unitCode);
	}
}
