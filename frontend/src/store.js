import { ref, computed } from 'vue'
import { Client } from '@stomp/stompjs'

// 전역 상태 변수들
export const connectionStatus = ref('연결 중...')
export const stompClient = ref(null)
export const playerName = ref('')
export const roomId = ref('')
export const currentSubscription = ref(null)

export const isJoined = ref(false)
export const roomState = ref(null)
export const gamePhase = ref('LOBBY') 

export const isWinner = ref(false)
export const fiveCards = ref([])
export const myHoleCards = ref([])

export const isSpectator = computed(() => {
  if (!roomState.value || !roomState.value.spectators) return false;
  return roomState.value.spectators[playerName.value] !== undefined;
})

// 공통 전송 함수
export const safePublish = (destination, payload) => {
  if (!stompClient.value || !stompClient.value.connected) {
    alert('서버와 연결이 끊어졌습니다. 새로고침 해주세요.');
    return;
  }
  stompClient.value.publish({
    destination: destination,
    body: JSON.stringify(payload)
  });
}

// 방 나가기 및 초기화 함수
export const leaveRoom = () => {
  safePublish(`/app/room/${roomId.value}/leave`, { type: 'LEAVE', sender: playerName.value })
  if (currentSubscription.value) { // 🌟 .value 추가
    currentSubscription.value.unsubscribe()
    currentSubscription.value = null
  }
  isJoined.value = false
  gamePhase.value = 'LOBBY'
  roomState.value = null
  isWinner.value = false
  fiveCards.value = []
  myHoleCards.value = []
}

// 기타 액션 함수들
export const toggleReady = () => safePublish(`/app/room/${roomId.value}/ready`, { type: 'READY', sender: playerName.value })
export const selectRps = (choice) => safePublish(`/app/room/${roomId.value}/rps`, { type: 'RPS_CHOICE', sender: playerName.value, data: choice })
export const selectCard = (card) => safePublish(`/app/room/${roomId.value}/selectCard`, { type: 'CARD_SELECT', sender: playerName.value, data: card })
export const guessDice = (guess) => safePublish(`/app/room/${roomId.value}/dice`, { type: 'DICE_GUESS', sender: playerName.value, data: guess })
export const restartGame = () => safePublish(`/app/room/${roomId.value}/restart`, { type: 'RESTART', sender: playerName.value })

export const sendBet = (action, amount = 0) => {
  if (roomState.value.currentTurn !== playerName.value) return alert('지금은 상대방의 턴입니다!');
  safePublish(`/app/room/${roomId.value}/bet`, { type: 'BET_ACTION', sender: playerName.value, data: { action: action, amount: amount } })
}

export const getCallAmount = () => {
  if (!roomState.value) return 0;
  const myBet = roomState.value.players[playerName.value]?.currentBet || 0;
  return roomState.value.highestBet - myBet;
}