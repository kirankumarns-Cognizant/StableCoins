package com.cognizant.stablecoins.ledger.besu;

import com.cognizant.stablecoins.flow.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.cognizant.stablecoins.ledger.besu.BesuAccountingTestData.*;

/** Actual PostgreSQL discovery SQL over minimal storage fixtures, not migration or cross-owner acceptance. */
@Testcontainers(disabledWithoutDocker=true)
class BesuAccountingWorkReadsIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    @BeforeAll static void schema() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()));
        jdbc.execute("create table besu_signed_submission(execution_id uuid primary key,document jsonb not null,fingerprint text not null,status text not null)");
        jdbc.execute("create table besu_ledger_receipt(execution_id uuid primary key)");
    }
    @BeforeEach void clear(){jdbc.execute("truncate besu_signed_submission,besu_ledger_receipt");}
    SettlementBinding.Execution insert(String status) {
        var e=execution("PAY-"+UUID.randomUUID());
        jdbc.update("insert into besu_signed_submission values (?,cast(? as jsonb),?,?)",e.executionId(),SettlementBindingJson.write(e),SettlementBindingJson.fingerprint(e),status);
        return e;
    }
    @Test void attemptedAndTerminalWorkSurvivesWithoutCurrentPaymentOrQuoteUntilAcknowledged() {
        var expected=new HashSet<UUID>();
        for(String status:List.of("ATTEMPTED","UNKNOWN","SUBMITTED","CONFIRMED","REVERTED"))expected.add(insert(status).executionId());
        insert("SIGNED");var acknowledged=insert("CONFIRMED");
        jdbc.update("insert into besu_ledger_receipt values (?)",acknowledged.executionId());
        var rows=new BesuAccountingWorkReads(jdbc).pending(null);
        assertThat(rows).extracting(BesuAccountingWorkReads.Candidate::id).containsExactlyInAnyOrderElementsOf(expected);
        rows.forEach(row->assertThat(row.execution().executionId()).isEqualTo(row.id()));
    }
    @Test void uuidKeysetCoversMoreThanOnePageAndOwnerAcknowledgementRemovesWork() {
        var expected=new HashSet<UUID>();for(int n=0;n<13;n++)expected.add(insert("CONFIRMED").executionId());
        var reads=new BesuAccountingWorkReads(jdbc);var first=reads.pending(null);assertThat(first).hasSize(10);
        var second=reads.pending(first.getLast().id());assertThat(second).hasSize(3);
        var seen=new HashSet<UUID>();first.forEach(r->assertThat(seen.add(r.id())).isTrue());second.forEach(r->assertThat(seen.add(r.id())).isTrue());
        assertThat(seen).isEqualTo(expected);assertThat(reads.pending(second.getLast().id())).isEmpty();
        first.forEach(r->jdbc.update("insert into besu_ledger_receipt values (?)",r.id()));
        assertThat(reads.pending(null)).extracting(BesuAccountingWorkReads.Candidate::id)
            .containsExactlyElementsOf(second.stream().map(BesuAccountingWorkReads.Candidate::id).toList());
    }
}
