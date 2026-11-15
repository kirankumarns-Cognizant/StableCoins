package com.cognizant.stablecoins.ledger.accounting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@ConfigurationProperties("app.ledger.accounting")
public class AccountingProperties {
    public enum Role {
        CORPORATE_USD("110001","USD","ASSET"), FUNDING_USD("110002","USD","ASSET"),
        CONVERSION_USD("110003","USD","ASSET"), ALLOCATION_USDC("220001","USDC","LIABILITY"),
        TREASURY_USDC("120001","USDC","ASSET"), TRANSIT_USDC("120002","USDC","ASSET"),
        BENEFICIARY_USDC("120003","USDC","ASSET"), GAS_EXPENSE_ETH("510001","ETH","EXPENSE"),
        GAS_CLEARING_ETH("130001","ETH","ASSET"), PROVIDER_USD("110004","USD","ASSET"),
        PAYABLE_USD("210001","USD","LIABILITY"), PROVIDER_INR("140001","INR","ASSET"), PAYABLE_INR("240001","INR","LIABILITY");
        public final String defaultCode, asset, type;
        Role(String code,String asset,String type) { this.defaultCode=code; this.asset=asset; this.type=type; }
    }
    private String version="adr015-v1";
    private Map<Role,String> accounts=new EnumMap<>(Role.class);
    public AccountingProperties() { for (Role role:Role.values()) accounts.put(role,role.defaultCode); }
    public String getVersion() { return version; }
    public void setVersion(String value) { version=value; }
    public Map<Role,String> getAccounts() { return accounts; }
    public void setAccounts(Map<Role,String> value) { accounts=value; }
}
