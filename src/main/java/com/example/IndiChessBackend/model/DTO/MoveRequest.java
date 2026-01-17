package com.example.IndiChessBackend.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveRequest {
    // CHANGE THESE FROM PRIMITIVE 'int' TO WRAPPER 'Integer'
    private Integer fromRow;
    private Integer fromCol;
    private Integer toRow;
    private Integer toCol;

    private String piece;
    private String promotedTo;
    private String capturedPiece;
    private Boolean castled; // Change from boolean to Boolean
    private Boolean isEnPassant; // Change from boolean to Boolean
    private Boolean isPromotion; // Change from boolean to Boolean
    private String fenBefore;
    private String fenAfter;
    private String[][] board;
    private Boolean isWhiteTurn; // Change from boolean to Boolean
    private String playerColor;
    private Long matchId;
    private LocalDateTime timestamp;
}