package com.example.IndiChessBackend.controller;

import com.example.IndiChessBackend.model.DTO.LoginDto;
import com.example.IndiChessBackend.model.DTO.LoginResponseDto;
import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.service.AuthService;
import com.example.IndiChessBackend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@CrossOrigin(value = "http://localhost:3000", allowCredentials = "true")
public class AuthController {

    private final AuthService authservice;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


    @PostMapping("signup")
    public ResponseEntity<User> handleSignup(@RequestBody User user){
//        System.out.println(user);
        return new ResponseEntity<>(authservice.save(user), HttpStatus.CREATED);
    }

    @PostMapping("login")
    public ResponseEntity<?> handleLogin(HttpServletRequest request,
                                                        HttpServletResponse response,
                                                        @RequestBody LoginDto loginDto) throws IOException {


        Authentication authObject = authenticationManager.
                authenticate(new
                        UsernamePasswordAuthenticationToken
                        (loginDto.getUsername(), loginDto.getPassword()));
        if(authObject.isAuthenticated()) {
            String tk = jwtService.generateToken(loginDto.getUsername());
            System.out.println("Inside Auth controller");
            System.out.println(tk);



            ResponseCookie cookie = ResponseCookie.from("JWT", tk).httpOnly(true).
                    secure(false).sameSite("lax").path("/").maxAge(3600).build();
            response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());



            return ResponseEntity.ok(tk);
        }

        return new ResponseEntity<>(new LoginResponseDto(null, "Auth Failed"), HttpStatus.BAD_REQUEST);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response) {

        ResponseCookie cookie = ResponseCookie.from("JWT", "")
                .httpOnly(true)
                .secure(false) // true in production
                .path("/")
                .maxAge(0)
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok("Logged out successfully");
    }


    @GetMapping("home")
    public ResponseEntity<?> handleHome(){
        System.out.println("Home");
        return ResponseEntity.ok("Home");
    }





}
