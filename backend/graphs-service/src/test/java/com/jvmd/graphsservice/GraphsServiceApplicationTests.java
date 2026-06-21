package com.jvmd.graphsservice;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("graphs-service requires Neo4j, Elasticsearch and Redis simultaneously — needs dedicated integration test setup")
class GraphsServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
