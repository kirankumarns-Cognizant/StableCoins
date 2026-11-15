package com.cognizant.stablecoins.payment.approval;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="routing_recommendation")
public class RoutingRecommendation {
    public enum State { DRAFT, PROPOSED, APPROVED, REJECTED, EXPIRED, SUPERSEDED, EXECUTED }
    @Id @Column(name="recommendation_id") public UUID id;
    @Column(name="payment_id",nullable=false,length=50,updatable=false) public String paymentId;
    @Column(name="correlation_id",nullable=false,length=60,updatable=false) public String correlationId;
    @Column(name="customer_id",nullable=false,updatable=false) public UUID customerId;
    @Column(nullable=false,updatable=false) public int version;
    @Column(name="required_approvals",nullable=false,updatable=false) public int requiredApprovals;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public State state;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb",updatable=false) public RecommendationDocument document;
    @Column(name="created_at",nullable=false,updatable=false) public Instant createdAt;
    @Column(name="expires_at",nullable=false,updatable=false) public Instant expiresAt;
}
