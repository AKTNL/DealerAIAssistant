package com.brand.agentpoc.auth.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.http.HttpMethod;

@Configuration
@EnableMethodSecurity
public class AuthSecurityConfiguration {

    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    public AuthSecurityConfiguration(
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain authSecurityFilterChain(
            HttpSecurity http,
            OpaqueTokenAuthenticationFilter authenticationFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/assets/**", "/favicon.ico", "/logo.png",
                                "/openapi.json", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/h2-console/**", "/actuator/health",
                                "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password", "/api/auth/logout-all").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/admin/audit-events/**").hasAuthority("USER_READ")
                        .requestMatchers("/api/admin/audit-events/**").hasAuthority("USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/users/**").hasAuthority("USER_READ")
                        .requestMatchers("/api/admin/users/**").hasAuthority("USER_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/roles/**").hasAuthority("ROLE_READ")
                        .requestMatchers("/api/admin/roles/**").hasAuthority("ROLE_MANAGE")
                        .requestMatchers("/api/admin/organizations/user-grants/**",
                                "/api/admin/organizations/role-grants/**")
                                .hasAuthority("ORGANIZATION_GRANT_MANAGE")
                        .requestMatchers(HttpMethod.GET, "/api/admin/organizations/**")
                                .hasAuthority("ORGANIZATION_READ")
                        .requestMatchers("/api/admin/organizations/**").hasAuthority("ORGANIZATION_MANAGE")
                        .requestMatchers("/api/dashboard").hasAuthority("DASHBOARD_READ")
                        .requestMatchers("/api/data-status", "/api/v1/data/**", "/api/*/metrics", "/api/*/details")
                                .hasAuthority("DATA_READ")
                        .requestMatchers("/api/chat/**").hasAuthority("CHAT_USE")
                        .requestMatchers(HttpMethod.GET, "/api/report-subscriptions/**")
                                .hasAuthority("REPORT_READ")
                        .requestMatchers("/api/report-subscriptions/**")
                                .hasAuthority("REPORT_GENERATE")
                        .requestMatchers(HttpMethod.GET, "/api/report-jobs/**")
                                .hasAuthority("REPORT_READ")
                        .requestMatchers("/api/report-jobs/**")
                                .hasAuthority("REPORT_GENERATE")
                        .requestMatchers(HttpMethod.GET, "/api/report-deliveries/**")
                                .hasAuthority("REPORT_READ")
                        .requestMatchers("/api/report-deliveries/**")
                                .hasAuthority("REPORT_GENERATE")
                        .requestMatchers(HttpMethod.POST, "/api/reports/**").hasAuthority("REPORT_GENERATE")
                        .requestMatchers(HttpMethod.GET, "/api/reports/**").hasAuthority("REPORT_READ")
                        .requestMatchers("/api/model-config/**").hasAuthority("MODEL_CONFIG_TEST")
                        .requestMatchers("/api/notification/**").hasAuthority("USER_MANAGE")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
