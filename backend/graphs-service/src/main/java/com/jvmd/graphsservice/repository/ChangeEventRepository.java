package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.ChangeEvent;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChangeEventRepository extends Neo4jRepository<ChangeEvent, String> {

    @Query("""
            MATCH (e:ChangeEvent {userId: $userId})
            RETURN e
            ORDER BY e.detectedAt DESC
            LIMIT 200
            """)
    List<ChangeEvent> findRecentByUser(@Param("userId") String userId);

    @Query("""
            MATCH (e:ChangeEvent {subscriptionId: $subscriptionId})
            RETURN e
            ORDER BY e.detectedAt DESC
            LIMIT 50
            """)
    List<ChangeEvent> findBySubscription(@Param("subscriptionId") String subscriptionId);

    @Query("""
            MATCH (e:ChangeEvent {userId: $userId, acknowledged: false})
            RETURN count(e) AS n
            """)
    int countUnreadByUser(@Param("userId") String userId);

    @Query("""
            MATCH (e:ChangeEvent {userId: $userId, acknowledged: false})
            SET e.acknowledged = true
            """)
    void acknowledgeAll(@Param("userId") String userId);
}
