package com.project.onlinebankingsystem.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          CustomAccessDeniedHandler customAccessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.customAccessDeniedHandler = customAccessDeniedHandler;
    }

    @org.springframework.beans.factory.annotation.Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF for REST API paths to allow stateless client interaction
                .csrf(csrf -> {
                    if (h2ConsoleEnabled) {
                        csrf.ignoringRequestMatchers("/api/**", "/h2-console/**");
                    } else {
                        csrf.ignoringRequestMatchers("/api/**");
                    }
                })
                // Frame options: enable sameOrigin in prod, permit frames only when H2 console is explicitly active in dev
                .headers(headers -> {
                    if (h2ConsoleEnabled) {
                        headers.frameOptions(frame -> frame.disable());
                    } else {
                        headers.frameOptions(frame -> frame.sameOrigin());
                    }
                })
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler(customAccessDeniedHandler)
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            String accept = request.getHeader("Accept");
                            boolean isApi = uri.startsWith("/api/") || (accept != null && accept.contains("application/json"));
                            if (isApi) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write("{\"timestamp\":\"" + java.time.LocalDateTime.now() + "\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Full authentication is required to access this resource\",\"path\":\"" + uri + "\"}");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                )
                .authorizeHttpRequests(auth -> {
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }
                    auth
                        // Public resources & pages
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/static/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/api/auth/**",
                                "/favicon.ico"
                        ).permitAll()
                        // Account creation paths strictly restricted to SUPER ADMIN only
                        .requestMatchers(
                                "/api/accounts/create",
                                "/accounts/create",
                                "/account/create",
                                "/api/accounts/open",
                                "/accounts/open",
                                "/open-account",
                                "/create-account"
                        ).hasAuthority("ROLE_SUPER_ADMIN")
                        // Super Admin restricted paths (ROLE_SUPER_ADMIN only)
                        .requestMatchers("/super-admin", "/super-admin/**", "/api/super-admin/**").hasAuthority("ROLE_SUPER_ADMIN")
                        // Admin restricted paths (ROLE_ADMIN only)
                        .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasAuthority("ROLE_ADMIN")
                        // Secured endpoints (Customer / Authenticated users)
                        .anyRequest().authenticated();
                })
                // Session management - default to session for UI views but allow stateless requests for REST APIs
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                // Form login for UI pages with custom handlers
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(customAuthenticationSuccessHandler)
                        .failureHandler(customAuthenticationFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                );

        // Register custom JWT Filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
