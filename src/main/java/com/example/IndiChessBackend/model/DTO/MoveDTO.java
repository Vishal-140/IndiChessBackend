package com.example.IndiChessBackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveDTO {
    private Integer fromRow;
    private Integer fromCol;
    private Integer toRow;
    private Integer toCol;
    private String piece;
    private String promotedTo;
    private String capturedPiece;
    private Boolean castled;
    private Boolean isEnPassant;
    private Boolean isPromotion;
    private String fenBefore;
    private String fenAfter;
    private String[][] board;
    private Boolean isWhiteTurn;
    private String playerColor;
    private Long matchId;
    private LocalDateTime timestamp;
    private String moveNotation;
    private String playerUsername;
}