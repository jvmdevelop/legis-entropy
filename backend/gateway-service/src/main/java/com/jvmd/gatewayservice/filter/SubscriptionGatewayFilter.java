package com.jvmd.gatewayservice.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SubscriptionGatewayFilter implements Filter {

    private static final Map<String, String> PLAN_GATES = Map.of(
        "/api/brain/contracts/risk-scan",
        "BASIC"
    );

    private static final Map<String, String> FEATURE_NAMES = Map.of(
        "/api/brain/contracts/risk-scan",
        "CONTRACT_RISK_SCAN"
    );

    private static final List<String> PLAN_ORDER = List.of(
        "FREE",
        "BASIC",
        "PRO"
    );

    @Override
    public void doFilter(
        ServletRequest servletRequest,
        ServletResponse servletResponse,
        FilterChain chain
    ) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        for (Map.Entry<String, String> gate : PLAN_GATES.entrySet()) {
            if (path.startsWith(gate.getKey())) {
                String userPlan = (String) request.getAttribute(
                    JwtGatewayFilter.ATTR_PLAN_TYPE
                );
                String requiredPlan = gate.getValue();

                if (!hasSufficientPlan(userPlan, requiredPlan)) {
                    String feature = FEATURE_NAMES.getOrDefault(
                        gate.getKey(),
                        gate.getKey()
                    );
                    sendPaymentRequired(response, feature, requiredPlan);
                    return;
                }
                break;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean hasSufficientPlan(String userPlan, String required) {
        int userIdx = PLAN_ORDER.indexOf(
            userPlan != null ? userPlan.toUpperCase() : "FREE"
        );
        int requiredIdx = PLAN_ORDER.indexOf(
            required != null ? required.toUpperCase() : "BASIC"
        );
        if (userIdx < 0) userIdx = 0;
        if (requiredIdx < 0) requiredIdx = 1;
        return userIdx >= requiredIdx;
    }

    private void sendPaymentRequired(
        HttpServletResponse response,
        String feature,
        String requiredPlan
    ) throws IOException {
        response.setStatus(402);
        response.setContentType("application/json;charset=UTF-8");
        response
            .getWriter()
            .write(
                "{\"error\":\"UPGRADE_REQUIRED\",\"feature\":\"" +
                    feature +
                    "\",\"requiredPlan\":\"" +
                    requiredPlan +
                    "\"}"
            );
    }
}
