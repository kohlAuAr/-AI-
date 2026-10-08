package com.campus.business.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(AccountRepository accounts) {
        return username -> {
            Account account = accounts.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
            return User.withUsername(account.getUsername()).password(account.getPasswordHash()).roles(account.getRole()).build();
        };
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/auth/session", "/api/clubs", "/api/clubs/**", "/api/activities", "/api/system", "/api/ai/**", "/actuator/health").permitAll()
                .requestMatchers("/api/auth/login", "/error").permitAll()
                // Existing AI development endpoints still use public synthetic data; data-scope authorization is pending.
                .requestMatchers("/api/ai/**").permitAll()
                .requestMatchers("/api/manage/**").hasRole("MANAGER")
                .anyRequest().authenticated())
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("status", "OK"));
                })
                .failureHandler((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("detail", "账号或密码不正确"));
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("status", "OK"));
                }))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("detail", "请先登录"));
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), Map.of("detail", "没有权限或安全令牌已失效，请刷新后重试"));
                }));
        return http.build();
    }
}
