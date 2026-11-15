package com.cognizant.stablecoins.ledger.event.consumer;

import com.cognizant.stablecoins.ledger.accounting.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.ledger.accounting.PostingEvidence.*;

class BlockchainConfirmedConsumerTest {
    final PaymentAccountingService accounting=mock(PaymentAccountingService.class);
    final ObjectMapper mapper=new ObjectMapper();
    final BlockchainConfirmedConsumer consumer=new BlockchainConfirmedConsumer(accounting,mapper);
    final String hash="0x"+"a".repeat(64);
    @BeforeEach void setup(){when(accounting.context(anyString(),anyString())).thenAnswer(i->new PaymentAccountingService.Context(i.getArgument(0),i.getArgument(1),
            new Terms(UUID.randomUUID(),UUID.randomUUID(),1,"SIMULATED-FUNDING-1",new BigDecimal("10000"),new BigDecimal("10000"),Outcome.USDC_WALLET,null)));}
    String event(String payment,String correlation,String type) throws Exception {
        var node=mapper.createObjectNode();node.put("eventId",UUID.randomUUID().toString());node.put("timestamp","2026-11-14T01:00:00Z");node.put("eventVersion","1.0");
        node.put("eventType",type);node.put("paymentId",payment);node.put("correlationId",correlation);
        var p=node.putObject("payload");p.put("txHash",hash);p.put("amount",10000);p.put("tokenSymbol","USDC");p.put("network","base-sepolia");
        p.put("blockNumber",123);p.put("chainId",84532);p.put("evidenceMode","LIVE");
        return mapper.writeValueAsString(node);
    }
    @Test void payAndCorrIdentifiersReachExistingLedgerWithoutConversion() throws Exception {
        consumer.consume(event("PAY-10001","CORR-10001","BlockchainTransactionConfirmed"));
        verify(accounting).context("PAY-10001","CORR-10001");
        verify(accounting).confirmed(argThat(c->c.paymentId().equals("PAY-10001") && c.correlationId().equals("CORR-10001")),eq(hash),eq(Mode.REAL_TESTNET));
    }
    @Test void legacyUuidStringsRemainUnchanged() throws Exception {
        String payment=UUID.randomUUID().toString(),correlation=UUID.randomUUID().toString();consumer.consume(event(payment,correlation,"BlockchainTransactionConfirmed"));verify(accounting).context(payment,correlation);
    }
    @Test void missingCorrelationDoesNotFabricateCorrelationFromPayment() throws Exception {
        String json=event("PAY-10001",null,"BlockchainTransactionConfirmed");assertThatThrownBy(()->consumer.consume(json)).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);
    }
    @Test void unrelatedEventIsRejectedBeforePosting() throws Exception {
        String json=event("PAY-10001","CORR-10001","PaymentCreated");assertThatThrownBy(()->consumer.consume(json)).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);
    }
    @Test void unclassifiedEvidenceIsNotCalledReal() throws Exception {String json=event("PAY-10001","CORR-10001","BlockchainTransactionConfirmed").replace("LIVE","UNKNOWN");assertThatThrownBy(()->consumer.consume(json)).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);}
    @Test void unknownAssetCannotDefaultToUsdc() throws Exception {String json=event("PAY-10001","CORR-10001","BlockchainTransactionConfirmed").replace("USDC","GBP");assertThatThrownBy(()->consumer.consume(json)).hasMessageContaining("Failed to deserialize");verifyNoInteractions(accounting);}
    @Test void mismatchedAmountIsRejectedBeforePosting() throws Exception {String json=event("PAY-10001","CORR-10001","BlockchainTransactionConfirmed").replace("10000,","9999,");assertThatThrownBy(()->consumer.consume(json)).hasMessage("CONFIRMED_AMOUNT_MISMATCH");verify(accounting,never()).confirmed(any(),any(),any());}
    @Test void mockEvidenceRemainsMock() throws Exception {consumer.consume(event("PAY-10001","CORR-10001","BlockchainTransactionConfirmed").replace("LIVE","MOCK"));verify(accounting).confirmed(any(),eq(hash),eq(Mode.MOCK));}
}
