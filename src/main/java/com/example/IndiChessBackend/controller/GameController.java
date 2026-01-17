package com.example.IndiChessBackend.controller;

import com.example.IndiChessBackend.model.DTO.*;
import com.example.IndiChessBackend.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class GameController {

    private final GameService gameService;

    // =========================
    // REST: GET GAME DETAILS
    // =========================
    @GetMapping("/{matchId}")
    public ResponseEntity<GameDTO> getGame(
            @PathVariable Long matchId,
            Principal principal
    ) {
        try {
            return ResponseEntity.ok(
                    gameService.getGameDetails(matchId, principal)
            );
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }
    }

    // =========================
    // WEBSOCKET: MAKE MOVE
    // =========================
    @MessageMapping("/game/{matchId}/move")
    public MoveDTO handleMove(
            @DestinationVariable Long matchId,
            @Payload MoveRequest moveRequest,
            Principal principal
    ) {
        return gameService.processMove(matchId, moveRequest, principal);
    }

    // =========================
    // WEBSOCKET: PLAYER JOIN
    // =========================
    @MessageMapping("/game/{matchId}/join")
    public GameStatusDTO handlePlayerJoin(
            @DestinationVariable Long matchId,
            @Payload JoinRequest joinRequest,
            Principal principal
    ) {
        return gameService.handlePlayerJoin(matchId, joinRequest, principal);
    }

    // =========================
    // WEBSOCKET: RESIGN
    // =========================
    @MessageMapping("/game/{matchId}/resign")
    public void handleResign(
            @DestinationVariable Long matchId,
            Principal principal
    ) {
        gameService.handleResignation(matchId, principal.getName());
    }

    // =========================
    // WEBSOCKET: DRAW OFFER
    // =========================
    @MessageMapping("/game/{matchId}/draw")
    public void handleDrawOffer(
            @DestinationVariable Long matchId,
            Principal principal
    ) {
        gameService.handleDrawOffer(matchId, principal.getName());
    }

    // =========================
    // WEBSOCKET: DRAW ACCEPT
    // =========================
    @MessageMapping("/game/{matchId}/draw/accept")
    public void handleDrawAccept(
            @DestinationVariable Long matchId,
            Principal principal
    ) {
        gameService.handleDrawAccept(matchId, principal.getName());
    }

    // =========================
    // WEBSOCKET: DRAW REJECT
    // =========================
    @MessageMapping("/game/{matchId}/draw/reject")
    public void handleDrawReject(
            @DestinationVariable Long matchId,
            Principal principal
    ) {
        gameService.handleDrawReject(matchId, principal.getName());
    }

    // =========================
    // WEBSOCKET: CHAT
    // =========================
    @MessageMapping("/game/{matchId}/chat")
    public Map<String, Object> handleChatMessage(
            @DestinationVariable Long matchId,
            @Payload Map<String, String> chatMessage,
            Principal principal
    ) {

        Map<String, Object> response = new HashMap<>();
        response.put("type", "CHAT_MESSAGE");
        response.put("from", principal.getName());
        response.put("message", chatMessage.get("message"));
        response.put("matchId", matchId);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    // =========================
    // REST: GAME STATUS
    // =========================
    @GetMapping("/{matchId}/status")
    public ResponseEntity<Map<String, Object>> getGameStatus(
            @PathVariable Long matchId
    ) {
        Map<String, Object> status = new HashMap<>();
        status.put("matchId", matchId);
        status.put("isActive", true);
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }

    // =========================
    // REST: MOVE HISTORY (OPTIONAL)
    // =========================
    @GetMapping("/{matchId}/moves")
    public ResponseEntity<Map<String, Object>> getMoveHistory(
            @PathVariable Long matchId
    ) {
        Map<String, Object> response = new HashMap<>();
        response.put("matchId", matchId);
        response.put("moves", new ArrayList<>());
        response.put("count", 0);
        return ResponseEntity.ok(response);
    }
}
