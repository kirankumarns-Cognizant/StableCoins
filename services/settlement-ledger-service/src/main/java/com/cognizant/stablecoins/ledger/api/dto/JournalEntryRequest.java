package com.cognizant.stablecoins.ledger.api.dto;

import java.math.BigDecimal;

public class JournalEntryRequest {
    private String accountCode;
    private String asset;
    private BigDecimal debit;
    private BigDecimal credit;

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }

    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
}
