// YutGame.java
// 도파민 윷놀이 한 판의 상태와 규칙 (12주차). 네트워크는 모름 -> 소켓 없이 테스트할 수 있음
// 메시지 보내기, 20초 타이머는 YutHandler가 함
//
// 규칙 (제안서 + 12주차에 정한 것)
//   - 4명, 말 4개씩. 도 개 걸 윷 모, 빽도(1칸 뒤로), 낙(5%, 그 던지기만 무효. 앞에 나온 결과가 없으면 차례 끝)
//   - 윷, 모, 잡기는 한 번 더. 던질 기회를 다 쓴 다음에 말을 옮김
//   - 내 말끼리 같은 칸이면 업혀서 같이 움직임. 모서리(5, 10), 가운데(22)에 멈추면 다음에 지름길
//   - 0번(마지막 칸)을 지나가야만 완주. 0번에 멈추면 그 칸에 서 있음
//   - 지옥 칸(17)에 딱 멈추면 그 말(업힌 말 포함) 출발점으로. 지나가는 건 괜찮음
//   - 잉태 칸(8)에 딱 멈추면 대기 중인 내 말 하나가 그 자리로 나와서 업힘 (말은 최대 4개, 대기 말이 없으면 효과 없음)
//   - 잡기와 칸 효과가 겹치면 잡기 먼저
//   - 말 4개 전부 완주하면 등수가 정해지고, 2등이 나올 때까지 게임 계속 (1등은 구경하거나 나가도 됨)
//
// 칸 번호 (윷판 그림은 설계 페이지 참고)
//   바깥 0(마지막 칸) 1~19, 5번 지름길 20 21 22 23 24 -> 15, 10번 지름길 25 26 22 27 28 -> 0, 가운데 22
//   출발 전 대기 -1, 완주 99

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class YutGame {

    public static final int WAIT = -1, DONE = 99;
    public static final int HELL = 17, BIRTH = 8; // 지옥, 잉태 칸 (12주차 확정)
    public static final int START_PIECES = 4;
    public static final int MAX_PLAYERS = 4;
    public static final double NAK = 0.05;
    public static final int PLACES = 2; // 2등까지 나오면 게임 끝

    // 말 하나. prev는 바로 전 칸 (빽도로 되돌아갈 칸, 가운데 22번을 어느 쪽에서 왔는지)
    static class Piece {
        int pos = WAIT;
        int prev = WAIT;
    }

    // 던진 결과
    public static class Throw {
        public final String result; // 도 개 걸 윷 모 빽도 낙
        public final String sticks; // 윷가락 4개, 1이 젖혀진 면. 첫째가 빽도 표시 가락

        Throw(String result, String sticks) {
            this.result = result;
            this.sticks = sticks;
        }
    }

    // 옮긴 결과
    public static class Move {
        public final List<Integer> moved = new ArrayList<>(); // 같이 움직인 말 번호
        public int from, to;
        public final List<String> events = new ArrayList<>(); // 잡기:닉:말들, 지옥, 잉태:나온말번호, 완주
        public boolean won;  // 이 이동으로 그 사람 말이 전부 완주함 (등수가 생김)
        public int rank;     // 그때 등수
        public boolean over; // 이 이동으로 게임이 끝남 (2등까지 나옴)
    }

    private final Random rnd;
    private String host;
    private final List<String> players = new ArrayList<>();
    private final Map<String, List<Piece>> pieces = new LinkedHashMap<>();
    private boolean started, over;
    private int turn;
    private final List<String> ranking = new ArrayList<>(); // 완주한 순서 (1등, 2등)
    private int throwsLeft;
    private final List<String> results = new ArrayList<>();

    public YutGame(String host, Random rnd) {
        this.host = host;
        this.rnd = rnd;
        players.add(host);
    }

    // ===================== 대기실 =====================

    public void join(String nick) throws YutException {
        if (started) throw new YutException("이미게임중");
        if (players.contains(nick)) throw new YutException("이미참가");
        if (players.size() >= MAX_PLAYERS) throw new YutException("인원초과");
        players.add(nick);
    }

    // 대기실에서 나가기. 방장이 나가면 다음 사람이 방장
    public void leaveLobby(String nick) {
        players.remove(nick);
        if (nick.equals(host)) host = players.isEmpty() ? null : players.get(0);
    }

    public void start(String nick) throws YutException {
        if (started) throw new YutException("이미게임중");
        if (!nick.equals(host)) throw new YutException("방장아님");
        if (players.size() < 2) throw new YutException("인원부족");
        started = true;
        for (String p : players) {
            ArrayList<Piece> list = new ArrayList<>();
            for (int i = 0; i < START_PIECES; i++) list.add(new Piece());
            pieces.put(p, list);
        }
        turn = 0;
        throwsLeft = 1;
    }

    // ===================== 차례 =====================

    public String current() {
        return players.get(turn);
    }

    private void checkTurn(String nick) throws YutException {
        if (!started || over) throw new YutException("게임없음");
        if (!pieces.containsKey(nick)) throw new YutException("참가안함");
        if (!nick.equals(current())) throw new YutException("내차례아님");
    }

    public Throw throwYut(String nick) throws YutException {
        checkTurn(nick);
        if (throwsLeft == 0) throw new YutException("던질기회없음");
        throwsLeft--;
        // 낙: 이번 던지기만 무효 (12주차 수정)
        //   첫 던지기에서 나오면 남은 결과가 없으니 그대로 차례 끝
        //   윷, 모, 잡기로 한 번 더 던지다 나오면 앞에서 나온 결과(도 개 걸 윷 모)는 살아 있어서 그걸로 옮김
        if (rnd.nextDouble() < NAK) return new Throw("낙", "0000");
        StringBuilder sb = new StringBuilder();
        int flat = 0;
        for (int i = 0; i < 4; i++) {
            boolean up = rnd.nextBoolean();
            sb.append(up ? '1' : '0');
            if (up) flat++;
        }
        String r;
        switch (flat) {
            case 1: r = sb.charAt(0) == '1' ? "빽도" : "도"; break; // 빽도 표시 가락 하나만 젖혀짐
            case 2: r = "개"; break;
            case 3: r = "걸"; break;
            case 4: r = "윷"; break;
            default: r = "모"; break; // 전부 엎어짐
        }
        if (r.equals("윷") || r.equals("모")) throwsLeft++;
        results.add(r);
        return new Throw(r, sb.toString());
    }

    static int steps(String result) throws YutException {
        switch (result) {
            case "빽도": return -1;
            case "도": return 1;
            case "개": return 2;
            case "걸": return 3;
            case "윷": return 4;
            case "모": return 5;
            default: throw new YutException("없는결과");
        }
    }

    // 한 칸 앞. first는 이번 이동의 첫 걸음인지 (모서리, 가운데에 서 있다가 출발하면 지름길)
    static int next(int cur, int prev, boolean first) {
        if (cur == WAIT) return 1;
        if (cur == 0) return DONE; // 마지막 칸에서 한 칸 더 가면 완주
        if (first) {
            if (cur == 5) return 20;
            if (cur == 10) return 25;
            if (cur == 22) return 27;
        }
        switch (cur) {
            case 19: return 0;
            case 20: return 21;
            case 21: return 22;
            case 22: return prev == 21 ? 23 : 27; // 5번 쪽에서 왔으면 15번 쪽으로, 아니면 0번 쪽으로
            case 23: return 24;
            case 24: return 15;
            case 25: return 26;
            case 26: return 22;
            case 27: return 28;
            case 28: return 0;
            default: return cur + 1; // 1~18
        }
    }

    // 빽도로 한 칸 뒤. 지나온 칸을 알면 거기로, 모르면 기본 길로
    static int back(int cur, int prev) {
        if (prev >= 0) return prev;
        switch (cur) {
            case 1: return 0;
            case 0: return 19;
            case 20: return 5;
            case 25: return 10;
            case 15: return 14;
            case 22: return 21;
            default: return cur - 1; // 2~19, 21, 23, 24, 26, 27, 28
        }
    }

    // pos(바로 전 칸 prev)에 있는 말이 result만큼 가면 { 도착 칸, 도착했을 때의 전 칸 }
    // 서버 판정(move)과 화면 미리보기(YutView의 반투명 칸)가 같은 계산을 쓰도록 따로 뺌
    public static int[] dest(int pos, int prev, String result) throws YutException {
        int k = steps(result);
        if (k < 0) return new int[] { back(pos, prev), -2 }; // 빽도 다음 전 칸은 모름 (다음 빽도는 기본 길)
        int cur = pos;
        for (int s = 0; s < k; s++) {
            int nx = next(cur, prev, s == 0);
            prev = cur;
            cur = nx;
            if (cur == DONE) break;
        }
        return new int[] { cur, prev };
    }

    // 화면에서 한 칸씩 움직이는 모습을 그릴 때 쓰는 지나가는 칸 목록 (마지막이 도착 칸, 완주면 99로 끝남)
    public static List<Integer> path(int pos, int prev, String result) throws YutException {
        ArrayList<Integer> list = new ArrayList<>();
        int k = steps(result);
        if (k < 0) {
            list.add(back(pos, prev));
            return list;
        }
        int cur = pos;
        for (int s = 0; s < k; s++) {
            int nx = next(cur, prev, s == 0);
            prev = cur;
            cur = nx;
            list.add(cur);
            if (cur == DONE) break;
        }
        return list;
    }

    // 쓸 수 있는 결과인지 (빽도는 판 위에 내 말이 있어야)
    private boolean usable(String nick, String result) {
        for (Piece p : pieces.get(nick)) {
            if (p.pos == DONE) continue;
            if (!result.equals("빽도") || p.pos != WAIT) return true;
        }
        return false;
    }

    public Move move(String nick, int id, String result) throws YutException {
        checkTurn(nick);
        if (throwsLeft > 0) throw new YutException("먼저던지기");
        if (!results.contains(result)) throw new YutException("없는결과");
        List<Piece> mine = pieces.get(nick);
        if (id < 0 || id >= mine.size()) throw new YutException("없는말");
        Piece piece = mine.get(id);
        int k = steps(result);
        if (piece.pos == DONE || (k < 0 && piece.pos == WAIT)) throw new YutException("못움직이는말");
        results.remove(result);

        Move m = new Move();
        m.from = piece.pos;
        // 업힌 말 (대기 중인 말은 하나씩만 나감)
        for (int i = 0; i < mine.size(); i++) {
            if (i == id || (piece.pos != WAIT && mine.get(i).pos == piece.pos)) m.moved.add(i);
        }

        int[] d = dest(piece.pos, piece.prev, result);
        int dest = d[0], newPrev = d[1];
        m.to = dest;

        if (dest == DONE) {
            for (int i : m.moved) mine.get(i).pos = DONE;
            m.events.add("완주");
        } else {
            for (int i : m.moved) {
                mine.get(i).pos = dest;
                mine.get(i).prev = newPrev;
            }
            // 1. 잡기 먼저
            for (Map.Entry<String, List<Piece>> e : pieces.entrySet()) {
                if (e.getKey().equals(nick)) continue;
                StringBuilder ids = new StringBuilder();
                List<Piece> theirs = e.getValue();
                for (int i = 0; i < theirs.size(); i++) {
                    if (theirs.get(i).pos != dest) continue;
                    theirs.get(i).pos = WAIT;
                    theirs.get(i).prev = WAIT;
                    if (ids.length() > 0) ids.append(",");
                    ids.append(i);
                }
                if (ids.length() > 0) {
                    m.events.add("잡기:" + e.getKey() + ":" + ids);
                    throwsLeft++; // 잡으면 한 번 더
                }
            }
            // 2. 칸 효과
            if (dest == HELL) {
                for (int i : m.moved) {
                    mine.get(i).pos = WAIT;
                    mine.get(i).prev = WAIT;
                }
                m.events.add("지옥");
            } else if (dest == BIRTH) {
                // 12주차 수정: 말은 최대 4개. 새로 생기는 대신 대기 중인 내 말 하나가 우물에서 나와 업힘
                // 대기 중인 말이 없으면(전부 판 위나 완주) 아무 일 없음
                for (int i = 0; i < mine.size(); i++) {
                    if (mine.get(i).pos != WAIT) continue;
                    mine.get(i).pos = dest;
                    mine.get(i).prev = newPrev;
                    m.events.add("잉태:" + i);
                    break;
                }
            }
        }
        m.won = true;
        for (Piece p : mine) if (p.pos != DONE) m.won = false;
        if (m.won) { // 등수 확정. 이 사람 차례는 여기서 끝 (남은 결과, 한 번 더도 없어짐)
            ranking.add(nick);
            m.rank = ranking.size();
            results.clear();
            throwsLeft = 0;
            checkOver();
            m.over = over;
        }
        return m;
    }

    // 던질 기회가 없을 때, 못 쓰는 결과를 버림 (판에 말이 없는데 빽도 같은 것). 버린 결과 목록
    public List<String> dropUnusable() {
        ArrayList<String> dropped = new ArrayList<>();
        if (!started || over || throwsLeft > 0) return dropped;
        for (String r : new ArrayList<>(results)) {
            if (!usable(current(), r)) {
                results.remove(r);
                dropped.add(r);
            }
        }
        return dropped;
    }

    // 던질 기회도 남은 결과도 없으면 다음 사람. 차례가 넘어갔으면 true
    public boolean nextTurnIfDone() {
        if (!started || over || throwsLeft > 0 || !results.isEmpty()) return false;
        do {
            turn = (turn + 1) % players.size();
        } while (ranking.contains(players.get(turn))); // 이미 등수가 정해진 사람은 건너뜀
        throwsLeft = 1;
        return true;
    }

    // 게임 중에 나감: 그 사람 말은 판에서 빠짐. 차례였으면 다음 사람
    // 1명만 남으면 게임 끝 (남은 사람 승). 남은 사람 닉네임, 아니면 null
    // 2등까지 나왔거나, 아직 안 끝난 사람이 1명뿐이면 끝 (그 1명은 다음 등수)
    private void checkOver() {
        int active = 0;
        String last = null;
        for (String p : players) {
            if (ranking.contains(p)) continue;
            active++;
            last = p;
        }
        if (ranking.size() >= PLACES || active <= 1) {
            if (active == 1 && ranking.size() < PLACES) ranking.add(last);
            over = true;
        }
    }

    // 게임 중에 나감 (기권). 이미 등수가 정해진 사람은 판에 그대로 두고 아무것도 안 바뀜
    public void removePlayer(String nick) {
        int idx = players.indexOf(nick);
        if (idx < 0 || ranking.contains(nick)) return;
        boolean wasTurn = started && !over && idx == turn;
        players.remove(idx);
        pieces.remove(nick);
        if (nick.equals(host)) host = players.isEmpty() ? null : players.get(0);
        if (!started || players.isEmpty()) {
            if (started) over = true;
            return;
        }
        checkOver();
        if (over) return;
        if (idx < turn) turn--;
        if (turn >= players.size()) turn = 0;
        while (ranking.contains(players.get(turn))) turn = (turn + 1) % players.size();
        if (wasTurn) {
            results.clear();
            throwsLeft = 1;
        }
    }

    public List<String> ranking() {
        return new ArrayList<>(ranking);
    }

    public int[] autoMoveChoice() {
        List<Piece> mine = pieces.get(current());
        for (int r = 0; r < results.size(); r++) {
            String res = results.get(r);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < mine.size(); i++) {
                    int pos = mine.get(i).pos;
                    if (pos == DONE) continue;
                    boolean onBoard = pos != WAIT;
                    if (pass == 0 && !onBoard) continue;
                    if (res.equals("빽도") && !onBoard) continue;
                    return new int[] { i, r };
                }
            }
        }
        return null;
    }

    // 규칙 테스트용: 던진 결과를 정해서 넣음 (YutGameTest에서만 씀)
    void giveResults(String... rs) {
        results.clear();
        for (String r : rs) results.add(r);
        throwsLeft = 0;
    }

    // 규칙 테스트용: 말 위치를 직접 놓음
    void place(String nick, int id, int pos, int prev) {
        Piece p = pieces.get(nick).get(id);
        p.pos = pos;
        p.prev = prev;
    }

    // ===================== 상태 읽기 =====================

    public boolean isStarted() {
        return started;
    }

    public boolean isOver() {
        return over;
    }

    public String host() {
        return host;
    }

    public List<String> players() {
        return new ArrayList<>(players);
    }

    public int throwsLeft() {
        return throwsLeft;
    }

    public List<String> results() {
        return new ArrayList<>(results);
    }

    // /YUT_BOARD 용: 닉:위치,위치;닉:위치,... 남은결과(없으면 -)
    public String boardText() {
        StringBuilder sb = new StringBuilder();
        for (String p : players) {
            if (sb.length() > 0) sb.append(";");
            sb.append(p).append(":");
            List<Piece> list = pieces.get(p);
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(list.get(i).pos);
            }
        }
        return sb + " " + (results.isEmpty() ? "-" : String.join(",", results));
    }
}
