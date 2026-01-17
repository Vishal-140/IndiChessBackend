package com.example.IndiChessBackend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(
        name = "moves",
        uniqueConstraints = @UniqueConstraint(columnNames = {"match_id", "ply"})
)
public class Move {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    private int ply;            // 1..N (half-move index)
    private int moveNumber;     // 1,2,3...

    @Enumerated(EnumType.STRING)
    private PieceColor color;   // WHITE / BLACK

    private String uci;         // e2e4
    private String san;         // e4

    private String fenBefore;
    private String fenAfter;

//    private Integer moveTimeMs;

    private LocalDateTime createdAt;
}

