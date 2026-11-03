package com.queueforge.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Configuration
public class SecurityConfig {

    private static final String API_KEY_HEADER =
        "X-QueueForge-Admin-Key";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
        @Value("${FLOWRELAY_DASHBOARD_USERNAME:}") String username,
        @Value("${FLOWRELAY_DASHBOARD_PASSWORD:}") String password,
        PasswordEncoder encoder
    ) {
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException(
                "Dashboard credentials must be configured"
            );
        }

        return new InMemoryUserDetailsManager(
            User.withUsername(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build()
        );
    }

    private boolean validApiKey(
        HttpServletRequest request,
        String configuredKey
    ) {
        String providedKey = request.getHeader(API_KEY_HEADER);

        return configuredKey != null
            && !configuredKey.isBlank()
            && providedKey != null
            && MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8)
            );
    }

    private boolean authenticatedAdmin(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && authentication.getAuthorities().stream()
                .anyMatch(a ->
                    "ROLE_ADMIN".equals(a.getAuthority())
                );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        QueueForgeProperties properties
    ) throws Exception {

        String apiKey = properties.getSecurity().getAdminApiKey();

        var csrfRepository =
            CookieCsrfTokenRepository.withHttpOnlyFalse();

        var csrfHandler =
            new CsrfTokenRequestAttributeHandler();

        var protectedRoutes = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/**"),
            new AntPathRequestMatcher("/swagger-ui"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/v3/api-docs"),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/actuator/metrics"),
            new AntPathRequestMatcher("/actuator/metrics/**"),
            new AntPathRequestMatcher("/actuator/prometheus")
        );

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepository)
                .csrfTokenRequestHandler(csrfHandler)
                .requireCsrfProtectionMatcher(request -> {
                    String method = request.getMethod();

                    if ("GET".equals(method)
                        || "HEAD".equals(method)
                        || "OPTIONS".equals(method)
                        || "TRACE".equals(method)) {
                        return false;
                    }

                    return !validApiKey(request, apiKey);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/assets/**",
                    "/favicon.ico",
                    "/login",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers(
                    "/api/**",
                    "/swagger-ui",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**",
                    "/actuator/metrics",
                    "/actuator/metrics/**",
                    "/actuator/prometheus"
                )
                .access((authentication, context) -> {
                    boolean allowed =
                        validApiKey(context.getRequest(), apiKey)
                        || authenticatedAdmin(authentication.get());

                    return new AuthorizationDecision(allowed);
                })
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                    protectedRoutes
                )
            )
            .formLogin(form -> form.permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .logoutSuccessUrl("/login")
                .permitAll()
            );

        return http.build();
    }
}
