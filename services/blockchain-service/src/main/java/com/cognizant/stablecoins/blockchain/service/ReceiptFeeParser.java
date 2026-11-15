package com.cognizant.stablecoins.blockchain.service;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.*;
/** Parses mined receipt quantities without silently dropping rollup fee fields. */
public final class ReceiptFeeParser {
 private ReceiptFeeParser(){}
 public static BlockchainReceiptResult parse(JsonNode r,long chainId){
  String hash=r.path("transactionHash").asText();
  if(!hash.matches("0x[0-9a-fA-F]{64}"))throw new IllegalArgumentException("INVALID_RECEIPT_HASH");
  var status=quantity(r,"status");if(status==null||!(status.equals(BigInteger.ZERO)||status.equals(BigInteger.ONE)))throw new IllegalArgumentException("INVALID_RECEIPT_STATUS");
  var gas=quantity(r,"gasUsed");var price=quantity(r,"effectiveGasPrice");
  var result=new BlockchainReceiptResult(hash,quantity(r,"blockNumber").longValueExact(),quantity(r,"status").equals(BigInteger.ONE),new BigDecimal(gas),price==null?null:new BigDecimal(price));
  if(gas.signum()<=0||price==null)return result.fees(null,false,"EXECUTION_FEE_MISSING");
  if(chainId==11155111L)return result.fees(BigInteger.ZERO,true,null);
  if(chainId!=84532L)return result.fees(null,false,"UNSUPPORTED_FEE_NETWORK");
  var l1=quantity(r,"l1Fee");if(l1==null)return result.fees(null,false,"L1_FEE_MISSING");
  var operator=quantity(r,"operatorFee");
  var scalar=quantity(r,"operatorFeeScalar");var constant=quantity(r,"operatorFeeConstant");
  // Non-zero operator parameters require an evidenced fee; fork-dependent formulas are not guessed.
  if(operator==null&&((scalar!=null&&scalar.signum()!=0)||(constant!=null&&constant.signum()!=0)))return result.fees(null,false,"OPERATOR_FEE_UNSUPPORTED");
  return result.fees(l1.add(operator==null?BigInteger.ZERO:operator),true,null);
 }
 public static BigInteger quantity(JsonNode r,String field){
  var node=r.get(field);if(node==null||node.isNull())return null;
  String value=node.asText();if(!value.matches("0x[0-9a-fA-F]{1,64}"))throw new IllegalArgumentException("INVALID_RECEIPT_QUANTITY");
  return new BigInteger(value.substring(2),16);
 }
}
