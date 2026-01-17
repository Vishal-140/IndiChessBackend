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

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        // ✅ GitHub fallback (email can be null)
        if (email == null) {
            String login = oauthUser.getAttribute("login"); // github username
            email = login + "@github.com";
        }

        User user = userRepo.getUserByEmailId(email);

        if (user == null) {
            user = new User();
            user.setEmailId(email);

            // ✅ generate UNIQUE username ONCE
            String username;
            do {
                username = "user_" + UUID.randomUUID().toString().substring(0, 8);
            } while (userRepo.existsByUsername(username));

            user.setUsername(username);
            userRepo.save(user);
        }


        String jwt = jwtService.generateToken(user.getUsername());

        Cookie cookie = new Cookie("JWT", jwt);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // localhost
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);

        response.addCookie(cookie);

        response.sendRedirect("http://localhost:3000/home");
    }
}
