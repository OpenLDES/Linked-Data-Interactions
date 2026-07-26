package org.openldes.ldio;

import org.openldes.ldio.pipeline.creation.valueobjects.ComponentProperties;
import org.openldes.ldio.pipeline.exception.InvalidConfigException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LdioLdesClientPropertiesTest {

	@Test
	void given_ExactlyOnceAndVersionMaterialisationAreBothExplicitlyEnabled_when_parseConfig_then_ThrowException() {
		final ComponentProperties properties = new ComponentProperties("pipeline", "cname", Map.of(
				LdioLdesClientPropertyKeys.USE_EXACTLY_ONCE_FILTER, String.valueOf(true),
				LdioLdesClientPropertyKeys.USE_VERSION_MATERIALISATION, String.valueOf(true)
		));

		assertThatThrownBy(() -> LdioLdesClientProperties.fromComponentProperties(properties))
				.isInstanceOf(InvalidConfigException.class)
				.hasMessage("Invalid config: \"The exactly once filter can not be enabled with version materialisation.\" .");
	}

	@Test
	void given_OrderedIsConfigured_when_parseConfig_then_ReturnConfiguredValue() {
		final ComponentProperties properties = new ComponentProperties("pipeline", "cname", Map.of(
				LdioLdesClientPropertyKeys.ORDERED, String.valueOf(true)
		));

		assertThat(LdioLdesClientProperties.fromComponentProperties(properties).isOrderedEnabled()).isTrue();
	}

	@Test
	void given_OrderedIsNotConfigured_when_parseConfig_then_ReturnDefaultFalse() {
		final ComponentProperties properties = new ComponentProperties("pipeline", "cname", Map.of());

		assertThat(LdioLdesClientProperties.fromComponentProperties(properties).isOrderedEnabled()).isFalse();
	}
}
