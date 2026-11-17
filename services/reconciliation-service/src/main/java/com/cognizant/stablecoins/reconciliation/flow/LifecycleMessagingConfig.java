package com.cognizant.stablecoins.reconciliation.flow;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.*;
@Configuration @EnableScheduling @ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class LifecycleMessagingConfig {
 @Bean @ConditionalOnMissingBean SqsClient sqsClient(Environment env){
  var builder=SqsClient.builder().region(Region.of(env.getProperty("connections.aws.region","us-west-2")));
  String endpoint=env.getProperty("connections.aws.endpoint","");
  if(!endpoint.isBlank())builder.endpointOverride(java.net.URI.create(endpoint)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(env.getRequiredProperty("connections.aws.local-access-key"),env.getRequiredProperty("connections.aws.local-secret-key"))));
  return builder.build();
 }
}
