<script setup>
import { ref, onMounted } from 'vue'
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

onMounted(() => {
  stompClient.value = new Client({
    //brokerURL: 'ws://localhost:8080/ws',
    brokerURL: 'wss://holdem-demo-backend.fly.dev/ws',
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
          alert('1차 미니게임: 가위바위보 시작!')
          gamePhase.value = 'MINIGAME_1'
        } else if (payload.type === 'MINIGAME_1_DRAW') {
          alert('가위바위보 무승부! 다시 선택해주세요.')
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
          myHoleCards.value = roomState.value.players[playerName.value].holeCards
          alert('첫 번째 카드 획득!\n이제 2차 미니게임(주사위 홀/짝)을 시작합니다!')
          gamePhase.value = 'MINIGAME_2'
        } else if (payload.type === 'MINIGAME_2_DRAW') {
          alert(payload.data) 
        } else if (payload.type === 'MINIGAME_2_RESULT') {
          const resultData = payload.data
          const isEven = resultData.diceNumber % 2 === 0 ? '짝수' : '홀수'
          
          if (resultData.winner === playerName.value) {
            isWinner.value = true
            fiveCards.value = resultData.cards
            alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n정답입니다! 두 번째 카드를 선택하세요.`)
          } else {
            isWinner.value = false
            fiveCards.value = []
            alert(`주사위 눈: ${resultData.diceNumber} (${isEven})\n틀렸습니다! 상대가 카드를 고르고 있습니다.`)
          }
          gamePhase.value = 'CARD_SELECT'
        } else if (payload.type === 'HOLDEM_START') {
          roomState.value = payload.data
          myHoleCards.value = roomState.value.players[playerName.value].holeCards
          alert('두 번째 카드 획득 완료!\n본격적인 텍사스 홀덤을 시작합니다!')
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

const joinRoom = () => {
  if (!playerName.value.trim()) return alert('이름을 입력해주세요!')
  isJoined.value = true
  stompClient.value.publish({
    destination: `/app/room/${roomId}/join`,
    body: JSON.stringify({ type: 'JOIN', sender: playerName.value })
  })
}

const toggleReady = () => {
  stompClient.value.publish({
    destination: `/app/room/${roomId}/ready`,
    body: JSON.stringify({ type: 'READY', sender: playerName.value })
  })
}

const selectRps = (choice) => {
  stompClient.value.publish({
    destination: `/app/room/${roomId}/rps`,
    body: JSON.stringify({ type: 'RPS_CHOICE', sender: playerName.value, data: choice })
  })
}

const selectCard = (card) => {
  stompClient.value.publish({
    destination: `/app/room/${roomId}/selectCard`,
    body: JSON.stringify({ type: 'CARD_SELECT', sender: playerName.value, data: card })
  })
}

const guessDice = (guess) => {
  stompClient.value.publish({
    destination: `/app/room/${roomId}/dice`,
    body: JSON.stringify({ type: 'DICE_GUESS', sender: playerName.value, data: guess })
  })
}

const sendBet = (action, amount = 0) => {
  if (roomState.value.currentTurn !== playerName.value) {
    alert('지금은 상대방의 턴입니다!');
    return;
  }
  stompClient.value.publish({
    destination: `/app/room/${roomId}/bet`,
    body: JSON.stringify({ 
      type: 'BET_ACTION', 
      sender: playerName.value, 
      data: { action: action, amount: amount } 
    })
  })
}

const getCallAmount = () => {
  if (!roomState.value) return 0;
  const myBet = roomState.value.players[playerName.value].currentBet;
  return roomState.value.highestBet - myBet;
}

const restartGame = () => {
  stompClient.value.publish({
    destination: `/app/room/${roomId}/restart`,
    body: JSON.stringify({ type: 'RESTART', sender: playerName.value })
  })
}
</script>

<template>
  <div style="padding: 20px; font-family: sans-serif;">
    <h1>텍사스 홀덤 미니게임 데모</h1>
    <p>상태: <strong>{{ connectionStatus }}</strong></p>
    
    <div v-if="myHoleCards.length > 0" style="background-color: #f0f8ff; padding: 10px; margin-bottom: 10px; border-radius: 5px;">
      <strong>내 핸드(Hole Cards):</strong> 
      <span v-for="card in myHoleCards" :key="card" style="margin-right: 10px; font-weight: bold; color: #d32f2f;">
        [{{ card }}]
      </span>
    </div>
    <hr />

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
      </div>
      <button @click="toggleReady">준비 (Ready)</button>
    </div>

    <div v-if="gamePhase === 'MINIGAME_1'">
      <h2>1차 미니게임: 가위바위보!</h2>
      <button @click="selectRps('SCISSORS')" style="margin-right: 10px; padding: 10px;">가위 ✌️</button>
      <button @click="selectRps('ROCK')" style="margin-right: 10px; padding: 10px;">바위 ✊</button>
      <button @click="selectRps('PAPER')" style="padding: 10px;">보 ✋</button>
    </div>

    <div v-if="gamePhase === 'CARD_SELECT'">
      <h2 v-if="isWinner">승리! 카드를 한 장 선택하세요.</h2>
      <h2 v-else>패배! 상대가 카드를 고르고 있습니다... 대기해주세요.</h2>
      
      <div v-if="isWinner" style="display: flex; gap: 10px;">
        <div v-for="card in fiveCards" :key="card" 
             @click="selectCard(card)"
             style="border: 1px solid black; padding: 20px; cursor: pointer; background: #eee;">
          {{ card }}
        </div>
      </div>
    </div>
    
    <div v-if="gamePhase === 'MINIGAME_2'">
      <h2>2차 미니게임: 주사위 홀/짝 맞추기!</h2>
      <p>서버에서 1~6 사이의 주사위를 굴립니다. 결과를 예측해보세요.</p>
      <button @click="guessDice('ODD')" style="margin-right: 10px; padding: 15px; font-size: 16px;">홀수 (ODD)</button>
      <button @click="guessDice('EVEN')" style="padding: 15px; font-size: 16px;">짝수 (EVEN)</button>
    </div>

    <div v-if="gamePhase === 'HOLDEM_MAIN'" style="background-color: #2e7d32; padding: 20px; border-radius: 10px; color: white;">
      <h2 style="text-align: center;">🃏 텍사스 홀덤 테이블 🃏</h2>
      
      <!-- 게임 종료 / 쇼다운 알림창 (승패 텍스트 추가) -->
      <div v-if="['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" 
           style="background: #ffb300; color: black; padding: 20px; text-align: center; border-radius: 10px; margin-bottom: 20px;">
        <h2 v-if="roomState?.holdemPhase === 'END'">게임 종료!</h2>
        <h2 v-if="roomState?.holdemPhase === 'SHOWDOWN'">패 공개! (SHOWDOWN)</h2>
        
        <!-- 서버에서 계산한 승패 및 족보 결과 출력 -->
        <h3 v-if="roomState?.winnerMessage" style="color: #ff5252; background: white; padding: 15px; border-radius: 8px; margin: 15px 0;">
          {{ roomState?.winnerMessage }}
        </h3>

        <button @click="restartGame" style="padding: 10px 20px; background: black; color: white; font-size: 16px; cursor: pointer; border: none; border-radius: 5px; margin-top: 10px;">
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
        <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 45%;">
          <h3>👤 {{ playerName }} (나)</h3>
          <p>보유 칩: <strong>{{ roomState?.players[playerName]?.chips }}</strong></p>
          <p>이번 라운드 베팅금: {{ roomState?.players[playerName]?.currentBet }}</p>
          <div style="display: flex; gap: 10px; margin-bottom: 10px;">
            <div v-for="card in myHoleCards" :key="card" style="background: white; color: black; padding: 10px; font-weight: bold;">
              {{ card }}
            </div>
          </div>
          
          <div v-if="roomState?.currentTurn === playerName && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px;">
            <p style="color: yellow;">👉 당신의 턴입니다!</p>
            
            <button v-if="getCallAmount() === 0" @click="sendBet('CHECK', 0)" style="margin-right: 5px; padding: 10px;">체크 (Check)</button>
            <button v-else @click="sendBet('CALL', 0)" style="margin-right: 5px; padding: 10px;">콜 (Call {{ getCallAmount() }}칩)</button>
            
            <button @click="sendBet('RAISE', 100)" style="margin-right: 5px; padding: 10px;">레이즈 (Raise 100)</button>
            <button @click="sendBet('FOLD', 0)" style="background-color: #d32f2f; color: white; border: none; padding: 10px;">폴드 (Fold)</button>
          </div>
          <div v-else-if="!['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px; color: #ccc;">
            ⏳ 상대방의 결정을 기다리는 중...
          </div>
        </div>

        <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 45%; text-align: right;">
          <div v-for="(player, name) in roomState?.players" :key="name" v-show="name !== playerName">
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