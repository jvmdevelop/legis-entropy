package com.jvmd.gatewayservice.config;

import com.jvmd.gatewayservice.filter.JwtGatewayFilter;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.function.Function;

@Configuration
public class RoutesConfig {

    private static Function<ServerRequest, ServerRequest> injectUserHeaders() {
        return request -> {
            String userId   = (String) request.servletRequest().getAttribute(JwtGatewayFilter.ATTR_USER_ID);
            String username = (String) request.servletRequest().getAttribute(JwtGatewayFilter.ATTR_USERNAME);
            String userRole = (String) request.servletRequest().getAttribute(JwtGatewayFilter.ATTR_USER_ROLE);
            String planType = (String) request.servletRequest().getAttribute(JwtGatewayFilter.ATTR_PLAN_TYPE);
            if (userId != null || username != null || userRole != null) {
                ServerRequest.Builder builder = ServerRequest.from(request);
                if (userId   != null) builder.header(JwtGatewayFilter.ATTR_USER_ID,   userId);
                if (username != null) builder.header(JwtGatewayFilter.ATTR_USERNAME,  username);
                if (userRole != null) builder.header(JwtGatewayFilter.ATTR_USER_ROLE, userRole);
                builder.header(JwtGatewayFilter.ATTR_PLAN_TYPE, planType != null ? planType : "FREE");
                return builder.build();
            }
            return request;
        };
    }

    @Bean
    public RouterFunction<ServerResponse> authRoutes() {
        return GatewayRouterFunctions.route("auth-service")
                .route(GatewayRequestPredicates.path("/api/authsituationssituations"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("situation-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> brainRoutes() {
        return GatewayRouterFunctions.route("llm-brain-service")
                .route(GatewayRequestPredicates.path("/api/brain/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("llm-brain-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> graphsRoutes() {
        return GatewayRouterFunctions.route("graphs-service")
                .route(GatewayRequestPredicates.path("/api/v1/graphs/**", "/api/v1/user-graphs/**", "/api/graph/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("graphs-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> workspaceRoutes() {
        return GatewayRouterFunctions.route("workspace-service")
                .route(GatewayRequestPredicates.path("/api/v1/workspaces/**", "/api/v1/collaboration/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("workspace-service"))
                .before(injectUserHeaders())
                .build();
    }

}
