package org.shalash.adminapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.shalash.adminapi.exception.UserNotAutherizedException;
import org.shalash.adminapi.service.AuthorizeRequestServelet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log =
            LoggerFactory.getLogger(AuthenticationFilter.class);

    private final AuthorizeRequestServelet authorizeRequestServelet;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (request.getRequestURI().equals("/user/generateHmac")) {
                filterChain.doFilter(request, response);
                return;
            }
            String header = request.getHeader("Authorization");
            if (header == null) {
                unauthorized(response, "Missing or invalid Authorization header");
                return;
            }

            String headerParts[] = header.split(",");
            if (headerParts.length != 3) {
                unauthorized(response, "Invalid Authorization header");
                return;
            }

            String accessKey = headerParts[0];
            long timestamp = Long.parseLong(headerParts[1]);
            String hmac = headerParts[2];

            log.info("Access key: {}", accessKey);

            if (!authorizeRequestServelet.isAuthorized(
                    request.getMethod(),
                    accessKey,
                    timestamp,
                    hmac
            )) {
                unauthorized(response, "Unauthorized request");
                return;
            }
        }
        catch (Exception e) {
            unauthorized(response, e.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }
    private void unauthorized(
            HttpServletResponse response,
            String message) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");

        response.getWriter().write("""
        {
            "status": 403,
            "error": "USER_NOT_AUTHORIZED",
            "message": "%s"
        }
        """.formatted(message));
    }
}
