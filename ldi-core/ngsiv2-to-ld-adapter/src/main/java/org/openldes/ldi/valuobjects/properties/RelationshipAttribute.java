package org.openldes.ldi.valuobjects.properties;

import org.openldes.ldi.exceptions.MalformedNgsiReferencePropertyException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import static org.openldes.ldi.config.NgsiV2ToLdMapping.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RelationshipAttribute extends LinkedDataAttribute {

	private final String object;

	@JsonCreator
	public RelationshipAttribute(@JsonProperty(NGSI_V2_KEY_TYPE) String type,
			@JsonProperty(NGSI_V2_KEY_VALUE) Object value,
			@JsonProperty(NGSI_V2_KEY_DATE_OBSERVED) String dateObserved,
			@JsonProperty(NGSI_V2_KEY_METADATA) MetaDataAttribute metaData) {
		super(type, null, dateObserved, metaData);
		this.type = NGSI_LD_ATTRIBUTE_TYPE_RELATIONSHIP;
		if (value.getClass().equals(String.class)) {
			this.object = (String) value;
		} else {
			throw new MalformedNgsiReferencePropertyException(value.getClass().getName(), value.toString());
		}
	}

	@JsonGetter(NGSI_LD_KEY_OBJECT)
	public String getObjectId() {
		return object;
	}
}
