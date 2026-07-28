package com.demo.pokerwithgame.pokerwithgame.model;

import lombok.Data;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class GameRoom {
    private String roomId;
    private Map<String, Player> players = new ConcurrentHashMap<>(); // sessionId -> Player
    private Map<String, Player> spectators = new ConcurrentHashMap<>();
    private List<String> deck = new ArrayList<>();
    private int currentRound = 1; // 1 = 첫번째 미니게임, 2 = 두번째 미니게임, 3 = 홀덤 시작

    // 가위바위보 선택 저장 (playerName -> "SCISSORS", "ROCK", "PAPER")
    private Map<String, String> rpsChoices = new ConcurrentHashMap<>();
    // 미니게임 승리자용 5장 임시 저장소
    private List<String> currentFiveCards = new ArrayList<>();

    private Map<String, String> diceGuesses = new ConcurrentHashMap<>(); // "ODD" 또는 "EVEN"
    private int baseDiceNumber; // 2차 미니게임 기준 주사위 숫자

    private String winnerMessage; // 예: "Player1 승리! (플러시)"

    // --- 새로 추가되는 홀덤 관련 변수 ---
    private int pot = 0; // 바닥에 쌓인 총 베팅 금액
    private List<String> communityCards = new ArrayList<>(); // 플랍, 턴, 리버 카드
    private String currentTurn; // 현재 베팅할 플레이어 이름
    private String holdemPhase = "PRE_FLOP"; // PRE_FLOP, FLOP, TURN, RIVER, SHOWDOWN
    private int highestBet = 0; // 현재 라운드의 최대 베팅금 (콜을 위해 필요)
    private int dealerPosition = -1; // 딜러 버튼 인덱스 (매 라운드 순환)

    public GameRoom(String roomId) {
        this.roomId = roomId;
        initDeck();
    }

    // 52장 트럼프 카드 세팅 및 셔플
    private void initDeck() {
        String[] suits = {"S", "D", "H", "C"}; // 스페이드, 다이아몬드, 하트, 클로버
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "T", "J", "Q", "K", "A"};

        for (String suit : suits) {
            for (String rank : ranks) {
                deck.add(suit + "_" + rank);
            }
        }
        Collections.shuffle(deck); // 카드 섞기
    }

    // 덱에서 원하는 장수만큼 카드 뽑기 (미니게임용 5장 뽑을 때 사용)
    public List<String> drawCards(int count) {
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!deck.isEmpty()) {
                drawn.add(deck.remove(0));
            }
        }
        return drawn;
    }
}