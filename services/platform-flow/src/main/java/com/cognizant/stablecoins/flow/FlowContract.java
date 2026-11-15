package com.cognizant.stablecoins.flow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Immutable cross-service contracts. No approval, wallet key or money-mutation capability. */
public final class FlowContract {
 private FlowContract(){}
 public record Request(UUID requestId,String paymentId,String correlationId,UUID customerId,
   BigDecimal sourceUsd,BigDecimal transferUsdc,String recipientWallet,String outcome,String requestedRail,
   String corridor,Instant deadline,Set<String> providerIds){
  public Request {
   Objects.requireNonNull(requestId);identifier(paymentId,50);identifier(correlationId,60);Objects.requireNonNull(customerId);
   sourceUsd=money(sourceUsd,2);transferUsdc=money(transferUsdc,6);
   if(sourceUsd.compareTo(transferUsdc)!=0)throw new IllegalArgumentException("ONLY_NOTIONAL_ONE_TO_ONE_SUPPORTED");
   if(recipientWallet==null||!recipientWallet.matches("0x[0-9a-fA-F]{40}")||recipientWallet.equalsIgnoreCase("0x"+"0".repeat(40)))throw new IllegalArgumentException("INVALID_RECIPIENT");
   recipientWallet=recipientWallet.toLowerCase(Locale.ROOT);
   if(!Set.of("USDC_WALLET","SIMULATED_BANK_USD","SIMULATED_BANK_INR").contains(outcome)||!Set.of("AUTO","STABLECOIN","STABLECOIN_PLUS_VISA").contains(requestedRail))throw new IllegalArgumentException("INVALID_FLOW_PATH");
   identifier(corridor,50);Objects.requireNonNull(deadline);Objects.requireNonNull(providerIds);
   if(providerIds.isEmpty()||providerIds.size()>20)throw new IllegalArgumentException("INVALID_PROVIDER_SET");
   providerIds.forEach(p->identifier(p,64));providerIds=Collections.unmodifiableSortedSet(new TreeSet<>(providerIds));
  }
 }
 public record Quote(UUID requestId,UUID quoteId,int version,String providerId,String simulationVersion,
   BigDecimal sourceUsd,BigDecimal transferUsdc,BigDecimal feeUsd,long completed,long attempts,long latencyMillis,
   BigDecimal fraudScore,boolean available,Instant createdAt,Instant expiresAt, String payoutCurrency, BigDecimal payoutAmount, BigDecimal fxRate, BigDecimal providerFiatAvailable){
  public Quote(UUID requestId,UUID quoteId,int version,String providerId,String simulationVersion,BigDecimal sourceUsd,BigDecimal transferUsdc,BigDecimal feeUsd,long completed,long attempts,long latencyMillis,BigDecimal fraudScore,boolean available,Instant createdAt,Instant expiresAt){this(requestId,quoteId,version,providerId,simulationVersion,sourceUsd,transferUsdc,feeUsd,completed,attempts,latencyMillis,fraudScore,available,createdAt,expiresAt,null,null,null,null);}
  public Quote {Objects.requireNonNull(requestId);Objects.requireNonNull(quoteId);identifier(providerId,64);identifier(simulationVersion,50);
   sourceUsd=money(sourceUsd,2);transferUsdc=money(transferUsdc,6);Objects.requireNonNull(feeUsd);
   if(payoutCurrency!=null){if(!Set.of("USD","INR").contains(payoutCurrency)||fxRate==null||fxRate.signum()<=0||fxRate.stripTrailingZeros().scale()>8||("USD".equals(payoutCurrency)&&fxRate.compareTo(BigDecimal.ONE)!=0)||providerFiatAvailable==null||providerFiatAvailable.signum()<0)throw new IllegalArgumentException("INVALID_PAYOUT_QUOTE");payoutAmount=money(payoutAmount,2);if(sourceUsd.multiply(fxRate).setScale(2,java.math.RoundingMode.HALF_EVEN).compareTo(payoutAmount)!=0)throw new IllegalArgumentException("PAYOUT_RATE_MISMATCH");}
   else if(payoutAmount!=null||fxRate!=null||providerFiatAvailable!=null)throw new IllegalArgumentException("INVALID_PAYOUT_QUOTE");Objects.requireNonNull(fraudScore);
   if(version<1||feeUsd.signum()<0||feeUsd.stripTrailingZeros().scale()>2||attempts<1||completed<0||completed>attempts||latencyMillis<1||fraudScore.signum()<0||fraudScore.compareTo(new BigDecimal("100"))>0||createdAt==null||expiresAt==null||!expiresAt.isAfter(createdAt))throw new IllegalArgumentException("INVALID_SIMULATED_QUOTE");
  }
 }
 public record Treasury(String evidenceMode,String network,long chainId,String tokenContract,String walletAddress,
   BigDecimal totalUsdc,BigDecimal reservedUsdc,BigDecimal availableUsdc,BigDecimal totalEth,BigDecimal reservedEth,
   BigDecimal availableEth,BigDecimal estimatedGas,BigDecimal gasLimit,BigDecimal gasPriceWei,BigDecimal requiredGasEth,
   Instant observedAt,Instant expiresAt,List<String> reasons){public Treasury{reasons=List.copyOf(reasons);}}
 public record Command(UUID executionId){public Command{Objects.requireNonNull(executionId);}}
 public record Execution(UUID executionId,UUID recommendationId,UUID preparationId,String paymentId,String correlationId,
   String status,Request request,UUID transactionId,Instant expiresAt){}
 public record Stored<T>(String status,T document,String failureCode){}
 public static BigDecimal money(BigDecimal value,int scale){if(value==null||value.signum()<=0||value.stripTrailingZeros().scale()>scale||value.precision()-value.scale()>12)throw new IllegalArgumentException("INVALID_FLOW_AMOUNT");return value.stripTrailingZeros();}
 public static void identifier(String value,int limit){if(value==null||value.length()>limit||!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"))throw new IllegalArgumentException("INVALID_FLOW_IDENTIFIER");}
}
