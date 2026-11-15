package com.cognizant.stablecoins.payment.agent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;
@Component @ConfigurationProperties("app.agent")
public class AgentSettings {
    private boolean enabled;
    private java.math.BigDecimal maximumInferenceCostUsd=new java.math.BigDecimal("1.00"),inputUsdPerMillion=java.math.BigDecimal.ZERO,outputUsdPerMillion=java.math.BigDecimal.ZERO;
    public java.math.BigDecimal getMaximumInferenceCostUsd(){return maximumInferenceCostUsd;}public void setMaximumInferenceCostUsd(java.math.BigDecimal v){maximumInferenceCostUsd=v;}
    public java.math.BigDecimal getInputUsdPerMillion(){return inputUsdPerMillion;}public void setInputUsdPerMillion(java.math.BigDecimal v){inputUsdPerMillion=v;}
    public java.math.BigDecimal getOutputUsdPerMillion(){return outputUsdPerMillion;}public void setOutputUsdPerMillion(java.math.BigDecimal v){outputUsdPerMillion=v;}
    java.math.BigDecimal estimatedCost(long in,long out){return inputUsdPerMillion.multiply(java.math.BigDecimal.valueOf(in)).add(outputUsdPerMillion.multiply(java.math.BigDecimal.valueOf(out))).movePointLeft(6);}

    private Set<UUID> allowedCustomerIds=Set.of();
    private int maximumRunsPerPayment=10,maximumRounds=12,maximumToolCalls=24,maximumOutputTokens=4096,maximumTotalTokens=250000,
        maximumRequestBytes=65536,maximumResponseBytes=131072,maximumConcurrentRuns=2,deadlineMs=90000;
    public void validate(){
        if(maximumInferenceCostUsd==null||inputUsdPerMillion==null||outputUsdPerMillion==null||maximumInferenceCostUsd.signum()<=0
            ||inputUsdPerMillion.signum()<0||outputUsdPerMillion.signum()<0||maximumInferenceCostUsd.compareTo(new java.math.BigDecimal("100"))>0
            ||enabled&&(inputUsdPerMillion.signum()<=0||outputUsdPerMillion.signum()<=0))throw new IllegalArgumentException("Enabled agent requires positive configured model pricing and inference budget");
        if(maximumRunsPerPayment<1||maximumRunsPerPayment>100||maximumRounds<1||maximumRounds>20||maximumToolCalls<1||maximumToolCalls>40||maximumOutputTokens<256||maximumOutputTokens>8192
            ||maximumTotalTokens<maximumOutputTokens||maximumTotalTokens>1000000||maximumRequestBytes<2048||maximumRequestBytes>131072
            ||maximumResponseBytes<2048||maximumResponseBytes>262144||maximumConcurrentRuns<1||maximumConcurrentRuns>8||deadlineMs<1000||deadlineMs>180000)
            throw new IllegalArgumentException("Invalid agent resource limits");
    }
    public void require(UUID customer){validate();if(!enabled)throw new AgentFailure("AGENT_DISABLED");if(!allowedCustomerIds.contains(customer))throw new AgentFailure("AGENT_CUSTOMER_NOT_ENABLED");}
    public int getMaximumRunsPerPayment(){return maximumRunsPerPayment;}public void setMaximumRunsPerPayment(int v){maximumRunsPerPayment=v;}
    public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}
    public Set<UUID> getAllowedCustomerIds(){return allowedCustomerIds;}public void setAllowedCustomerIds(Set<UUID> v){allowedCustomerIds=Set.copyOf(v);}
    public int getMaximumRounds(){return maximumRounds;}public void setMaximumRounds(int v){maximumRounds=v;}
    public int getMaximumToolCalls(){return maximumToolCalls;}public void setMaximumToolCalls(int v){maximumToolCalls=v;}
    public int getMaximumOutputTokens(){return maximumOutputTokens;}public void setMaximumOutputTokens(int v){maximumOutputTokens=v;}
    public int getMaximumTotalTokens(){return maximumTotalTokens;}public void setMaximumTotalTokens(int v){maximumTotalTokens=v;}
    public int getMaximumRequestBytes(){return maximumRequestBytes;}public void setMaximumRequestBytes(int v){maximumRequestBytes=v;}
    public int getMaximumResponseBytes(){return maximumResponseBytes;}public void setMaximumResponseBytes(int v){maximumResponseBytes=v;}
    public int getMaximumConcurrentRuns(){return maximumConcurrentRuns;}public void setMaximumConcurrentRuns(int v){maximumConcurrentRuns=v;}
    public int getDeadlineMs(){return deadlineMs;}public void setDeadlineMs(int v){deadlineMs=v;}
}
