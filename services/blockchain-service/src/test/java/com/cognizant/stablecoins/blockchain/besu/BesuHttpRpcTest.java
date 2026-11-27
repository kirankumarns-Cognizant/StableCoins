package com.cognizant.stablecoins.blockchain.besu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class BesuHttpRpcTest {
    @Test void realLoopbackTransportRoundTripsJsonRpcWithoutWeb3jJacksonConflicts() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var json = new ObjectMapper();
        server.createContext("/", exchange -> {
            try {
                var request = json.readTree(exchange.getRequestBody());
                byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":" + request.path("id") + ",\"result\":\"0x1\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        server.start();
        try (var rpc = new BesuHttpRpc("http://127.0.0.1:" + server.getAddress().getPort())) {
            assertThat(rpc.call("eth_chainId", List.of()).asText()).isEqualTo("0x1");
            assertThat(rpc.call("eth_blockNumber", List.of()).asText()).isEqualTo("0x1");
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(strings={"error", "wrong-id", "duplicate", "redirect", "large", "http-error", "retry-after", "trailing"})
    void rejectsMalformedOrUnsafeResponsesWithRedactedSingleAttempt(String scenario) throws Exception {
        var count = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            count.incrementAndGet();
            try {
                String response = switch (scenario) {
                    case "error" -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"message\":\"secret-fixture-do-not-expose\"}}";
                    case "wrong-id" -> "{\"jsonrpc\":\"2.0\",\"id\":999,\"result\":null}";
                    case "duplicate" -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":2,\"result\":null}";
                    case "large" -> "x".repeat(2 * 1024 * 1024 + 1);
                    case "trailing" -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":null} {}";
                    default -> "secret-fixture-do-not-expose";
                };
                int status = scenario.equals("redirect") ? 307 : (scenario.equals("http-error") || scenario.equals("retry-after")) ? 503 : 200;
                if (status == 307) exchange.getResponseHeaders().set("Location", "/next");
                if (scenario.equals("retry-after")) exchange.getResponseHeaders().set("Retry-After", "0");
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        try (var rpc = new BesuHttpRpc("http://127.0.0.1:" + server.getAddress().getPort())) {
            assertThatThrownBy(() -> rpc.call("eth_sendRawTransaction", List.of("0xdeadbeef")))
                    .hasMessage("BESU_RPC_UNAVAILABLE_OR_REJECTED").hasNoCause();
            assertThat(count).hasValue(1);
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(strings={"file:///tmp/rpc", "http://user:secret@localhost:8545", "http://localhost:8545/?key=secret"})
    void invalidServerEndpointIsRedacted(String url) {
        assertThatThrownBy(() -> new BesuHttpRpc(url)).hasMessage("BESU_RPC_URL_INVALID").hasNoCause();
    }
}
