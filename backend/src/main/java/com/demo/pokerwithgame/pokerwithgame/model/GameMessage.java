package com.demo.pokerwithgame.pokerwithgame.model;

import lombok.Data;

@Data
public class GameMessage {
    private String type;    // 메시지 타입 (예: JOIN, READY, STATE_UPDATE, MINIGAME_1_START)
    private String sender;  // 보낸 사람 이름
    private Object data;    // 전달할 추가 데이터 (방 상태 등)
}
