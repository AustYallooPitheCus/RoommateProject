// YutHandler.java
// 도파민 윷놀이 명령 처리 (12주차). 규칙 판정은 YutGame, 이 클래스는 매칭 / 친구 초대 / 게임 알림 / 20초 타이머
// 12주차 수정: 처음엔 단체방 안에서 했는데, 게임은 게임 탭에서만 하도록 바꿈
//   무조건 4명. 기본은 같은 성별 랜덤(익명) 매칭이고, 친구를 초대해서 같이 들어갈 수도 있음
//   친구 = 내가 찜한 사람 + 1:1 대화한 사람 (접속 중이어야 초대 가능)
//
//   /YUT_FRIENDS                  초대할 수 있는 친구 목록
//   /YUT_INVITE 닉                친구 초대 (파티가 없으면 내가 방장인 파티를 새로 만듦)
//   /YUT_ACCEPT 방장닉, /YUT_DECLINE 방장닉     초대 받은 사람
//   /YUT_PARTY_LEAVE              파티에서 나가기
//   /YUT_QUEUE                    (방장) 시작. 4명이면 바로, 모자라면 빈자리를 랜덤 매칭으로 채움
//   /YUT_CANCEL                   매칭 취소
//   /YUT_THROW, /YUT_MOVE 말번호 결과, /YUT_LEAVE(기권, 등수가 정해진 사람은 그냥 나가기)      게임 중
// 누가 말을 다 들여보내면 /YUT_RANK 이름 등수, 2등까지 나오면 /YUT_END 게임 1등,2등 이유
//
// 게임 안에서는 모두 "토끼", "펭귄" 같은 동물 이름으로 부름 (판 정보는 모두에게 똑같이 보냄)
// 시작할 때 /YUT_MATCH로 사람마다 "누가 누구인지" 표를 따로 줌. 같은 파티 친구면 닉네임, 아니면 -
// 화면에는 모두 "플레이어1~4"(차례 순서)로 보이고, 친구만 그 밑에 (닉네임)이 붙음
// 파티, 대기열, 게임은 전부 이 객체 하나로 잠금(synchronized). 사용자 스레드와 타이머 스레드가 같이 건드리기 때문
// 실패는 /YUT_FAIL 이유

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class YutHandler implements CommandHandler {

    static final int TURN_SECONDS = 20;
    static final String[] ANIMALS = { "토끼", "고양이", "다람쥐", "펭귄", "여우", "판다", "수달", "햄스터" };

    // 같이 들어가는 묶음. 혼자 시작해도 1명짜리 파티를 만듦
    static class Party {
        UserService leader;
        final List<UserService> members = new ArrayList<>();
        final LinkedHashSet<UserService> pending = new LinkedHashSet<>(); // 초대했는데 아직 답 안 한 사람
        boolean queued;

        Party(UserService leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    // 진행 중인 게임 하나
    static class Session {
        final String id;
        final YutGame game;
        final Map<String, UserService> byName = new LinkedHashMap<>(); // 동물 이름 -> 사람
        final Map<String, Party> partyOf = new HashMap<>();            // 동물 이름 -> 원래 파티 (친구끼리 알아보기용)
        int version; // 20초 타이머 구분용. 행동이 있을 때마다 1씩 올라가서 예전 타이머는 무시됨

        Session(String id, YutGame game) {
            this.id = id;
            this.game = game;
        }
    }

    private final ServerContext ctx;
    private final Random rnd = new Random();
    private final Map<UserService, Party> parties = new HashMap<>();
    private final Map<UserService, Session> playing = new HashMap<>();
    private final List<Party> queue = new ArrayList<>(); // 먼저 들어온 순서
    private int nextId = 1;
    // 20초 타이머. 데몬 스레드라 서버를 끄면 같이 꺼짐
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "yut-timer");
        t.setDaemon(true);
        return t;
    });

    YutHandler(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public String[] commands() {
        return new String[] { "/YUT_FRIENDS", "/YUT_INVITE", "/YUT_ACCEPT", "/YUT_DECLINE", "/YUT_PARTY_LEAVE",
                "/YUT_QUEUE", "/YUT_CANCEL", "/YUT_THROW", "/YUT_MOVE", "/YUT_LEAVE" };
    }

    @Override
    public synchronized void handle(UserService u, String cmd, String[] args) {
        String arg = args.length > 1 ? args[1] : "";
        String rest = args.length > 2 ? arg + " " + args[2] : arg;
        try {
            switch (cmd) {
                case "/YUT_FRIENDS":     u.WriteOne("/YUT_FRIENDS " + friendList(u)); break;
                case "/YUT_INVITE":      invite(u, arg); break;
                case "/YUT_ACCEPT":      accept(u, arg); break;
                case "/YUT_DECLINE":     decline(u, arg); break;
                case "/YUT_PARTY_LEAVE": leaveParty(u); break;
                case "/YUT_QUEUE":       enqueue(u); break;
                case "/YUT_CANCEL":      cancel(u); break;
                case "/YUT_THROW":       doThrow(session(u), name(u)); break;
                case "/YUT_MOVE":        doMove(session(u), name(u), rest); break;
                case "/YUT_LEAVE":       forfeit(u); break;
            }
        } catch (YutException e) {
            u.WriteOne("/YUT_FAIL " + e.getMessage());
        }
    }

    // ===================== 친구 =====================

    // 찜했거나 1:1 대화를 한 사람
    private boolean isFriend(UserService me, UserService other) {
        String a = me.account().id, b = other.account().id;
        if (ctx.store.isLiked(a, b)) return true;
        for (ServerStore.Dm d : ctx.store.lastPerPartner(a)) {
            if (d.from.equals(b) || d.to.equals(b)) return true;
        }
        return false;
    }

    // 다른 파티에 있거나, 매칭 중이거나, 게임 중이면 바쁨
    private boolean busy(UserService u) {
        if (playing.containsKey(u)) return true;
        Party p = parties.get(u);
        return p != null && (p.queued || p.members.size() > 1 || !p.pending.isEmpty());
    }

    // 닉:가능|바쁨|초대중,...  (같은 성별, 접속 중, 이미 내 파티에 있는 사람은 뺌)
    private String friendList(UserService me) {
        Party mine = parties.get(me);
        StringBuilder sb = new StringBuilder();
        for (UserService o : ctx.usersCopy()) {
            if (o == me || !o.gender().equals(me.gender()) || !isFriend(me, o)) continue;
            if (mine != null && mine.members.contains(o)) continue;
            String state = mine != null && mine.pending.contains(o) ? "초대중" : busy(o) ? "바쁨" : "가능";
            if (sb.length() > 0) sb.append(",");
            sb.append(o.nick()).append(":").append(state);
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private UserService findUser(UserService me, String nick) throws YutException {
        for (UserService o : ctx.usersCopy()) {
            if (o.nick().equals(nick) && o.gender().equals(me.gender())) return o; // 다른 성별은 없는 사람처럼
        }
        throw new YutException("없는사용자");
    }

    // ===================== 파티 =====================

    // 내 파티 (없으면 1명짜리로 새로 만듦)
    private Party myParty(UserService u) {
        return parties.computeIfAbsent(u, Party::new);
    }

    private void invite(UserService u, String nick) throws YutException {
        if (playing.containsKey(u)) throw new YutException("이미게임중");
        UserService target = findUser(u, nick);
        if (target == u) throw new YutException("형식오류");
        if (!isFriend(u, target)) throw new YutException("친구아님");
        Party p = myParty(u);
        if (p.leader != u) throw new YutException("방장아님");
        if (p.queued) throw new YutException("매칭중");
        if (p.members.contains(target) || p.pending.contains(target)) throw new YutException("이미초대");
        if (busy(target)) throw new YutException("상대바쁨");
        if (p.members.size() + p.pending.size() >= YutGame.MAX_PLAYERS) throw new YutException("인원초과");
        p.pending.add(target);
        target.WriteOne("/YUT_INVITED " + u.nick());
        partyState(p);
    }

    private Party invitedBy(UserService u, String leaderNick) throws YutException {
        for (Party p : new LinkedHashSet<>(parties.values())) {
            if (p.leader.nick().equals(leaderNick) && p.pending.contains(u)) return p;
        }
        throw new YutException("초대만료");
    }

    private void accept(UserService u, String leaderNick) throws YutException {
        if (playing.containsKey(u)) throw new YutException("이미게임중");
        Party p = invitedBy(u, leaderNick);
        p.pending.remove(u);
        Party old = parties.get(u);
        if (old != null) leaveParty(u); // 혼자 만들어 둔 파티가 있었으면 정리하고 넘어감
        p.members.add(u);
        parties.put(u, p);
        partyState(p);
    }

    private void decline(UserService u, String leaderNick) throws YutException {
        Party p = invitedBy(u, leaderNick);
        p.pending.remove(u);
        p.leader.WriteOne("/YUT_DECLINED " + u.nick());
        partyState(p);
    }

    // 방장이 나가면 다음 사람이 방장. 매칭 중이었으면 매칭도 취소
    private void leaveParty(UserService u) {
        Party p = parties.remove(u);
        if (p == null) return;
        if (p.queued) unqueue(p);
        p.members.remove(u);
        u.WriteOne("/YUT_PARTY -");
        if (p.leader == u) {
            for (UserService inv : p.pending) inv.WriteOne("/YUT_INVITE_GONE " + u.nick());
            p.pending.clear();
            if (!p.members.isEmpty()) p.leader = p.members.get(0);
        }
        if (!p.members.isEmpty()) partyState(p);
    }

    // /YUT_PARTY 방장 멤버들 초대중인사람들  (파티 사람 모두에게)
    private void partyState(Party p) {
        StringBuilder m = new StringBuilder(), w = new StringBuilder();
        for (UserService x : p.members) m.append(m.length() > 0 ? "," : "").append(x.nick());
        for (UserService x : p.pending) w.append(w.length() > 0 ? "," : "").append(x.nick());
        String msg = "/YUT_PARTY " + p.leader.nick() + " " + m + " " + (w.length() == 0 ? "-" : w.toString());
        for (UserService x : p.members) x.WriteOne(msg);
    }

    // ===================== 매칭 =====================

    private void enqueue(UserService u) throws YutException {
        if (playing.containsKey(u)) throw new YutException("이미게임중");
        Party p = myParty(u);
        if (p.leader != u) throw new YutException("방장아님");
        if (p.queued) throw new YutException("매칭중");
        for (UserService inv : p.pending) inv.WriteOne("/YUT_INVITE_GONE " + u.nick()); // 답 안 한 초대는 취소
        p.pending.clear();
        p.queued = true;
        queue.add(p);
        partyState(p);
        ctx.log("윷놀이 매칭 시작 " + u.nick() + " (" + p.members.size() + "명)");
        match(u.gender());
    }

    private void cancel(UserService u) throws YutException {
        Party p = parties.get(u);
        if (p == null || !p.queued) throw new YutException("매칭안함");
        unqueue(p);
        for (UserService x : p.members) x.WriteOne("/YUT_CANCELED " + u.nick());
        if (p.members.size() == 1) parties.remove(u); // 혼자였으면 파티도 정리
        else partyState(p);
    }

    private void unqueue(Party p) {
        p.queued = false;
        queue.remove(p);
        queueState(p.leader.gender());
    }

    // 대기열을 4명짜리 자리(방)로 나눠 봄. 먼저 온 파티부터, 들어갈 수 있는 첫 자리에 넣음
    // 매칭 중인 사람은 자기 자리가 몇 명 찼는지 실시간으로 보고(/YUT_QUEUED), 4명이 차면 바로 시작
    private List<List<Party>> groups(String gender) {
        List<List<Party>> bins = new ArrayList<>();
        List<Integer> size = new ArrayList<>();
        for (Party p : queue) {
            if (!p.leader.gender().equals(gender)) continue;
            int n = p.members.size();
            int at = -1;
            for (int b = 0; b < bins.size(); b++) {
                if (size.get(b) + n <= YutGame.MAX_PLAYERS) {
                    at = b;
                    break;
                }
            }
            if (at < 0) {
                bins.add(new ArrayList<>());
                size.add(0);
                at = bins.size() - 1;
            }
            bins.get(at).add(p);
            size.set(at, size.get(at) + n);
        }
        return bins;
    }

    private static int count(List<Party> bin) {
        int n = 0;
        for (Party p : bin) n += p.members.size();
        return n;
    }

    private void match(String gender) {
        boolean again = true;
        while (again) {
            again = false;
            for (List<Party> bin : groups(gender)) {
                if (count(bin) == YutGame.MAX_PLAYERS) {
                    start(bin);
                    again = true;
                    break;
                }
            }
        }
        queueState(gender);
    }

    // 매칭 중인 사람에게 "내 자리에 지금 몇 명 모였는지" 알림 (/YUT_QUEUED 모인인원)
    private void queueState(String gender) {
        for (List<Party> bin : groups(gender)) {
            int n = count(bin);
            for (Party p : bin) for (UserService x : p.members) x.WriteOne("/YUT_QUEUED " + n);
        }
    }

    private void start(List<Party> pick) {
        List<String> names = new ArrayList<>(Arrays.asList(ANIMALS));
        Collections.shuffle(names, rnd);
        List<UserService> people = new ArrayList<>();
        List<Party> from = new ArrayList<>();
        for (Party p : pick) {
            queue.remove(p);
            p.queued = false;
            for (UserService x : p.members) {
                people.add(x);
                from.add(p);
                parties.remove(x); // 게임에 들어가면 파티는 끝 (끝나고 다시 모임)
            }
        }
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

    synchronized void disconnected(UserService u) {
        try {
            if (playing.containsKey(u)) forfeit(u);
        } catch (YutException e) {
            // 이미 빠짐
        }
        leaveParty(u);
        // 나를 초대해 둔 파티에서도 빼기
        for (Party p : new LinkedHashSet<>(parties.values())) {
            if (p.pending.remove(u)) partyState(p);
        }
    }
}
