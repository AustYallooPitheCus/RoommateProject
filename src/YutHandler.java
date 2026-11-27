// YutHandler.java
// 도파민 윷놀이 명령 처리 (12주차). 규칙 판정은 YutGame, 이 클래스는 게임 알림 / 20초 타이머
// 사람 모으기(파티, 친구 초대, 랜덤 매칭)는 13주차에 Matchmaker로 뺌 (할리갈리도 같이 씀). 윷놀이는 무조건 4명
//   /YUT_FRIENDS, /YUT_INVITE 닉, /YUT_ACCEPT, /YUT_DECLINE, /YUT_PARTY_LEAVE, /YUT_QUEUE, /YUT_CANCEL  -> Matchmaker
//   /YUT_THROW, /YUT_MOVE 말번호 결과, /YUT_LEAVE(기권, 등수가 정해진 사람은 그냥 나가기)      게임 중
// 누가 말을 다 들여보내면 /YUT_RANK 이름 등수, 2등까지 나오면 /YUT_END 게임 1등,2등 이유
//
// 게임 안에서는 모두 "토끼", "펭귄" 같은 동물 이름으로 부름 (판 정보는 모두에게 똑같이 보냄)
// 시작할 때 /YUT_MATCH로 사람마다 "누가 누구인지" 표를 따로 줌. 같은 파티 친구면 닉네임, 아니면 -
// 화면에는 모두 "플레이어1~4"(차례 순서)로 보이고, 친구만 그 밑에 (닉네임)이 붙음
// 게임은 이 객체 하나로 잠금(synchronized). 사용자 스레드와 타이머 스레드가 같이 건드리기 때문
// 실패는 /YUT_FAIL 이유

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class YutHandler implements CommandHandler {

    static final int TURN_SECONDS = 20;
    static final String[] ANIMALS = { "토끼", "고양이", "다람쥐", "펭귄", "여우", "판다", "수달", "햄스터" };

    // 진행 중인 게임 하나
    static class Session {
        final String id;
        final YutGame game;
        final Map<String, UserService> byName = new LinkedHashMap<>(); // 동물 이름 -> 사람
        final Map<String, Matchmaker.Party> partyOf = new HashMap<>(); // 동물 이름 -> 원래 파티 (친구끼리 알아보기용)
        int version; // 20초 타이머 구분용. 행동이 있을 때마다 1씩 올라가서 예전 타이머는 무시됨

        Session(String id, YutGame game) {
            this.id = id;
            this.game = game;
        }
    }

    private final ServerContext ctx;
    private final Random rnd = new Random();
    // 게임 중인 사람. Matchmaker가 잠금 없이 읽어서 ConcurrentHashMap (바꾸는 건 이 객체 잠금 안에서만)
    private final Map<UserService, Session> playing = new java.util.concurrent.ConcurrentHashMap<>();
    final Matchmaker mm;
    private int nextId = 1;
    // 20초 타이머. 데몬 스레드라 서버를 끄면 같이 꺼짐
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "yut-timer");
        t.setDaemon(true);
        return t;
    });

    YutHandler(ServerContext ctx) {
        this.ctx = ctx;
        mm = new Matchmaker(ctx, "/YUT_", "윷놀이", YutGame.MAX_PLAYERS, YutGame.MAX_PLAYERS, playing::containsKey);
        mm.setStarter(this::startGame);
    }

    @Override
    public String[] commands() {
        List<String> c = new ArrayList<>(Arrays.asList(mm.commands()));
        c.addAll(Arrays.asList("/YUT_THROW", "/YUT_MOVE", "/YUT_LEAVE"));
        return c.toArray(new String[0]);
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        String arg = args.length > 1 ? args[1] : "";
        String rest = args.length > 2 ? arg + " " + args[2] : arg;
        if (mm.handles(cmd)) { // 사람 모으기는 Matchmaker가 (자기 잠금으로)
            try {
                mm.handle(u, cmd, arg);
            } catch (MatchException e) {
                u.WriteOne("/YUT_FAIL " + e.getMessage());
            }
            return;
        }
        synchronized (this) {
            try {
                switch (cmd) {
                    case "/YUT_THROW": doThrow(session(u), name(u)); break;
                    case "/YUT_MOVE":  doMove(session(u), name(u), rest); break;
                    case "/YUT_LEAVE": forfeit(u); break;
                }
            } catch (YutException e) {
                u.WriteOne("/YUT_FAIL " + e.getMessage());
            }
        }
    }

    boolean playing(UserService u) {
        return playing.containsKey(u);
    }

    // Matchmaker가 4명을 모으면 부름 (Matchmaker 잠금 안에서 불리고, 여기서 게임 잠금을 잡음)
    private synchronized void startGame(List<UserService> people, List<Matchmaker.Party> from) {
        List<String> names = new ArrayList<>(Arrays.asList(ANIMALS));
        Collections.shuffle(names, rnd);
        // 차례 순서도 섞음 (파티끼리 붙어서 시작하지 않게)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) order.add(i);
        Collections.shuffle(order, rnd);

        String first = names.get(0);
        Session s = new Session("g" + nextId++, new YutGame(first, rnd));
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            String name = names.get(k);
            try {
                if (k > 0) s.game.join(name);
            } catch (YutException e) {
                // 이름이 다 달라서 안 생김
            }
            s.byName.put(name, people.get(i));
            s.partyOf.put(name, from.get(i));
            playing.put(people.get(i), s);
        }
        try {
            s.game.start(first);
        } catch (YutException e) {
            // 4명이 다 모였으니 안 생김
        }
        ctx.log("윷놀이 시작 [" + s.id + "] " + s.game.players());

        // 사람마다 이름표를 따로 만듦: 나랑 같은 파티면 닉네임, 아니면 - (화면에는 플레이어N만)
        for (Map.Entry<String, UserService> me : s.byName.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, UserService> o : s.byName.entrySet()) {
                boolean friend = s.partyOf.get(o.getKey()) == s.partyOf.get(me.getKey());
                if (sb.length() > 0) sb.append(",");
                sb.append(o.getKey()).append("=").append(friend ? o.getValue().nick() : "-"); // 모르는 사람은 닉네임 없음
            }
            me.getValue().WriteOne("/YUT_MATCH " + s.id + " " + me.getKey() + " " + sb);
        }
        send(s, "/YUT_START " + s.id + " " + String.join(",", s.game.players()) + " " + YutGame.START_PIECES + " "
                + YutGame.HELL + " " + YutGame.BIRTH);
        send(s, "/YUT_BOARD " + s.id + " " + s.game.boardText());
        turn(s);
    }

    // ===================== 게임 =====================

    private Session session(UserService u) throws YutException {
        Session s = playing.get(u);
        if (s == null) throw new YutException("게임없음");
        return s;
    }

    private String name(UserService u) throws YutException {
        Session s = session(u);
        for (Map.Entry<String, UserService> e : s.byName.entrySet()) if (e.getValue() == u) return e.getKey();
        throw new YutException("참가안함");
    }

    private void send(Session s, String msg) {
        for (Map.Entry<String, UserService> e : s.byName.entrySet()) {
            if (playing.get(e.getValue()) == s) e.getValue().WriteOne(msg); // 나간 사람에게는 안 보냄
        }
    }

    // 지금 차례 알림 + 20초 타이머 다시 시작
    private void turn(Session s) {
        send(s, "/YUT_TURN " + s.id + " " + s.game.current() + " " + TURN_SECONDS);
        final int version = ++s.version;
        timer.schedule(() -> timeout(s, version), TURN_SECONDS, TimeUnit.SECONDS);
    }

    private void doThrow(Session s, String name) throws YutException {
        YutGame.Throw t = s.game.throwYut(name);
        List<String> left = s.game.results();
        send(s, "/YUT_THROWN " + s.id + " " + name + " " + t.result + " " + t.sticks + " "
                + (left.isEmpty() ? "-" : String.join(",", left)));
        afterAction(s, name);
    }

    // args: "말번호 결과"
    private void doMove(Session s, String name, String args) throws YutException {
        String[] a = args.split(" ");
        if (a.length != 2) throw new YutException("형식오류");
        int id;
        try {
            id = Integer.parseInt(a[0]);
        } catch (NumberFormatException e) {
            throw new YutException("없는말");
        }
        YutGame.Move m = s.game.move(name, id, a[1]);
        StringBuilder moved = new StringBuilder();
        for (int i : m.moved) moved.append(moved.length() > 0 ? "," : "").append(i);
        send(s, "/YUT_MOVED " + s.id + " " + name + " " + moved + " " + m.from + " " + m.to + " " + a[1] + " "
                + (m.events.isEmpty() ? "-" : String.join("|", m.events)));
        send(s, "/YUT_BOARD " + s.id + " " + s.game.boardText());
        if (m.won) { // 말이 전부 들어옴 -> 등수. 2등까지 나왔으면 끝, 아니면 남은 사람끼리 계속
            if (m.over) {
                end(s, String.join(",", s.game.ranking()), "완주");
                return;
            }
            send(s, "/YUT_RANK " + s.id + " " + name + " " + m.rank);
        }
        afterAction(s, name);
    }

    // 던지거나 옮긴 다음: 못 쓰는 결과 버리기 -> 차례 넘기기 -> 차례 알림(타이머 다시)
    private void afterAction(Session s, String name) {
        for (String dropped : s.game.dropUnusable()) send(s, "/YUT_SKIP " + s.id + " " + name + " " + dropped + " 판에말없음");
        if (s.game.nextTurnIfDone()) send(s, "/YUT_BOARD " + s.id + " " + s.game.boardText());
        turn(s);
    }

    private void end(Session s, String winner, String reason) {
        send(s, "/YUT_END " + s.id + " " + winner + " " + reason);
        ctx.log("윷놀이 끝 [" + s.id + "] " + winner + " " + reason);
        for (UserService x : s.byName.values()) if (playing.get(x) == s) playing.remove(x);
        s.version++; // 남은 타이머 무시
    }

    // 기권 (게임 중 나가기, 연결이 끊긴 것도 여기로)
    // 이미 등수가 정해진 사람(1등)은 게임에 영향 없이 빠지기만 함 (구경 그만)
    private void forfeit(UserService u) throws YutException {
        Session s = session(u);
        String name = name(u);
        playing.remove(u);
        u.WriteOne("/YUT_END " + s.id + " - 나감");
        if (s.game.ranking().contains(name)) return;
        s.game.removePlayer(name);
        send(s, "/YUT_OUT " + s.id + " " + name);
        if (s.game.isOver()) {
            List<String> r = s.game.ranking();
            end(s, r.isEmpty() ? "-" : String.join(",", r), r.isEmpty() ? "취소" : "기권");
        } else {
            send(s, "/YUT_BOARD " + s.id + " " + s.game.boardText());
            turn(s);
        }
    }

    // 20초 동안 아무것도 안 하면 서버가 대신 던지거나 옮김
    private synchronized void timeout(Session s, int version) {
        YutGame g = s.game;
        if (g.isOver() || version != s.version) return; // 그 사이에 뭔가 했으면 무시
        String name = g.current();
        send(s, "/YUT_TIMEOUT " + s.id + " " + name);
        try {
            if (g.throwsLeft() > 0) {
                doThrow(s, name);
            } else {
                int[] c = g.autoMoveChoice();
                if (c != null) doMove(s, name, c[0] + " " + g.results().get(c[1]));
                else afterAction(s, name);
            }
        } catch (YutException e) {
            ctx.log("윷놀이 자동 진행 실패 [" + s.id + "] " + e.getMessage());
        }
    }

    // ===================== 연결이 끊김 =====================

    // 게임에서 빠지고(게임 잠금), 그다음 파티, 초대에서 빠짐(Matchmaker 잠금). 두 잠금을 겹쳐 잡지 않음
    void disconnected(UserService u) {
        synchronized (this) {
            try {
                if (playing.containsKey(u)) forfeit(u);
            } catch (YutException e) {
                // 이미 빠짐
            }
        }
        mm.disconnected(u);
    }
}
