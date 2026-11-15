package com.cognizant.stablecoins.payment.approval;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Append-only actor decision: duplicate actor/action is constrained in PostgreSQL. */
@Entity @Table(name="routing_decision")
public class RoutingDecision {
    @Id public UUID id;
    @Column(name="recommendation_id",nullable=false,updatable=false) public UUID recommendationId;
    @Column(name="actor_id",nullable=false,length=255,updatable=false) public String actorId;
    @Column(nullable=false,length=30,updatable=false) public String action;
    @Column(name="decided_at",nullable=false,updatable=false) public Instant decidedAt;
}
