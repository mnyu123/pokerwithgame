package com.demo.pokerwithgame.pokerwithgame.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Player {
    private String sessionId; // 웹소켓 세션 ID
    private String playerName;
    private List<String> holeCards = new ArrayList<>(); // 획득한 카드 (최대 2장)
    private boolean isReady = false;

    // --- 새로 추가되는 홀덤 관련 변수 ---
    private int chips = 10000;     // 기본 지급 칩
    private int currentBet = 0;    // 이번 라운드에 베팅한 금액
    private boolean isFolded = false; // 다이(포기) 여부

    // 새로 추가할 변수: 이번 라운드에 액션을 취했는지 여부
    private boolean hasActed = false;

    public Player(String sessionId, String playerName) {
        this.sessionId = sessionId;
        this.playerName = playerName;
    }
}