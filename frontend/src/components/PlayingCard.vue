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

// 어떤 텍스트가 와도 완벽하게 문양과 숫자를 찾아내는 로직
const imageUrl = computed(() => {
  // 숨김 처리이거나 'BACK' 카드는 바로 뒷면 반환
  if (props.hidden || !props.card || props.card === 'BACK') {
    return 'https://deckofcardsapi.com/static/img/back.png'
  }
  
  // 대문자로 변환하여 비교를 쉽게 만듦
  const text = props.card.toUpperCase()

  // 1. 문양(Suit) 추출
  let suit = 'S' // 기본값 스페이드(S)
  if (text.includes('♥') || text.includes('H') || text.includes('하트')) suit = 'H'
  else if (text.includes('♦') || text.includes('D') || text.includes('다이아')) suit = 'D'
  else if (text.includes('♣') || text.includes('C') || text.includes('클로버') || text.includes('클럽')) suit = 'C'

  // 2. 숫자(Rank) 추출
  let rank = ''
  if (text.includes('A')) rank = 'A'
  else if (text.includes('K')) rank = 'K'
  else if (text.includes('Q')) rank = 'Q'
  else if (text.includes('J')) rank = 'J'
  else if (text.includes('10') || text.includes('T')) rank = '0' // API 규칙: 10은 '0'으로 표기
  else {
    // 2~9 사이의 숫자가 포함되어 있는지 정규식으로 탐색
    const match = text.match(/[2-9]/)
    if (match) rank = match[0]
  }

  // 만약 숫자나 기호를 아예 찾지 못했다면 에러 방지용 뒷면 반환
  if (!rank) {
    return 'https://deckofcardsapi.com/static/img/back.png'
  }

  // 추출된 랭크와 문양을 조합하여 최종 이미지 URL 생성 (예: 9H.png, 0S.png)
  return `https://deckofcardsapi.com/static/img/${rank}${suit}.png`
})
</script>

<template>
  <div style="display: flex; flex-direction: column; align-items: center;">
    <img 
      :src="imageUrl" 
      :alt="card" 
      class="playing-card"
      style="width: 60px; height: 84px; border-radius: 4px; box-shadow: 2px 2px 5px rgba(0,0,0,0.3); background-color: white;" 
    />
    <!-- 이미지 아래에 백엔드에서 보낸 텍스트를 작게 표시하여 디버깅을 돕습니다 -->
    <span v-if="!hidden && card !== 'BACK'" style="font-size: 11px; color: #fff; margin-top: 4px; font-weight: bold; text-shadow: 1px 1px 2px black;">
      {{ card }}
    </span>
  </div>
</template>