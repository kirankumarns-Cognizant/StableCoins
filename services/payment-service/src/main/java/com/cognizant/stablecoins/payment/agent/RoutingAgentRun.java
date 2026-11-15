package com.cognizant.stablecoins.payment.agent;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="routing_agent_run")
public class RoutingAgentRun {
    public enum Status { RUNNING, COMPLETED, FAILED, TIMED_OUT }
    @Id @Column(name="run_id") public UUID id;
    @Column(name="payment_id",nullable=false,updatable=false,length=50) public String paymentId;
    @Column(name="correlation_id",nullable=false,updatable=false,length=60) public String correlationId;
    @Column(name="customer_id",nullable=false,updatable=false) public UUID customerId;
    @Column(name="actor_id",nullable=false,updatable=false,length=200) public String actorId;
    @Column(name="recommendation_id",nullable=false,updatable=false) public UUID recommendationId;
    @Column(name="idempotency_hash",nullable=false,updatable=false,length=64) public String idempotencyHash;
    @Column(nullable=false,updatable=false,length=64) public String fingerprint;
    @Column(name="configured_model",nullable=false,updatable=false,length=120) public String configuredModel;
    @Column(name="configured_provider",nullable=false,updatable=false,length=20) public String configuredProvider;
    @Column(name="inference_region",nullable=false,updatable=false,length=40) public String inferenceRegion;
    @Column(name="prompt_version",nullable=false,updatable=false,length=60) public String promptVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public Status status;
    @Column(name="created_at",nullable=false,updatable=false) public Instant createdAt;
    @Column(name="deadline_at",nullable=false,updatable=false) public Instant deadlineAt;
    @Column(name="finished_at") public Instant finishedAt;
    @Column(name="failure_code",length=80) public String failureCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") public AgentReport report;
}
