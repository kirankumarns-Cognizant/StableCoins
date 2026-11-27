package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.ledger.api.dto.JournalEntryRequest;
import com.cognizant.stablecoins.ledger.exception.JournalValidationException;
import com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

/** Separate ADR-016 mapping. Never remaps existing USDC/ETH accounts. */
@Component
@ConditionalOnProperty(name="app.besu.accounting.enabled",havingValue="true")
public class BesuAccountingPolicy implements SmartInitializingSingleton {
    public static final String VERSION="adr016-control-v1";
    public enum Role {
        TREASURY("150001","DEMOUSD","ASSET"), TRANSIT("150002","DEMOUSD","ASSET"),
        DELIVERED("150003","DEMOUSD","ASSET"), ALLOCATION("250001","DEMOUSD","LIABILITY"),
        GAS_EXPENSE("550001","BESU_GAS","EXPENSE"), GAS_CLEARING("150004","BESU_GAS","ASSET");
        public final String code,asset,type;
        Role(String code,String asset,String type) { this.code=code;this.asset=asset;this.type=type; }
    }
    private final LedgerAccountRepository accounts;
    public BesuAccountingPolicy(LedgerAccountRepository accounts) { this.accounts=accounts; }
    @Override public void afterSingletonsInstantiated() { for(var role:Role.values()) check(role); }
    private void check(Role role) {
        var account=accounts.findByAccountCode(role.code).orElseThrow(BesuAccountingPolicy::failure);
        if (!account.isActive() || !role.asset.equals(account.getAsset()) || !role.type.equals(account.getAccountType())) throw failure();
    }
    public JournalEntryRequest line(Role role,BigDecimal amount,boolean debit) {
        check(role);
        var line=new JournalEntryRequest();line.setAccountCode(role.code);line.setAsset(role.asset);
        if(debit) line.setDebit(amount); else line.setCredit(amount);
        return line;
    }
    private static JournalValidationException failure() { return new JournalValidationException("BESU_ACCOUNTING_POLICY_INVALID"); }
}
