package com.nashi.repo;

import com.nashi.domain.Event;
import com.nashi.enums.EventStatus;
import com.nashi.enums.Region;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<Event, String> {

    List<Event> findByStatusAndDateTimeAfterOrderByDateTimeAsc(
            EventStatus status, 
            LocalDateTime now
    );


    List<Event> findByStatusAndRegionAndDateTimeAfterOrderByDateTimeAsc(
            EventStatus status, 
            Region region, 
            LocalDateTime now
    );



    /**
     * Find all events waiting for moderation.
     * Sorted by date so admins see the most urgent ones first.
     */
    List<Event> findByStatusOrderByDateTimeAsc(EventStatus status);
}