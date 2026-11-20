package com.olawale.budgettracker.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.olawale.budgettracker.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The door that replaces httpBasic: reads "Authorization: Bearer <jwt>",
 * verifies it is a genuine ACCESS token, and populates the SecurityContext.
 *
 * It never rejects on its own — a missing/invalid token simply leaves the
 * request anonymous, and the authorizeHttpRequests rules produce the 401.
 * (One enforcement point for "who may enter": SecurityConfig.)
 *
 * Deliberately NOT a @Component: registered manually in SecurityConfig so
 * Spring Boot doesn't also auto-register it as a global servlet filter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.verify(header.substring(BEARER_PREFIX.length()).trim(), "access")
                    .ifPresent(claims -> {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        claims.getSubject(), // principal = user id
                                        null,               // no credentials post-authentication
                                        List.of(new SimpleGrantedAuthority(
                                                "ROLE_" + claims.get("role", String.class))));
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }
}
