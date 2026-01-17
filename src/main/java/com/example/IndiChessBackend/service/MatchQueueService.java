package com.example.IndiChessBackend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MatchQueueService {
    // Store which user has which match
    private final Map<String, Long> userToMatchId = new ConcurrentHashMap<>();
    private final Map<String, String> matchToUsers = new ConcurrentHashMap<>();

    public void addPendingMatch(String player1, String player2, Long matchId) {
        userToMatchId.put(player1, matchId);
        userToMatchId.put(player2, matchId);
        matchToUsers.put(matchId.toString(), player1 + "," + player2);
    }

    public Long getPendingMatchId(String username) {
        Long matchId = userToMatchId.get(username);
        if (matchId != null) {
            // Clean up after retrieving
            userToMatchId.remove(username);
            String users = matchToUsers.get(matchId.toString());
            if (users != null) {
                String[] playerUsernames = users.split(",");
                for (String player : playerUsernames) {
                    userToMatchId.remove(player);
                }
                matchToUsers.remove(matchId.toString());
            }
        }
        return matchId;
    }
}