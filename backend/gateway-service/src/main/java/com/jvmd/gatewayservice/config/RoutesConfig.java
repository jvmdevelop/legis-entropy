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
                .route(GatewayRequestPredicates.path("/api/auth/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("auth-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userRoutes() {
        return GatewayRouterFunctions.route("user-service")
                .route(GatewayRequestPredicates.path("/api/users/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> adminRoutes() {
        return GatewayRouterFunctions.route("admin-service")
                .route(GatewayRequestPredicates.path("/api/admin/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("user-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> chatRoutes() {
        return GatewayRouterFunctions.route("chat-service")
                .route(GatewayRequestPredicates.path("/api/conversations/**", "/api/messages/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("chat-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> userDocumentsRoutes() {
        return GatewayRouterFunctions.route("document-service")
                .route(GatewayRequestPredicates.path("/api/user-documents/**"), HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("document-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> documentsRoutes() {
        return GatewayRouterFunctions.route("documents-service")
                .route(GatewayRequestPredicates.path(
                                "/api/documents/**",
                                "/api/evidence-pack/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("documents-manager-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> lawRoutes() {
        return GatewayRouterFunctions.route("law-service")
                .route(GatewayRequestPredicates.path("/api/v1/kz-laws/**", "/api/laws/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("law-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> voiceRoutes() {
        return GatewayRouterFunctions.route("voice-service")
                .route(GatewayRequestPredicates.path("/api/voice-messages/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("voice-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> templateRoutes() {
        return GatewayRouterFunctions.route("template-service")
                .route(GatewayRequestPredicates.path("/api/templates/**", "/api/generated-documents/**"),
                        HandlerFunctions.http())
                .filter(LoadBalancerFilterFunctions.lb("template-service"))
                .before(injectUserHeaders())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> situationRoutes() {
        return GatewayRouterFunctions.route("situation-service")
                .route(GatewayRequestPredicates.path(
                                "/api/graph/conflicts/**",
                                "/api/v1/user-graphs/*/situations/**",
                                "/api/v1/user-graphs/*/situations"),
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
