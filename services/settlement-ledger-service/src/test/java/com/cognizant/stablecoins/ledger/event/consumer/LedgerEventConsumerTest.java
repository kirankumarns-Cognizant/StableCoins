package com.cognizant.stablecoins.ledger.event.consumer;
import com.cognizant.stablecoins.ledger.accounting.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LedgerEventConsumerTest {
    final ObjectMapper mapper=new ObjectMapper();final PaymentAccountingService accounting=mock(PaymentAccountingService.class);
    final StablecoinFundingConsumer funding=new StablecoinFundingConsumer(accounting,mapper);
    final BlockchainSubmittedConsumer submitted=new BlockchainSubmittedConsumer(accounting,mapper);
    final Terms terms=new Terms(UUID.randomUUID(),UUID.randomUUID(),1,"SIM-FUNDING-1",new BigDecimal("10000"),new BigDecimal("9995"),Outcome.USDC_WALLET,null);
    ObjectNode event(String type,String version){var e=mapper.createObjectNode();e.put("eventId",UUID.randomUUID().toString());e.put("eventType",type);e.put("eventVersion",version);e.put("paymentId","PAY-1");e.put("correlationId","CORR-1");e.put("timestamp","2026-11-14T01:00:00Z");e.putObject("payload");return e;}
    ObjectNode fundingEvent(){var e=event("StablecoinFundingCompleted","2.0");var p=(ObjectNode)e.get("payload");p.put("simulation",true);p.put("fundingMode","SIMULATED");p.put("sourceCurrency","USD");p.put("destinationAsset","USDC");p.set("terms",mapper.valueToTree(terms));return e;}
    ObjectNode submittedEvent(){var e=event("BlockchainTransactionSubmitted","1.0");var p=(ObjectNode)e.get("payload");p.put("txHash","0x"+"a".repeat(64));p.put("network","base-sepolia");p.put("chainId",84532);p.put("tokenSymbol","USDC");p.put("amount",9995);p.put("evidenceMode","MOCK");return e;}
    @Test void fundingCarriesImmutableQuoteTermsToExistingAccountingCommand(){funding.consume(fundingEvent().toString());verify(accounting).fundAndConvert(new PaymentAccountingService.Context("PAY-1","CORR-1",terms));}
    @Test void legacyFundingCannotInventQuoteBindings(){var e=fundingEvent();e.put("eventVersion","1.0");assertThatThrownBy(()->funding.consume(e.toString())).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);}
    @Test void simulatedFundingCannotClaimRealMoney(){var e=fundingEvent();((ObjectNode)e.get("payload")).put("simulation",false);assertThatThrownBy(()->funding.consume(e.toString())).hasMessage("SIMULATED_FUNDING_EVIDENCE_REQUIRED");verifyNoInteractions(accounting);}
    @Test void missingQuoteTermsAreRejected(){var e=fundingEvent();((ObjectNode)e.get("payload")).remove("terms");assertThatThrownBy(()->funding.consume(e.toString())).hasMessage("ACCEPTED_QUOTE_TERMS_REQUIRED");verifyNoInteractions(accounting);}
    @Test void duplicateJsonKeysAreRejected(){String e=fundingEvent().toString().replace("\"eventVersion\":\"2.0\"","\"eventVersion\":\"2.0\",\"eventVersion\":\"1.0\"");assertThatThrownBy(()->funding.consume(e)).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);}
    @Test void missingEventIdentityIsRejected(){var e=fundingEvent();e.remove("eventId");assertThatThrownBy(()->funding.consume(e.toString())).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);}
    @Test void submissionPreservesMockModeAndBoundAllocation(){when(accounting.context("PAY-1","CORR-1")).thenReturn(new PaymentAccountingService.Context("PAY-1","CORR-1",terms));submitted.consume(submittedEvent().toString());verify(accounting).submitted(any(),eq("0x"+"a".repeat(64)),eq(Mode.MOCK));}
    @Test void changedSubmittedAmountIsRejected(){when(accounting.context("PAY-1","CORR-1")).thenReturn(new PaymentAccountingService.Context("PAY-1","CORR-1",terms));var e=submittedEvent();((ObjectNode)e.get("payload")).put("amount",9994);assertThatThrownBy(()->submitted.consume(e.toString())).hasMessage("SUBMITTED_AMOUNT_MISMATCH");verify(accounting,never()).submitted(any(),any(),any());}
    @Test void invalidNetworkNeverGetsAccountingContext(){var e=submittedEvent();((ObjectNode)e.get("payload")).put("chainId",1);assertThatThrownBy(()->submitted.consume(e.toString())).hasMessage("UNSUPPORTED_CHAIN_EVIDENCE");verifyNoInteractions(accounting);}
}
