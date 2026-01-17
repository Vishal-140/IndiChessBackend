package com.example.IndiChessBackend.controller;
import com.example.IndiChessBackend.model.DTO.*;
import com.example.IndiChessBackend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class GameController {

    private final GameService gameService;

    // REST endpoint to get game details
    @GetMapping("/{matchId}")
    public ResponseEntity<GameDTO> getGame(@PathVariable Long matchId,
                                           @CookieValue(value = "JWT", required = false) String token) {
        try {
            // In a real implementation, you'd validate the token and get user info
            GameDTO game = gameService.getGameDetails(matchId, null); // Pass request if needed
            return ResponseEntity.ok(game);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // WebSocket endpoint for making moves
    @MessageMapping("/game/{matchId}/move")
    @SendTo("/topic/moves/{matchId}")
    public MoveDTO handleMove(@DestinationVariable Long matchId,
                              @Payload MoveRequest moveRequest,
                              Principal principal) {
        try {
            System.out.println("Received move for game " + matchId + " from " + principal.getName());
            return gameService.processMove(matchId, moveRequest, principal);
        } catch (Exception e) {
            System.err.println("Error processing move: " + e.getMessage());
            MoveDTO errorMove = new MoveDTO();
            errorMove.setMatchId(matchId);
            errorMove.setMoveNotation("ERROR: " + e.getMessage());
            return errorMove;
        }
    }

    // WebSocket endpoint for player joining
    @MessageMapping("/game/{matchId}/join")
    @SendTo("/topic/game/{matchId}")
    public GameStatusDTO handlePlayerJoin(@DestinationVariable Long matchId,
                                          @Payload JoinRequest joinRequest,
                                          Principal principal) {
        try {
            System.out.println("Player " + principal.getName() + " joining game " + matchId);
            return gameService.handlePlayerJoin(matchId, joinRequest, principal);
        } catch (Exception e) {
            System.err.println("Error handling player join: " + e.getMessage());
            GameStatusDTO errorStatus = new GameStatusDTO();
            errorStatus.setMatchId(matchId);
            errorStatus.setStatus("ERROR: " + e.getMessage());
            return errorStatus;
        }
    }

    // WebSocket endpoint for resigning
    @MessageMapping("/game/{matchId}/resign")
    @SendTo("/topic/game-state/{matchId}")
    public Map<String, Object> handleResign(@DestinationVariable Long matchId,
                                            Principal principal) {
        try {
            System.out.println("Player " + principal.getName() + " resigning from game " + matchId);
            gameService.handleResignation(matchId, principal.getName());

            Map<String, Object> response = new HashMap<>();
            response.put("type", "RESIGNATION");
            response.put("player", principal.getName());
            response.put("matchId", matchId);
            response.put("timestamp", System.currentTimeMillis());
            return response;
        } catch (Exception e) {
            System.err.println("Error handling resignation: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    // WebSocket endpoint for draw offer
    @MessageMapping("/game/{matchId}/draw")
    @SendToUser("/queue/draw-offers")
    public Map<String, Object> handleDrawOffer(@DestinationVariable Long matchId,
                                               Principal principal) {
        try {
            System.out.println("Player " + principal.getName() + " offering draw in game " + matchId);
            gameService.handleDrawOffer(matchId, principal.getName());

            Map<String, Object> response = new HashMap<>();
            response.put("type", "DRAW_OFFER_SENT");
            response.put("matchId", matchId);
            response.put("timestamp", System.currentTimeMillis());
            return response;
        } catch (Exception e) {
            System.err.println("Error handling draw offer: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    // WebSocket endpoint for accepting draw
    @MessageMapping("/game/{matchId}/draw/accept")
    @SendTo("/topic/game-state/{matchId}")
    public Map<String, Object> handleDrawAccept(@DestinationVariable Long matchId,
                                                Principal principal) {
        try {
            System.out.println("Player " + principal.getName() + " accepting draw in game " + matchId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "DRAW_ACCEPTED");
            response.put("player", principal.getName());
            response.put("matchId", matchId);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "DRAW");
            return response;
        } catch (Exception e) {
            System.err.println("Error handling draw accept: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    // WebSocket endpoint for chat messages
    @MessageMapping("/game/{matchId}/chat")
    @SendTo("/topic/chat/{matchId}")
    public Map<String, Object> handleChatMessage(@DestinationVariable Long matchId,
                                                 @Payload Map<String, String> chatMessage,
                                                 Principal principal) {
        try {
            System.out.println("Chat message from " + principal.getName() + " in game " + matchId);

            Map<String, Object> response = new HashMap<>();
            response.put("type", "CHAT_MESSAGE");
            response.put("from", principal.getName());
            response.put("message", chatMessage.get("message"));
            response.put("matchId", matchId);
            response.put("timestamp", System.currentTimeMillis());
            return response;
        } catch (Exception e) {
            System.err.println("Error handling chat message: " + e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return error;
        }
    }

    // REST endpoint to check game status
    @GetMapping("/{matchId}/status")
    public ResponseEntity<Map<String, Object>> getGameStatus(@PathVariable Long matchId) {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("matchId", matchId);
            status.put("isActive", true);
            status.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // REST endpoint to get move history (optional)
    @GetMapping("/{matchId}/moves")
    public ResponseEntity<Map<String, Object>> getMoveHistory(@PathVariable Long matchId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("matchId", matchId);
            response.put("moves", new ArrayList<>()); // Return empty list for now
            response.put("count", 0);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
