package com.example.IndiChessBackend.service;

import com.example.IndiChessBackend.model.DTO.*;
import com.example.IndiChessBackend.model.Match;
import com.example.IndiChessBackend.model.User;
import com.example.IndiChessBackend.repo.MatchRepo;
import com.example.IndiChessBackend.repo.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.model.MatchStatus;


import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GameService {

    private final MatchRepo matchRepo;
    private final UserRepo userRepo;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MyUserDetailsService userDetailsService;
    private static final int BLITZ_INCREMENT = 1; // +1 second


    // In-memory storage for active games (can be replaced with Redis for production)
    private final Map<Long, GameState> activeGames = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> gamePlayers = new ConcurrentHashMap<>();

    // Helper class to store game state
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


    public GameDTO getGameDetails(Long matchId, HttpServletRequest request) {
        String username = getUsernameFromRequest(request);
        if (username == null) {
            throw new RuntimeException("User not authenticated");
        }

        Optional<Match> matchOpt = matchRepo.findById(matchId);
        if (matchOpt.isEmpty()) {
            throw new RuntimeException("Game not found");
        }

        Match match = matchOpt.get();

        // Determine player color
        String playerColor = determinePlayerColor(match, username);

        // Determine if it's this player's turn
        boolean isMyTurn = determineMyTurn(match, username);

        // Get or initialize game state
        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            gameState = initializeGameState(match);
            activeGames.put(matchId, gameState);

            // Store player usernames
            List<String> players = new ArrayList<>();
            players.add(match.getPlayer1().getUsername());
            players.add(match.getPlayer2().getUsername());
            gamePlayers.put(matchId, players);
        }

        // Create response DTO
        GameDTO gameDTO = new GameDTO();
        gameDTO.setId(match.getId());
        gameDTO.setPlayer1(match.getPlayer1());
        gameDTO.setPlayer2(match.getPlayer2());
        gameDTO.setStatus(gameState.getStatus());
        gameDTO.setPlayerColor(playerColor);
        gameDTO.setMyTurn(isMyTurn);
        gameDTO.setBoard(gameState.getBoard());
        gameDTO.setFen(convertBoardToFEN(gameState.getBoard(), gameState.isWhiteTurn()));
        gameDTO.setCreatedAt(match.getCreatedAt());
        gameDTO.setUpdatedAt(match.getUpdatedAt());

        return gameDTO;
    }

    private String determinePlayerColor(Match match, String username) {
        if (match.getPlayer1().getUsername().equals(username)) {
            return "white"; // Player1 is white
        } else if (match.getPlayer2().getUsername().equals(username)) {
            return "black"; // Player2 is black
        }
        throw new RuntimeException("User not part of this game");
    }

    private boolean determineMyTurn(Match match, String username) {
        GameState gameState = activeGames.get(match.getId());
        if (gameState == null) {
            // First move - white starts
            return match.getPlayer1().getUsername().equals(username);
        }

        // Determine whose turn it is based on game state
        boolean isWhiteTurn = gameState.isWhiteTurn();
        if (isWhiteTurn) {
            return match.getPlayer1().getUsername().equals(username);
        } else {
            return match.getPlayer2().getUsername().equals(username);
        }
    }

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

        GameState gameState = new GameState();
        gameState.setBoard(initialBoard);
        gameState.setWhiteTurn(true); // White starts
        gameState.setStatus("IN_PROGRESS");
        gameState.setPlayer1Username(match.getPlayer1().getUsername());
        gameState.setPlayer2Username(match.getPlayer2().getUsername());
        gameState.setLastMoveTime(LocalDateTime.now());

        return gameState;
    }
    private void updateClock(Match match, boolean wasWhiteTurn, LocalDateTime lastMoveTime) {

        if (match.getGameType() == GameType.STANDARD) return;

        long secondsUsed = java.time.Duration
                .between(lastMoveTime, LocalDateTime.now())
                .getSeconds();

        if (wasWhiteTurn) {
            match.setWhiteTime(match.getWhiteTime() - (int) secondsUsed);

            if (match.getGameType() == GameType.BLITZ) {
                match.setWhiteTime(match.getWhiteTime() + BLITZ_INCREMENT);
            }

            if (match.getWhiteTime() <= 0) {
                match.setStatus(MatchStatus.PLAYER2_WON);
            }

        } else {
            match.setBlackTime(match.getBlackTime() - (int) secondsUsed);

            if (match.getGameType() == GameType.BLITZ) {
                match.setBlackTime(match.getBlackTime() + BLITZ_INCREMENT);
            }

            if (match.getBlackTime() <= 0) {
                match.setStatus(MatchStatus.PLAYER1_WON);
            }
        }
    }


    public MoveDTO processMove(Long matchId, MoveRequest moveRequest, Principal principal) {

        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        String username = principal.getName();

        // ✅ BASIC VALIDATION
        if (moveRequest.getFromRow() == null || moveRequest.getFromCol() == null ||
                moveRequest.getToRow() == null || moveRequest.getToCol() == null) {
            throw new RuntimeException("Move coordinates cannot be null");
        }

        if (moveRequest.getPiece() == null || moveRequest.getPiece().isEmpty()) {
            throw new RuntimeException("Piece cannot be null or empty");
        }

        if (moveRequest.getPlayerColor() == null) {
            throw new RuntimeException("Player color cannot be null");
        }

        // ✅ GET GAME STATE
        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            throw new RuntimeException("Game not found or not active");
        }

        boolean isWhiteTurn = gameState.isWhiteTurn();
        String expectedPlayer = isWhiteTurn
                ? gameState.getPlayer1Username()
                : gameState.getPlayer2Username();

        if (!username.equals(expectedPlayer)) {
            throw new RuntimeException("Not your turn");
        }

        // ✅ COLOR CHECK
        String playerColor = moveRequest.getPlayerColor();
        if (isWhiteTurn && !playerColor.equals("white")) {
            throw new RuntimeException("White's turn");
        }
        if (!isWhiteTurn && !playerColor.equals("black")) {
            throw new RuntimeException("Black's turn");
        }

        // ✅ BOARD CHECK
        String[][] newBoard = moveRequest.getBoard();
        if (newBoard == null) {
            throw new RuntimeException("Board cannot be null");
        }

        // ==========================
        // ⏱ TIMER UPDATE (IMPORTANT)
        // ==========================
        Match match = matchRepo.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        updateClock(match, isWhiteTurn, gameState.getLastMoveTime());
        matchRepo.save(match);

        // If time over → end game
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            gameState.setStatus(match.getStatus().name());
        }

        // ==========================
        // UPDATE GAME STATE
        // ==========================
        gameState.setBoard(newBoard);
        gameState.setWhiteTurn(!isWhiteTurn);
        gameState.setLastMoveTime(LocalDateTime.now());
        gameState.setStatus("IN_PROGRESS");

        activeGames.put(matchId, gameState);

        // ==========================
        // DATABASE UPDATE
        // ==========================
        try {
            updateMatchInDatabase(matchId, moveRequest);
        } catch (Exception e) {
            System.err.println("DB update failed: " + e.getMessage());
        }

        // ==========================
        // CREATE RESPONSE
        // ==========================
        MoveDTO moveDTO = new MoveDTO();
        moveDTO.setFromRow(moveRequest.getFromRow());
        moveDTO.setFromCol(moveRequest.getFromCol());
        moveDTO.setToRow(moveRequest.getToRow());
        moveDTO.setToCol(moveRequest.getToCol());
        moveDTO.setPiece(moveRequest.getPiece());
        moveDTO.setPromotedTo(moveRequest.getPromotedTo());
        moveDTO.setCapturedPiece(moveRequest.getCapturedPiece());
        moveDTO.setCastled(Boolean.TRUE.equals(moveRequest.getCastled()));
        moveDTO.setIsEnPassant(Boolean.TRUE.equals(moveRequest.getIsEnPassant()));
        moveDTO.setIsPromotion(Boolean.TRUE.equals(moveRequest.getIsPromotion()));
        moveDTO.setFenBefore(moveRequest.getFenBefore());
        moveDTO.setFenAfter(moveRequest.getFenAfter());
        moveDTO.setBoard(newBoard);
        moveDTO.setIsWhiteTurn(!isWhiteTurn);
        moveDTO.setPlayerColor(playerColor);
        moveDTO.setMatchId(matchId);
        moveDTO.setTimestamp(LocalDateTime.now());
        moveDTO.setMoveNotation(createMoveNotation(moveRequest));
        moveDTO.setPlayerUsername(username);

        return moveDTO;
    }


    private String createMoveNotation(MoveRequest move) {
        int fromRow = move.getFromRow();
        int fromCol = move.getFromCol();
        int toRow = move.getToRow();
        int toCol = move.getToCol();
        String piece = move.getPiece();

        String fromSquare = colToFile(fromCol) + (8 - fromRow);
        String toSquare = colToFile(toCol) + (8 - toRow);

        if (move.getCastled()) {
            return toCol == 6 ? "O-O" : "O-O-O"; // Kingside or Queenside
        }

        String pieceSymbol = piece.toUpperCase();
        if ("p".equalsIgnoreCase(piece)) {
            pieceSymbol = "";
        }

        String capture = move.getCapturedPiece() != null && !move.getCapturedPiece().isEmpty() ? "x" : "";

        return pieceSymbol + capture + toSquare;
    }

    private String colToFile(int col) {
        return String.valueOf((char) ('a' + col));
    }

    private void updateMatchInDatabase(Long matchId, MoveRequest moveRequest) {
        try {
            Optional<Match> matchOpt = matchRepo.findById(matchId);
            if (matchOpt.isPresent()) {
                Match match = matchOpt.get();

                // Update basic match info
                // match.setUpdatedAt(LocalDateTime.now()); // Remove this line if Match entity doesn't have updatedAt

                // Update game state fields if they exist in your Match entity
                if (moveRequest.getFenAfter() != null) {
                    match.setFenCurrent(moveRequest.getFenAfter());
                    System.out.println("📝 Updated FEN to: " + moveRequest.getFenAfter());
                }

                // Update last move in UCI format
                String uci = createUCI(moveRequest);
                if (!uci.isEmpty()) {
                    match.setLastMoveUci(uci);
                    System.out.println("📝 Updated last move UCI: " + uci);
                }

                // Increment ply counter
                Integer currentPly = match.getCurrentPly();
                if (currentPly == null) {
                    currentPly = 0;
                }
                match.setCurrentPly(currentPly + 1);
                System.out.println("📝 Updated ply to: " + (currentPly + 1));

                // Update match status if needed (e.g., if game ended)
                // match.setStatus(MatchStatus.IN_PROGRESS); // or other status

                // Save the updated match
                matchRepo.save(match);
                System.out.println("💾 Database updated for match: " + matchId);
            } else {
                System.out.println("⚠️ Match not found in database: " + matchId);
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating match in database: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - we want to continue even if DB update fails
        }
    }

    private String createUCI(MoveRequest move) {
        // Add null checks
        if (move.getFromCol() == null || move.getFromRow() == null ||
                move.getToCol() == null || move.getToRow() == null) {
            return "";
        }

        try {
            String fromFile = Character.toString((char) ('a' + move.getFromCol()));
            int fromRank = 8 - move.getFromRow();
            String toFile = Character.toString((char) ('a' + move.getToCol()));
            int toRank = 8 - move.getToRow();

            String uci = fromFile + fromRank + toFile + toRank;

            // Add promotion piece if it's a promotion
            if (Boolean.TRUE.equals(move.getIsPromotion()) && move.getPromotedTo() != null) {
                String promotedPiece = move.getPromotedTo().toLowerCase();
                if (promotedPiece.equals("q")) uci += "q";
                else if (promotedPiece.equals("r")) uci += "r";
                else if (promotedPiece.equals("b")) uci += "b";
                else if (promotedPiece.equals("n")) uci += "n";
            }

            return uci;
        } catch (Exception e) {
            System.err.println("Error creating UCI notation: " + e.getMessage());
            return "";
        }
    }

    public GameStatusDTO handlePlayerJoin(Long matchId, JoinRequest joinRequest, Principal principal) {
        String username = principal.getName();

        GameState gameState = activeGames.get(matchId);
        if (gameState == null) {
            // Initialize game if not already active
            Optional<Match> matchOpt = matchRepo.findById(matchId);
            if (matchOpt.isPresent()) {
                gameState = initializeGameState(matchOpt.get());
                activeGames.put(matchId, gameState);
            } else {
                throw new RuntimeException("Game not found");
            }
        }

        GameStatusDTO statusDTO = new GameStatusDTO();
        statusDTO.setMatchId(matchId);
        statusDTO.setStatus(gameState.getStatus());
        statusDTO.setPlayerColor(joinRequest.getPlayerColor());
        statusDTO.setMyTurn(determineMyTurn(matchId, username));
        statusDTO.setBoard(gameState.getBoard());
        statusDTO.setFen(convertBoardToFEN(gameState.getBoard(), gameState.isWhiteTurn()));

        return statusDTO;
    }

    private boolean determineMyTurn(Long matchId, String username) {
        GameState gameState = activeGames.get(matchId);
        if (gameState == null) return false;

        boolean isWhiteTurn = gameState.isWhiteTurn();
        if (isWhiteTurn) {
            return gameState.getPlayer1Username().equals(username);
        } else {
            return gameState.getPlayer2Username().equals(username);
        }
    }

    private String convertBoardToFEN(String[][] board, boolean isWhiteTurn) {
        StringBuilder fen = new StringBuilder();

        for (int row = 0; row < 8; row++) {
            int emptyCount = 0;
            for (int col = 0; col < 8; col++) {
                String piece = board[row][col];
                if (piece == null || piece.isEmpty()) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(piece);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < 7) {
                fen.append("/");
            }
        }

        // Add active color
        fen.append(" ").append(isWhiteTurn ? "w" : "b");

        // Add castling rights (simplified - assume all available)
        fen.append(" ").append("KQkq");

        // Add en passant target
        fen.append(" ").append("-");

        // Add halfmove clock and fullmove number
        fen.append(" ").append("0 1");

        return fen.toString();
    }

    public void handleResignation(Long matchId, String username) {
        GameState gameState = activeGames.get(matchId);
        if (gameState != null) {
            gameState.setStatus("RESIGNED");
            activeGames.put(matchId, gameState);

            // Notify players
            GameStatusDTO statusDTO = new GameStatusDTO();
            statusDTO.setMatchId(matchId);
            statusDTO.setStatus("RESIGNED");
            statusDTO.setPlayerColor(getPlayerColor(matchId, username));

            messagingTemplate.convertAndSend("/topic/game-state/" + matchId, statusDTO);
        }
    }

    public void handleDrawOffer(Long matchId, String username) {
        GameState gameState = activeGames.get(matchId);
        if (gameState != null) {
            String opponent = getOpponentUsername(matchId, username);

            // Send draw offer to opponent
            Map<String, Object> drawOffer = new HashMap<>();
            drawOffer.put("type", "DRAW_OFFER");
            drawOffer.put("from", username);
            drawOffer.put("matchId", matchId);
            drawOffer.put("timestamp", LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(opponent, "/queue/draw-offers", drawOffer);
        }
    }

    private String getPlayerColor(Long matchId, String username) {
        List<String> players = gamePlayers.get(matchId);
        if (players != null && players.size() >= 2) {
            if (players.get(0).equals(username)) {
                return "white";
            } else if (players.get(1).equals(username)) {
                return "black";
            }
        }
        return null;
    }

    private String getOpponentUsername(Long matchId, String username) {
        List<String> players = gamePlayers.get(matchId);
        if (players != null && players.size() >= 2) {
            if (players.get(0).equals(username)) {
                return players.get(1);
            } else if (players.get(1).equals(username)) {
                return players.get(0);
            }
        }
        return null;
    }

    private String getUsernameFromRequest(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            String username = jwtService.extractUsername(token);
            if (username != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    return jwtService.extractUsername(token);
                }
            }
        }
        return null;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        // Also check cookies
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("JWT".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    public void cleanupInactiveGames() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(2);
        activeGames.entrySet().removeIf(entry ->
                entry.getValue().getLastMoveTime().isBefore(cutoff)
        );
    }
}