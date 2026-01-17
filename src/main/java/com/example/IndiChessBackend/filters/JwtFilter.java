package com.example.IndiChessBackend.filters;

import com.example.IndiChessBackend.service.JwtService;
import com.example.IndiChessBackend.service.MyUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final MyUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1️⃣ Extract token from HTTP-only cookie
        String token = extractTokenFromCookies(request);
        System.out.println("Inside jwt filter");
        System.out.println(token);
        String username = null;

        if (token != null) {
            // Extract username from token
            username = jwtService.extractUsername(token);
        }

        // 2️⃣ Authenticate user if not already authenticated
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            System.out.println(username);
            // Validate token if user exists and token is valid
            if (jwtService.isTokenValid(token, userDetails)) {
                // Create authentication token
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                // Set additional details if needed (e.g., HTTP request)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set authentication in SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 3️⃣ Continue filter chain
        filterChain.doFilter(request, response);
    }

    // Helper method to extract token from cookies
    private String extractTokenFromCookies(HttpServletRequest request) {
        // Iterate through cookies to find the JWT cookie
        String token = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JWT".equals(cookie.getName())) { // Look for JWT cookie
                    token = cookie.getValue();
                    break;
                }
            }
        }
        return token;
    }

}
