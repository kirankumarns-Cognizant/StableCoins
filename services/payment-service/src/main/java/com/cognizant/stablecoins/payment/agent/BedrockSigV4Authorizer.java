package com.cognizant.stablecoins.payment.agent;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.*;
import software.amazon.awssdk.regions.Region;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
/** AWS SDK SigV4 signer with ECS task-role/default-chain credentials. No stored AWS access key. */
public final class BedrockSigV4Authorizer implements AgentRequestAuthorizer {
    private final AgentProviderProperties properties;private final AwsCredentialsProvider credentials;
    public BedrockSigV4Authorizer(AgentProviderProperties p,AwsCredentialsProvider credentials){this.properties=p;this.credentials=credentials;}
    public Map<String,String> headers(URI endpoint,byte[] body){
        properties.validate();
        if(properties.getProvider()!=AgentProviderProperties.Provider.BEDROCK||properties.getAuthMode()!=AgentProviderProperties.AuthMode.AWS_SIGV4)
            throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
        URI configured=URI.create(properties.getBaseUrl().replaceAll("/+$","")+"/responses");
        if(!configured.equals(endpoint))throw new AgentFailure("AGENT_AUTH_ENDPOINT_MISMATCH");
        try{
            var request=SdkHttpFullRequest.builder().uri(endpoint).method(SdkHttpMethod.POST).putHeader("Content-Type","application/json")
                .contentStreamProvider(()->new ByteArrayInputStream(body)).build();
            String service=endpoint.getHost().startsWith("bedrock-mantle.")?"bedrock-mantle":"bedrock";
            var signed=Aws4Signer.create().sign(request,Aws4SignerParams.builder().awsCredentials(credentials.resolveCredentials())
                .signingRegion(Region.of(properties.getRegion())).signingName(service).build());
            var headers=new LinkedHashMap<String,String>();signed.headers().forEach((name,values)->{
                if(!name.equalsIgnoreCase("Host"))headers.put(name,String.join(",",values));
            });return Map.copyOf(headers);
        }catch(Exception e){throw new AgentFailure("AGENT_AWS_AUTH_UNAVAILABLE");}
    }
}
