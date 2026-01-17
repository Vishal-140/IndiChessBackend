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

        Optional<Long> matchId =
                matchService.createMatch(request, gameType);

        Map<String, Long> res = new HashMap<>();
        res.put("matchId", matchId.orElse(-2L));
        return ResponseEntity.ok(res);
    }

    // =========================
    // CHECK MATCH
    // =========================
    @GetMapping("/check-match")
    public ResponseEntity<Map<String, Long>> checkMatch(
            HttpServletRequest request,
            @RequestParam(defaultValue = "STANDARD") GameType gameType
    ) {

        Optional<Long> matchId =
                matchService.checkMatch(request, gameType);

        Map<String, Long> res = new HashMap<>();
        res.put("matchId", matchId.orElse(-2L));
        return ResponseEntity.ok(res);
    }

    // =========================
    // CANCEL WAITING
    // =========================
    @PostMapping("/cancel-waiting")
    public ResponseEntity<Map<String, Boolean>> cancelWaiting(
            HttpServletRequest request,
            @RequestParam(defaultValue = "STANDARD") GameType gameType
    ) {

        boolean cancelled =
                matchService.cancelWaiting(request, gameType);

        return ResponseEntity.ok(
                Map.of("cancelled", cancelled)
        );
    }

    // =========================
    // GAME DETAILS
    // =========================
    @GetMapping("/{matchId}")
    public ResponseEntity<Map<String, Object>> getGameDetails(
            @PathVariable Long matchId,
            HttpServletRequest request
    ) {

        return ResponseEntity.ok(
                matchService.getGameDetailsForFrontend(matchId, request)
        );
    }
}
