package org.openldes.ldi.requestexecutor.valueobjects;

import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpTimeoutsTest {

	@Test
	void defaults_shouldBeFinite() {
		final HttpTimeouts defaults = HttpTimeouts.defaults();

		assertThat(defaults.connect()).isEqualTo(Duration.ofSeconds(30));
		assertThat(defaults.connectionRequest()).isEqualTo(Duration.ofMinutes(1));
		assertThat(defaults.socket()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void applyTo_shouldSetAllThreeTimeoutsInMillis() {
		final HttpTimeouts timeouts = new HttpTimeouts(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofMinutes(2));

		final RequestConfig config = timeouts.applyTo(RequestConfig.custom()).build();

		assertThat(config.getConnectTimeout()).isEqualTo(5_000);
		assertThat(config.getConnectionRequestTimeout()).isEqualTo(10_000);
		assertThat(config.getSocketTimeout()).isEqualTo(120_000);
	}

	@ParameterizedTest
	@ValueSource(strings = {"PT0S", "-PT1S"})
	void shouldRejectZeroOrNegativeTimeouts(String value) {
		final Duration invalid = Duration.parse(value);
		final Duration valid = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new HttpTimeouts(invalid, valid, valid)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HttpTimeouts(valid, invalid, valid)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new HttpTimeouts(valid, valid, invalid)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectTimeoutsLargerThanIntMillis() {
		final Duration tooLong = Duration.ofMillis(Integer.MAX_VALUE + 1L);
		final Duration valid = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new HttpTimeouts(valid, valid, tooLong)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectNull() {
		final Duration valid = Duration.ofSeconds(1);

		assertThatThrownBy(() -> new HttpTimeouts(null, valid, valid)).isInstanceOf(NullPointerException.class);
	}

}
