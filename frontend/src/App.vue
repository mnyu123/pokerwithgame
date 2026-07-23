<script setup>
import { ref, onMounted, computed } from 'vue'
import { Client } from '@stomp/stompjs'

const connectionStatus = ref('연결 중...')
const stompClient = ref(null)

const playerName = ref('')
const isJoined = ref(false)
const roomState = ref(null)
// LOBBY -> MINIGAME_1 -> CARD_SELECT -> MINIGAME_2 -> HOLDEM_MAIN
const gamePhase = ref('LOBBY') 

const roomId = 'demo-room'

const isWinner = ref(false)
const fiveCards = ref([])
const myHoleCards = ref([])

// 내가 관전자인지 확인하는 computed 변수
const isSpectator = computed(() => {
  if (!roomState.value || !roomState.value.spectators) return false;
  return roomState.value.spectators[playerName.value] !== undefined;
})

onMounted(() => {
  stompClient.value = new Client({
    brokerURL: 'wss://holdem-demo-backend.fly.dev/ws', // 로컬 테스트 시에는 'ws://localhost:8080/ws'로 변경
    onConnect: () => {
      connectionStatus.value = '서버와 웹소켓 연결 성공!'
      
      stompClient.value.subscribe(`/topic/room/${roomId}`, (message) => {
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
          if (resultData.winner === playerName.value) {
            isWinner.value = true
            fiveCards.value = resultData.cards
          } else {
            isWinner.value = false
            fiveCards.value = []
          }
          gamePhase.value = 'CARD_SELECT'
        } else if (payload.type === 'MINIGAME_2_START') {
          roomState.value = payload.data
          if (!isSpectator.value) {
            myHoleCards.value = roomState.value.players[playerName.value].holeCards
            alert('첫 번째 카드 획득!\n이제 2차 미니게임(주사위 홀/짝)을 시작합니다!')
          }
          gamePhase.value = 'MINIGAME_2'
        } else if (payload.type === 'MINIGAME_2_DRAW') {
          if (!isSpectator.value) alert(payload.data) 
        } else if (payload.type === 'MINIGAME_2_RESULT') {
          const resultData = payload.data
          const isEven = resultData.diceNumber % 2 === 0 ? '짝수' : '홀수'
          
          if (!isSpectator.value) {
            if (resultData.winner === playerName.value) {
              isWinner.value = true
              fiveCards.value = resultData.cards
              alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n정답입니다! 두 번째 카드를 선택하세요.`)
            } else {
              isWinner.value = false
              fiveCards.value = []
              alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n틀렸습니다! 상대가 카드를 고르고 있습니다.`)
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
    },
    onStompError: (frame) => {
      console.error('에러 발생: ' + frame.headers['message'])
    }
  })
  stompClient.value.activate()
})

// 통신 에러 방어 로직이 적용된 공통 전송 함수
const safePublish = (destination, payload) => {
  if (!stompClient.value || !stompClient.value.connected) {
    alert('서버와 연결이 끊어졌습니다. 새로고침 해주세요.');
    return;
  }
  stompClient.value.publish({
    destination: destination,
    body: JSON.stringify(payload)
  });
}

const joinRoom = () => {
  if (!playerName.value.trim()) return alert('이름을 입력해주세요!')
  isJoined.value = true
  safePublish(`/app/room/${roomId}/join`, { type: 'JOIN', sender: playerName.value })
}

// 방 나가기 함수
const leaveRoom = () => {
  // 서버에 퇴장 메시지 전송
  safePublish(`/app/room/${roomId}/leave`, { type: 'LEAVE', sender: playerName.value })
  
  // 내 화면 상태 초기화
  isJoined.value = false
  gamePhase.value = 'LOBBY'
  roomState.value = null
  isWinner.value = false
  fiveCards.value = []
  myHoleCards.value = []
  playerName.value = '' // 원한다면 이름도 초기화
}

const toggleReady = () => {
  safePublish(`/app/room/${roomId}/ready`, { type: 'READY', sender: playerName.value })
}

const selectRps = (choice) => {
  safePublish(`/app/room/${roomId}/rps`, { type: 'RPS_CHOICE', sender: playerName.value, data: choice })
}

const selectCard = (card) => {
  safePublish(`/app/room/${roomId}/selectCard`, { type: 'CARD_SELECT', sender: playerName.value, data: card })
}

const guessDice = (guess) => {
  safePublish(`/app/room/${roomId}/dice`, { type: 'DICE_GUESS', sender: playerName.value, data: guess })
}

const sendBet = (action, amount = 0) => {
  if (roomState.value.currentTurn !== playerName.value) {
    alert('지금은 상대방의 턴입니다!');
    return;
  }
  safePublish(`/app/room/${roomId}/bet`, { 
    type: 'BET_ACTION', 
    sender: playerName.value, 
    data: { action: action, amount: amount } 
  })
}

const getCallAmount = () => {
  if (!roomState.value) return 0;
  const myBet = roomState.value.players[playerName.value]?.currentBet || 0;
  return roomState.value.highestBet - myBet;
}

const restartGame = () => {
  safePublish(`/app/room/${roomId}/restart`, { type: 'RESTART', sender: playerName.value })
}
</script>

<template>
  <div style="padding: 20px; font-family: sans-serif;">
    <h1>텍사스 홀덤 미니게임 데모</h1>
    <p>상태: <strong>{{ connectionStatus }}</strong></p>
    
    <!-- 관전자 알림 배지 -->
    <div v-if="isSpectator" style="background-color: #607d8b; color: white; padding: 10px; text-align: center; font-weight: bold; margin-bottom: 10px; border-radius: 5px;">
      👁️ 현재 관전 모드로 접속 중입니다. 게임 진행 상황만 볼 수 있습니다.
    </div>

    <!-- 내 핸드 (관전자는 안 보임) -->
    <div v-if="!isSpectator && myHoleCards.length > 0" style="background-color: #f0f8ff; padding: 10px; margin-bottom: 10px; border-radius: 5px;">
      <strong>내 핸드(Hole Cards):</strong> 
      <span v-for="card in myHoleCards" :key="card" style="margin-right: 10px; font-weight: bold; color: #d32f2f;">
        [{{ card }}]
      </span>
    </div>
    <hr />

    <!-- 🌟 방 나가기 버튼 (접속 후에만 표시) 🌟 -->
    <div v-if="isJoined" style="text-align: right; margin-bottom: 10px;">
      <button @click="leaveRoom" style="background-color: #757575; color: white; padding: 5px 15px; border: none; border-radius: 4px; cursor: pointer;">
        🚪 방 나가기
      </button>
    </div>

    <div v-if="!isJoined">
      <h3>플레이어 이름 입력</h3>
      <input v-model="playerName" placeholder="이름을 입력하세요" />
      <button @click="joinRoom">방 입장하기</button>
    </div>

    <div v-if="isJoined && gamePhase === 'LOBBY'">
      <h2>접속 중인 방: {{ roomId }}</h2>
      <p>내 이름: {{ playerName }}</p>
      
      <div v-if="roomState">
        <h3>현재 방 인원 ({{ Object.keys(roomState.players).length }} / 2)</h3>
        <ul>
          <li v-for="(player, name) in roomState.players" :key="name">
            {{ name }} - {{ player.ready ? '✅ 준비완료' : '⏳ 대기중' }}
          </li>
        </ul>
        <h4 v-if="Object.keys(roomState.spectators).length > 0">관전자 목록</h4>
        <ul>
          <li v-for="(player, name) in roomState.spectators" :key="name" style="color: gray;">
            {{ name }}
          </li>
        </ul>
      </div>
      <button v-if="!isSpectator" @click="toggleReady">준비 (Ready)</button>
    </div>

    <div v-if="gamePhase === 'MINIGAME_1'">
      <h2>1차 미니게임: 가위바위보!</h2>
      <div v-if="!isSpectator">
        <button @click="selectRps('SCISSORS')" style="margin-right: 10px; padding: 10px;">가위 ✌️</button>
        <button @click="selectRps('ROCK')" style="margin-right: 10px; padding: 10px;">바위 ✊</button>
        <button @click="selectRps('PAPER')" style="padding: 10px;">보 ✋</button>
      </div>
      <p v-else>플레이어들이 가위바위보를 진행 중입니다...</p>
    </div>

    <div v-if="gamePhase === 'CARD_SELECT'">
      <h2 v-if="!isSpectator && isWinner">승리! 카드를 한 장 선택하세요.</h2>
      <h2 v-else-if="!isSpectator">패배! 상대가 카드를 고르고 있습니다... 대기해주세요.</h2>
      <h2 v-else>가위바위보 승자가 카드를 고르고 있습니다...</h2>
      
      <div v-if="!isSpectator && isWinner" style="display: flex; gap: 10px;">
        <div v-for="card in fiveCards" :key="card" 
             @click="selectCard(card)"
             style="border: 1px solid black; padding: 20px; cursor: pointer; background: #eee;">
          {{ card }}
        </div>
      </div>
    </div>
    
    <div v-if="gamePhase === 'MINIGAME_2'">
      <h2>2차 미니게임: 주사위 홀/짝 맞추기!</h2>
      <p>서버에서 1~6 사이의 주사위를 굴립니다.</p>
      <div v-if="!isSpectator">
        <button @click="guessDice('ODD')" style="margin-right: 10px; padding: 15px; font-size: 16px;">홀수 (ODD)</button>
        <button @click="guessDice('EVEN')" style="padding: 15px; font-size: 16px;">짝수 (EVEN)</button>
      </div>
      <p v-else>플레이어들이 주사위 결과를 예측 중입니다...</p>
    </div>

    <div v-if="gamePhase === 'HOLDEM_MAIN'" style="background-color: #2e7d32; padding: 20px; border-radius: 10px; color: white;">
      <h2 style="text-align: center;">🃏 텍사스 홀덤 테이블 🃏</h2>
      
      <!-- 게임 종료 / 쇼다운 알림창 -->
      <div v-if="['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" 
           style="background: #ffb300; color: black; padding: 20px; text-align: center; border-radius: 10px; margin-bottom: 20px;">
        <h2 v-if="roomState?.holdemPhase === 'END'">게임 종료!</h2>
        <h2 v-if="roomState?.holdemPhase === 'SHOWDOWN'">패 공개! (SHOWDOWN)</h2>
        
        <h3 v-if="roomState?.winnerMessage" style="color: #ff5252; background: white; padding: 15px; border-radius: 8px; margin: 15px 0;">
          {{ roomState?.winnerMessage }}
        </h3>

        <button v-if="!isSpectator" @click="restartGame" style="padding: 10px 20px; background: black; color: white; font-size: 16px; cursor: pointer; border: none; border-radius: 5px; margin-top: 10px;">
          다음 라운드 진행하기 (초기화)
        </button>
      </div>

      <div style="text-align: center; margin: 20px 0; padding: 15px; background: rgba(0,0,0,0.3); border-radius: 8px;">
        <h3>💰 총 상금 (POT): {{ roomState?.pot }} 칩</h3>
        <p>현재 단계: <strong>{{ roomState?.holdemPhase }}</strong></p>
        <div style="display: flex; justify-content: center; gap: 10px; min-height: 80px;">
          <div v-for="card in roomState?.communityCards" :key="card" 
               style="background: white; color: black; padding: 20px 10px; border-radius: 5px; font-weight: bold; width: 50px;">
            {{ card }}
          </div>
        </div>
      </div>

      <div style="display: flex; justify-content: space-between;">
        <!-- 내 정보 패널 (관전자일 때는 플레이어 1 표시용으로 대체) -->
        <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 45%;">
          <h3>{{ isSpectator ? '👤 플레이어 1' : `👤 ${playerName} (나)` }}</h3>
          <p v-if="!isSpectator">보유 칩: <strong>{{ roomState?.players[playerName]?.chips }}</strong></p>
          <p v-if="!isSpectator">이번 라운드 베팅금: {{ roomState?.players[playerName]?.currentBet }}</p>
          
          <div v-if="!isSpectator" style="display: flex; gap: 10px; margin-bottom: 10px;">
            <div v-for="card in myHoleCards" :key="card" style="background: white; color: black; padding: 10px; font-weight: bold;">
              {{ card }}
            </div>
          </div>
          
          <div v-if="!isSpectator && roomState?.currentTurn === playerName && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px;">
            <p style="color: yellow;">👉 당신의 턴입니다!</p>
            <button v-if="getCallAmount() === 0" @click="sendBet('CHECK', 0)" style="margin-right: 5px; padding: 10px;">체크 (Check)</button>
            <button v-else @click="sendBet('CALL', 0)" style="margin-right: 5px; padding: 10px;">콜 (Call {{ getCallAmount() }}칩)</button>
            <button @click="sendBet('RAISE', 100)" style="margin-right: 5px; padding: 10px;">레이즈 (Raise 100)</button>
            <button @click="sendBet('FOLD', 0)" style="background-color: #d32f2f; color: white; border: none; padding: 10px;">폴드 (Fold)</button>
          </div>
          <div v-else-if="!isSpectator && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px; color: #ccc;">
            ⏳ 상대방의 결정을 기다리는 중...
          </div>
          <div v-if="isSpectator" style="color: yellow; margin-top: 10px;">(진행 중인 플레이어입니다)</div>
        </div>

        <!-- 상대방 정보 패널 -->
        <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 45%; text-align: right;">
          <div v-for="(player, name) in roomState?.players" :key="name" v-show="isSpectator || name !== playerName">
             <h3>🤖 {{ name }} (상대방)</h3>
             <p>보유 칩: <strong>{{ player.chips }}</strong></p>
             <p>이번 라운드 베팅금: {{ player.currentBet }}</p>
             <p v-if="player.isFolded" style="color: red; font-weight: bold;">(포기함)</p>
             
             <div v-if="roomState?.holdemPhase === 'SHOWDOWN' && !player.isFolded" style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px;">
               <div v-for="card in player.holeCards" :key="card" style="background: #ffcc80; color: black; padding: 10px; font-weight: bold;">
                 {{ card }}
               </div>
             </div>
             <div v-else-if="player.holeCards.length > 0 && !player.isFolded" style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px; color: #aaa;">
               <div style="background: #555; padding: 10px;">[카드 뒷면]</div>
               <div style="background: #555; padding: 10px;">[카드 뒷면]</div>
             </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>