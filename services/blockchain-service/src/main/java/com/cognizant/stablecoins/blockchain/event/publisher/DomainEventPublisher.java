package com.cognizant.stablecoins.blockchain.event.publisher;

import com.cognizant.stablecoins.blockchain.event.DomainEvent;

public interface DomainEventPublisher {
    void publish(DomainEvent<?> event);
}
