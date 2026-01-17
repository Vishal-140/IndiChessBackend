package com.example.IndiChessBackend.service;

import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.model.Match;
import com.example.IndiChessBackend.model.MatchStatus;
import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.repo.MatchRepo;
import com.example.IndiChessBackend.repo.UserRepo;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.IndiChessBackend.model.MatchStatus.IN_PROGRESS;

@Service
public class MatchService {

    // Store waiting players and their match IDs
    private static final Map<String, Long> waitingPlayers = new ConcurrentHashMap<>();
    private static final Map<Long, String[]> matchPlayers = new ConcurrentHashMap<>();

    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final MatchRepo matchRepo;
    private final GameService gameService;

    @Autowired
    MatchService(JwtService jwtService,
                 UserRepo userRepo,
                 MatchRepo matchRepo,
                 GameService gameService) {

        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.matchRepo = matchRepo;
        this.gameService = gameService;

        // Clean up old entries periodically (optional)
        new Timer().schedule(new TimerTask() {
            public void run() {
                cleanupOldEntries();
            }
        }, 0, 60000);
    }

    public String getJwtFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JWT".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void cleanupOldEntries() {
        // Optional cleanup logic
    }

    // =========================
    // CREATE MATCH
    // =========================
    public Optional<Long> createMatch(HttpServletRequest request) {

        String token = getJwtFromCookie(request);
        String userName = jwtService.extractUsername(token);

        if (userName == null) {
            return Optional.empty();
        }

        System.out.println("User " + userName + " requesting match");

        // ✅ DEFAULT GAME TYPE
        GameType gameType = GameType.STANDARD;

        synchronized (this) {

            for (String waitingPlayer : waitingPlayers.keySet()) {

                if (!waitingPlayer.equals(userName)) {

                    User player1 = userRepo.getUserByUsername(waitingPlayer);
                    User player2 = userRepo.getUserByUsername(userName);

                    if (player1 != null && player2 != null) {

                        // ✅ CORRECT MATCH CREATION
                        Match newMatch = new Match(
                                player1,
                                player2,
                                MatchStatus.IN_PROGRESS,
                                gameType
                        );

                        matchRepo.save(newMatch);
                        Long matchId = newMatch.getId();

                        matchPlayers.put(matchId, new String[]{waitingPlayer, userName});
                        waitingPlayers.remove(waitingPlayer);

                        System.out.println("Match created: " + matchId);

                        // Initialize game state
                        gameService.getGameDetails(matchId, request);

                        return Optional.of(matchId);
                    }
                }
            }

            // No opponent found
            waitingPlayers.put(userName, -1L);
            System.out.println("User " + userName + " added to waiting queue");

            return Optional.of(-1L);
        }
    }

    // =========================
    // CHECK MATCH
    // =========================
    public Optional<Long> checkMatch(HttpServletRequest request) {

        String token = getJwtFromCookie(request);
        String userName = jwtService.extractUsername(token);

        if (userName == null) {
            return Optional.empty();
        }

        synchronized (this) {

            if (waitingPlayers.containsKey(userName)) {
                return Optional.of(-1L);
            }

            for (Map.Entry<Long, String[]> entry : matchPlayers.entrySet()) {

                String[] players = entry.getValue();

                if (players[0].equals(userName) || players[1].equals(userName)) {

                    Long matchId = entry.getKey();

                    matchPlayers.remove(matchId);
                    waitingPlayers.remove(players[0]);
                    waitingPlayers.remove(players[1]);

                    System.out.println("Returning match " + matchId + " to " + userName);
                    return Optional.of(matchId);
                }
            }
        }

        return Optional.empty();
    }

    // =========================
    // CANCEL WAITING
    // =========================
    public boolean cancelWaiting(HttpServletRequest request) {

        String token = getJwtFromCookie(request);
        String userName = jwtService.extractUsername(token);

        if (userName == null) {
            return false;
        }

        synchronized (this) {
            boolean removed = waitingPlayers.remove(userName) != null;
            if (removed) {
                System.out.println("User " + userName + " cancelled waiting");
            }
            return removed;
        }
    }

    private Map<String, Object> createPlayerInfo(User user) {
        Map<String, Object> playerInfo = new HashMap<>();
        playerInfo.put("id", user.getUserId());
        playerInfo.put("username", user.getUsername());
        return playerInfo;
    }

    private boolean determineIfMyTurn(Match match, boolean isPlayer1) {

        Integer currentPly = match.getCurrentPly();
        if (currentPly == null) {
            currentPly = 0;
        }

        boolean isWhiteTurn = currentPly % 2 == 0;
        return (isPlayer1 && isWhiteTurn) || (!isPlayer1 && !isWhiteTurn);
    }

    // =========================
    // GAME DETAILS FOR FRONTEND
    // =========================
    public Map<String, Object> getGameDetailsForFrontend(Long matchId,
                                                         HttpServletRequest request) {

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

        User player1 = match.getPlayer1();
        User player2 = match.getPlayer2();

        boolean isPlayer1 = player1.getUsername().equals(username);
        boolean isPlayer2 = player2 != null && player2.getUsername().equals(username);

        if (!isPlayer1 && !isPlayer2) {
            throw new RuntimeException("Not authorized");
        }

        String playerColor = isPlayer1 ? "white" : "black";
        boolean isMyTurn = determineIfMyTurn(match, isPlayer1);

        Map<String, Object> response = new HashMap<>();
        response.put("matchId", match.getId());
        response.put("player1", createPlayerInfo(player1));

        if (player2 != null) {
            response.put("player2", createPlayerInfo(player2));
        }

        response.put("status", match.getStatus().toString());
        response.put("playerColor", playerColor);
        response.put("isMyTurn", isMyTurn);
        response.put("createdAt", match.getCreatedAt());
        response.put("startedAt", match.getStartedAt());
        response.put("currentPly", match.getCurrentPly());
        response.put("fenCurrent", match.getFenCurrent());
        response.put("whiteTime", match.getWhiteTime());
        response.put("blackTime", match.getBlackTime());
        response.put("gameType", match.getGameType());

        return response;
    }
}
