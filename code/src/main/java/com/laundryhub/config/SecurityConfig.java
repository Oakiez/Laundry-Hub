package com.laundryhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.laundryhub.security.ApiAccessDeniedHandler;
import com.laundryhub.security.ApiAuthenticationEntryPoint;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ApiAuthenticationEntryPoint apiEntryPoint,
                                                   ApiAccessDeniedHandler apiAccessDeniedHandler) throws Exception {
        RequestMatcher apiRequests = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**",
                                "/api/v1/auth/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // URL rules only for whole areas; per-endpoint rules live in each controller as @PreAuthorize
                        .requestMatchers("/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true))
                // wrong Basic credentials are rejected by the Basic filter itself, so it needs the JSON entry point too
                // (this also drops the WWW-Authenticate header that would pop up the browser's login dialog)
                .httpBasic(basic -> basic.authenticationEntryPoint(apiEntryPoint))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // REST clients get the standard JSON error (401/403) instead of a redirect to the HTML login page
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(apiEntryPoint, apiRequests)
                        .defaultAccessDeniedHandlerFor(apiAccessDeniedHandler, apiRequests))
                // the REST API authenticates per request (Basic Auth), so CSRF only matters for the web pages
                .csrf(csrf -> csrf.ignoringRequestMatchers(apiRequests));
        return http.build();
    }
}
