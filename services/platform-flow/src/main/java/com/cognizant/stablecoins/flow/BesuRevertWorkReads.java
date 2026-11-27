package com.cognizant.stablecoins.flow;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
/** Read-only bounded restart discovery; authority is always revalidated by the owner. */
public final class BesuRevertWorkReads {
    public static final int BATCH_SIZE=10;private final JdbcTemplate jdbc;
    public BesuRevertWorkReads(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<BesuClosureWorkReads.Candidate> pending(UUID after,boolean compensation) {
        String query=compensation ? """
            select a.execution_id,a.execution_document::text,a.execution_fingerprint
            from payment_besu_recovery_authorization a
            left join besu_ledger_compensation c on c.execution_id=a.execution_id
            where c.execution_id is null
            """ : """
            select f.execution_id,f.execution_document::text,f.execution_fingerprint
            from payment_besu_funding f join besu_signed_submission s on s.execution_id=f.execution_id
            join besu_ledger_receipt a on a.execution_id=f.execution_id
            left join payment_besu_failure c on c.execution_id=f.execution_id
            where s.status='REVERTED' and c.execution_id is null
            """;
        String alias=compensation?"a":"f";
        return jdbc.query(query+" and (cast(? as uuid) is null or "+alias+".execution_id>cast(? as uuid)) order by "+alias+".execution_id limit ?",
            (r,n)->new BesuClosureWorkReads.Candidate(r.getObject(1,UUID.class),r.getString(2),r.getString(3)),after,after,BATCH_SIZE);
    }
}
