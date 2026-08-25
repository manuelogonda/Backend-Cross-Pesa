package com.manuelorg.cross_pesa.config;

import com.manuelorg.cross_pesa.auth.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;


import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Allows us to use @PreAuthorize("hasRole('ADMIN')") on controllers later
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF because our tokens are immune to it (we don't use cookies for auth)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})

//        Exception Handling: Force API endpoints to return 401 instead of redirecting
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                // Use a lambda instead of a built-in matcher class!
                                request -> request.getServletPath().startsWith("/api/v1/")
                        )
                )

                // 2. Route Whitelisting
                .authorizeHttpRequests(auth -> auth
                        // CRITICAL: Allow all browser preflight OPTIONS requests globally
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Allow anyone to register or login
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Allow Google OAuth2 login flow endpoints
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Flutterwave webhook — called by Flutterwave servers, no JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/flutterwave").permitAll()
                        // Gateway payout webhook — HMAC-signed (X-Webhook-Signature), no JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/payout-update").permitAll()
                        // Paystack payout webhook — HMAC-SHA512-signed (x-paystack-signature), no JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/paystack").permitAll()
                        // Smile ID KYC webhook — shared-secret token (X-Callback-Token), no JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/kyc/webhook/smile-id").permitAll()
                        // Actuator: liveness/readiness public, everything else admin-only
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        //admin
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")                        // All other endpoints require a valid JWT
                        .anyRequest().authenticated()
                )

                // 3. Disable Server Sessions (Strictly Stateless)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Set the Auth Provider and inject our custom filter BEFORE the standard Username/Password filter
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                // 5. Google OAuth2 Login Config
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2SuccessHandler)
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://frontend-cross-pesa.onrender.com","http://localhost:5173")); // Vite dev port and live render url
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}