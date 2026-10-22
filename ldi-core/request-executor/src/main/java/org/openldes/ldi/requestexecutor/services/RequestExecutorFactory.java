package org.openldes.ldi.requestexecutor.services;

import org.openldes.ldi.requestexecutor.executor.RequestExecutor;
import org.openldes.ldi.requestexecutor.executor.clientcredentials.ClientCredentialsConfig;
import org.openldes.ldi.requestexecutor.executor.edc.EdcRequestExecutor;
import org.openldes.ldi.requestexecutor.executor.edc.services.TokenService;
import org.openldes.ldi.requestexecutor.executor.edc.valueobjects.EdcUrlProxy;
import org.openldes.ldi.requestexecutor.executor.noauth.DefaultConfig;
import org.openldes.ldi.requestexecutor.valueobjects.HttpTimeouts;
import org.apache.http.Header;

import java.util.ArrayList;
import java.util.Collection;

public class RequestExecutorFactory {

    private final boolean enableRedirectHandling;

    public RequestExecutorFactory() {
        this(true);
    }

    public RequestExecutorFactory(boolean enableRedirectHandling) {
        this.enableRedirectHandling = enableRedirectHandling;
    }

    public RequestExecutor createNoAuthExecutor(Collection<Header> headers) {
        return createNoAuthExecutor(headers, HttpTimeouts.defaults());
    }

    public RequestExecutor createNoAuthExecutor(Collection<Header> headers, HttpTimeouts timeouts) {
        return new DefaultConfig(headers, enableRedirectHandling, timeouts).createRequestExecutor();
    }

    public RequestExecutor createNoAuthExecutor() {
        return createNoAuthExecutor(new ArrayList<>());
    }

    public RequestExecutor createClientCredentialsExecutor(Collection<Header> headers,
                                                           String clientId,
                                                           String secret,
                                                           String tokenEndpoint,
                                                           String scope) {
        return createClientCredentialsExecutor(headers, clientId, secret, tokenEndpoint, scope, HttpTimeouts.defaults());
    }

    public RequestExecutor createClientCredentialsExecutor(Collection<Header> headers,
                                                           String clientId,
                                                           String secret,
                                                           String tokenEndpoint,
                                                           String scope,
                                                           HttpTimeouts timeouts) {
        var config = new ClientCredentialsConfig(headers, clientId, secret, tokenEndpoint, scope, enableRedirectHandling, timeouts);
        return config.createRequestExecutor();
    }

    public RequestExecutor createClientCredentialsExecutor(String clientId,
                                                           String secret,
                                                           String tokenEndpoint,
                                                           String scope) {
        return createClientCredentialsExecutor(new ArrayList<>(), clientId, secret, tokenEndpoint, scope);
    }

    public RequestExecutor createEdcExecutor(RequestExecutor requestExecutor,
                                             TokenService tokenService,
                                             EdcUrlProxy edcUrlProxy) {
        return new EdcRequestExecutor(requestExecutor, tokenService, edcUrlProxy);
    }

}
