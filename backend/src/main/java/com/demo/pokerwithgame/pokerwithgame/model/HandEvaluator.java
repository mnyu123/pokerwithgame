package com.demo.pokerwithgame.pokerwithgame.model;

import java.util.*;

public class HandEvaluator {

    public static HandResult evaluate(List<String> holeCards, List<String> communityCards) {
        List<String> allCards = new ArrayList<>(holeCards);
        allCards.addAll(communityCards);

        Map<Integer, Integer> rankCounts = new HashMap<>();
        Map<Character, List<Integer>> suitMap = new HashMap<>();

        // 카드 파싱 (숫자와 문양 분리)
        for (String card : allCards) {
            char suit = card.charAt(0);
            int rank = parseRank(card.charAt(2));

            rankCounts.put(rank, rankCounts.getOrDefault(rank, 0) + 1);
            suitMap.putIfAbsent(suit, new ArrayList<>());
            suitMap.get(suit).add(rank);
        }

        // 1. 플러시 체크 (동일 문양 5개 이상)
        boolean isFlush = false;
        List<Integer> flushRanks = null;
        for (List<Integer> ranks : suitMap.values()) {
            if (ranks.size() >= 5) {
                isFlush = true;
                flushRanks = new ArrayList<>(ranks);
                Collections.sort(flushRanks, Collections.reverseOrder());
                break;
            }
        }

        // 2. 스트레이트 체크
        List<Integer> distinctRanks = new ArrayList<>(rankCounts.keySet());
        Collections.sort(distinctRanks, Collections.reverseOrder());
        if (distinctRanks.contains(14)) {
            distinctRanks.add(1); // 백스트레이트 (A-2-3-4-5) 지원을 위해 A(14)를 1로도 취급
        }

        int straightHigh = 0;
        int consecutive = 1;
        for (int i = 0; i < distinctRanks.size() - 1; i++) {
            if (distinctRanks.get(i) - distinctRanks.get(i + 1) == 1) {
                consecutive++;
                if (consecutive == 5) {
                    straightHigh = distinctRanks.get(i - 3); // 5개 연속 중 가장 높은 숫자
                    break;
                }
            } else {
                consecutive = 1;
            }
        }

        // 3. 스트레이트 플러시 체크
        int straightFlushHigh = 0;
        if (isFlush) {
            List<Integer> fRanks = new ArrayList<>(flushRanks);
            if (fRanks.contains(14)) fRanks.add(1);
            int fConsecutive = 1;
            for (int i = 0; i < fRanks.size() - 1; i++) {
                if (fRanks.get(i) - fRanks.get(i + 1) == 1) {
                    fConsecutive++;
                    if (fConsecutive == 5) {
                        straightFlushHigh = fRanks.get(i - 3);
                        break;
                    }
                } else {
                    fConsecutive = 1;
                }
            }
        }

        // 4. 빈도수별 랭크 정리 (포카드, 트리플, 페어 찾기)
        List<Integer> quads = new ArrayList<>();
        List<Integer> trips = new ArrayList<>();
        List<Integer> pairs = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : rankCounts.entrySet()) {
            int r = entry.getKey();
            int c = entry.getValue();
            if (c == 4) quads.add(r);
            else if (c == 3) trips.add(r);
            else if (c == 2) pairs.add(r);
        }
        Collections.sort(quads, Collections.reverseOrder());
        Collections.sort(trips, Collections.reverseOrder());
        Collections.sort(pairs, Collections.reverseOrder());

        // 5. 최종 점수(Score) 계산 및 족보 판정
        // int 범위를 넘을 수 있으므로 long 사용
        long score = 0;
        String handName = "";

        if (straightFlushHigh > 0) {
            score = 8000000L + straightFlushHigh;
            handName = "스트레이트 플러시";
        } else if (!quads.isEmpty()) {
            score = 7000000L + (quads.get(0) * 10000L) + getHighCard(distinctRanks, quads.get(0));
            handName = "포카드";
        } else if (!trips.isEmpty() && (!pairs.isEmpty() || trips.size() > 1)) {
            int primary = trips.get(0);
            int secondary = trips.size() > 1 ? trips.get(1) : pairs.get(0);
            score = 6000000L + (primary * 10000L) + secondary;
            handName = "풀하우스";
        } else if (isFlush) {
            score = 5000000L + flushRanks.get(0) * 10000L + flushRanks.get(1) * 100L + flushRanks.get(2);
            handName = "플러시";
        } else if (straightHigh > 0) {
            score = 4000000L + straightHigh;
            handName = "스트레이트";
        } else if (!trips.isEmpty()) {
            score = 3000000L + (trips.get(0) * 10000L) + getHighCard(distinctRanks, trips.get(0));
            handName = "트리플";
        } else if (pairs.size() >= 2) {
            score = 2000000L + (pairs.get(0) * 10000L) + (pairs.get(1) * 100L) + getHighCardExclude(distinctRanks, pairs.get(0), pairs.get(1));
            handName = "투페어";
        } else if (pairs.size() == 1) {
            score = 1000000L + (pairs.get(0) * 10000L) + getHighCardExclude(distinctRanks, pairs.get(0), -1);
            handName = "원페어";
        } else {
            score = distinctRanks.get(0) * 10000L + distinctRanks.get(1) * 100L + distinctRanks.get(2);
            handName = "하이 카드";
        }

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

    // 족보에 사용된 카드를 제외한 가장 높은 숫자(키커) 반환
    private static int getHighCard(List<Integer> ranks, int exclude) {
        for (int r : ranks) {
            if (r != exclude && r != 1) return r;
        }
        return 0;
    }

    private static int getHighCardExclude(List<Integer> ranks, int ex1, int ex2) {
        for (int r : ranks) {
            if (r != ex1 && r != ex2 && r != 1) return r;
        }
        return 0;
    }

    public static class HandResult {
        public String handName;
        public long score; // 점수 범위를 위해 long으로 변경
        public HandResult(String handName, long score) {
            this.handName = handName;
            this.score = score;
        }
    }
}