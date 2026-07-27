<script setup>
import { ref, computed, watch } from 'vue'
import { roomState, playerName, isSpectator, myHoleCards, sendBet, getCallAmount, restartGame } from '../store.js'
import PlayingCard from './PlayingCard.vue'

// 🌟 슬라이더로 조절할 커스텀 레이즈 금액 (기본값 100)
const customRaiseAmount = ref(100)

// 🌟 내 남은 칩에서 콜(Call) 비용을 빼고, 추가로 걸 수 있는 최대 칩 계산
const maxRaiseable = computed(() => {
  if (!roomState.value || isSpectator.value) return 0;
  const myChips = roomState.value.players[playerName.value]?.chips || 0;
  const callCost = getCallAmount();
  return Math.max(0, myChips - callCost);
})

// 내 턴이 오거나 칩이 변동될 때, 슬라이더 값이 최대치를 넘지 않도록 안전하게 보정
watch(maxRaiseable, (newMax) => {
  if (customRaiseAmount.value > newMax) {
    customRaiseAmount.value = newMax > 0 ? newMax : 0;
  }
})
</script>

<template>
  <div style="background-color: #2e7d32; padding: 20px; border-radius: 10px; color: white;">
    <h2 style="text-align: center;">🃏 텍사스 홀덤 테이블 🃏</h2>
    
    <!-- 게임 종료 / 쇼다운 알림창 -->
    <div v-if="['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="background: #ffb300; color: black; padding: 20px; text-align: center; border-radius: 10px; margin-bottom: 20px;">
      <h2 v-if="roomState?.holdemPhase === 'END'">게임 종료!</h2>
      <h2 v-if="roomState?.holdemPhase === 'SHOWDOWN'">패 공개! (SHOWDOWN)</h2>
      <h3 v-if="roomState?.winnerMessage" style="color: #ff5252; background: white; padding: 15px; border-radius: 8px; margin: 15px 0;">
        {{ roomState?.winnerMessage }}
      </h3>
      <button v-if="!isSpectator" @click="restartGame" style="padding: 10px 20px; background: black; color: white; font-size: 16px; cursor: pointer; border: none; border-radius: 5px;">
        다음 라운드 진행하기 (초기화)
      </button>
    </div>

    <!-- 중앙 팟(POT) 및 커뮤니티 카드 -->
    <div style="text-align: center; margin: 20px 0; padding: 15px; background: rgba(0,0,0,0.3); border-radius: 8px;">
      <h3>💰 총 상금 (POT): {{ roomState?.pot }} 칩</h3>
      <p>현재 단계: <strong>{{ roomState?.holdemPhase }}</strong></p>
      
      <div style="display: flex; justify-content: center; gap: 10px; min-height: 84px; margin-top: 15px;">
        <PlayingCard v-for="card in roomState?.communityCards" :key="card" :card="card" />
      </div>
    </div>

    <div style="display: flex; justify-content: space-between;">
      <!-- 내 패널 -->
      <div style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 45%;">
        <h3>{{ isSpectator ? '👤 플레이어 1' : `👤 ${playerName} (나)` }}</h3>
        <p v-if="!isSpectator">보유 칩: <strong>{{ roomState?.players[playerName]?.chips }}</strong></p>
        <p v-if="!isSpectator">이번 라운드 베팅금: {{ roomState?.players[playerName]?.currentBet }}</p>
        
        <div v-if="!isSpectator" style="display: flex; gap: 10px; margin-bottom: 10px;">
          <PlayingCard v-for="card in myHoleCards" :key="card" :card="card" />
        </div>
        
        <!-- 🌟 베팅 컨트롤 UI 영역 🌟 -->
        <div v-if="!isSpectator && roomState?.currentTurn === playerName && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px;">
          <p style="color: yellow;">👉 당신의 턴입니다!</p>
          
          <button v-if="getCallAmount() === 0" @click="sendBet('CHECK', 0)" style="margin-right: 5px; padding: 10px; cursor: pointer;">체크 (Check)</button>
          <button v-else @click="sendBet('CALL', 0)" style="margin-right: 5px; padding: 10px; cursor: pointer;">콜 (Call {{ getCallAmount() }}칩)</button>
          
          <!-- 슬라이더 및 올인 버튼 (올릴 수 있는 칩이 있을 때만 표시) -->
          <div v-if="maxRaiseable > 0" style="display: inline-block; background: rgba(0,0,0,0.5); padding: 10px; border-radius: 5px; margin: 5px; vertical-align: top;">
            <div style="margin-bottom: 5px; text-align: left;">
              <span style="color: #ffcc80; font-size: 13px; font-weight: bold;">➕ 추가 레이즈: {{ customRaiseAmount }} 칩</span>
            </div>
            <input 
              type="range" 
              min="10" 
              :max="maxRaiseable" 
              step="10" 
              v-model.number="customRaiseAmount" 
              style="width: 150px; cursor: pointer;"
            >
            <div style="margin-top: 5px;">
              <button @click="sendBet('RAISE', customRaiseAmount)" style="margin-right: 5px; padding: 8px 12px; background-color: #fbc02d; color: black; border: none; font-weight: bold; cursor: pointer; border-radius: 4px;">
                레이즈
              </button>
              <button @click="sendBet('RAISE', maxRaiseable)" style="padding: 8px 12px; background-color: #d32f2f; color: white; border: none; font-weight: bold; cursor: pointer; border-radius: 4px;">
                올인! (ALL-IN)
              </button>
            </div>
          </div>

          <button @click="sendBet('FOLD', 0)" style="background-color: #757575; color: white; border: none; padding: 10px; margin-left: 5px; cursor: pointer; border-radius: 4px;">폴드 (Fold)</button>
        </div>
        <div v-else-if="!isSpectator && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="margin-top: 10px; color: #ccc;">
          ⏳ 상대방의 결정을 기다리는 중...
        </div>
      </div>

      <!-- 상대방 패널 -->
      <div style="width: 50%; display: flex; flex-wrap: wrap; gap: 10px; justify-content: flex-end;">
        <div v-for="(player, name) in roomState?.players" :key="name" v-show="isSpectator || name !== playerName" 
             style="background: rgba(255,255,255,0.1); padding: 15px; border-radius: 8px; width: 100%; text-align: right;">
           
           <h3>🤖 {{ name }}</h3>
           <p>보유 칩: <strong>{{ player.chips }}</strong></p>
           <p>이번 라운드 베팅금: {{ player.currentBet }}</p>
           <p v-if="player.isFolded" style="color: red; font-weight: bold;">(포기함)</p>
           
           <div v-if="roomState?.holdemPhase === 'SHOWDOWN' && !player.isFolded" style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px;">
             <PlayingCard v-for="card in player.holeCards" :key="card" :card="card" />
           </div>
           <div v-else-if="player.holeCards.length > 0 && !player.isFolded" style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 10px;">
             <PlayingCard card="BACK" :hidden="true" />
             <PlayingCard card="BACK" :hidden="true" />
           </div>
           <div v-if="roomState?.currentTurn === name && !['END', 'SHOWDOWN'].includes(roomState?.holdemPhase)" style="color: yellow; margin-top: 10px; font-weight: bold;">
             👉 현재 턴 진행 중
           </div>
        </div>
      </div>
    </div>
  </div>
</template>