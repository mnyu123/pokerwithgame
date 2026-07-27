<script setup>
import { onMounted, onBeforeUnmount } from 'vue'
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
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

const handleBeforeUnload = () => {
  if (isJoined.value) leaveRoom()
}

// 방 입장 및 라우팅(구독) 로직은 메인 컴포넌트에 유지
const joinRoom = () => {
  if (!playerName.value.trim() || !roomId.value.trim()) return alert('방 이름과 이름을 모두 입력해주세요!')
  
  if (currentSubscription) currentSubscription.unsubscribe()

  currentSubscription = stompClient.value.subscribe(`/topic/room/${roomId.value}`, (message) => {
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
        alert('첫 번째 카드 획득!\n이제 2차 미니게임(주사위)을 시작합니다!')
      }
      gamePhase.value = 'MINIGAME_2'
    } else if (payload.type === 'MINIGAME_2_RESULT') {
      const resultData = payload.data
      roomState.value = resultData.roomState 
      
      if (!isSpectator.value) {
        myHoleCards.value = roomState.value.players[playerName.value].holeCards
        const isEven = resultData.diceNumber % 2 === 0 ? '짝수' : '홀수'
        const myResult = resultData[playerName.value]
        
        if (myResult.isCorrect) {
          isWinner.value = true
          fiveCards.value = myResult.cards
          alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n정답입니다! 두 번째 카드를 선택하세요.`)
        } else {
          isWinner.value = false
          fiveCards.value = []
          alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n틀렸습니다! 랜덤으로 카드가 지급되었습니다.`)
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
  <div style="padding: 20px; font-family: sans-serif;">
    <h1>텍사스 홀덤 미니게임 데모 (다중 방)</h1>
    <p>상태: <strong>{{ connectionStatus }}</strong></p>
    
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

    <!-- 로그인 폼 -->
    <div v-if="!isJoined" style="background: #e0f7fa; padding: 20px; border-radius: 8px;">
      <h3>방 번호와 이름을 입력하세요</h3>
      <div style="margin-bottom: 10px;">
        <label style="display: inline-block; width: 80px; font-weight: bold;">방 이름:</label>
        <input v-model="roomId" placeholder="예: room123" style="padding: 5px;" />
      </div>
      <div style="margin-bottom: 10px;">
        <label style="display: inline-block; width: 80px; font-weight: bold;">내 이름:</label>
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