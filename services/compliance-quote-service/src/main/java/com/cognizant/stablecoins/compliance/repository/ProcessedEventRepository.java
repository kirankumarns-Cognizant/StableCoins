package com.cognizant.stablecoins.compliance.repository;

import com.cognizant.stablecoins.compliance.model.ProcessedEvent;
import com.cognizant.stablecoins.compliance.model.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="insert into compliance_processed_event(event_id,consumer_name) values (:eventId,:consumer) on conflict(event_id,consumer_name) do nothing",nativeQuery=true)
    int claim(@org.springframework.data.repository.query.Param("eventId") String eventId,@org.springframework.data.repository.query.Param("consumer") String consumer);
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);
}
