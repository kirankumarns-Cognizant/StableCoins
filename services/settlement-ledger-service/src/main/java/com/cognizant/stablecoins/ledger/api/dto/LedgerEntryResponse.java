package com.cognizant.stablecoins.ledger.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class LedgerEntryResponse {
    private UUID entryId;
    private String accountCode;
    private String accountName;
    private String asset;
    private BigDecimal debit;
    private BigDecimal credit;
    private UUID reversalOf;

    public String getExactDebit(){return debit==null?null:debit.toPlainString();}
    public String getExactCredit(){return credit==null?null:credit.toPlainString();}
    public UUID getEntryId() { return entryId; }
    public void setEntryId(UUID entryId) { this.entryId = entryId; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }

    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }

    public UUID getReversalOf() { return reversalOf; }
    public void setReversalOf(UUID reversalOf) { this.reversalOf = reversalOf; }
}
