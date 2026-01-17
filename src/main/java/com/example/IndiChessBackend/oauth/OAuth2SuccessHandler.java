package com.example.IndiChessBackend.oauth;

import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.repo.UserRepo;
import com.example.IndiChessBackend.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRepo userRepo;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        // Get OAuth user details
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        System.out.println("OAuth Email: " + email);
        System.out.println("OAuth Name: " + name);

        // Safety check (some providers may not return email)
        if (email == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not found from OAuth provider");
            return;
        }

        // Check if user already exists
        User user = userRepo.getUserByEmailId(email);

        if (user == null) {
            // Create new user if not exists
            user = new User();
            user.setEmailId(email);

            // If name is null, generate a random username
            if (name == null || name.isBlank()) {
                name = "user_" + UUID.randomUUID();
            }

            user.setUsername(name);

            // Save new user
            userRepo.save(user);
        }


        // Generate JWT token (IMPORTANT: use username, not name directly)
        String jwt = jwtService.generateToken(user.getUsername());

        System.out.println("Generated JWT: " + jwt);

        // Store JWT in HTTP-only cookie
        Cookie jwtCookie = new Cookie("JWT", jwt);
        jwtCookie.setHttpOnly(true); // Prevent JavaScript access
        jwtCookie.setPath("/"); // Available for whole app
        jwtCookie.setMaxAge(60 * 60); // 1 hour

        // Set secure=false for localhost, true for production (HTTPS)
        jwtCookie.setSecure(false);

        response.addCookie(jwtCookie);

        // Redirect to frontend home page
        response.sendRedirect("http://localhost:3000/home");
    }
}
