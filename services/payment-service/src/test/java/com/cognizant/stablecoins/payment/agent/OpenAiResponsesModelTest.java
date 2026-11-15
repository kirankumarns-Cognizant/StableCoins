package com.cognizant.stablecoins.payment.agent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import java.net.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OpenAiResponsesModelTest {
    HttpClient http=mock(HttpClient.class);AgentKeyProvider keys=mock(AgentKeyProvider.class);
    AgentProviderProperties p=provider();AgentSettings limits=settings();
    @BeforeEach void key(){when(keys.load()).thenReturn("sk-test-only");}
    @SuppressWarnings("unchecked") void response(int status,String body){
        HttpResponse<InputStream> r=mock(HttpResponse.class);when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any()))
            .thenReturn(CompletableFuture.completedFuture(r));
    }
    AgentModel.Reply call(){return new OpenAiResponsesModel(p,limits,keys,JSON,http).exchange(JSON.createObjectNode().put("model",p.getModel()),Duration.ofSeconds(5));}
    @Test void responsesWireContractAndUsageAreParsed(){response(200,"{\"id\":\"resp-test\",\"model\":\"model-snapshot\",\"status\":\"completed\",\"usage\":{\"input_tokens\":12,\"output_tokens\":8},\"output\":[]}");
        var result=call();assertThat(result.inputTokens()).isEqualTo(12);assertThat(result.outputTokens()).isEqualTo(8);
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).sendAsync(captor.capture(),any());
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.openai.com/v1/responses");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer sk-test-only");
    }
    @Test void providerErrorDetailsAreDiscardedAndNeverRetried(){response(429,"secret-provider-body");
        assertThatThrownBy(this::call).hasMessage("AGENT_PROVIDER_RATE_LIMITED");verify(http,times(1)).sendAsync(any(),any());}
    @Test void incompleteOutputFailsClosed(){response(200,"{\"status\":\"incomplete\",\"output\":[]}");assertThatThrownBy(this::call).hasMessage("AGENT_INCOMPLETE_RESPONSE");}
    @Test void nonIntegerTokenUsageIsRejected(){response(200,"{\"status\":\"completed\",\"output\":[],\"usage\":{\"input_tokens\":1.5,\"output_tokens\":2}}");assertThatThrownBy(this::call).hasMessage("AGENT_INVALID_USAGE");}
    @Test void oversizedBodyIsRejected(){limits.setMaximumResponseBytes(2048);response(200,"x".repeat(2049));assertThatThrownBy(this::call).hasMessage("AGENT_RESPONSE_LIMIT");}
    @Test void timeoutCancelsPendingRequestWithoutRetry(){p.setTimeoutMs(100);CompletableFuture<HttpResponse<InputStream>> pending=new CompletableFuture<>();
        when(http.sendAsync(any(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(pending);
        assertThatThrownBy(this::call).hasMessage("AGENT_PROVIDER_TIMEOUT");assertThat(pending.isCancelled()).isTrue();verify(http,times(1)).sendAsync(any(),any());}
    @Test void malformedProviderJsonDoesNotEscapeIntoError(){response(200,"private-invalid-json");assertThatThrownBy(this::call).hasMessage("AGENT_PROVIDER_UNAVAILABLE");}
    @Test void insecureEndpointIsRejectedBeforeSecretLookup(){p.setBaseUrl("http://untrusted.test");assertThatThrownBy(this::call).hasMessage("AGENT_PROVIDER_NOT_CONFIGURED");verifyNoInteractions(keys,http);}
    @Test void noModelDefaultIsInvented(){p.setModel("");assertThatThrownBy(this::call).hasMessage("AGENT_PROVIDER_NOT_CONFIGURED");verifyNoInteractions(keys,http);}
    @Test void instructionRedactsRecognizableSecretsAddressesAndEmail(){assertThat(AgentInput.redact("sk-secret-value foo@example.test 0x"+"a".repeat(64)))
        .doesNotContain("sk-secret-value","foo@example.test","a".repeat(64));}
}
