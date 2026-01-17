package com.example.IndiChessBackend.service;

import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.model.Match;
import com.example.IndiChessBackend.model.MatchStatus;
import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.repo.MatchRepo;
import com.example.IndiChessBackend.repo.UserRepo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchService {

    // =========================
    // MATCHMAKING STORAGE
    // =========================

    // Waiting players per game type
    private static final Map<GameType, Map<String, Long>> waitingPlayers =
            new ConcurrentHashMap<>();

    // When user started waiting
    private static final Map<String, Long> waitingStartTime =
            new ConcurrentHashMap<>();

    private static final long MAX_WAIT_TIME = 90_000; // 90 seconds

    // matchId -> [player1, player2]
    private static final Map<Long, String[]> matchPlayers =
            new ConcurrentHashMap<>();

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final MatchRepo matchRepo;

    public MatchService(
            JwtService jwtService,
            UserRepo userRepo,
            MatchRepo matchRepo
    ) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.matchRepo = matchRepo;

        // Init queue for each game type
        for (GameType type : GameType.values()) {
            waitingPlayers.put(type, new ConcurrentHashMap<>());
        }
    }

    // =========================
    // JWT FROM COOKIE
    // =========================
    private String getJwtFromCookie(HttpServletRequest request) {

        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if ("JWT".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // =========================
    // CREATE MATCH
    // =========================
    public Optional<Long> createMatch(
            HttpServletRequest request,
            GameType gameType
    ) {

        // Safety: default game type
        if (gameType == null) {
            gameType = GameType.STANDARD;
        }

        String token = getJwtFromCookie(request);
        String username = jwtService.extractUsername(token);

        if (username == null) {
            return Optional.empty();
        }

        Map<String, Long> queue = waitingPlayers.get(gameType);

        synchronized (queue) {

            // Already waiting → do not add again
            if (queue.containsKey(username)) {
                return Optional.of(-1L); // still waiting
            }

            // Try to find opponent
            for (String waitingUser : queue.keySet()) {

                if (!waitingUser.equals(username)) {

                    User p1 = userRepo.getUserByUsername(waitingUser);
                    User p2 = userRepo.getUserByUsername(username);

                    if (p1 == null || p2 == null) {
                        continue;
                    }

                    Match match = new Match(
                            p1,
                            p2,
                            MatchStatus.IN_PROGRESS,
                            gameType
                    );

                    // ⏱ TIME CONTROL
                    if (gameType == GameType.RAPID) {
                        match.setWhiteTime(600); // 10 minutes
                        match.setBlackTime(600);
                    } else if (gameType == GameType.BLITZ) {
                        match.setWhiteTime(180); // 3 minutes
                        match.setBlackTime(180);
                    }

                    matchRepo.save(match);

                    Long matchId = match.getId();

                    matchPlayers.put(
                            matchId,
                            new String[]{waitingUser, username}
                    );

                    queue.remove(waitingUser);
                    waitingStartTime.remove(waitingUser);

                    return Optional.of(matchId);
                }
            }

            // No opponent → start waiting
            queue.put(username, -1L);
            waitingStartTime.put(username, System.currentTimeMillis());

            return Optional.of(-1L); // waiting
        }
    }

    // =========================
    // CHECK MATCH
    // =========================
    public Optional<Long> checkMatch(
            HttpServletRequest request,
            GameType gameType
    ) {

        // Safety: default game type
        if (gameType == null) {
            gameType = GameType.STANDARD;
        }

        String token = getJwtFromCookie(request);
        String username = jwtService.extractUsername(token);

        if (username == null) return Optional.empty();

        Map<String, Long> queue = waitingPlayers.get(gameType);
        if (queue == null) return Optional.empty();

        synchronized (queue) {

            // ✅ MATCH FOUND (check first)
            for (Map.Entry<Long, String[]> entry : matchPlayers.entrySet()) {

                String[] players = entry.getValue();

                if (players[0].equals(username) ||
                        players[1].equals(username)) {

                    Long matchId = entry.getKey();

                    matchPlayers.remove(matchId);
                    queue.remove(players[0]);
                    queue.remove(players[1]);
                    waitingStartTime.remove(players[0]);
                    waitingStartTime.remove(players[1]);

                    return Optional.of(matchId);
                }
            }

            // ⏳ TIMEOUT CHECK
            Long startTime = waitingStartTime.get(username);
            if (startTime != null) {
                long waited = System.currentTimeMillis() - startTime;

                if (waited > MAX_WAIT_TIME) {
                    queue.remove(username);
                    waitingStartTime.remove(username);
                    return Optional.of(-2L); // timeout
                }
            }

            // ⏳ STILL WAITING
            if (queue.containsKey(username)) {
                return Optional.of(-1L);
            }
        }

        return Optional.empty();
    }


    // =========================
    // CANCEL WAITING
    // =========================
    public boolean cancelWaiting(
            HttpServletRequest request,
            GameType gameType
    ) {

        // Safety: default game type
        if (gameType == null) {
            gameType = GameType.STANDARD;
        }

        String token = getJwtFromCookie(request);
        String username = jwtService.extractUsername(token);

        if (username == null) {
            return false;
        }

        Map<String, Long> queue = waitingPlayers.get(gameType);
        if (queue == null) {
            return false;
        }

        // Remove from waiting
        waitingStartTime.remove(username);
        return queue.remove(username) != null;
    }

    // =========================
    // GAME DETAILS
    // =========================
    public Map<String, Object> getGameDetailsForFrontend(
            Long matchId,
            HttpServletRequest request
    ) {

        String token = getJwtFromCookie(request);
        if (token == null) {
            throw new RuntimeException("Not authenticated");
        }

        String username = jwtService.extractUsername(token);
        if (username == null) {
            throw new RuntimeException("Invalid token");
        }

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // Ensure user is part of this match
        boolean isPlayer1 = match.getPlayer1().getUsername().equals(username);
        boolean isPlayer2 = match.getPlayer2().getUsername().equals(username);

        if (!isPlayer1 && !isPlayer2) {
            throw new RuntimeException("User not part of this game");
        }

        boolean isWhite = isPlayer1;

        Map<String, Object> res = new HashMap<>();
        res.put("matchId", matchId);
        res.put("playerColor", isWhite ? "white" : "black");
        res.put("status", match.getStatus());
        res.put("whiteTime", match.getWhiteTime());
        res.put("blackTime", match.getBlackTime());
        res.put("gameType", match.getGameType());

        return res;
    }
}
