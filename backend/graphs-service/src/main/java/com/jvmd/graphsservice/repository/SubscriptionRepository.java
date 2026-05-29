package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.Subscription;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends Neo4jRepository<Subscription, String> {

    @Query("""
            MATCH (s:Subscription {userId: $userId})
            RETURN s
            ORDER BY s.createdAt DESC
            """)
    List<Subscription> findByUser(@Param("userId") String userId);

    @Query("""
            MATCH (s:Subscription {userId: $userId, lawCode: $lawCode, country: $country, articleNumber: $articleNumber})
            RETURN s LIMIT 1
            """)
    Optional<Subscription> findOne(@Param("userId") String userId,
                                   @Param("lawCode") String lawCode,
                                   @Param("country") String country,
                                   @Param("articleNumber") String articleNumber);

    @Query("MATCH (s:Subscription) RETURN s")
    List<Subscription> findAllActive();
}
