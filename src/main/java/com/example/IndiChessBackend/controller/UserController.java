package com.example.IndiChessBackend.controller;

import com.example.IndiChessBackend.model.DTO.LoginDto;
import com.example.IndiChessBackend.service.JwtService;
import com.example.IndiChessBackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    @GetMapping("/hello")
    public String showHello(){
        return "Hello";
    }

    @GetMapping("/user/username")
    public ResponseEntity<String> getUser(){
        System.out.println("User");
        return new ResponseEntity<>("User", HttpStatus.OK);
    }


}
