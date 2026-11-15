package com.cognizant.stablecoins.payment.event.publisher;

import com.cognizant.stablecoins.payment.event.DomainEvent;

/**
 * Port for publishing domain events (ADR-004).
 * Implementations: AwsEventBridgePublisher (production), LocalSqsEventPublisher (local/test).
 */
public interface DomainEventPublisher {

    void publish(DomainEvent<?> event);
}
