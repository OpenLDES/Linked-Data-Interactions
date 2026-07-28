package org.openldes.ldi.requestexecutor.domain.valueobjects;

import org.openldes.ldi.requestexecutor.valueobjects.GetRequest;
import org.openldes.ldi.requestexecutor.valueobjects.Request;
import org.openldes.ldi.requestexecutor.valueobjects.RequestHeaders;
import org.openldes.ldi.requestexecutor.valueobjects.Response;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseTest {

	@ParameterizedTest
	@ValueSource(strings = {"LOCATION", "lOcAtIon", "location"})
	void getValueOfHeader(String headerName) {
		Response response = new Response(null, List.of(new BasicHeader("location", "value")), 302, "body");

		assertThat(response.getFirstHeaderValue(headerName)).contains("value");
	}

	@Test
	void test_isOk() {
		assertThat(new Response(null, List.of(), HttpStatus.SC_MOVED_TEMPORARILY, "body").isOk()).isFalse();
		assertThat(new Response(null, List.of(), HttpStatus.SC_OK, "body").isOk()).isTrue();
	}

	@ParameterizedTest
	@ArgumentsSource(TestGetRedirectLocation.RedirectProvider.class)
	void test_isRedirect(int statusCode, boolean isRedirect) {
		assertThat(new Response(null, List.of(), statusCode, "body").isRedirect()).isEqualTo(isRedirect);
	}

	@Test
	void test_isNotModified() {
		assertThat(new Response(null, List.of(), HttpStatus.SC_NOT_MODIFIED, "body").isNotModified()).isTrue();
		assertThat(new Response(null, List.of(), HttpStatus.SC_MOVED_TEMPORARILY, "body").isNotModified()).isFalse();
		assertThat(new Response(null, List.of(), HttpStatus.SC_OK, "body").isNotModified()).isFalse();
	}

	@Test
	void test_hasStatus() {
		assertThat(new Response(null, List.of(), 500, "body").hasStatus(List.of(500))).isTrue();
		assertThat(new Response(null, List.of(), 500, "body").hasStatus(List.of(400, 500))).isTrue();
		assertThat(new Response(null, List.of(), 500, "body").hasStatus(List.of(200, 204))).isFalse();
	}

	@Nested
	class TestGetRedirectLocation {
		@Test
		void shouldReturnEmpty_whenNoLocationHeader() {
			Request request = new GetRequest("https://example.com", RequestHeaders.empty());
			Response response = new Response(request, List.of(), 302, "body");

			assertThat(response.getRedirectLocation()).isEmpty();
		}

		@Test
		void shouldReturnEmptyWhenRequestIsMissing() {
			Header header = new BasicHeader(HttpHeaders.LOCATION, "/root");
			Response response = new Response(null, List.of(header), 302, "body");

			assertThat(response.getRedirectLocation()).isEmpty();
		}

		@Test
		void shouldPreservePortWhenResolvingRootRelativeLocation() {
			Request request = new GetRequest("http://localhost:12121/entry", RequestHeaders.empty());
			Header header = new BasicHeader(HttpHeaders.LOCATION, "/root");
			Response response = new Response(request, List.of(header), 302, "body");

			assertThat(response.getRedirectLocation()).contains("http://localhost:12121/root");
		}

		@ParameterizedTest
		@ArgumentsSource(LocationProvider.class)
		void shouldResolveLocationAgainstRequestUrl(String location, String expectedLocation) {
			Request request = new GetRequest("https://example.com/blog/article", RequestHeaders.empty());
			Header header = new BasicHeader(HttpHeaders.LOCATION, location);
			Response response = new Response(request, List.of(header), 302, "body");

			assertThat(response.getRedirectLocation()).contains(expectedLocation);
		}

		static class LocationProvider implements ArgumentsProvider {
			@Override
			public Stream<Arguments> provideArguments(ExtensionContext context) {
				return Stream.of(
						Arguments.of("https://example.com/blog/chat", "https://example.com/blog/chat"),
						Arguments.of("/blog/chat", "https://example.com/blog/chat"),
						Arguments.of("chat", "https://example.com/blog/chat"));
			}
		}

		static class RedirectProvider implements ArgumentsProvider {
			@Override
			public Stream<Arguments> provideArguments(ExtensionContext extensionContext) {
				return Stream.of(
						Arguments.of(HttpStatus.SC_MOVED_PERMANENTLY, true),
						Arguments.of(HttpStatus.SC_MOVED_TEMPORARILY, true),
						Arguments.of(HttpStatus.SC_TEMPORARY_REDIRECT, true),
						Arguments.of(308, true),
						Arguments.of(HttpStatus.SC_OK, false),
						Arguments.of(HttpStatus.SC_NOT_MODIFIED, false)
				);
			}
		}
	}

}