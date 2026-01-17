package com.example.IndiChessBackend.model;

import java.util.Arrays;

public enum MatchStatus {

    IN_PROGRESS(-2),
    PLAYER1_WON(-1),
    DRAW(0),
    PLAYER2_WON(1);


    private final int code;

    MatchStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MatchStatus fromCode(int code) {
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Invalid GameStatus code: " + code));
    }
}
