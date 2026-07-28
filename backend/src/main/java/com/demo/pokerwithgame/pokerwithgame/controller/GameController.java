package com.demo.pokerwithgame.pokerwithgame.controller;

import com.demo.pokerwithgame.pokerwithgame.model.GameMessage;
import com.demo.pokerwithgame.pokerwithgame.model.GameRoom;
import com.demo.pokerwithgame.pokerwithgame.model.HandEvaluator;
import com.demo.pokerwithgame.pokerwithgame.model.Player;
import com.demo.pokerwithgame.pokerwithgame.service.RoomManager;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class GameController {

    // 방 목록 API를 위한 DTO (Data Transfer Object)
    @AllArgsConstructor
    public static class RoomInfo {
        public String roomId;
        public int playerCount;
        public int spectatorCount;
        public String gamePhase;
    }


    private final RoomManager roomManager;
    private final SimpMessagingTemplate messagingTemplate;

    // 세션 ID와 [방 이름, 플레이어 이름]을 매핑해두는 저장소 (연결 끊김 감지용)
    private final Map<String, String[]> sessionMap = new ConcurrentHashMap<>();

    // 1. 입장 로직 (최대 4인으로 확장)
    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, @Payload GameMessage message, SimpMessageHeaderAccessor headerAccessor) {
        GameRoom room = roomManager.getRoom(roomId);
        String playerName = message.getSender();
        
        String sessionId = headerAccessor.getSessionId();
        sessionMap.put(sessionId, new String[]{roomId, playerName});

        if (!room.getPlayers().containsKey(playerName) && !room.getSpectators().containsKey(playerName)) {
            if (room.getPlayers().size() < 4) { // 🌟 최대 4인으로 변경
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

        if (sessionMap.containsKey(sessionId)) {
            String[] info = sessionMap.get(sessionId);
            String roomId = info[0];
            String playerName = info[1];
            sessionMap.remove(sessionId);

            GameRoom room = roomManager.getRoom(roomId);
            if (room != null) {
                if (room.getPlayers().containsKey(playerName)) {
                    room.getPlayers().remove(playerName);
                    
                    // 남은 인원이 1명이면 기권승 처리
                    long activeCount = room.getPlayers().values().stream().filter(p -> !p.isFolded()).count();
                    if (!"LOBBY".equals(room.getHoldemPhase()) && !"END".equals(room.getHoldemPhase()) && activeCount <= 1) {
                        room.setHoldemPhase("END");
                        room.setWinnerMessage("다른 플레이어들이 모두 나가서 기권승 처리되었습니다! 🏆");
                    }
                } else if (room.getSpectators().containsKey(playerName)) {
                    room.getSpectators().remove(playerName);
                }

                GameMessage response = new GameMessage();
                response.setType("STATE_UPDATE");
                response.setData(room);
                messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
            }
        }
    }

    @MessageMapping("/room/{roomId}/ready")
    public void toggleReady(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        Player p = room.getPlayers().get(message.getSender());
        if (p != null) p.setReady(!p.isReady());

        // 최소 2명 이상이고, 들어온 '모든' 플레이어가 준비되었을 때 시작
        boolean allReady = room.getPlayers().size() >= 2 && 
                           room.getPlayers().values().stream().allMatch(Player::isReady);

        if (allReady) {
            room.setHoldemPhase("MINIGAME_1");
            GameMessage nextMsg = new GameMessage();
            nextMsg.setType("MINIGAME_1_START");
            nextMsg.setData(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, nextMsg);
        } else {
            GameMessage updateMsg = new GameMessage();
            updateMsg.setType("STATE_UPDATE");
            updateMsg.setData(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, updateMsg);
        }
    }

    // 1차 미니게임 (가위바위보) 처리: /app/room/{roomId}/rps
    @MessageMapping("/room/{roomId}/rps")
    public void selectRps(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        room.getRpsChoices().put(message.getSender(), (String) message.getData());

        if (room.getRpsChoices().size() == room.getPlayers().size()) {
            evaluateRpsAndSendResult(roomId, room);
        }
    }

    // 가위바위보 승패 판정 및 5장 뽑기 로직
    private void evaluateRpsAndSendResult(String roomId, GameRoom room) {
        String[] rps = {"ROCK", "PAPER", "SCISSORS"};
        String serverChoice = rps[new Random().nextInt(3)]; // 서버가 임의로 선택

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("serverChoice", serverChoice);

        for (Player p : room.getPlayers().values()) {
            String pChoice = room.getRpsChoices().get(p.getPlayerName());
            boolean isWin = false;

            if (pChoice.equals("ROCK") && serverChoice.equals("SCISSORS")) isWin = true;
            if (pChoice.equals("SCISSORS") && serverChoice.equals("PAPER")) isWin = true;
            if (pChoice.equals("PAPER") && serverChoice.equals("ROCK")) isWin = true;

            Map<String, Object> pResult = new HashMap<>();
            pResult.put("isWin", isWin);
            
            if (isWin) {
                pResult.put("cards", room.drawCards(5)); // 이기면 5장 선택권 부여
            } else {
                p.getHoleCards().add(room.drawCards(1).get(0)); // 지거나 비기면 랜덤 1장 즉시 부여
                pResult.put("cards", new ArrayList<>());
            }
            resultData.put(p.getPlayerName(), pResult);
        }

        room.getRpsChoices().clear();
        resultData.put("roomState", room);

        GameMessage resultMsg = new GameMessage();
        resultMsg.setType("MINIGAME_1_RESULT");
        resultMsg.setData(resultData);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, resultMsg);

        // 모두가 카드를 1장씩 가지게 되었다면 바로 2차 미니게임으로
        checkNextPhase(room, roomId, 1, "MINIGAME_2_START");
    }

    // 카드 선택 처리: /app/room/{roomId}/selectCard
    @MessageMapping("/room/{roomId}/selectCard")
    public void selectCard(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        Player picker = room.getPlayers().get(message.getSender());
        
        if (picker != null) {
            picker.getHoleCards().add((String) message.getData());
        }

        // 각 상황에 맞춰 다음 페이즈로 자동으로 넘어가는지 체크
        checkNextPhase(room, roomId, 1, "MINIGAME_2_START");
        checkNextPhase(room, roomId, 2, "HOLDEM_START");
    }

    // 2. selectCard 메서드 아래에 새로운 베팅 처리 엔드포인트를 추가하세요.
    @MessageMapping("/room/{roomId}/bet")
    public void processBet(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        String sender = message.getSender();
        
        if (!sender.equals(room.getCurrentTurn())) return;

        Player currentPlayer = room.getPlayers().get(sender);
        Map<String, Object> data = (Map<String, Object>) message.getData();
        String action = (String) data.get("action");
        int amount = data.containsKey("amount") ? (int) data.get("amount") : 0;

        currentPlayer.setHasActed(true);

        if ("FOLD".equals(action)) {
            currentPlayer.setFolded(true);
            
            // 본인이 폴드했는데 남은 사람이 1명이면 그 사람의 승리로 즉시 종료
            List<Player> activePlayers = room.getPlayers().values().stream().filter(p -> !p.isFolded()).toList();
            if (activePlayers.size() == 1) {
                room.setHoldemPhase("END");
                Player winner = activePlayers.get(0);
                winner.setChips(winner.getChips() + room.getPot());
                room.setWinnerMessage(winner.getPlayerName() + " 승리! 🏆 (나머지 모두 기권)");
                
                GameMessage response = new GameMessage();
                response.setType("STATE_UPDATE");
                response.setData(room);
                messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
                return;
            }
        } else if ("CALL".equals(action)) {
            int callAmount = room.getHighestBet() - currentPlayer.getCurrentBet();
            currentPlayer.setChips(currentPlayer.getChips() - callAmount);
            currentPlayer.setCurrentBet(currentPlayer.getCurrentBet() + callAmount);
            room.setPot(room.getPot() + callAmount);
        } else if ("RAISE".equals(action)) {
            int raiseAmount = room.getHighestBet() - currentPlayer.getCurrentBet() + amount;
            currentPlayer.setChips(currentPlayer.getChips() - raiseAmount);
            currentPlayer.setCurrentBet(currentPlayer.getCurrentBet() + raiseAmount);
            room.setPot(room.getPot() + raiseAmount);
            room.setHighestBet(currentPlayer.getCurrentBet());

            // 레이즈가 나오면 다른 사람들은 다시 액션을 취해야 하므로 hasActed 초기화
            room.getPlayers().values().stream()
                .filter(p -> !p.isFolded() && !p.getPlayerName().equals(sender))
                .forEach(p -> p.setHasActed(false));
        }

        // 라운드 종료 체크: 폴드하지 않은 모두가 액션을 했고, 모두의 베팅금이 최고 베팅금과 일치하는지
        boolean roundOver = room.getPlayers().values().stream()
                .filter(p -> !p.isFolded())
                .allMatch(p -> p.isHasActed() && p.getCurrentBet() == room.getHighestBet());

        if (roundOver) {
            nextPhase(room);
        } else {
            room.setCurrentTurn(getNextTurn(room, currentPlayer.getPlayerName()));
        }

        GameMessage response = new GameMessage();
        response.setType("STATE_UPDATE");
        response.setData(room);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
    }

    // 블라인드 베팅 적용 및 턴 설정
    private void applyBlindsAndSetTurn(GameRoom room) {
        List<Player> activePlayers = room.getPlayers().values().stream()
                .filter(p -> !p.isFolded() && p.getChips() > 0)
                .sorted(Comparator.comparing(Player::getPlayerName))
                .toList();

        if (activePlayers.size() < 2) return; // 2명 미만이면 블라인드 없음

        // 딜러 버튼 위치 순환
        room.setDealerPosition((room.getDealerPosition() + 1) % activePlayers.size());
        int dealerIndex = room.getDealerPosition();

        // 스몰블라인드, 빅블라인드 플레이어 결정
        int sbIndex = (activePlayers.size() == 2) ? dealerIndex : (dealerIndex + 1) % activePlayers.size();
        int bbIndex = (activePlayers.size() == 2) ? (dealerIndex + 1) % activePlayers.size() : (dealerIndex + 2) % activePlayers.size();

        Player sbPlayer = activePlayers.get(sbIndex);
        Player bbPlayer = activePlayers.get(bbIndex);

        int smallBlindAmount = 800;
        int bigBlindAmount = 1000;

        // 블라인드 강제 베팅 (올인 상황 고려)
        int sbBet = Math.min(smallBlindAmount, sbPlayer.getChips());
        sbPlayer.setChips(sbPlayer.getChips() - sbBet);
        sbPlayer.setCurrentBet(sbBet);

        int bbBet = Math.min(bigBlindAmount, bbPlayer.getChips());
        bbPlayer.setChips(bbPlayer.getChips() - bbBet);
        bbPlayer.setCurrentBet(bbBet);

        room.setPot(sbBet + bbBet);
        room.setHighestBet(Math.max(sbBet, bbBet));

        // 첫 베팅 순서는 빅블라인드 다음 사람
        int firstTurnIndex = (bbIndex + 1) % activePlayers.size();
        room.setCurrentTurn(activePlayers.get(firstTurnIndex).getPlayerName());
    }

    // 2. processBet 바로 아래에 페이즈 전환 메서드를 추가하세요.
    private void nextPhase(GameRoom room) {
        // 다음 베팅 라운드를 위해 플레이어들의 베팅금과 액션 상태 초기화
        room.getPlayers().values().forEach(p -> {
            p.setCurrentBet(0);
            p.setHasActed(false);
        });
        room.setHighestBet(0);
        room.setCurrentTurn(getNextTurn(room, null));

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
            evaluateShowdown(room);
        }
    }

    // 4. 승패 자동 판독 메서드 추가
    private void evaluateShowdown(GameRoom room) {
        List<Player> active = room.getPlayers().values().stream()
                .filter(p -> !p.isFolded()).toList();

        if (!active.isEmpty()) {
            long maxScore = -1;
            List<Player> winners = new ArrayList<>();
            Map<String, String> handNames = new HashMap<>();

            for (Player p : active) {
                HandEvaluator.HandResult r = HandEvaluator.evaluate(p.getHoleCards(), room.getCommunityCards());
                handNames.put(p.getPlayerName(), r.handName);
                
                if (r.score > maxScore) {
                    maxScore = r.score;
                    winners.clear();
                    winners.add(p);
                } else if (r.score == maxScore) {
                    winners.add(p); // 동점자 발생
                }
            }

            // 승자끼리 팟 분할
            int splitPot = room.getPot() / winners.size();
            StringBuilder winnerMsg = new StringBuilder();
            
            for (Player w : winners) {
                w.setChips(w.getChips() + splitPot);
                winnerMsg.append(w.getPlayerName()).append("(").append(handNames.get(w.getPlayerName())).append(") ");
            }
            
            if (winners.size() == 1) {
                room.setWinnerMessage(winnerMsg.toString() + "승리! 🏆");
            } else {
                room.setWinnerMessage(winnerMsg.toString() + " - 공동 우승! 🤝 팟을 나눕니다.");
            }
        }
    }

    // selectCard 메서드 아래에 새로운 주사위 판정 엔드포인트 추가!
    @MessageMapping("/room/{roomId}/dice")
    public void guessDice(@DestinationVariable String roomId, @Payload GameMessage message) {
        GameRoom room = roomManager.getRoom(roomId);
        room.getDiceGuesses().put(message.getSender(), (String) message.getData());

        if (room.getDiceGuesses().size() == room.getPlayers().size()) {
            evaluateDiceAndSendResult(roomId, room);
        }
    }

    private void evaluateDiceAndSendResult(String roomId, GameRoom room) {
        int baseDiceNumber = room.getBaseDiceNumber();

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("baseDiceNumber", baseDiceNumber);

        // 모든 플레이어를 순회하며 개별 판정
        for (Player p : room.getPlayers().values()) {
            String guess = room.getDiceGuesses().get(p.getPlayerName()); // "HIGHER" or "LOWER"
            int playerDiceNumber = new Random().nextInt(6) + 1;

            boolean isWin = false;
            if ("HIGHER".equals(guess) && playerDiceNumber > baseDiceNumber) {
                isWin = true;
            } else if ("LOWER".equals(guess) && playerDiceNumber < baseDiceNumber) {
                isWin = true;
            }

            Map<String, Object> pResult = new HashMap<>();
            pResult.put("isWin", isWin);
            pResult.put("playerDiceNumber", playerDiceNumber); // 플레이어 주사위 결과도 보내줌
            if (isWin) {
                pResult.put("cards", room.drawCards(5));
            } else {
                p.getHoleCards().add(room.drawCards(1).get(0));
                pResult.put("cards", new ArrayList<>());
            }
            resultData.put(p.getPlayerName(), pResult);
        }

        room.getDiceGuesses().clear();
        room.setBaseDiceNumber(0); // 다음 게임을 위해 초기화
        resultData.put("roomState", room);

        GameMessage resultMsg = new GameMessage();
        resultMsg.setType("MINIGAME_2_RESULT");
        resultMsg.setData(resultData);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, resultMsg);

        checkNextPhase(room, roomId, 2, "HOLDEM_START");
    }

    // 🌟 페이즈 전환 공통 체크 로직 (모두가 N장의 카드를 가졌는지 확인)
    private void checkNextPhase(GameRoom room, String roomId, int targetCardCount, String nextPhaseType) {
        boolean allReady = room.getPlayers().values().stream()
                .allMatch(p -> p.getHoleCards().size() == targetCardCount);

        if (allReady) {
            if (nextPhaseType.equals("HOLDEM_START")) {
                room.setHoldemPhase("PRE_FLOP");
                applyBlindsAndSetTurn(room); // 블라인드 룰 적용 및 첫 턴 설정
            } else if (nextPhaseType.equals("MINIGAME_2_START")) {
                room.setBaseDiceNumber(new Random().nextInt(6) + 1); // 기준 주사위 설정
            }
            
            GameMessage nextPhaseMsg = new GameMessage();
            nextPhaseMsg.setType(nextPhaseType);
            nextPhaseMsg.setData(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, nextPhaseMsg);
        }
    }

    // 🌟 3~4인용 알파벳 순서 기반 안정적인 턴 계산 로직
    private String getNextTurn(GameRoom room, String currentTurn) {
        List<String> activePlayers = room.getPlayers().values().stream()
                .filter(p -> !p.isFolded())
                .map(Player::getPlayerName)
                .sorted() // 순서 꼬임을 방지하기 위해 플레이어 이름을 정렬하여 순환
                .toList();

        if (activePlayers.isEmpty()) return null;
        if (currentTurn == null) return activePlayers.get(0);

        int currentIndex = activePlayers.indexOf(currentTurn);
        if (currentIndex == -1 || currentIndex == activePlayers.size() - 1) {
            return activePlayers.get(0);
        }
        return activePlayers.get(currentIndex + 1);
    }

    private void checkHoldemPhaseAndSend(GameRoom room, String roomId) {
        boolean isHoldemPhase = room.getPlayers().values().stream().allMatch(p -> p.getHoleCards().size() == 2);

        if (isHoldemPhase) {
            String firstPlayer = new ArrayList<>(room.getPlayers().keySet()).get(0);
            room.setCurrentTurn(firstPlayer);
            room.setHoldemPhase("PRE_FLOP");

            GameMessage nextPhaseMsg = new GameMessage();
            nextPhaseMsg.setType("HOLDEM_START");
            nextPhaseMsg.setData(room);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, nextPhaseMsg);
        }
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
        room.setBaseDiceNumber(0);
        room.setWinnerMessage(null); // 방 상태 초기화 부분에 추가
        room.setDealerPosition(-1); // 딜러 버튼 위치 초기화

        // 플레이어 상태 초기화 (칩은 유지)
        for (Player p : room.getPlayers().values()) {
            p.getHoleCards().clear();
            p.setCurrentBet(0);
            p.setFolded(false);
            p.setHasActed(false);
            p.setReady(false); // 다시 Ready를 눌러야 미니게임 시작
        }

        // 칩이 0이 된 플레이어를 관전자로 이동
        List<Player> bankruptPlayers = new ArrayList<>();
        for (Player p : room.getPlayers().values()) {
            if (p.getChips() <= 0) {
                bankruptPlayers.add(p);
            }
        }
        for (Player bankruptPlayer : bankruptPlayers) {
            room.getPlayers().remove(bankruptPlayer.getPlayerName());
            room.getSpectators().put(bankruptPlayer.getPlayerName(), bankruptPlayer);
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

    // --- 방 목록 조회를 위한 REST API ---
    @CrossOrigin(origins = "*") // 모든 도메인에서의 요청을 허용 (개발용)
    @GetMapping("/api/rooms")
    @ResponseBody
    public List<RoomInfo> getRoomList() {
        return roomManager.getAllRooms().values().stream()
                .map(room -> new RoomInfo(
                        room.getRoomId(),
                        room.getPlayers().size(),
                        room.getSpectators().size(),
                        getPhaseTextForApi(room.getHoldemPhase())
                ))
                .collect(Collectors.toList());
    }

    private String getPhaseTextForApi(String holdemPhase) {
        if (holdemPhase == null || "LOBBY".equals(holdemPhase) || "END".equals(holdemPhase)) {
            return "대기중";
        }
        return "게임중";
    }
}