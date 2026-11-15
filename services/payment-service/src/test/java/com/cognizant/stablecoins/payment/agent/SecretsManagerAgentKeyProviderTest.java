package com.cognizant.stablecoins.payment.agent;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;
import static com.cognizant.stablecoins.payment.agent.AgentFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class SecretsManagerAgentKeyProviderTest {
    @Test void retrievesOnlyConfiguredSecretAndClosesClient(){var client=mock(SecretsManagerClient.class);when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder().secretString("sk-test-only").build());
        assertThat(new SecretsManagerAgentKeyProvider(()->client,provider()).load()).isEqualTo("sk-test-only");
        verify(client).getSecretValue(GetSecretValueRequest.builder().secretId("test-only-agent-secret").build());verify(client).close();}
    @Test void secretLookupFailureDoesNotLeakSdkMessage(){var client=mock(SecretsManagerClient.class);when(client.getSecretValue(any(GetSecretValueRequest.class))).thenThrow(new IllegalStateException("private-credential-detail"));
        assertThatThrownBy(()->new SecretsManagerAgentKeyProvider(()->client,provider()).load()).hasMessage("AGENT_SECRET_UNAVAILABLE");}
    @Test void jsonSecretIsRejectedInsteadOfBeingLoggedOrSentAsBearer(){var client=mock(SecretsManagerClient.class);when(client.getSecretValue(any(GetSecretValueRequest.class))).thenReturn(GetSecretValueResponse.builder().secretString("{\"key\":\"sk-test-only\"}").build());
        assertThatThrownBy(()->new SecretsManagerAgentKeyProvider(()->client,provider()).load()).hasMessage("AGENT_SECRET_UNAVAILABLE");}
}
