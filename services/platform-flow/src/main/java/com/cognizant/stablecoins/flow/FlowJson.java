package com.cognizant.stablecoins.flow;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
/** Strict bounded domain envelopes shared by new command consumers. */
public final class FlowJson {
 private FlowJson(){}
 public record Envelope(String eventId,String eventType,String eventVersion,String paymentId,String correlationId,Instant timestamp,JsonNode payload){}
 public static Envelope read(ObjectMapper mapper,String body,String expected){return read(mapper,body,expected,Set.of("1.0"));}
 public static Envelope read(ObjectMapper mapper,String body,String expected,Set<String> versions){
  try{
   if(body==null||body.length()>131072)throw new IllegalArgumentException();
   var strict=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
   Envelope event=strict.readValue(body,Envelope.class);
   FlowContract.identifier(event.eventId(),64);FlowContract.identifier(event.paymentId(),50);FlowContract.identifier(event.correlationId(),60);
   if(!expected.equals(event.eventType())||!versions.contains(event.eventVersion())||event.timestamp()==null||event.timestamp().isAfter(Instant.now().plusSeconds(60))||event.payload()==null||!event.payload().isObject())throw new IllegalArgumentException();
   return event;
  }catch(Exception error){throw new IllegalArgumentException("INVALID_FLOW_EVENT");}
 }
 public static <T>T convert(ObjectMapper mapper,JsonNode value,Class<T> type){try{return mapper.treeToValue(value,type);}catch(Exception error){throw new IllegalArgumentException("INVALID_FLOW_PAYLOAD");}}
 public static String json(ObjectMapper mapper,Object value){try{return mapper.writeValueAsString(value);}catch(Exception error){throw new IllegalArgumentException("FLOW_SERIALIZATION_FAILED");}}
 public static <T>T value(ObjectMapper mapper,String value,Class<T> type){try{return mapper.readerFor(type).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(value);}catch(Exception error){throw new IllegalArgumentException("INVALID_STORED_FLOW_DOCUMENT");}}
 public static String fingerprint(ObjectMapper mapper,Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(mapper,value).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
}
