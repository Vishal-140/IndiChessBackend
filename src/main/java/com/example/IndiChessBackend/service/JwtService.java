package com.example.IndiChessBackend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // Secret key (must be Base64 encoded & at least 256-bit for HS256)
    private static final String SECRET =
            "YWx1ZXNnbzhxMzdnNHRpZnFiaHJlZmc4ZzMxMjRpYjgwMWc3YnIxOGI3Z2IxN2c0Yg==";

    // Generate JWT token using username
    public String generateToken(String username) {

        return Jwts.builder()
                .setSubject(username) // username stored inside token
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(
                        new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 5)
                ) // 5 hours
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    //  Convert secret string to signing key
    private Key getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }
    //  Extract username from token
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }
    //  Extract all claims
    private Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    //  Validate token with user details
    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername())
                && !isTokenExpired(token);
    }
    //  Check if token expired
    private boolean isTokenExpired(String token) {
        return extractClaims(token)
                .getExpiration()
                .before(new Date());
    }
}
