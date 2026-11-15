package com.cognizant.stablecoins.payment.agent;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component @ConfigurationProperties("connections.ai")
public class AgentProviderProperties {
    public enum Provider { OPENAI, BEDROCK }
    public enum AuthMode { SECRET_BEARER, AWS_SIGV4 }
    private Provider provider=Provider.OPENAI;private AuthMode authMode=AuthMode.SECRET_BEARER;
    private String region="us-east-1";
    public Provider getProvider(){return provider;}public void setProvider(Provider v){provider=v;}
    public AuthMode getAuthMode(){return authMode;}public void setAuthMode(AuthMode v){authMode=v;}
    public String getRegion(){return region;}public void setRegion(String v){region=v;}
    private String baseUrl="https://api.openai.com/v1",model="",apiKeySecretName="";
    private int timeoutMs=30000;
    public void validate(){
        try{
            URI uri=URI.create(baseUrl);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                ||provider==null||authMode==null||model==null||!model.matches("[A-Za-z0-9._:-]{1,120}")||timeoutMs<100||timeoutMs>120000)
                throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
            if(authMode==AuthMode.SECRET_BEARER&&(apiKeySecretName==null||apiKeySecretName.isBlank()))throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
            if(provider==Provider.OPENAI&&authMode!=AuthMode.SECRET_BEARER)throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
            if(provider==Provider.BEDROCK){
                if(region==null||!region.matches("[a-z]{2}-[a-z]+-[0-9]"))throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
                boolean host=uri.getHost().equals("bedrock-runtime."+region+".amazonaws.com")||uri.getHost().equals("bedrock-mantle."+region+".api.aws");
                if(!host||!uri.getPath().replaceAll("/+$","").equals("/openai/v1")||uri.getPort()!=-1)throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");
            }
        }catch(AgentFailure e){throw e;}catch(Exception e){throw new AgentFailure("AGENT_PROVIDER_NOT_CONFIGURED");}
    }

    public String getBaseUrl(){return baseUrl;}public void setBaseUrl(String v){baseUrl=v;}
    public String getModel(){return model;}public void setModel(String v){model=v;}
    public String getApiKeySecretName(){return apiKeySecretName;}public void setApiKeySecretName(String v){apiKeySecretName=v;}
    public int getTimeoutMs(){return timeoutMs;}public void setTimeoutMs(int v){timeoutMs=v;}
}
