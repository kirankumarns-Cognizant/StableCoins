package com.cognizant.stablecoins.payment.settlement;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.ResultSet;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.cognizant.stablecoins.payment.settlement.BindingFixtures.*;

class SettlementQuoteStoreTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final SettlementQuoteStore store = new SettlementQuoteStore(jdbc);
    final Map<UUID, Object[]> rows = new HashMap<>();
    final SettlementBinding.Instruction i = instruction();
    final SettlementQuote quote = SettlementQuoteFixtures.quote(i, UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"}) void setup() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("customer_id", i.customerId(), "correlation_id", i.correlationId())));
        when(jdbc.update(startsWith("insert into payment_settlement_quote"), any(Object[].class))).thenAnswer(c -> {
            Object[] args = (Object[])c.getRawArguments()[1];
            return rows.putIfAbsent((UUID)args[0], args.clone()) == null ? 1 : 0;
        });
        when(jdbc.query(startsWith("select quote_id"), any(RowMapper.class), any(Object[].class))).thenAnswer(c -> {
            Object[] args = (Object[])c.getRawArguments()[2]; Object[] row = rows.get(args[0]);
            if (row == null) return List.of();
            var rs = mock(ResultSet.class);
            for (int col = 1; col <= 3; col++) when(rs.getObject(col, UUID.class)).thenReturn((UUID)row[col]);
            when(rs.getString(4)).thenReturn((String)row[4]); when(rs.getString(5)).thenReturn((String)row[5]);
            when(rs.getString(6)).thenReturn((String)row[7]); when(rs.getString(7)).thenReturn((String)row[8]);
            return List.of(((RowMapper)c.getArgument(1)).mapRow(rs, 0));
        });
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }
    @Test void exactDraftReplaySurvivesCanonicalStorage() {
        store.capture(quote, "operator"); store.capture(quote, "operator");
        assertThat(rows).hasSize(1);
        assertThat(store.find(quote.review().recommendationId())).contains(quote);
    }
    @Test void changedQuoteCannotReplaceExistingDraft() {
        store.capture(quote, "operator");
        var changed = SettlementQuoteFixtures.quote(i, quote.review().preparationId(), quote.review().recommendationId());
        assertThatThrownBy(() -> store.capture(changed, "operator")).hasMessage("SETTLEMENT_QUOTE_KEY_CONFLICT");
        assertThat(store.find(quote.review().recommendationId())).contains(quote);
    }
    @Test void fingerprintTamperingFailsClosed() {
        store.capture(quote, "operator"); rows.get(quote.review().recommendationId())[8] = "0".repeat(64);
        assertThatThrownBy(() -> store.find(quote.review().recommendationId())).hasMessage("SETTLEMENT_QUOTE_INTEGRITY_FAILED");
    }
    @Test void paymentAndPreparationColumnsMustAgreeWithDocument() {
        store.capture(quote, "operator"); var row = rows.get(quote.review().recommendationId());
        for (int col : new int[]{1, 2, 3, 4, 5}) {
            Object original = row[col]; row[col] = col <= 3 ? UUID.randomUUID() : "wrong";
            assertThatThrownBy(() -> store.find(quote.review().recommendationId())).hasMessage("SETTLEMENT_QUOTE_INTEGRITY_FAILED");
            row[col] = original;
        }
    }
    @Test void writeCannotRunOutsideOwnerTransaction() {
        TransactionSynchronizationManager.clear();
        assertThatThrownBy(() -> store.capture(quote, "operator")).hasMessage("PAYMENT_OWNER_TRANSACTION_REQUIRED");
        verifyNoInteractions(jdbc);
    }
    @Test void foreignCustomerCannotCaptureDraft() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(Map.of("customer_id", UUID.randomUUID(), "correlation_id", i.correlationId())));
        assertThatThrownBy(() -> store.capture(quote, "operator")).hasMessage("SETTLEMENT_PAYMENT_SCOPE_MISMATCH");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
