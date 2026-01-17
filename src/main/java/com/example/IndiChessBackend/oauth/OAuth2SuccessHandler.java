package com.example.IndiChessBackend.oauth;

import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.repo.UserRepo;
import com.example.IndiChessBackend.service.AuthService;
import com.example.IndiChessBackend.service.JwtService;
import com.example.IndiChessBackend.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

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

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        System.out.println(email);
        System.out.println(name);

        // Generate JWT token
        String jwt = jwtService.generateToken(name);
        System.out.println("Inside oauth Success");
        System.out.println(jwt);

        // Create or get user from the database (you should have a user service for this)
        User user = userRepo.getUserByEmailId(email);
        if (user == null) {
            // User doesn't exist, create the user
            user = new User();
            user.setEmailId(email);
            user.setUsername(name); // Set the user's name (or other data if needed)
            userRepo.save(user); // Save the new user to the database
        }

        // Store JWT in HTTP-only cookie
        Cookie jwtCookie = new Cookie("JWT", jwt);
        jwtCookie.setHttpOnly(true); // Prevents JavaScript from accessing the cookie
        jwtCookie.setPath("/"); // Make sure the cookie is accessible for the entire domain
        jwtCookie.setMaxAge(3600); // Optional: set cookie expiration (e.g., 1 hour)
        jwtCookie.setSecure(true); // Optional: set to true if using HTTPS
        response.addCookie(jwtCookie); // Add the cookie to the response
        response.sendRedirect("http://localhost:3000/home");
    }

}
