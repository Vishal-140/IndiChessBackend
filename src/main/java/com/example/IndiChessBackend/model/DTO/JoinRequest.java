package com.example.IndiChessBackend.model.DTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRequest {
    private String type;
    private String playerColor;
    private LocalDateTime timestamp;
}
