package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.SettlementQuote;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** No default quote/provider/fee. These settings cannot activate private payments. */
@Component
@ConfigurationProperties(prefix = "app.besu.quote")
public class SettlementQuoteProperties {
    private SettlementQuote.Terms terms;
    public SettlementQuote.Terms getTerms() { return terms; }
    public void setTerms(SettlementQuote.Terms value) { terms = value; }
}
