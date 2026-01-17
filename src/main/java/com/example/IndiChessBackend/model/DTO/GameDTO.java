package com.example.IndiChessBackend.model.DTO;

import com.example.IndiChessBackend.model.GameType;
import com.example.IndiChessBackend.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameDTO {

    private Long id;

    private User player1;
    private User player2;

    private String status;

    // For the requesting player: "white" or "black"
    private String playerColor;

    private boolean isMyTurn;

    private String[][] board;

    private String fen;

    // ⏱ TIME (seconds)
    private Integer whiteTime;
    private Integer blackTime;

    // 🎮 GAME MODE
    private GameType gameType;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
