// HalliGame.java
// 죽음의 피흘리기 할리갈리 규칙 (13주차). 네트워크 없이 규칙만 (YutGame처럼 따로 떼서 테스트)
//   2~4명. 카드는 진짜 할리갈리처럼 56장(과일 4종 x 14장)을 섞어서 나눠 줌: 2명 28장, 3명 18장, 4명 14장
//   피는 3방울
//   차례대로 내 카드 더미에서 한 장씩 냄. 각자 맨 위 카드만 보임
//   내 카드를 다 내면 내 차례는 건너뛰지만 종은 칠 수 있음
//   펼쳐진 카드 중 같은 과일이 합쳐서 딱 5개면 종을 침
//     5개가 아닌데 치면(틀림) 친 사람 피 -1
//     5개일 때 제일 먼저 친 사람은 피 +1, 판의 카드도 가져감. 늦게 쳤거나 못 친 사람은 그대로
//   피는 점수도 겸함 (13주차 수정): 3방울로 시작, 맞으면 +1, 틀리면 -1, 8방울을 먼저 모으면 이김, 0이면 탈락
//     판이 정리되면 펼쳐진 카드를 전부 이긴 사람이 가져가서(카드 더미 밑으로) 이긴 사람부터 다시 냄
//   내는 척(페이크): 1판에 3번까지, 내 차례에만. 손이 나가는 모습만 보이고 카드는 안 나감
//   피가 0이 되면 탈락(그 사람 맨 위 카드는 판에서 안 보임). 마지막 1명이 남으면 끝
//   살아 있는 사람 모두 카드가 없고 종을 칠 일도 없으면 끝 (피 많은 순서로 등수)
// 종이 맞았을 때는 바로 정산하지 않고 잠깐(서버가 정함) 다른 사람이 치는 순서를 모은 뒤 resolve()로 정산

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class HalliGame {

    public static final int HP = 3, WIN_HP = 8, FAKES = 3, MIN_PLAYERS = 2, MAX_PLAYERS = 4, CARDS = 56;
    public static final String[] FRUITS = { "딸기", "바나나", "라임", "자두" };
    // 진짜 할리갈리 카드 구성: 과일 하나에 1개짜리 5장, 2개 3장, 3개 3장, 4개 2장, 5개 1장
    private static final int[] DECK = { 1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5 };

    public static class Card {
        public final int fruit, count;

        Card(int fruit, int count) {
            this.fruit = fruit;
            this.count = count;
        }

        @Override
        public String toString() {
            return fruit + ":" + count;
        }
    }

    // 정산 결과
    public static class Round {
        public String winner;
        public final List<String> losers = new ArrayList<>();
        public final List<String> out = new ArrayList<>(); // 이번에 탈락한 사람
    }

    private final Random rnd;
    private final List<String> players = new ArrayList<>(); // 처음 순서 그대로 (화면 자리)
    private final Map<String, Integer> hp = new LinkedHashMap<>(), fakes = new LinkedHashMap<>();
    private final Map<String, Card> top = new LinkedHashMap<>();
    private final Map<String, java.util.ArrayDeque<Card>> hand = new LinkedHashMap<>(); // 아직 안 낸 내 카드 더미
    private final List<Card> table = new ArrayList<>();                                // 판에 나온 카드 전부 (이긴 사람이 가져감)
    private final List<String> outOrder = new ArrayList<>(); // 탈락한 순서
    private int turn;
    private boolean over;
    private List<String> bell;                               // 종이 맞은 뒤 친 순서 (null이면 종 정산 중 아님)

    public HalliGame(List<String> names, Random rnd) throws MatchException {
        if (names.size() < MIN_PLAYERS || names.size() > MAX_PLAYERS) throw new MatchException("인원부족");
        this.rnd = rnd;
        for (String n : names) {
            players.add(n);
            hp.put(n, HP);
            fakes.put(n, FAKES);
            hand.put(n, new java.util.ArrayDeque<>());
        }
        // 56장을 섞어서 똑같이 나눔 (3명이면 18장씩, 남는 2장은 안 씀)
        List<Card> deck = new ArrayList<>();
        for (int f = 0; f < FRUITS.length; f++) for (int c : DECK) deck.add(new Card(f, c));
        java.util.Collections.shuffle(deck, rnd);
        int each = CARDS / names.size();
        for (int i = 0; i < names.size(); i++) hand.get(names.get(i)).addAll(deck.subList(i * each, (i + 1) * each));
    }

    public int cardsLeft(String n) {
        java.util.ArrayDeque<Card> h = hand.get(n);
        return h == null ? 0 : h.size();
    }

    // 살아 있는 사람 중 카드가 남은 사람이 없음 (더 낼 사람이 없음)
    public boolean stuck() {
        for (String p : players) if (alive(p) && cardsLeft(p) > 0) return false;
        return true;
    }

    public List<String> players() {
        return new ArrayList<>(players);
    }

    public boolean alive(String n) {
        return hp.containsKey(n) && hp.get(n) > 0 && !outOrder.contains(n);
    }

    public int hp(String n) {
        return hp.getOrDefault(n, 0);
    }

    public int fakesLeft(String n) {
        return fakes.getOrDefault(n, 0);
    }

    public Card top(String n) {
        return top.get(n);
    }

    public String current() {
        return players.get(turn);
    }

    public boolean isOver() {
        return over;
    }

    public boolean bellOpen() {
        return bell != null;
    }

    private void check(String n) throws MatchException {
        if (over) throw new MatchException("게임끝");
        if (!alive(n)) throw new MatchException("탈락함");
    }

    private void myTurn(String n) throws MatchException {
        check(n);
        if (bell != null) throw new MatchException("종정산중");
        if (!current().equals(n)) throw new MatchException("내차례아님");
        if (cardsLeft(n) == 0) throw new MatchException("카드없음");
    }

    // 다음 살아 있고 카드가 남은 사람에게 차례 (아무도 카드가 없으면 그대로 둠)
    private void nextTurn() {
        if (over || stuck()) return;
        do {
            turn = (turn + 1) % players.size();
        } while (!alive(players.get(turn)) || cardsLeft(players.get(turn)) == 0);
    }

    // 카드 내기
    public Card flip(String n) throws MatchException {
        myTurn(n);
        Card c = hand.get(n).poll();
        top.put(n, c);
        table.add(c);
        nextTurn();
        return c;
    }

    // 내는 척. 차례는 그대로 (진짜 카드는 따로 내야 함)
    public int fake(String n) throws MatchException {
        myTurn(n);
        int left = fakes.get(n);
        if (left <= 0) throw new MatchException("페이크없음");
        fakes.put(n, left - 1);
        return left - 1;
    }

    // 지금 펼쳐진 카드 중 합이 딱 5인 과일이 있는지
    public boolean five() {
        int[] sum = new int[FRUITS.length];
        for (Map.Entry<String, Card> e : top.entrySet()) if (alive(e.getKey())) sum[e.getValue().fruit] += e.getValue().count;
        for (int s : sum) if (s == 5) return true;
        return false;
    }

    // 종 치기. 결과: "맞음"(제일 먼저), "늦음"(정산 중에 친 순서만 기록), "이미침", "틀림"(피 -1)
    public String ring(String n) throws MatchException {
        check(n);
        if (bell != null) {
            if (bell.contains(n)) return "이미침";
            bell.add(n);
            return "늦음";
        }
        if (five()) {
            bell = new ArrayList<>();
            bell.add(n);
            return "맞음";
        }
        hurt(n);
        return "틀림";
    }

    // 피 -1. 0이 되면 탈락
    private boolean hurt(String n) {
        int h = hp.get(n) - 1;
        hp.put(n, h);
        if (h > 0) return false;
        eliminate(n);
        return true;
    }

    private void eliminate(String n) {
        if (outOrder.contains(n)) return;
        boolean wasTurn = !over && current().equals(n);
        outOrder.add(n);
        hp.put(n, 0);
        top.remove(n);
        int alive = 0;
        for (String p : players) if (alive(p)) alive++;
        if (alive <= 1) {
            over = true;
            bell = null;
            return;
        }
        if (wasTurn && bell == null) nextTurn();
    }

    // 카드가 다 떨어져서 끝냄 (서버가 판단: 더 낼 사람도 없고 종 칠 일도 없음)
    public void endByCards() {
        over = true;
        bell = null;
    }

    // 종이 맞은 뒤 모은 순서로 정산. 이긴 사람 피 +1(8방울이면 끝), 판의 카드는 전부 이긴 사람 더미 밑으로, 이긴 사람부터 다시
    public Round resolve() {
        Round r = new Round();
        if (bell == null) return r;
        r.winner = bell.get(0);
        // 피는 잘못 쳤을 때만 깎이므로 맞은 종 정산에서는 아무도 안 깎임 (losers는 빈 채로)
        bell = null;
        for (String l : r.losers) if (hurt(l)) r.out.add(l);
        if (alive(r.winner)) {
            hand.get(r.winner).addAll(table);
            hp.put(r.winner, hp.get(r.winner) + 1);
            if (hp.get(r.winner) >= WIN_HP) over = true; // 8방울 모음 -> 이김 (등수는 피 많은 순서라 1등)
        }
        table.clear();
        top.clear();
        if (!over) {
            turn = players.indexOf(r.winner);
            if (!alive(r.winner) || cardsLeft(r.winner) == 0) nextTurn();
        }
        return r;
    }

    // 나감 (기권, 연결 끊김)
    public void leave(String n) {
        if (!alive(n)) return;
        if (bell != null) bell.remove(n);
        eliminate(n);
        if (bell != null && bell.isEmpty()) bell = null;
    }

    // 등수: 살아남은 사람(피 많은 순서), 그다음은 늦게 탈락한 순서
    public List<String> ranking() {
        List<String> r = new ArrayList<>();
        for (String p : players) if (alive(p)) r.add(p);
        r.sort((a, b) -> hp(b) - hp(a));
        for (int i = outOrder.size() - 1; i >= 0; i--) r.add(outOrder.get(i));
        return r;
    }

    // 판 상태: 이름:피:카드(과일,개수 또는 -):남은카드수;...   (화면을 새로 그릴 때)
    public String boardText() {
        StringBuilder sb = new StringBuilder();
        for (String p : players) {
            Card c = top.get(p);
            if (sb.length() > 0) sb.append(";");
            sb.append(p).append(":").append(hp(p)).append(":").append(c == null ? "-" : c.fruit + "," + c.count).append(":").append(cardsLeft(p));
        }
        return sb.toString();
    }

    // 테스트용: 맨 위 카드 정하기
    void place(String n, int fruit, int count) {
        top.put(n, new Card(fruit, count));
    }

    // 테스트용: 남은 카드 수 맞추기 (앞에서부터 버림)
    void setCards(String n, int k) {
        while (cardsLeft(n) > k) hand.get(n).poll();
    }

    void setHp(String n, int h) {
        hp.put(n, h);
    }
}
