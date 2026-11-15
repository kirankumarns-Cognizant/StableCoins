package com.cognizant.stablecoins.payment.agent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RoutingAgentRunRepository extends JpaRepository<RoutingAgentRun,UUID> {
    Optional<RoutingAgentRun> findByPaymentIdAndActorIdAndIdempotencyHash(String paymentId,String actorId,String idempotencyHash);
    List<RoutingAgentRun> findByPaymentIdAndStatus(String paymentId,RoutingAgentRun.Status status);
    long countByPaymentId(String paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RoutingAgentRun r where r.id=:id")
    Optional<RoutingAgentRun> lockById(@Param("id") UUID id);
}
