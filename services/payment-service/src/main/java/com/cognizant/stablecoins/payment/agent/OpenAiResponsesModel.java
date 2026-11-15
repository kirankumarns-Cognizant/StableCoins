package com.cognizant.stablecoins.payment.agent;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
/** Responses API over the JDK client. No framework, hosted tools, redirects or automatic retries. */
public class OpenAiResponsesModel implements AgentModel {
    private final AgentProviderProperties properties;private final AgentSettings limits;
    private final AgentRequestAuthorizer authorizer;private final HttpClient http;private final ObjectMapper mapper;
    public OpenAiResponsesModel(AgentProviderProperties p,AgentSettings limits,AgentKeyProvider keys,ObjectMapper mapper){
        this(p,limits,(endpoint,body)->java.util.Map.of("Authorization","Bearer "+keys.load()),mapper);
    }
    public OpenAiResponsesModel(AgentProviderProperties p,AgentSettings limits,AgentRequestAuthorizer authorizer,ObjectMapper mapper){
        this(p,limits,authorizer,mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    OpenAiResponsesModel(AgentProviderProperties p,AgentSettings limits,AgentKeyProvider keys,ObjectMapper mapper,HttpClient http){
        this(p,limits,(endpoint,body)->java.util.Map.of("Authorization","Bearer "+keys.load()),mapper,http);
    }
    OpenAiResponsesModel(AgentProviderProperties p,AgentSettings limits,AgentRequestAuthorizer authorizer,ObjectMapper mapper,HttpClient http){
        this.properties=p;this.limits=limits;this.authorizer=authorizer;this.mapper=mapper;this.http=http;
    }
    public Reply exchange(ObjectNode request,Duration remaining){
        properties.validate();if(remaining.isNegative()||remaining.isZero())throw new AgentFailure("AGENT_DEADLINE_EXCEEDED");
        try{
            byte[] bytes=mapper.writeValueAsBytes(request);if(bytes.length>limits.getMaximumRequestBytes())throw new AgentFailure("AGENT_CONTEXT_LIMIT");
            // Secret lookup consumes the same wall-clock budget as inference.
            long started=System.nanoTime();
            URI uri=URI.create(properties.getBaseUrl().replaceAll("/+$","")+"/responses");
            var authentication=authorizer.headers(uri,bytes);
            long available=remaining.toMillis()-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            if(available<=0)throw new AgentFailure("AGENT_DEADLINE_EXCEEDED");
            Duration timeout=Duration.ofMillis(Math.min(available,properties.getTimeoutMs()));
            var builder=HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type","application/json");
            authentication.forEach((name,value)->{if(!name.equalsIgnoreCase("Content-Type"))builder.header(name,value);});
            var call=builder.POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
            long sent=System.nanoTime();
            var pending=http.sendAsync(call,HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response;
            try{response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
            catch(TimeoutException|InterruptedException e){pending.cancel(true);throw e;}
            try(InputStream stream=response.body()){
                if(response.statusCode()!=200)throw new AgentFailure(switch(response.statusCode()){
                    case 401,403->"AGENT_PROVIDER_ACCESS_DENIED";case 429->"AGENT_PROVIDER_RATE_LIMITED";default->"AGENT_PROVIDER_UNAVAILABLE";});
                long bodyTime=timeout.toMillis()-TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-sent);
                if(bodyTime<=0)throw new AgentFailure("AGENT_PROVIDER_TIMEOUT");
                // BodyHandlers.ofInputStream completes on headers, so bound body reading explicitly too.
                try(var reader=Executors.newVirtualThreadPerTaskExecutor()){
                    Future<byte[]> body=reader.submit(()->stream.readNBytes(limits.getMaximumResponseBytes()+1));
                    byte[] content;
                    try{content=body.get(bodyTime,TimeUnit.MILLISECONDS);}catch(TimeoutException e){stream.close();body.cancel(true);throw new AgentFailure("AGENT_PROVIDER_TIMEOUT");}
                    if(content.length>limits.getMaximumResponseBytes())throw new AgentFailure("AGENT_RESPONSE_LIMIT");
                    JsonNode json=mapper.readTree(content);
                    if(!"completed".equals(json.path("status").asText())||!json.path("output").isArray())throw new AgentFailure("AGENT_INCOMPLETE_RESPONSE");
                    JsonNode usage=json.path("usage");
                    if(!usage.path("input_tokens").isIntegralNumber()||!usage.path("output_tokens").isIntegralNumber()||!usage.path("input_tokens").canConvertToLong()||!usage.path("output_tokens").canConvertToLong()
                        ||usage.path("input_tokens").asLong()<0||usage.path("output_tokens").asLong()<0)throw new AgentFailure("AGENT_INVALID_USAGE");
                    return new Reply(json.get("output"),usage.get("input_tokens").asLong(),usage.get("output_tokens").asLong(),json.path("id").asText(),json.path("model").asText());
                }
            }
        }catch(AgentFailure e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AgentFailure("AGENT_INTERRUPTED");}
        catch(TimeoutException e){throw new AgentFailure("AGENT_PROVIDER_TIMEOUT");}catch(Exception e){throw new AgentFailure("AGENT_PROVIDER_UNAVAILABLE");}
    }
}
