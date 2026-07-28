<script setup>
import { onMounted, onBeforeUnmount, computed, ref } from 'vue'
import { Client } from '@stomp/stompjs'

// 스토어에서 상태와 함수 가져오기
import { 
  connectionStatus, stompClient, playerName, roomId, currentSubscription, 
  isJoined, roomState, gamePhase, isWinner, fiveCards, myHoleCards, isSpectator, 
  safePublish, leaveRoom 
} from './store.js'

// 컴포넌트 임포트
import GameLobby from './components/GameLobby.vue'
import MiniGames from './components/MiniGames.vue'
import HoldemTable from './components/HoldemTable.vue'
import PlayingCard from './components/PlayingCard.vue'

// 현재 게임 단계를 사용자 친화적 텍스트로 변환
const phaseText = computed(() => {
  switch(gamePhase.value) {
    case 'LOBBY': return '로비 (플레이어 대기 중)';
    case 'MINIGAME_1': return '1차 미니게임: 가위바위보';
    case 'CARD_SELECT': return '카드 선택';
    case 'MINIGAME_2': return '2차 미니게임: 주사위 High & Low';
    case 'HOLDEM_MAIN': return '텍사스 홀덤';
    default: return '연결 중...';
  }
})

// 방 목록 상태
const roomList = ref([])
let roomListInterval = null

onMounted(() => {
  stompClient.value = new Client({
    brokerURL: 'wss://holdem-demo-backend.fly.dev/ws', // 로컬 시 'ws://localhost:8080/ws'
    onConnect: () => {
      connectionStatus.value = '서버와 웹소켓 연결 성공!'
    },
    onStompError: (frame) => {
      console.error('에러 발생: ' + frame.headers['message'])
      connectionStatus.value = '연결 실패'
    }
  })
  stompClient.value.activate()
  
  // 방 목록 가져오기 시작
  fetchRoomList()
  roomListInterval = setInterval(fetchRoomList, 5000) // 5초마다 목록 갱신

  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  if (roomListInterval) clearInterval(roomListInterval) // 인터벌 정리
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

const handleBeforeUnload = () => {
  if (isJoined.value) leaveRoom()
}

// 방 목록 가져오기 함수
const fetchRoomList = async () => {
  try {
    // 로컬 개발 시에는 vite.config.js 프록시 설정을 통해 '/api/rooms'로 요청해야 합니다.
    const response = await fetch('https://holdem-demo-backend.fly.dev/api/rooms')
    if (response.ok) {
      roomList.value = await response.json()
    } else {
      console.error('방 목록을 가져오는데 실패했습니다.')
    }
  } catch (error) {
    console.error('방 목록 API 요청 중 에러 발생:', error)
  }
}

// 방 목록에서 바로 참여하는 함수
const quickJoin = (id) => {
  roomId.value = id
  joinRoom()
}

// 방 입장 및 라우팅(구독) 로직은 메인 컴포넌트에 유지
const joinRoom = () => {
  if (!playerName.value.trim() || !roomId.value.trim()) return alert('방 이름과 이름을 모두 입력해주세요!')
  
  // 🌟 .value를 붙여서 참조 값을 수정합니다.
  if (currentSubscription.value) currentSubscription.value.unsubscribe()

  // 🌟 여기서도 .value 에 할당합니다.
  currentSubscription.value = stompClient.value.subscribe(`/topic/room/${roomId.value}`, (message) => {
    const payload = JSON.parse(message.body)
    
    if (payload.type === 'STATE_UPDATE') {
      roomState.value = payload.data
      if (roomState.value.holdemPhase === 'LOBBY') {
          gamePhase.value = 'LOBBY'
          myHoleCards.value = []
          isWinner.value = false
          fiveCards.value = []
      }
    } else if (payload.type === 'MINIGAME_1_START') {
      if (!isSpectator.value) alert('1차 미니게임: 가위바위보 시작!')
      gamePhase.value = 'MINIGAME_1'
    } else if (payload.type === 'MINIGAME_1_DRAW') {
      if (!isSpectator.value) alert('가위바위보 무승부! 다시 선택해주세요.')
    } else if (payload.type === 'MINIGAME_1_RESULT') {
      const resultData = payload.data
      roomState.value = resultData.roomState 
      
      if (!isSpectator.value) {
        myHoleCards.value = roomState.value.players[playerName.value].holeCards
        const serverChoice = resultData.serverChoice
        const myResult = resultData[playerName.value]
        
        const rpsMap = { 'ROCK': '바위✊', 'PAPER': '보✋', 'SCISSORS': '가위✌️' }
        
        if (myResult.isWin) {
          isWinner.value = true
          fiveCards.value = myResult.cards
          alert(`서버의 선택: ${rpsMap[serverChoice]}\n가위바위보 승리! 5장 중 한 장을 선택하세요.`)
        } else {
          isWinner.value = false
          fiveCards.value = []
          alert(`서버의 선택: ${rpsMap[serverChoice]}\n가위바위보 패배/무승부! 서버가 랜덤 카드를 부여합니다.`)
        }
      }
      gamePhase.value = 'CARD_SELECT'
    } else if (payload.type === 'MINIGAME_2_START') {
      roomState.value = payload.data
      if (!isSpectator.value) {
        myHoleCards.value = roomState.value.players[playerName.value].holeCards
        alert(`첫 번째 카드 획득!\n이제 2차 미니게임(주사위 High & Low)을 시작합니다!\n기준 숫자는 [${roomState.value.baseDiceNumber}] 입니다.`)
      }
      gamePhase.value = 'MINIGAME_2'
    } else if (payload.type === 'MINIGAME_2_RESULT') {
      const resultData = payload.data
      roomState.value = resultData.roomState 
      
      if (!isSpectator.value) {
        myHoleCards.value = roomState.value.players[playerName.value].holeCards
        const myResult = resultData[playerName.value]
        const alertMsg = `기준 숫자: ${resultData.baseDiceNumber}\n나의 주사위: ${myResult.playerDiceNumber}\n\n`;
        
        if (myResult.isWin) {
          isWinner.value = true
          fiveCards.value = myResult.cards
          alert(alertMsg + `예측 성공! 두 번째 카드를 선택하세요.`)
        } else {
          isWinner.value = false
          fiveCards.value = []
          alert(alertMsg + `예측 실패! 랜덤으로 카드가 지급되었습니다.`)
        }
      }
      gamePhase.value = 'CARD_SELECT'
    } else if (payload.type === 'HOLDEM_START') {
      roomState.value = payload.data
      if (!isSpectator.value) {
        myHoleCards.value = roomState.value.players[playerName.value].holeCards
        alert('두 번째 카드 획득 완료!\n본격적인 텍사스 홀덤을 시작합니다!')
      }
      gamePhase.value = 'HOLDEM_MAIN'
    }
  })

  isJoined.value = true
  safePublish(`/app/room/${roomId.value}/join`, { type: 'JOIN', sender: playerName.value })
}
</script>

<template>
  <div id="app-container">
    <h1>텍사스 홀덤 미니게임 데모 (다중 방)</h1>
    <p>상태: <strong>{{ connectionStatus }}</strong></p>
    <p v-if="isJoined">현재 단계: <strong style="color: #007bff;">{{ phaseText }}</strong></p>
    
    <div v-if="isSpectator" style="background-color: #607d8b; color: white; padding: 10px; text-align: center; font-weight: bold; margin-bottom: 10px; border-radius: 5px;">
      👁️ 현재 관전 모드로 접속 중입니다. (진행 상황만 표시됨)
    </div>

    <!-- 앱 최상단 내 카드 이미지 출력 -->
    <div v-if="!isSpectator && myHoleCards.length > 0" style="background-color: #f0f8ff; padding: 10px; margin-bottom: 10px; border-radius: 5px;">
      <strong>내 핸드(Hole Cards):</strong> 
      <div style="display: flex; gap: 10px; margin-top: 10px;">
        <PlayingCard v-for="card in myHoleCards" :key="card" :card="card" />
      </div>
    </div>
    <hr />

    <div v-if="isJoined" style="text-align: right; margin-bottom: 10px;">
      <button @click="leaveRoom" style="background-color: #757575; color: white; padding: 5px 15px; border: none; border-radius: 4px; cursor: pointer;">
        🚪 방 나가기
      </button>
    </div>

    <!-- 방 목록 (로그인 전) -->
    <div v-if="!isJoined" class="room-list-container">
      <h3>생성된 방 목록 (5초마다 갱신)</h3>
      <div v-if="roomList.length > 0" class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>방 이름</th>
              <th>인원</th>
              <th>상태</th>
              <th>참여</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="room in roomList" :key="room.roomId">
              <td>{{ room.roomId }}</td>
              <td>{{ room.playerCount }} / 4</td>
              <td :class="{ 'status-ingame': room.gamePhase === '게임중' }">{{ room.gamePhase }}</td>
              <td>
                <button @click="quickJoin(room.roomId)" class="join-btn" :disabled="room.playerCount >= 4 && room.gamePhase === '게임중'">
                  입장
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else>생성된 방이 없습니다. 아래에서 새 방을 만들어보세요!</p>
    </div>

    <!-- 로그인 폼 -->
    <div v-if="!isJoined" class="login-form">
      <h3>방에 참여하거나 새로 생성하세요</h3>
      <div class="form-row">
        <label>방 이름:</label>
        <input v-model="roomId" placeholder="예: room123" style="padding: 5px;" />
      </div>
      <div class="form-row">
        <label>내 이름:</label>
        <input v-model="playerName" placeholder="닉네임 입력" style="padding: 5px;" @keyup.enter="joinRoom" />
      </div>
      <button @click="joinRoom" style="padding: 8px 15px; background: #00838f; color: white; border: none; border-radius: 4px; cursor: pointer;">
        방 입장하기
      </button>
    </div>

    <!-- 컴포넌트 렌더링 영역 (조건에 따라 표시) -->
    <GameLobby v-if="gamePhase === 'LOBBY'" />
    
    <MiniGames v-if="['MINIGAME_1', 'CARD_SELECT', 'MINIGAME_2'].includes(gamePhase)" />
    
    <HoldemTable v-if="gamePhase === 'HOLDEM_MAIN'" />
    
  </div>
</template>

<style>
#app-container {
  max-width: 800px;
  margin: 0 auto;
  padding: 20px;
  font-family: sans-serif;
}

.room-list-container {
  margin-bottom: 30px;
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  border: 1px solid #eee;
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 10px;
}

th, td {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: center;
}

th {
  background-color: #f2f2f2;
}

.status-ingame {
  color: #d32f2f;
  font-weight: bold;
}

.join-btn {
  padding: 4px 10px;
  background: #1e88e5;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}
.join-btn:disabled {
  background: #9e9e9e;
  cursor: not-allowed;
}

.login-form {
  background: #e0f7fa;
  padding: 20px;
  border-radius: 8px;
}

.login-form .form-row {
  margin-bottom: 10px;
  display: flex;
  align-items: center;
}

.login-form label {
  display: inline-block;
  width: 80px;
  font-weight: bold;
}

/* 작은 화면 대응 */
@media (max-width: 600px) {
  .login-form .form-row {
    flex-direction: column;
    align-items: flex-start;
  }
  .login-form input {
    width: 100%;
    box-sizing: border-box; /* padding 포함해서 width 100% */
  }
}
</style>