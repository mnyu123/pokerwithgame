package com.demo.pokerwithgame.pokerwithgame.controller;

import com.demo.pokerwithgame.pokerwithgame.model.GameMessage;
import com.demo.pokerwithgame.pokerwithgame.model.GameRoom;
import com.demo.pokerwithgame.pokerwithgame.model.HandEvaluator;
import com.demo.pokerwithgame.pokerwithgame.model.Player;
import com.demo.pokerwithgame.pokerwithgame.service.RoomManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class GameController {

    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    // 세션 ID와 [방 이름, 플레이어 이름]을 매핑해두는 저장소 (연결 끊김 감지용)
    private final Map<String, String[]> sessionMap = new ConcurrentHashMap<>();

    // 플레이어 방 입장 처리: /app/room/{roomId}/join
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, @Payload GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        GameRoom room = roomManager.getRoom(roomId);
        String playerName = message.getSender();

        // 🌟 입장할 때 세션 ID를 기록해 둡니다.
        String sessionId = headerAccessor.getSessionId();
        sessionMap.put(sessionId, new String[]{roomId, playerName});

        if (!room.getPlayers().containsKey(playerName) && !room.getSpectators().containsKey(playerName)) {
            if (room.getPlayers().size() < 2) {
                room.getPlayers().put(playerName, new Player(playerName, playerName));
            } else {
                room.getSpectators().put(playerName, new Player(playerName, playerName));
            }
        }

        GameMessage response = new GameMessage();
        response.setType("STATE_UPDATE");
        response.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
    }

    // 2. 사용자가 탭을 끄거나 새로고침할 때 자동으로 실행되는 이벤트 리스너 추가
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // 세션 맵에서 어떤 방에 있던 누가 나갔는지 확인
        if (sessionMap.containsKey(sessionId)) {
            String[] info = sessionMap.get(sessionId);
            String roomId = info[0];
            String playerName = info[1];
            sessionMap.remove(sessionId);

            GameRoom room = roomManager.getRoom(roomId);
            if (room != null) {
                if (room.getPlayers().containsKey(playerName)) {
                    room.getPlayers().remove(playerName);

                    // 진행 중이었다면 기권승 처리
                    if (!"LOBBY".equals(room.getHoldemPhase()) && !"END".equals(room.getHoldemPhase())) {
                        room.setHoldemPhase("END");
                        room.setWinnerMessage("상대방(" + playerName + ")의 통신이 끊어졌습니다! 기권승 🏆");
                    }
                } else if (room.getSpectators().containsKey(playerName)) {
                    room.getSpectators().remove(playerName);
                }

                // 남은 인원들에게 방 상태 즉시 갱신
                GameMessage response = new GameMessage();
                response.setType("STATE_UPDATE");
                response.setData(room);
                messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
            }
        }
    }

    // 플레이어 준비 완료 처리: /app/room/{roomId}/ready
    @MessageMapping("/room/{roomId}/ready")
    public void ready(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        Player player = room.getPlayers().get(message.getSender());

        if (player != null) {
            player.setReady(true);
        }

        // 방에 2명이 있고, 둘 다 Ready 상태인지 확인
        boolean allReady = room.getPlayers().size() == 2 &&
                room.getPlayers().values().stream().allMatch(Player::isReady);

        if (allReady) {
            // 모두 준비 완료 -> 1차 미니게임 시작 알림 발송
            GameMessage startMessage = new GameMessage();
            startMessage.setType("MINIGAME_1_START");
            startMessage.setData("가위바위보를 준비하세요!");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, startMessage);
        } else {
            // 아직 한 명만 레디한 상태면 방 상태만 업데이트
            GameMessage response = new GameMessage();
            response.setType("STATE_UPDATE");
            response.setData(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
        }
    }

    // 1차 미니게임 (가위바위보) 처리: /app/room/{roomId}/rps
    @MessageMapping("/room/{roomId}/rps")
    public void playRps(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String player = message.getSender();
        String choice = (String) message.getData(); // "SCISSORS", "ROCK", "PAPER"

        // 플레이어의 선택 저장
        room.getRpsChoices().put(player, choice);

        // 두 명 모두 선택을 완료했는지 확인
        if (room.getRpsChoices().size() == 2) {
            evaluateRpsAndSendResult(roomId, room);
        }
    }

    // 가위바위보 승패 판정 및 5장 뽑기 로직
    private void evaluateRpsAndSendResult(String roomId, GameRoom room) {
        // 방에 있는 두 명의 플레이어 이름 추출
        List<String> playerNames = new ArrayList<>(room.getRpsChoices().keySet());
        String p1 = playerNames.get(0);
        String p2 = playerNames.get(1);
        String c1 = room.getRpsChoices().get(p1);
        String c2 = room.getRpsChoices().get(p2);

        // 무승부 처리
        if (c1.equals(c2)) {
            room.getRpsChoices().clear(); // 선택 초기화 후 재경기
            GameMessage drawMsg = new GameMessage();
            drawMsg.setType("MINIGAME_1_DRAW");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, drawMsg);
            return;
        }

        // 승자 판정
        String winner = null;
        String loser = null;
        if ((c1.equals("SCISSORS") && c2.equals("PAPER")) ||
                (c1.equals("ROCK") && c2.equals("SCISSORS")) ||
                (c1.equals("PAPER") && c2.equals("ROCK"))) {
            winner = p1; loser = p2;
        } else {
            winner = p2; loser = p1;
        }

        // 덱에서 5장을 뽑아서 방 상태에 임시 저장
        List<String> fiveCards = room.drawCards(5);
        room.setCurrentFiveCards(fiveCards);

        // 결과를 묶어서 프론트엔드로 전송
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("winner", winner);
        resultData.put("loser", loser);
        resultData.put("cards", fiveCards); // 5장의 카드 배열 전송

        GameMessage resultMsg = new GameMessage();
        resultMsg.setType("MINIGAME_1_RESULT");
        resultMsg.setData(resultData);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, resultMsg);
    }

    // 카드 선택 처리: /app/room/{roomId}/selectCard
    @MessageMapping("/room/{roomId}/selectCard")
    public void selectCard(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String winnerName = message.getSender();
        String selectedCard = (String) message.getData();

        // 1. 승자에게 카드 지급
        Player winner = room.getPlayers().get(winnerName);
        winner.getHoleCards().add(selectedCard);

        // 2. 패자에게 남은 카드 1장 랜덤 지급
        List<String> remainingCards = new ArrayList<>(room.getCurrentFiveCards());
        remainingCards.remove(selectedCard);
        Collections.shuffle(remainingCards);
        String loserCard = remainingCards.get(0);

        Player loser = room.getPlayers().values().stream()
                .filter(p -> !p.getPlayerName().equals(winnerName))
                .findFirst().orElse(null);

        if (loser != null) {
            loser.getHoleCards().add(loserCard);
        }

        // 3. 필드 초기화
        room.getCurrentFiveCards().clear();
        room.getRpsChoices().clear();
        room.getDiceGuesses().clear(); // 주사위 기록도 초기화

        // 4. 보유 카드가 2장인지 확인하여 분기 처리
        boolean isHoldemPhase = room.getPlayers().values().stream().allMatch(p -> p.getHoleCards().size() == 2);

        GameMessage nextPhaseMsg = new GameMessage();
        if (isHoldemPhase) {
            // 방의 첫 번째 플레이어에게 턴을 넘김
            String firstPlayer = new ArrayList<>(room.getPlayers().keySet()).get(0);
            room.setCurrentTurn(firstPlayer);
            room.setHoldemPhase("PRE_FLOP");

            nextPhaseMsg.setType("HOLDEM_START");
        } else {
            nextPhaseMsg.setType("MINIGAME_2_START");
        }
        nextPhaseMsg.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, nextPhaseMsg);
    }

    // 2. selectCard 메서드 아래에 새로운 베팅 처리 엔드포인트를 추가하세요.
    @MessageMapping("/room/{roomId}/bet")
    public void processBet(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String player = message.getSender();

        if (!player.equals(room.getCurrentTurn())) return; // 내 턴이 아니면 무시

        Map<String, Object> betData = (Map<String, Object>) message.getData();
        String action = (String) betData.get("action"); // "FOLD", "CHECK", "CALL", "RAISE"
        // 안전한 형변환
        int amount = betData.containsKey("amount") ? ((Number) betData.get("amount")).intValue() : 0;

        Player currentPlayer = room.getPlayers().get(player);
        Player opponent = room.getPlayers().values().stream()
                .filter(p -> !p.getPlayerName().equals(player))
                .findFirst().orElse(null);

        currentPlayer.setHasActed(true); // 행동 완료 처리

        if ("FOLD".equals(action)) {
            currentPlayer.setFolded(true);
            room.setHoldemPhase("END");
            opponent.setChips(opponent.getChips() + room.getPot());
            room.setWinnerMessage(opponent.getPlayerName() + " 기권승! (상대방 폴드)");
        }
        else if ("RAISE".equals(action) || "CALL".equals(action)) {
            // 콜인 경우 차액만 계산
            if ("CALL".equals(action)) {
                amount = room.getHighestBet() - currentPlayer.getCurrentBet();
            }

            currentPlayer.setChips(currentPlayer.getChips() - amount);
            currentPlayer.setCurrentBet(currentPlayer.getCurrentBet() + amount);
            room.setPot(room.getPot() + amount);

            if (currentPlayer.getCurrentBet() > room.getHighestBet()) {
                room.setHighestBet(currentPlayer.getCurrentBet());
                opponent.setHasActed(false); // 상대방이 다시 콜/레이즈를 해야 하므로 행동 상태 초기화
            }
        }

        // 라운드 종료 판정: 둘 다 행동을 했고, 베팅 금액이 같으면 다음 페이즈로 이동
        if (!"END".equals(room.getHoldemPhase()) &&
                currentPlayer.isHasActed() && opponent.isHasActed() &&
                currentPlayer.getCurrentBet() == opponent.getCurrentBet()) {

            nextPhase(room);
        } else if (!"END".equals(room.getHoldemPhase())) {
            // 라운드가 안 끝났으면 턴 넘기기
            room.setCurrentTurn(opponent.getPlayerName());
        }

        GameMessage updateMsg = new GameMessage();
        updateMsg.setType("STATE_UPDATE");
        updateMsg.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, updateMsg);
    }

    // 2. processBet 바로 아래에 페이즈 전환 메서드를 추가하세요.
    private void nextPhase(GameRoom room) {
        room.getPlayers().values().forEach(p -> {
            p.setCurrentBet(0);
            p.setHasActed(false);
        });
        room.setHighestBet(0);

        String firstPlayer = new ArrayList<>(room.getPlayers().keySet()).get(0);
        room.setCurrentTurn(firstPlayer);

        String currentPhase = room.getHoldemPhase();
        if ("PRE_FLOP".equals(currentPhase)) {
            room.setHoldemPhase("FLOP");
            room.getCommunityCards().addAll(room.drawCards(3));
        } else if ("FLOP".equals(currentPhase)) {
            room.setHoldemPhase("TURN");
            room.getCommunityCards().addAll(room.drawCards(1));
        } else if ("TURN".equals(currentPhase)) {
            room.setHoldemPhase("RIVER");
            room.getCommunityCards().addAll(room.drawCards(1));
        } else if ("RIVER".equals(currentPhase)) {
            room.setHoldemPhase("SHOWDOWN");
            evaluateShowdown(room); // 승패 자동 판정!
        }
    }

    // 4. 승패 자동 판독 메서드 추가
    private void evaluateShowdown(GameRoom room) {
        List<Player> active = room.getPlayers().values().stream()
                .filter(p -> !p.isFolded()).toList();

        if (active.size() == 2) {
            Player p1 = active.get(0);
            Player p2 = active.get(1);

            HandEvaluator.HandResult r1 = HandEvaluator.evaluate(p1.getHoleCards(), room.getCommunityCards());
            HandEvaluator.HandResult r2 = HandEvaluator.evaluate(p2.getHoleCards(), room.getCommunityCards());

            if (r1.score > r2.score) {
                p1.setChips(p1.getChips() + room.getPot());
                room.setWinnerMessage(p1.getPlayerName() + " 승리! 🏆 (" + r1.handName + ")");
            } else if (r2.score > r1.score) {
                p2.setChips(p2.getChips() + room.getPot());
                room.setWinnerMessage(p2.getPlayerName() + " 승리! 🏆 (" + r2.handName + ")");
            } else {
                // 무승부 (팟 스플릿)
                p1.setChips(p1.getChips() + room.getPot() / 2);
                p2.setChips(p2.getChips() + room.getPot() / 2);
                room.setWinnerMessage("무승부! 🤝 (" + r1.handName + ") - 팟을 나눕니다.");
            }
        }
    }

    // selectCard 메서드 아래에 새로운 주사위 판정 엔드포인트 추가!
    @MessageMapping("/room/{roomId}/dice")
    public void playDice(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String player = message.getSender();
        String guess = (String) message.getData(); // "ODD" (홀) 또는 "EVEN" (짝)

        room.getDiceGuesses().put(player, guess);

        if (room.getDiceGuesses().size() == 2) {
            evaluateDiceAndSendResult(roomId, room);
        }
    }

    private void evaluateDiceAndSendResult(String roomId, GameRoom room) {
        List<String> playerNames = new ArrayList<>(room.getDiceGuesses().keySet());
        String p1 = playerNames.get(0);
        String p2 = playerNames.get(1);
        String g1 = room.getDiceGuesses().get(p1);
        String g2 = room.getDiceGuesses().get(p2);

        // 주사위 굴리기 (1~6 랜덤)
        int diceNumber = new Random().nextInt(6) + 1;
        String actualResult = (diceNumber % 2 == 0) ? "EVEN" : "ODD";

        boolean p1Correct = g1.equals(actualResult);
        boolean p2Correct = g2.equals(actualResult);

        // 둘 다 맞추거나 둘 다 틀린 경우 -> 무승부 (다시 굴림)
        if (p1Correct == p2Correct) {
            room.getDiceGuesses().clear();
            GameMessage drawMsg = new GameMessage();
            drawMsg.setType("MINIGAME_2_DRAW");
            drawMsg.setData("주사위 결과: " + diceNumber + ". 무승부입니다! 다시 예측해주세요.");
            messagingTemplate.convertAndSend("/topic/room/" + roomId, drawMsg);
            return;
        }

        // 승자와 패자 결정
        String winner = p1Correct ? p1 : p2;
        String loser = p1Correct ? p2 : p1;

        // 새로운 5장 뽑기
        List<String> fiveCards = room.drawCards(5);
        room.setCurrentFiveCards(fiveCards);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("winner", winner);
        resultData.put("loser", loser);
        resultData.put("cards", fiveCards);
        resultData.put("diceNumber", diceNumber);

        GameMessage resultMsg = new GameMessage();
        resultMsg.setType("MINIGAME_2_RESULT");
        resultMsg.setData(resultData);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, resultMsg);
    }

    // 게임 리셋(다음 라운드 준비): /app/room/{roomId}/restart
    @MessageMapping("/room/{roomId}/restart")
    public void restartGame(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);

        // 방 상태 초기화
        room.setPot(0);
        room.getCommunityCards().clear();
        room.setHighestBet(0);
        room.setHoldemPhase("LOBBY");
        room.getCurrentFiveCards().clear();
        room.getRpsChoices().clear();
        room.getDiceGuesses().clear();
        room.setWinnerMessage(null); // 방 상태 초기화 부분에 추가

        // 플레이어 상태 초기화 (칩은 유지)
        for (Player p : room.getPlayers().values()) {
            p.getHoleCards().clear();
            p.setCurrentBet(0);
            p.setFolded(false);
            p.setHasActed(false);
            p.setReady(false); // 다시 Ready를 눌러야 미니게임 시작
        }

        GameMessage updateMsg = new GameMessage();
        updateMsg.setType("STATE_UPDATE");
        updateMsg.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, updateMsg);
    }

    // 플레이어 방 퇴장 처리: /app/room/{roomId}/leave
    @MessageMapping("/room/{roomId}/leave")
    public void leaveRoom(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String playerName = message.getSender();

        // 1. 플레이어 목록에서 제거
        if (room.getPlayers().containsKey(playerName)) {
            room.getPlayers().remove(playerName);

            // 게임 진행 중에 나갔다면 남은 사람의 승리로 게임 종료 처리
            if (!"LOBBY".equals(room.getHoldemPhase()) && !"END".equals(room.getHoldemPhase())) {
                room.setHoldemPhase("END");
                room.setWinnerMessage("상대방(" + playerName + ")이 도망갔습니다! 기권승 🏆");
            }
        }
        // 2. 관전자 목록에서 제거
        else if (room.getSpectators().containsKey(playerName)) {
            room.getSpectators().remove(playerName);
        }

        // 3. 변경된 방 상태를 남은 사람들에게 브로드캐스트
        GameMessage response = new GameMessage();
        response.setType("STATE_UPDATE");
        response.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
    }
}