package com.demo.pokerwithgame.pokerwithgame.service;

import com.demo.pokerwithgame.pokerwithgame.model.GameRoom;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    public GameRoom getRoom(String roomId) {
        // 방이 없으면 자동으로 새로 생성해서 반환 (다중 방 지원의 핵심!)
        return rooms.computeIfAbsent(roomId, GameRoom::new);
    }

    /**
     * 현재 생성된 모든 게임방의 정보를 담은 Map을 반환합니다.
     * @return 모든 게임방 Map
     */
    public Map<String, GameRoom> getAllRooms() {
        return this.rooms;
    }
}
