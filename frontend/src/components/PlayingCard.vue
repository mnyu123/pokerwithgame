<script setup>
import { computed } from 'vue'

const props = defineProps({
  card: {
    type: String,
    required: true
  },
  hidden: {
    type: Boolean,
    default: false
  }
})

// 텍스트를 무료 이미지 리소스 URL로 변환
const imageUrl = computed(() => {
  if (props.hidden) return 'https://deckofcardsapi.com/static/img/back.png'
  
  // 예: "♠ A", "♥ 10" 또는 "S T" 등 백엔드 포맷을 파싱
  const match = props.card.match(/([♠♥♦♣SHDC])\s*([A23456789TJQK0-9]+)/i)
  if (!match) return 'https://deckofcardsapi.com/static/img/back.png'

  const suitChar = match[1]
  let rankChar = match[2]

  // 문양 치환
  let suit = 'S'
  if (['♥', 'H'].includes(suitChar)) suit = 'H'
  else if (['♦', 'D'].includes(suitChar)) suit = 'D'
  else if (['♣', 'C'].includes(suitChar)) suit = 'C'

  // 숫자 치환 (API는 10을 '0'으로 표기)
  if (rankChar === '10' || rankChar === 'T') rankChar = '0'

  return `https://deckofcardsapi.com/static/img/${rankChar}${suit}.png`
})
</script>

<template>
  <img 
    :src="imageUrl" 
    :alt="card" 
    class="playing-card"
    style="width: 60px; height: 84px; border-radius: 4px; box-shadow: 2px 2px 5px rgba(0,0,0,0.3); background-color: white;" 
  />
</template>