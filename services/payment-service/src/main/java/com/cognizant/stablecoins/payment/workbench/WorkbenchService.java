package com.cognizant.stablecoins.payment.workbench;

import com.cognizant.stablecoins.payment.service.PaymentService;
import com.cognizant.stablecoins.payment.intent.AuthorizedCustomer;
import com.cognizant.stablecoins.payment.security.CustomerAccessProperties;
import com.cognizant.stablecoins.payment.config.RoutingPolicyProperties;
import com.cognizant.stablecoins.flow.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;

/** ADR-007/009: read-only customer projection; never calls the private Visa HTTP API. */
@Service
@ConditionalOnProperty(name="app.flow.enabled",havingValue="true")
public class WorkbenchService {
 private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final PaymentService payments;
 private final CustomerAccessProperties access; private final RoutingPolicyProperties policies;
 public WorkbenchService(JdbcTemplate jdbc,ObjectMapper mapper,PaymentService payments,CustomerAccessProperties access,RoutingPolicyProperties policies){this.jdbc=jdbc;this.mapper=mapper;this.payments=payments;this.access=access;this.policies=policies;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> catalog(AuthorizedCustomer actor){
  var result=new LinkedHashMap<String,Object>();
  var membership=access.getMemberships().get(actor.actorId());
  // Defense in depth for callers other than the controller.
  if(membership==null||!membership.customerId().equals(actor.customerId())||!membership.roles().contains(com.cognizant.stablecoins.platform.security.CustomerMembershipProperties.Role.PAYMENT_OPERATOR))throw new IllegalStateException("CUSTOMER_MEMBERSHIP_REQUIRED");
  result.put("customerId",actor.customerId());result.put("actorId",actor.actorId());result.put("roles",membership.roles());
  result.put("policy",exact(policies.getPolicies().get(actor.customerId())));
  result.put("accounts",rows("select account_id,currency,account_type,balance,status from account where customer_id=? and status='ACTIVE' order by account_id limit 100",actor.customerId()));
  result.put("beneficiaries",rows("select beneficiary_id,name,country,currency,wallet_address,status from beneficiary where customer_id=? and status='ACTIVE' order by beneficiary_id limit 100",actor.customerId()));
  result.put("payments",rows("select p.payment_id,p.correlation_id,p.amount,p.source_currency,p.status,p.created_at,p.intent_snapshot #>> '{instruction,deliverBy}' as deliver_by from payment p join account a on a.account_id=p.sender_account_id where a.customer_id=? order by p.created_at desc,p.payment_id limit 50",actor.customerId()));
  result.put("paymentWindowLimit",50);result.put("catalogLimit",100);return result;
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> evidence(String id,AuthorizedCustomer actor){
  // Must precede every cross-owner read, including empty or unavailable sections.
  payments.getPayment(id,actor);
  var result=new LinkedHashMap<String,Object>();
  result.put("paymentId",id);
  result.put("preparations",rows("select request_id,status,recommendation_id,failure_code,created_at from payment_flow_preparation where payment_id=? order by created_at desc,request_id limit 50",id));
  result.put("recommendations",rows("select recommendation_id,version,state,created_at,expires_at from routing_recommendation where payment_id=? order by version desc limit 50",id));
  result.put("decisions",rows("select d.recommendation_id,d.actor_id,d.action,d.decided_at from routing_decision d join routing_recommendation r on r.recommendation_id=d.recommendation_id where r.payment_id=? order by d.decided_at,d.id",id));
  result.put("agentRuns",rows("select run_id,recommendation_id,status,created_at,failure_code from routing_agent_run where payment_id=? order by created_at desc,run_id limit 50",id));
  result.put("execution",rows("select execution_id,recommendation_id,preparation_id,status,transaction_id,failure_code from payment_execution where payment_id=?",id));
  result.put("closure",rows("select phase,failure_code from payment_closure where payment_id=?",id));
  var flows=new FlowReadModels(jdbc,mapper);var lifecycle=new LifecycleReadModels(jdbc,mapper);
  result.put("treasury",section(()->jdbc.query("select request_id from payment_flow_preparation where payment_id=? order by created_at desc,request_id limit 1",(r,n)->r.getObject(1,UUID.class),id).stream().findFirst().flatMap(flows::treasury).orElse(null)));
  result.put("blockchain",section(()->{
   var ids=jdbc.query("select execution_id from payment_execution where payment_id=? and transaction_id is not null",(r,n)->r.getObject(1,UUID.class),id);
   return ids.isEmpty()?null:lifecycle.chain(flows.execution(ids.getFirst()));
  }));
  result.put("journals",section(()->lifecycle.journals(id)));
  result.put("payout",section(()->lifecycle.payout(id).orElse(null)));
  result.put("reconciliation",section(()->jdbc.query("select document::text from reconciliation where payment_id=? order by created_at desc,reconciliation_id desc limit 1",(r,n)->FlowJson.value(mapper,r.getString(1),JsonNode.class),id).stream().findFirst().orElse(null)));
  return result;
 }
 private Map<String,Object> section(Supplier<Object> read){
  try{Object value=read.get();if(value instanceof FlowContract.Stored<?> stored&&stored.document()==null)return Map.of("status",stored.status(),"reason",stored.failureCode()==null?"EVIDENCE_NOT_READY":stored.failureCode());return Map.of("status",value==null?"PENDING":"AVAILABLE","data",exact(value));}
  catch(IllegalStateException|NoSuchElementException|IllegalArgumentException error){return Map.of("status","REVIEW_REQUIRED","reason","EVIDENCE_BINDING_OR_READ_FAILED");}
 }
 private List<Map<String,Object>> rows(String sql,Object id){return jdbc.query(sql,(r,n)->{
  var row=new LinkedHashMap<String,Object>();var meta=r.getMetaData();
  for(int col=1;col<=meta.getColumnCount();col++) {String column=meta.getColumnLabel(col);Object value=r.getObject(col);if("deliver_by".equals(column)&&value instanceof String stored)value=deadline(stored);row.put(column,value instanceof BigDecimal d?d.toPlainString():value instanceof Timestamp t?t.toInstant().toString():value);}
  return row;
 },id);}
 /** Hibernate JSONB can store Instant as epoch seconds; the public catalog always exposes ISO text. */
 private String deadline(String stored){
  if(!stored.matches("-?\\d+(?:\\.\\d{1,9})?"))return stored;
  try{
   return mapper.readerFor(java.time.Instant.class).with(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS).readValue(stored).toString();
  }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){return stored;}
 }
 /** Decimal strings preserve wei and ledger precision across the JSON/JavaScript boundary. */
 public JsonNode exact(Object value){return strings(mapper.valueToTree(value));}
 private JsonNode strings(JsonNode node){
  if(node.isFloatingPointNumber())return TextNode.valueOf(node.decimalValue().toPlainString());
  if(node.isObject()){var out=mapper.createObjectNode();node.fields().forEachRemaining(e->out.set(e.getKey(),strings(e.getValue())));return out;}
  if(node.isArray()){var out=mapper.createArrayNode();node.forEach(e->out.add(strings(e)));return out;}
  return node;
 }
}
