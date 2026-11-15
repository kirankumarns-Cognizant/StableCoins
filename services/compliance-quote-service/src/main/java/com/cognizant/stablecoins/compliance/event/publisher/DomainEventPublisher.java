package com.cognizant.stablecoins.compliance.event.publisher;

import com.cognizant.stablecoins.compliance.event.DomainEvent;

/**
 * Port for publishing domain events (ADR-004).
 */
public interface DomainEventPublisher {

    void publish(DomainEvent<?> event);
}
