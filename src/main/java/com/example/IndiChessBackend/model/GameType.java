package com.example.IndiChessBackend.model;

public enum GameType {

    STANDARD(null, null),   // no time limit
    BLITZ(180, 180),        // 3 minutes + 1 sec increment (handled later)
    RAPID(600, 600);        // 10 minutes

    private final Integer whiteTime;
    private final Integer blackTime;

    GameType(Integer whiteTime, Integer blackTime) {
        this.whiteTime = whiteTime;
        this.blackTime = blackTime;
    }

    public Integer getWhiteTime() {
        return whiteTime;
    }

    public Integer getBlackTime() {
        return blackTime;
    }
}
