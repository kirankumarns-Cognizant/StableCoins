package com.cognizant.stablecoins.payment.agent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static org.assertj.core.api.Assertions.*;
class BedrockSigV4AuthorizerTest {
    AgentProviderProperties properties(String host){var p=provider();p.setProvider(AgentProviderProperties.Provider.BEDROCK);p.setAuthMode(AgentProviderProperties.AuthMode.AWS_SIGV4);p.setApiKeySecretName("");p.setRegion("us-east-2");p.setBaseUrl("https://"+host+"/openai/v1");p.setModel("openai.gpt-5.6-terra");return p;}
    AgentRequestAuthorizer auth(AgentProviderProperties p){return new BedrockSigV4Authorizer(p,StaticCredentialsProvider.create(AwsSessionCredentials.create("TESTACCESSKEY","test-only-secret","test-session-token")));}
    @Test void taskRoleCredentialsSignRuntimeRequestForCorrectService(){var p=properties("bedrock-runtime.us-east-2.amazonaws.com");var headers=auth(p).headers(URI.create(p.getBaseUrl()+"/responses"),"{}".getBytes(StandardCharsets.UTF_8));
        assertThat(headers.get("Authorization")).startsWith("AWS4-HMAC-SHA256").contains("/us-east-2/bedrock/aws4_request");
        assertThat(headers).containsEntry("X-Amz-Security-Token","test-session-token");assertThat(headers.keySet()).noneMatch(k->k.equalsIgnoreCase("Host"));
        assertThat(headers.toString()).doesNotContain("test-only-secret");}
    @Test void mantleUsesItsOwnSigningService(){var p=properties("bedrock-mantle.us-east-2.api.aws");var headers=auth(p).headers(URI.create(p.getBaseUrl()+"/responses"),new byte[0]);
        assertThat(headers.get("Authorization")).contains("/us-east-2/bedrock-mantle/aws4_request");}
    @Test void arbitraryDestinationNeverReceivesAwsSignature(){var p=properties("bedrock-runtime.us-east-2.amazonaws.com");assertThatThrownBy(()->auth(p).headers(URI.create("https://attacker.test/responses"),new byte[0])).hasMessage("AGENT_AUTH_ENDPOINT_MISMATCH");}
    @Test void configuredRegionMustMatchBedrockHost(){var p=properties("bedrock-runtime.us-east-1.amazonaws.com");assertThatThrownBy(p::validate).hasMessage("AGENT_PROVIDER_NOT_CONFIGURED");}
    @Test void sigv4CannotBeSentToDirectOpenAi(){var p=provider();p.setAuthMode(AgentProviderProperties.AuthMode.AWS_SIGV4);assertThatThrownBy(p::validate).hasMessage("AGENT_PROVIDER_NOT_CONFIGURED");}
    @Test void iamModeRequiresNoStaticApiSecret(){var p=properties("bedrock-runtime.us-east-2.amazonaws.com");assertThatCode(p::validate).doesNotThrowAnyException();}
    @Test void providerCredentialsErrorIsSanitized(){var p=properties("bedrock-runtime.us-east-2.amazonaws.com");var auth=new BedrockSigV4Authorizer(p,()->{throw new IllegalStateException("sensitive-sdk-detail");});
        assertThatThrownBy(()->auth.headers(URI.create(p.getBaseUrl()+"/responses"),new byte[0])).hasMessage("AGENT_AWS_AUTH_UNAVAILABLE");}
}
