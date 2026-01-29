package org.openldes.ldio.exceptions;

import org.openldes.ldio.auth.KafkaAuthStrategy;

import java.util.Arrays;

public class SecurityProtocolNotSupportedException extends IllegalArgumentException {

	public SecurityProtocolNotSupportedException(String securityProtocolKey) {
		super(new IllegalArgumentException("Invalid '%s', the supported protocols are: %s".formatted(
				securityProtocolKey,
				Arrays.stream(KafkaAuthStrategy.values()).map(Enum::name).toList())));
	}

}
