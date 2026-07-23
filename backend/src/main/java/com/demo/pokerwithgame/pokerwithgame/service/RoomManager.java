package com.demo.pokerwithgame.pokerwithgame.service;

import com.demo.pokerwithgame.pokerwithgame.model.GameRoom;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {
    // 메모리 DB 역할 (roomId를 키값으로 방 객체 저장)
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    // 데모용 기본 방(로비) 하나를 미리 만들어 둡니다.
    public RoomManager() {
        createRoom("demo-room");
    }

    public GameRoom createRoom(String roomId) {
        GameRoom room = new GameRoom(roomId);
        rooms.put(roomId, room);
        return room;
    }

    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }
}
