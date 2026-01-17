package com.example.IndiChessBackend.service;

import com.example.IndiChessBackend.model.DTO.*;
import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.model.Match;
import com.example.IndiChessBackend.model.MatchStatus;
import com.example.IndiChessBackend.repo.MatchRepo;
import com.example.IndiChessBackend.repo.UserRepo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameService {

    private final MatchRepo matchRepo;
    private final UserRepo userRepo;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int BLITZ_INCREMENT = 1; // +1 second increment for blitz

    // =========================
    // IN-MEMORY GAME STORAGE
    // =========================
    private final Map<Long, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> gamePlayers = new ConcurrentHashMap<>();

    // =========================
    // GAME STATE CLASS
    // =========================
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GameState {
        private String[][] board;
        private boolean isWhiteTurn;
        private String status;
        private String player1Username;
        private String player2Username;
        private LocalDateTime lastMoveTime;
    }

    // =========================
    // GET GAME DETAILS (REST)
    // =========================
    public GameDTO getGameDetails(Long matchId, Principal principal) {

        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        String username = principal.getName();

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // Determine player color
        String playerColor = determinePlayerColor(match, username);

        // Determine if it's this player's turn
        boolean isMyTurn = determineMyTurn(match, username);

        // Get or initialize game state
        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            gameState = initializeGameState(match);
            activeGames.put(matchId, gameState);

            // Store players
            gamePlayers.put(matchId, List.of(
                    match.getPlayer1().getUsername(),
                    match.getPlayer2().getUsername()
            ));
        }

        // Build response DTO
        GameDTO gameDTO = new GameDTO();
        gameDTO.setId(match.getId());
        gameDTO.setPlayer1(match.getPlayer1());
        gameDTO.setPlayer2(match.getPlayer2());
        gameDTO.setStatus(gameState.getStatus());
        gameDTO.setPlayerColor(playerColor);
        gameDTO.setMyTurn(isMyTurn);
        gameDTO.setBoard(gameState.getBoard());
        gameDTO.setFen(convertBoardToFEN(
                gameState.getBoard(),
                gameState.isWhiteTurn()
        ));
        gameDTO.setCreatedAt(match.getCreatedAt());
        gameDTO.setUpdatedAt(match.getUpdatedAt());

        return gameDTO;
    }

    // =========================
    // PLAYER COLOR
    // =========================
    private String determinePlayerColor(Match match, String username) {
        if (match.getPlayer1().getUsername().equals(username)) {
            return "white";
        } else if (match.getPlayer2().getUsername().equals(username)) {
            return "black";
        }
        throw new RuntimeException("User not part of this game");
    }

    // =========================
    // TURN LOGIC
    // =========================
    private boolean determineMyTurn(Match match, String username) {
        GameState gameState = activeGames.get(match.getId());

        // First move → white starts
        if (gameState == null) {
            return match.getPlayer1().getUsername().equals(username);
        }

        boolean isWhiteTurn = gameState.isWhiteTurn();
        return isWhiteTurn
                ? match.getPlayer1().getUsername().equals(username)
                : match.getPlayer2().getUsername().equals(username);
    }

    // =========================
    // INITIAL BOARD SETUP
    // =========================
    private GameState initializeGameState(Match match) {

        String[][] initialBoard = {
                {"r", "n", "b", "q", "k", "b", "n", "r"},
                {"p", "p", "p", "p", "p", "p", "p", "p"},
                {"", "", "", "", "", "", "", ""},
                {"", "", "", "", "", "", "", ""},
                {"", "", "", "", "", "", "", ""},
                {"", "", "", "", "", "", "", ""},
                {"P", "P", "P", "P", "P", "P", "P", "P"},
                {"R", "N", "B", "Q", "K", "B", "N", "R"}
        };

        return new GameState(
                initialBoard,
                true,
                "IN_PROGRESS",
                match.getPlayer1().getUsername(),
                match.getPlayer2().getUsername(),
                LocalDateTime.now()
        );
    }

    // =========================
// CLOCK UPDATE
// =========================
    private void updateClock(
            Match match,
            boolean wasWhiteTurn,
            LocalDateTime lastMoveTime
    ) {

        // No clock for standard games
        if (match.getGameType() == GameType.STANDARD) {
            return;
        }

        long secondsUsed = java.time.Duration
                .between(lastMoveTime, LocalDateTime.now())
                .getSeconds();

        // Safety check
        if (secondsUsed < 0) {
            secondsUsed = 0;
        }

        if (wasWhiteTurn) {

            int newWhiteTime = match.getWhiteTime() - (int) secondsUsed;

            // Blitz increment (+1)
            if (match.getGameType() == GameType.BLITZ) {
                newWhiteTime += BLITZ_INCREMENT;
            }

            match.setWhiteTime(Math.max(newWhiteTime, 0));

            // Time over
            if (match.getWhiteTime() <= 0) {
                match.setWhiteTime(0);
                match.setStatus(MatchStatus.PLAYER2_WON);
            }

        } else {

            int newBlackTime = match.getBlackTime() - (int) secondsUsed;

            // Blitz increment (+1)
            if (match.getGameType() == GameType.BLITZ) {
                newBlackTime += BLITZ_INCREMENT;
            }

            match.setBlackTime(Math.max(newBlackTime, 0));

            // Time over
            if (match.getBlackTime() <= 0) {
                match.setBlackTime(0);
                match.setStatus(MatchStatus.PLAYER1_WON);
            }
        }
    }

    // =========================
// PROCESS MOVE (WS)
// =========================
    public MoveDTO processMove(
            Long matchId,
            MoveRequest moveRequest,
            Principal principal
    ) {

        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        String username = principal.getName();

        // Basic validation
        if (moveRequest.getFromRow() == null ||
                moveRequest.getFromCol() == null ||
                moveRequest.getToRow() == null ||
                moveRequest.getToCol() == null) {
            throw new RuntimeException("Move coordinates cannot be null");
        }

        if (moveRequest.getPiece() == null ||
                moveRequest.getPiece().isEmpty()) {
            throw new RuntimeException("Piece cannot be null");
        }

        if (moveRequest.getPlayerColor() == null) {
            throw new RuntimeException("Player color cannot be null");
        }

        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            throw new RuntimeException("Game not active");
        }

        // ❌ Do not allow moves after game end
        if (!"IN_PROGRESS".equals(gameState.getStatus())) {
            throw new RuntimeException("Game already finished");
        }

        boolean isWhiteTurn = gameState.isWhiteTurn();
        String expectedPlayer = isWhiteTurn
                ? gameState.getPlayer1Username()
                : gameState.getPlayer2Username();

        if (!username.equals(expectedPlayer)) {
            throw new RuntimeException("Not your turn");
        }

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        // ⏱ Update clock BEFORE move
        updateClock(match, isWhiteTurn, gameState.getLastMoveTime());
        matchRepo.save(match);

        // ❌ Stop if time over
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {

            String winner =
                    match.getStatus() == MatchStatus.PLAYER1_WON
                            ? match.getPlayer1().getUsername()
                            : match.getPlayer2().getUsername();

            gameState.setStatus("GAME_OVER");
            activeGames.put(matchId, gameState);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "GAME_OVER");
            payload.put("reason", "TIME_OUT");
            payload.put("winner", winner);
            payload.put("matchId", matchId);
            payload.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend(
                    "/topic/game-state/" + matchId,
                    (Object) payload
            );

            throw new RuntimeException("Time over");
        }

        // ✅ Update game state
        gameState.setBoard(moveRequest.getBoard());
        gameState.setWhiteTurn(!isWhiteTurn);
        gameState.setLastMoveTime(LocalDateTime.now());
        gameState.setStatus("IN_PROGRESS");

        activeGames.put(matchId, gameState);

        // Update DB (FEN, ply, UCI)
        updateMatchInDatabase(matchId, moveRequest);

        // Build move response
        MoveDTO moveDTO = new MoveDTO();
        moveDTO.setMatchId(matchId);
        moveDTO.setPlayerUsername(username);
        moveDTO.setBoard(moveRequest.getBoard());
        moveDTO.setPlayerColor(moveRequest.getPlayerColor());
        moveDTO.setIsWhiteTurn(!isWhiteTurn);
        moveDTO.setMoveNotation(createMoveNotation(moveRequest));
        moveDTO.setTimestamp(LocalDateTime.now());

        return moveDTO;
    }



    // =========================
    // MOVE NOTATION
    // =========================
    private String createMoveNotation(MoveRequest move) {

        if (move.getCastled()) {
            return move.getToCol() == 6 ? "O-O" : "O-O-O";
        }

        String piece = move.getPiece().equalsIgnoreCase("p")
                ? ""
                : move.getPiece().toUpperCase();

        String capture =
                move.getCapturedPiece() != null ? "x" : "";

        return piece + capture +
                colToFile(move.getToCol()) +
                (8 - move.getToRow());
    }

    private String colToFile(int col) {
        return String.valueOf((char) ('a' + col));
    }

    // =========================
    // DB UPDATE
    // =========================
    private void updateMatchInDatabase(
            Long matchId,
            MoveRequest moveRequest
    ) {

        matchRepo.findById(matchId).ifPresent(match -> {

            if (moveRequest.getFenAfter() != null) {
                match.setFenCurrent(moveRequest.getFenAfter());
            }

            match.setLastMoveUci(createUCI(moveRequest));

            Integer ply = match.getCurrentPly() == null ? 0 : match.getCurrentPly();
            match.setCurrentPly(ply + 1);

            matchRepo.save(match);
        });
    }

    private String createUCI(MoveRequest move) {

        if (move.getFromCol() == null ||
                move.getFromRow() == null ||
                move.getToCol() == null ||
                move.getToRow() == null) {
            return "";
        }

        return "" +
                (char) ('a' + move.getFromCol()) +
                (8 - move.getFromRow()) +
                (char) ('a' + move.getToCol()) +
                (8 - move.getToRow());
    }

    // =========================
// HANDLE PLAYER JOIN
// =========================
    public GameStatusDTO handlePlayerJoin(
            Long matchId,
            JoinRequest joinRequest,
            Principal principal
    ) {

        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        String username = principal.getName();

        // Fetch match
        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Game not found"));

        // ✅ Ensure user is part of this match
        boolean isPlayer1 = match.getPlayer1().getUsername().equals(username);
        boolean isPlayer2 = match.getPlayer2().getUsername().equals(username);

        if (!isPlayer1 && !isPlayer2) {
            throw new RuntimeException("User not part of this game");
        }

        // Get or initialize game state
        GameState gameState = activeGames.computeIfAbsent(
                matchId,
                id -> initializeGameState(match)
        );

        // ✅ Store players (used by resign, draw, chat)
        gamePlayers.putIfAbsent(
                matchId,
                List.of(
                        match.getPlayer1().getUsername(),
                        match.getPlayer2().getUsername()
                )
        );

        // Determine color (DO NOT trust frontend blindly)
        String playerColor = isPlayer1 ? "white" : "black";

        GameStatusDTO dto = new GameStatusDTO();
        dto.setMatchId(matchId);
        dto.setStatus(gameState.getStatus());
        dto.setPlayerColor(playerColor);
        dto.setMyTurn(determineMyTurn(match, username));
        dto.setBoard(gameState.getBoard());
        dto.setFen(convertBoardToFEN(
                gameState.getBoard(),
                gameState.isWhiteTurn()
        ));

        return dto;
    }

    // =========================
// HANDLE RESIGNATION
// =========================
    public void handleResignation(Long matchId, String username) {

        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            throw new RuntimeException("Game not active");
        }

        // ❌ Do not allow resignation if game already finished
        if (!"IN_PROGRESS".equals(gameState.getStatus())) {
            throw new RuntimeException("Game already finished");
        }

        // Update in-memory state
        gameState.setStatus("GAME_OVER");
        activeGames.put(matchId, gameState);

        // Determine winner
        String winner;

        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        if (match.getPlayer1().getUsername().equals(username)) {
            match.setStatus(MatchStatus.PLAYER2_WON);
            winner = match.getPlayer2().getUsername();
        } else if (match.getPlayer2().getUsername().equals(username)) {
            match.setStatus(MatchStatus.PLAYER1_WON);
            winner = match.getPlayer1().getUsername();
        } else {
            throw new RuntimeException("User not part of this match");
        }

        matchRepo.save(match);

        // ✅ Unified GAME_OVER payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_OVER");
        payload.put("reason", "RESIGNATION");
        payload.put("winner", winner);
        payload.put("resignedBy", username);
        payload.put("matchId", matchId);
        payload.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(
                "/topic/game-state/" + matchId,
                (Object) payload
        );
    }



    // =========================
// HANDLE DRAW OFFER
// =========================
    public void handleDrawOffer(Long matchId, String username) {

        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            throw new RuntimeException("Game not active");
        }

        // ❌ No draw offer if game already ended
        if (!"IN_PROGRESS".equals(gameState.getStatus())) {
            throw new RuntimeException("Cannot offer draw. Game already finished");
        }

        String opponent = getOpponentUsername(matchId, username);
        if (opponent == null) {
            throw new RuntimeException("Opponent not found");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DRAW_OFFER");
        payload.put("from", username);
        payload.put("matchId", matchId);
        payload.put("timestamp", System.currentTimeMillis());

        // ✅ Safe WebSocket send
        messagingTemplate.convertAndSendToUser(
                opponent,
                "/queue/draw-offers",
                payload
        );
    }

    // =========================
// DRAW ACCEPT
// =========================
    public void handleDrawAccept(Long matchId, String username) {

        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            throw new RuntimeException("Game not active");
        }

        // ❌ Do not allow if game already ended
        if (!"IN_PROGRESS".equals(gameState.getStatus())) {
            throw new RuntimeException("Game already finished");
        }

        // ✅ Ensure user is part of match
        String opponent = getOpponentUsername(matchId, username);
        if (opponent == null) {
            throw new RuntimeException("User not part of this game");
        }

        // Update in-memory state
        gameState.setStatus("GAME_OVER");
        activeGames.put(matchId, gameState);

        // Update DB (prevent double update)
        matchRepo.findById(matchId).ifPresent(match -> {
            if (match.getStatus() == MatchStatus.IN_PROGRESS) {
                match.setStatus(MatchStatus.DRAW);
                matchRepo.save(match);
            }
        });

        // ✅ Unified GAME_OVER payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_OVER");
        payload.put("reason", "DRAW");
        payload.put("acceptedBy", username);
        payload.put("matchId", matchId);
        payload.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(
                "/topic/game-state/" + matchId,
                (Object) payload
        );
    }

    // =========================
// DRAW REJECT
// =========================
    public void handleDrawReject(Long matchId, String username) {

        GameState gameState = activeGames.get(matchId);
        if (gameState == null || !"IN_PROGRESS".equals(gameState.getStatus())) {
            return;
        }

        String opponent = getOpponentUsername(matchId, username);
        if (opponent == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DRAW_REJECTED");
        payload.put("matchId", matchId);
        payload.put("by", username);
        payload.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSendToUser(
                opponent,
                "/queue/draw-offers",
                (Object) payload
        );
    }

    // =========================
// HELPERS
// =========================
    private String getOpponentUsername(Long matchId, String username) {

        List<String> players = gamePlayers.get(matchId);
        if (players == null || players.size() < 2) return null;

        return players.get(0).equals(username)
                ? players.get(1)
                : players.get(0);
    }

    private String convertBoardToFEN(
            String[][] board,
            boolean isWhiteTurn
    ) {

        StringBuilder fen = new StringBuilder();

        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                if (board[r][c].isEmpty()) {
                    empty++;
                } else {
                    if (empty > 0) {
                        fen.append(empty);
                        empty = 0;
                    }
                    fen.append(board[r][c]);
                }
            }
            if (empty > 0) fen.append(empty);
            if (r < 7) fen.append("/");
        }

        fen.append(isWhiteTurn ? " w " : " b ");
        fen.append("KQkq - 0 1");

        return fen.toString();
    }
}