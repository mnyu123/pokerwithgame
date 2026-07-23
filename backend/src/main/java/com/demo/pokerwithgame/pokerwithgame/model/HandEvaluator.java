package com.demo.pokerwithgame.pokerwithgame.model;

import java.util.*;

public class HandEvaluator {

    public static HandResult evaluate(List<String> holeCards, List<String> communityCards) {
        List<String> allCards = new ArrayList<>(holeCards);
        allCards.addAll(communityCards);

        Map<Integer, Integer> rankCounts = new HashMap<>();
        Map<Character, Integer> suitCounts = new HashMap<>();

        for (String card : allCards) {
            char suit = card.charAt(0);
            int rank = parseRank(card.charAt(2));

            suitCounts.put(suit, suitCounts.getOrDefault(suit, 0) + 1);
            rankCounts.put(rank, rankCounts.getOrDefault(rank, 0) + 1);
        }

        boolean isFlush = suitCounts.values().stream().anyMatch(count -> count >= 5);
        boolean isStraight = checkStraight(rankCounts.keySet());

        // 같은 숫자(랭크)가 몇 장씩 있는지 내림차순 정렬
        List<Integer> frequencies = new ArrayList<>(rankCounts.values());
        frequencies.sort(Collections.reverseOrder());

        int score = 0;
        String handName = "하이 카드";

        // 족보 판별 (간략화된 데모 버전)
        if (isStraight && isFlush) {
            score = 8000; handName = "스트레이트 플러시";
        } else if (frequencies.get(0) == 4) {
            score = 7000; handName = "포카드";
        } else if (frequencies.get(0) == 3 && frequencies.get(1) >= 2) {
            score = 6000; handName = "풀하우스";
        } else if (isFlush) {
            score = 5000; handName = "플러시";
        } else if (isStraight) {
            score = 4000; handName = "스트레이트";
        } else if (frequencies.get(0) == 3) {
            score = 3000; handName = "트리플";
        } else if (frequencies.get(0) == 2 && frequencies.get(1) == 2) {
            score = 2000; handName = "투페어";
        } else if (frequencies.get(0) == 2) {
            score = 1000; handName = "원페어";
        }

        // 동점 시 하이 카드 판별을 위해 가장 높은 카드 숫자를 점수에 합산
        int highCard = Collections.max(rankCounts.keySet());
        score += highCard;

        return new HandResult(handName, score);
    }

    private static int parseRank(char r) {
        if (r == 'T') return 10;
        if (r == 'J') return 11;
        if (r == 'Q') return 12;
        if (r == 'K') return 13;
        if (r == 'A') return 14;
        return r - '0';
    }

    private static boolean checkStraight(Set<Integer> ranks) {
        List<Integer> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted);
        if (sorted.contains(14)) sorted.add(1); // A는 1로도 사용됨
        Collections.sort(sorted);

        int consecutive = 1;
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (sorted.get(i + 1) - sorted.get(i) == 1) {
                consecutive++;
                if (consecutive >= 5) return true;
            } else if (sorted.get(i + 1) - sorted.get(i) != 0) {
                consecutive = 1;
            }
        }
        return false;
    }

    // 결과 저장용 내부 객체
    public static class HandResult {
        public String handName;
        public int score;
        public HandResult(String handName, int score) {
            this.handName = handName;
            this.score = score;
        }
    }
}