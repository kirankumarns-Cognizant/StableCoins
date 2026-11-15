package com.cognizant.stablecoins.payment.approval;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="routing_audit")
public class RoutingAudit {
    @Id public UUID id;
    @Column(name="recommendation_id",nullable=false,updatable=false) public UUID recommendationId;
    @Column(name="payment_id",nullable=false,length=50,updatable=false) public String paymentId;
    @Column(name="correlation_id",nullable=false,length=60,updatable=false) public String correlationId;
    @Column(name="actor_id",nullable=false,length=255,updatable=false) public String actorId;
    @Column(nullable=false,length=40,updatable=false) public String action;
    @Column(name="recorded_at",nullable=false,updatable=false) public Instant recordedAt;
}
