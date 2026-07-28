<script setup>
import { gamePhase, isSpectator, isWinner, fiveCards, selectRps, selectCard, guessDice, roomState } from '../store.js'
import PlayingCard from './PlayingCard.vue'
</script>

<template>
  <div>
    <!-- 1차 미니게임: 가위바위보 -->
    <div v-if="gamePhase === 'MINIGAME_1'">
      <h2>1차 미니게임: 가위바위보!</h2>
      <div v-if="!isSpectator" class="button-group">
        <button @click="selectRps('SCISSORS')" class="game-button">가위 ✌️</button>
        <button @click="selectRps('ROCK')" class="game-button">바위 ✊</button>
        <button @click="selectRps('PAPER')" class="game-button">보 ✋</button>
      </div>
      <p v-else>플레이어들이 가위바위보를 진행 중입니다...</p>
    </div>

    <!-- 카드 선택 UI (텍스트 카드를 PlayingCard 이미지로 교체!) -->
    <div v-if="gamePhase === 'CARD_SELECT'">
      <h2 v-if="!isSpectator && isWinner">카드를 한 장 선택하세요.</h2>
      <h2 v-else-if="!isSpectator">상대가 카드를 고르고 있습니다... 대기해주세요.</h2>
      <h2 v-else>플레이어들이 카드를 고르고 있습니다...</h2>
      
      <div v-if="!isSpectator && isWinner" class="card-selection-area">
        <div v-for="card in fiveCards" :key="card" @click="selectCard(card)" style="cursor: pointer; transition: transform 0.2s;" onmouseover="this.style.transform='translateY(-10px)'" onmouseout="this.style.transform='translateY(0)'">
          <PlayingCard :card="card" />
        </div>
      </div>
    </div>
    
    <!-- 2차 미니게임: 주사위 -->
    <div v-if="gamePhase === 'MINIGAME_2'">
      <h2>2차 미니게임: 주사위 High & Low!</h2>
      <p>서버가 굴린 기준 숫자는 <strong style="font-size: 24px; color: #d32f2f;">{{ roomState.baseDiceNumber }}</strong> 입니다.</p>
      <p>내가 굴릴 주사위가 이 숫자보다 높을까요, 낮을까요? (같으면 패배)</p>
      <div v-if="!isSpectator" class="button-group">
        <button @click="guessDice('HIGHER')" class="game-button higher">높다 (Higher)</button>
        <button @click="guessDice('LOWER')" class="game-button lower">낮다 (Lower)</button>
      </div>
      <p v-else>플레이어들이 결과를 예측 중입니다...</p>
    </div>
  </div>
</template>

<style scoped>
.button-group {
  display: flex;
  gap: 10px;
  flex-wrap: wrap; /* 작은 화면에서 버튼이 다음 줄로 넘어가도록 함 */
  margin-top: 15px;
}

.game-button {
  padding: 15px;
  font-size: 16px;
  cursor: pointer;
  border: 2px solid transparent;
  border-radius: 8px;
  transition: all 0.2s;
}
.game-button:hover {
  transform: translateY(-3px);
  box-shadow: 0 4px 8px rgba(0,0,0,0.2);
}
.higher { background-color: #4caf50; color: white; }
.lower { background-color: #f44336; color: white; }

.card-selection-area {
  display: flex;
  gap: 10px;
  margin-top: 15px;
  flex-wrap: wrap; /* 작은 화면에서 카드가 다음 줄로 넘어가도록 함 */
  justify-content: center; /* 카드들을 가운데 정렬 */
}
</style>