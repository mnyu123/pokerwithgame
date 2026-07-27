<script setup>
import { gamePhase, isSpectator, isWinner, fiveCards, selectRps, selectCard, guessDice } from '../store.js'
import PlayingCard from './PlayingCard.vue'
</script>

<template>
  <div>
    <!-- 1차 미니게임: 가위바위보 -->
    <div v-if="gamePhase === 'MINIGAME_1'">
      <h2>1차 미니게임: 가위바위보!</h2>
      <div v-if="!isSpectator">
        <button @click="selectRps('SCISSORS')" style="margin-right: 10px; padding: 10px;">가위 ✌️</button>
        <button @click="selectRps('ROCK')" style="margin-right: 10px; padding: 10px;">바위 ✊</button>
        <button @click="selectRps('PAPER')" style="padding: 10px;">보 ✋</button>
      </div>
      <p v-else>플레이어들이 가위바위보를 진행 중입니다...</p>
    </div>

    <!-- 카드 선택 UI (텍스트 카드를 PlayingCard 이미지로 교체!) -->
    <div v-if="gamePhase === 'CARD_SELECT'">
      <h2 v-if="!isSpectator && isWinner">카드를 한 장 선택하세요.</h2>
      <h2 v-else-if="!isSpectator">상대가 카드를 고르고 있습니다... 대기해주세요.</h2>
      <h2 v-else>플레이어들이 카드를 고르고 있습니다...</h2>
      
      <div v-if="!isSpectator && isWinner" style="display: flex; gap: 10px; margin-top: 15px;">
        <div v-for="card in fiveCards" :key="card" @click="selectCard(card)" style="cursor: pointer; transition: transform 0.2s;" onmouseover="this.style.transform='translateY(-10px)'" onmouseout="this.style.transform='translateY(0)'">
          <PlayingCard :card="card" />
        </div>
      </div>
    </div>
    
    <!-- 2차 미니게임: 주사위 -->
    <div v-if="gamePhase === 'MINIGAME_2'">
      <h2>2차 미니게임: 주사위 홀/짝 맞추기!</h2>
      <p>서버에서 1~6 사이의 주사위를 굴립니다.</p>
      <div v-if="!isSpectator">
        <button @click="guessDice('ODD')" style="margin-right: 10px; padding: 15px; font-size: 16px;">홀수 (ODD)</button>
        <button @click="guessDice('EVEN')" style="padding: 15px; font-size: 16px;">짝수 (EVEN)</button>
      </div>
      <p v-else>플레이어들이 주사위 결과를 예측 중입니다...</p>
    </div>
  </div>
</template>