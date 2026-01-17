package com.example.IndiChessBackend.controller;

import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class MatchController {

    private final MatchService matchService;

    // =========================
    // CREATE MATCH
    // =========================
    @PostMapping
    public ResponseEntity<Map<String, Long>> createMatch(
            HttpServletRequest request,
            @RequestParam(defaultValue = "STANDARD") GameType gameType
    ) {

        Optional<Long> matchIdOpt = matchService.createMatch(request, gameType);

        Map<String, Long> response = new HashMap<>();
        if (matchIdOpt.isPresent()) {
            response.put("matchId", matchIdOpt.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("matchId", -2L); // -2 indicates error
            return ResponseEntity.badRequest().body(response);
        }
    }

    // =========================
    // CHECK MATCH
    // =========================
    @GetMapping("/check-match")
    public ResponseEntity<Map<String, Long>> checkMatch(HttpServletRequest request) {

        Optional<Long> matchIdOpt = matchService.checkMatch(request);

        Map<String, Long> response = new HashMap<>();
        if (matchIdOpt.isPresent()) {
            response.put("matchId", matchIdOpt.get());
            return ResponseEntity.ok(response);
        } else {
            response.put("matchId", -2L);
            return ResponseEntity.badRequest().body(response);
        }
    }

    // =========================
    // CANCEL WAITING
    // =========================
    @PostMapping("/cancel-waiting")
    public ResponseEntity<Map<String, Boolean>> cancelWaiting(HttpServletRequest request) {

        boolean cancelled = matchService.cancelWaiting(request);

        Map<String, Boolean> response = new HashMap<>();
        response.put("cancelled", cancelled);
        return ResponseEntity.ok(response);
    }

    // =========================
    // GAME DETAILS
    // =========================
    @GetMapping("/{matchId}")
    public ResponseEntity<Map<String, Object>> getGameDetails(
            @PathVariable Long matchId,
            HttpServletRequest request) {

        try {
            Map<String, Object> response =
                    matchService.getGameDetailsForFrontend(matchId, request);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {

            if (e.getMessage().contains("Not authenticated")) {
                return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
            } else if (e.getMessage().contains("Not authorized")) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
            } else if (e.getMessage().contains("not found")) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.status(500)
                    .body(Map.of("error", "Internal server error"));
        }
    }
}
