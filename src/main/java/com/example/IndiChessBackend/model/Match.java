package com.example.IndiChessBackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "matches")
@Data
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "player1_id", nullable = false)
    private User player1;

    @ManyToOne
    @JoinColumn(name = "player2_id", nullable = false)
    private User player2;

    @Enumerated(EnumType.STRING)
    private MatchStatus status; // PLAYER1_WON, DRAW, PLAYER2_WON

    private Integer currentPly; // move count

    @Column(name = "fen_current", length = 200)
    private String fenCurrent;

    @Column(name = "last_move_uci", length = 10)
    private String lastMoveUci;

    @OneToMany(
            mappedBy = "match",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("ply ASC")
    private List<Move> moves = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private GameType gameType;

    // ⏱ TIME (seconds)
    @Column(name = "white_time")
    private Integer whiteTime;

    @Column(name = "black_time")
    private Integer blackTime;

    @PastOrPresent
    private LocalDateTime startedAt;

    @FutureOrPresent
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @PastOrPresent
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ✅ MAIN CONSTRUCTOR
    public Match(User player1, User player2, MatchStatus status, GameType gameType) {
        this.player1 = player1;
        this.player2 = player2;
        this.status = status;
        this.gameType = gameType;
        this.currentPly = 0;
        this.createdAt = LocalDateTime.now();
        this.startedAt = LocalDateTime.now();

        // set time by game type
        if (gameType == GameType.BLITZ) {
            this.whiteTime = 180; // 3 min
            this.blackTime = 180;
        } else if (gameType == GameType.RAPID) {
            this.whiteTime = 600; // 10 min
            this.blackTime = 600;
        } else { // STANDARD
            this.whiteTime = null;
            this.blackTime = null;
        }
    }

    public Match() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.startedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
