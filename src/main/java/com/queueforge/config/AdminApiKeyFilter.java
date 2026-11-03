
package com.queueforge.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminApiKeyFilter implements Filter {

    private static final String HEADER = "X-QueueForge-Admin-Key";

    private final String adminApiKey;
    private final boolean requireApiKey;

    public AdminApiKeyFilter(
            QueueForgeProperties properties,
            @Value("${FLOWRELAY_REQUIRE_API_KEY:false}")
            boolean requireApiKey
    ) {
        this.adminApiKey =
                properties.getSecurity().getAdminApiKey();
        this.requireApiKey = requireApiKey;
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest req =
                (HttpServletRequest) request;

        String path = req.getRequestURI();

        boolean adminRoute =
                path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/workers/");

        boolean protectedRoute =
                path.startsWith("/api/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator/metrics")
                || path.startsWith("/actuator/prometheus");

        boolean needsAuthentication =
                requireApiKey ? protectedRoute : adminRoute;

        if (!needsAuthentication) {
            chain.doFilter(request, response);
            return;
        }

        if (!requireApiKey &&
                (adminApiKey == null || adminApiKey.isBlank())) {
            chain.doFilter(request, response);
            return;
        }

        String providedKey = req.getHeader(HEADER);

        if (adminApiKey != null
                && !adminApiKey.isBlank()
                && providedKey != null
                && MessageDigest.isEqual(
                    adminApiKey.getBytes(StandardCharsets.UTF_8),
                    providedKey.getBytes(StandardCharsets.UTF_8))) {

            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse res =
                (HttpServletResponse) response;

        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write(
                "{\"error\":\"Unauthorized\","
                + "\"message\":\"Valid API key required\"}"
        );
    }
}

