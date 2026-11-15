package com.cognizant.stablecoins.ledger.accounting;

import com.cognizant.stablecoins.ledger.api.dto.JournalEntryRequest;
import com.cognizant.stablecoins.ledger.exception.JournalValidationException;
import com.cognizant.stablecoins.ledger.repository.LedgerAccountRepository;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.HashSet;
import static com.cognizant.stablecoins.ledger.accounting.AccountingProperties.Role;

@Component
public class AccountingPolicy implements SmartInitializingSingleton {
    private final AccountingProperties properties;
    private final LedgerAccountRepository accounts;
    public AccountingPolicy(AccountingProperties properties,LedgerAccountRepository accounts) { this.properties=properties; this.accounts=accounts; }
    @Override public void afterSingletonsInstantiated() { validate(); }
    public void validate() {
        if (properties.getVersion()==null || !properties.getVersion().matches("[a-zA-Z0-9._-]{1,60}")) throw failure();
        var codes=new HashSet<String>();
        for (Role role:Role.values()) { if(!codes.add(code(role))) throw failure(); }
    }
    public String version() { return properties.getVersion(); }
    public String code(Role role) {
        String code=properties.getAccounts().get(role);
        if(code==null || !code.matches("[A-Za-z0-9]{1,10}")) throw failure();
        var account=accounts.findByAccountCode(code).orElseThrow(AccountingPolicy::failure);
        if(!account.isActive() || !role.asset.equals(account.getAsset()) || !role.type.equals(account.getAccountType())) throw failure();
        return code;
    }
    public JournalEntryRequest line(Role role,BigDecimal amount,boolean debit) {
        var line=new JournalEntryRequest(); line.setAccountCode(code(role)); line.setAsset(role.asset);
        if(debit) line.setDebit(amount); else line.setCredit(amount);
        return line;
    }
    private static JournalValidationException failure() { return new JournalValidationException("ACCOUNTING_POLICY_CONFIGURATION_INVALID"); }
}
