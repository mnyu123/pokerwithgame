<script setup>
import { roomState, playerName, isSpectator, myHoleCards, sendBet, getCallAmount, restartGame } from '../store.js'
import PlayingCard from './PlayingCard.vue'
</script>

<template>
  <div style="background-color: #2e7d32; padding: 20px; border-radius: 10px; color: white;">
    <h2 style="text-align: center;">🃏 텍사스 홀덤 테이블 🃏</h2>
    
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

    <div style="text-align: center; margin: 20px 0; padding: 15px; background: rgba(0,0,0,0.3); border-radius: 8px;">
      <h3>💰 총 상금 (POT): {{ roomState?.pot }} 칩</h3>
      <p>현재 단계: <strong>{{ roomState?.holdemPhase }}</strong></p>
      
      <!-- 커뮤니티 카드 이미지 출력 -->
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
      </div>

      <!-- 상대방 패널 (여러 명이 들어갈 수 있도록 flex-wrap 적용) -->
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