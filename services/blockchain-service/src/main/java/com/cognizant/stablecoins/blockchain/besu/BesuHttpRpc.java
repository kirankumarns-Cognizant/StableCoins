package com.cognizant.stablecoins.blockchain.besu;

import com.cognizant.stablecoins.blockchain.service.BlockchainRpcException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.*;

/** Separate connection pool; bounded JSON, fixed endpoint, no redirects or connection retries. */
public final class BesuHttpRpc implements BesuRpc, AutoCloseable {
    private static final int LIMIT = 2 * 1024 * 1024;
    private final OkHttpClient client;
    private final String endpoint;
    private final AtomicLong ids = new AtomicLong();
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public BesuHttpRpc(String endpoint) {
        try {
            var uri = URI.create(endpoint);
            if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null)
                throw new IllegalArgumentException();
        } catch (Exception ignored) { throw new IllegalArgumentException("BESU_RPC_URL_INVALID"); }
        this.endpoint = endpoint;
        client = new OkHttpClient.Builder().connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10)).callTimeout(Duration.ofSeconds(15))
                .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .addNetworkInterceptor(chain -> {
                    Attempt attempt = chain.request().tag(Attempt.class);
                    if (attempt == null || attempt.count.incrementAndGet() != 1)
                        throw new java.io.IOException("BESU_HTTP_ATTEMPT_LIMIT");
                    return chain.proceed(chain.request());
                }).build();
    }
    @Override public JsonNode call(String method, List<?> parameters) {
        long id = ids.incrementAndGet();
        try {
            byte[] body = json.writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", parameters));
            var request = new Request.Builder().url(endpoint)
                    .tag(Attempt.class, new Attempt())
                    .post(RequestBody.create(body, MediaType.get("application/json"))).build();
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IllegalArgumentException();
                if (response.body().contentLength() > LIMIT) throw new IllegalArgumentException();
                byte[] bytes = response.body().byteStream().readNBytes(LIMIT + 1);
                if (bytes.length > LIMIT) throw new IllegalArgumentException();
                var result = json.readTree(bytes);
                if (result == null || !result.isObject() || !"2.0".equals(result.path("jsonrpc").asText())
                        || !result.path("id").isIntegralNumber() || !result.path("id").canConvertToLong()
                        || result.path("id").longValue() != id || result.has("error") || !result.has("result"))
                    throw new IllegalArgumentException();
                return result.get("result");
            }
        } catch (Exception ignored) {
            // No cause: URLs, provider messages and signed bytes must not reach structured logs.
            throw new BlockchainRpcException("BESU_RPC_UNAVAILABLE_OR_REJECTED");
        }
    }
    private static final class Attempt {
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
    }
    @Override public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
