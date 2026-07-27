<script setup>
import { roomId, playerName, isJoined, roomState, isSpectator, toggleReady } from '../store.js'
// 조인 로직은 App.vue에 있으므로 상태만 받아와 렌더링합니다.
</script>

<template>
  <div v-if="isJoined" style="margin-bottom: 20px;">
    <h2>접속 중인 방: {{ roomId }}</h2>
    <p>내 이름: {{ playerName }}</p>
    
    <div v-if="roomState">
      <h3>현재 방 인원 ({{ Object.keys(roomState.players).length }} / 4)</h3>
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
    <button v-if="!isSpectator" @click="toggleReady" style="padding: 10px 20px; background: #4caf50; color: white; border: none; cursor: pointer;">준비 (Ready)</button>
  </div>
</template>