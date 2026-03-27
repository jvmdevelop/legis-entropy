package kz.legis.entropy.config;

import kz.legis.entropy.handler.AnalysisHandler;
import kz.legis.entropy.handler.GraphHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> routes(GraphHandler graphHandler, AnalysisHandler analysisHandler) {
        return RouterFunctions.route()
                .GET("/health",             req -> ServerResponse.ok().bodyValue(java.util.Map.of("status", "ok")))
                .GET("/api/graph",          graphHandler::getGraph)
                .GET("/api/stats",          graphHandler::getStats)
                .GET("/api/stream/analyze", analysisHandler::streamAnalysis)
                .POST("/api/search",        analysisHandler::search)
                .POST("/api/compare",       analysisHandler::compare)
                .build();
    }
}
