// HalliHandler.java
// 죽음의 피흘리기 할리갈리 명령 처리 (13주차). 규칙 판정은 HalliGame, 이 클래스는 알림 / 타이머
//   사람 모으기: /HG_FRIENDS, /HG_INVITE, /HG_ACCEPT, /HG_DECLINE, /HG_PARTY_LEAVE, /HG_QUEUE, /HG_CANCEL, /HG_START_NOW -> Matchmaker
//     친구끼리 2~4명이면 방장이 바로 시작(/HG_START_NOW)
//     랜덤 매칭은 먼저 들어온 사람이 방장. 2명부터 방장이 시작할 수 있고, 2명째부터 1분 지나면 자동 시작,
//     4명이 차면 10초 뒤 자동 시작
//   게임 중: /HG_FLIP(카드 내기), /HG_FAKE(내는 척), /HG_RING(종), /HG_LEAVE(나가기)
// 보내는 것 (게임 사람 모두에게, 이름은 윷놀이처럼 동물 이름)
//   /HG_MATCH 게임 내이름 이름=닉|-,...      (사람마다 다름)
//   /HG_START 게임 순서 피 페이크수 목표피
//   /HG_TURN 게임 이름 초                    한 차례 5초, 지나면 서버가 대신 냄
//   /HG_FLIP 게임 이름 과일 개수 남은카드
//   /HG_FAKE 게임 이름                       내는 척 (남은 횟수는 본인만 /HG_FAKES 남은수)
//   /HG_RING 게임 이름 결과 피               결과: 맞음, 늦음, 틀림
//   /HG_ROUND 게임 이긴사람 깎인사람들        맞은 종 정산 (이긴 사람이 카드를 가져감. 피 깎이는 사람은 없어서 깎인사람들은 -)
//   /HG_BOARD 게임 이름:피:과일,개수:남은카드;...
//   /HG_OUT 게임 이름                        탈락이나 나감
//   /HG_END 게임 등수순서 이유
// 실패는 /HG_FAIL 이유

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HalliHandler implements CommandHandler {

    static final int TURN_SECONDS = 5;
    static final int BELL_MS = 800; // 종이 맞은 뒤 잠깐 기다렸다 정산 (그사이 늦게 친 사람은 "늦음"만 표시, 피가 두 번 깎이지 않게)

    static class Session {
        final String id;
        final HalliGame game;
        final Map<String, UserService> byName = new LinkedHashMap<>();
        final Map<String, Matchmaker.Party> partyOf = new HashMap<>();
        int version; // 차례 타이머 구분용

        Session(String id, HalliGame game) {
            this.id = id;
            this.game = game;
        }
    }

    private final ServerContext ctx;
    private final Random rnd = new Random();
    private final Map<UserService, Session> playing = new ConcurrentHashMap<>();
    final Matchmaker mm;
    private int nextId = 1;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "halli-timer");
        t.setDaemon(true);
        return t;
    });

    HalliHandler(ServerContext ctx) {
        this.ctx = ctx;
        mm = new Matchmaker(ctx, "/HG_", "할리갈리", HalliGame.MIN_PLAYERS, HalliGame.MAX_PLAYERS, playing::containsKey);
        mm.setWait(60000, 10000); // 랜덤 매칭: 2명째부터 1분 지나면 모인 인원으로, 4명이 차면 10초 뒤 자동 시작 (그 전에 방장이 시작 가능)
        mm.setStarter(this::startGame);
    }

    @Override
    public String[] commands() {
        List<String> c = new ArrayList<>(Arrays.asList(mm.commands()));
        c.addAll(Arrays.asList("/HG_FLIP", "/HG_FAKE", "/HG_RING", "/HG_LEAVE"));
        return c.toArray(new String[0]);
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        if (mm.handles(cmd)) {
            try {
                mm.handle(u, cmd, args.length > 1 ? args[1] : "");
            } catch (MatchException e) {
                u.WriteOne("/HG_FAIL " + e.getMessage());
            }
            return;
        }
        synchronized (this) {
            try {
                Session s = playing.get(u);
                if (s == null) throw new MatchException("게임없음");
                String name = nameOf(s, u);
                switch (cmd) {
                    case "/HG_FLIP":  flip(s, name); break;
                    case "/HG_FAKE":
                        int left = s.game.fake(name);
                        send(s, "/HG_FAKE " + s.id + " " + name);
                        u.WriteOne("/HG_FAKES " + s.id + " " + left);
                        break;
                    case "/HG_RING":  ring(s, name); break;
                    case "/HG_LEAVE": leave(u); break;
                }
            } catch (MatchException e) {
                u.WriteOne("/HG_FAIL " + e.getMessage());
            }
        }
    }

    private static String nameOf(Session s, UserService u) {
        for (Map.Entry<String, UserService> e : s.byName.entrySet()) if (e.getValue() == u) return e.getKey();
        return "";
    }

    private void send(Session s, String msg) {
        for (UserService x : s.byName.values()) if (playing.get(x) == s) x.WriteOne(msg);
    }

    // Matchmaker가 2~4명을 모으면 부름
    private synchronized void startGame(List<UserService> people, List<Matchmaker.Party> from) {
        List<String> names = new ArrayList<>(Arrays.asList(YutHandler.ANIMALS));
        Collections.shuffle(names, rnd);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < people.size(); i++) order.add(i);
        Collections.shuffle(order, rnd);
        List<String> seat = new ArrayList<>(names.subList(0, people.size()));
        HalliGame g;
        try {
            g = new HalliGame(seat, rnd);
        } catch (MatchException e) {
            return; // 2~4명만 들어오므로 안 생김
        }
        Session s = new Session("h" + nextId++, g);
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            s.byName.put(seat.get(k), people.get(i));
            s.partyOf.put(seat.get(k), from.get(i));
            playing.put(people.get(i), s);
        }
        ctx.log("할리갈리 시작 [" + s.id + "] " + seat);
        for (Map.Entry<String, UserService> me : s.byName.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, UserService> o : s.byName.entrySet()) {
                boolean friend = s.partyOf.get(o.getKey()) == s.partyOf.get(me.getKey());
                if (sb.length() > 0) sb.append(",");
                sb.append(o.getKey()).append("=").append(friend ? o.getValue().nick() : "-");
            }
            me.getValue().WriteOne("/HG_MATCH " + s.id + " " + me.getKey() + " " + sb);
        }
        send(s, "/HG_START " + s.id + " " + String.join(",", seat) + " " + HalliGame.HP + " " + HalliGame.FAKES + " " + HalliGame.WIN_HP);
        send(s, "/HG_BOARD " + s.id + " " + g.boardText());
        turn(s);
    }

    // 차례 알림 + 5초 타이머
    // 아무도 낼 카드가 없으면 차례 알림 없이 5초 기다림 (그사이 5개면 종을 칠 수 있음). 아무 일 없으면 끝
    private void turn(Session s) {
        if (s.game.isOver()) return;
        final int v = ++s.version;
        if (s.game.stuck()) {
            send(s, "/HG_TURN " + s.id + " - " + TURN_SECONDS);
        } else {
            send(s, "/HG_TURN " + s.id + " " + s.game.current() + " " + TURN_SECONDS);
        }
        timer.schedule(() -> timeout(s, v), TURN_SECONDS, TimeUnit.SECONDS);
    }

    private void flip(Session s, String name) throws MatchException {
        HalliGame.Card c = s.game.flip(name);
        send(s, "/HG_FLIP " + s.id + " " + name + " " + c.fruit + " " + c.count + " " + s.game.cardsLeft(name));
        turn(s);
    }

    private void ring(Session s, String name) throws MatchException {
        String r = s.game.ring(name);
        if (r.equals("이미침")) return;
        send(s, "/HG_RING " + s.id + " " + name + " " + r + " " + s.game.hp(name));
        if (r.equals("틀림")) {
            if (!s.game.alive(name)) out(s, name);
            if (s.game.isOver()) end(s, "완주");
        } else if (r.equals("맞음")) { // 잠깐 다른 사람 치는 순서를 모은 뒤 정산. 그동안 차례 타이머는 멈춤
            s.version++;
            timer.schedule(() -> resolve(s), BELL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void resolve(Session s) {
        if (!s.game.bellOpen()) return;
        HalliGame.Round r = s.game.resolve();
        send(s, "/HG_ROUND " + s.id + " " + r.winner + " " + (r.losers.isEmpty() ? "-" : String.join(",", r.losers)));
        send(s, "/HG_BOARD " + s.id + " " + s.game.boardText());
        for (String o : r.out) out(s, o);
        if (s.game.isOver()) end(s, "목표");
        else turn(s);
    }

    private void out(Session s, String name) {
        send(s, "/HG_OUT " + s.id + " " + name);
    }

    private void end(Session s, String reason) {
        send(s, "/HG_END " + s.id + " " + String.join(",", s.game.ranking()) + " " + reason);
        ctx.log("할리갈리 끝 [" + s.id + "] " + s.game.ranking());
        for (UserService x : s.byName.values()) if (playing.get(x) == s) playing.remove(x);
        s.version++;
    }

    // 나가기. 탈락한 사람은 구경하다 나가는 것이라 게임에 영향 없음
    private void leave(UserService u) {
        Session s = playing.remove(u);
        if (s == null) return;
        String name = nameOf(s, u);
        u.WriteOne("/HG_END " + s.id + " - 나감");
        if (!s.game.alive(name) || s.game.isOver()) return;
        boolean wasTurn = s.game.current().equals(name);
        s.game.leave(name);
        out(s, name);
        send(s, "/HG_BOARD " + s.id + " " + s.game.boardText());
        if (s.game.isOver()) end(s, "기권");
        else if (wasTurn && !s.game.bellOpen()) turn(s);
    }

    // 5초 동안 안 내면 서버가 대신 냄. 카드가 다 떨어진 상태로 5초가 지나면 끝
    private synchronized void timeout(Session s, int v) {
        if (v != s.version || s.game.isOver() || s.game.bellOpen()) return;
        if (s.game.stuck()) {
            s.game.endByCards();
            end(s, "카드소진");
            return;
        }
        try {
            flip(s, s.game.current());
        } catch (MatchException e) {
            ctx.log("할리갈리 자동 내기 실패 [" + s.id + "] " + e.getMessage());
        }
    }

    void disconnected(UserService u) {
        synchronized (this) {
            leave(u);
        }
        mm.disconnected(u);
    }
}
