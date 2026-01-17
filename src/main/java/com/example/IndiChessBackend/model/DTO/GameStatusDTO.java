package com.example.IndiChessBackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStatusDTO {
    private String status;
    private Long matchId;
    private String playerColor;
    private boolean isMyTurn;
    private String[][] board;
    private String fen;
}