// Matchmaker.java
// 미니게임 같이 할 사람 모으기 (13주차). 윷놀이에서 쓰던 파티, 친구 초대, 랜덤 매칭을 할리갈리도 같이 쓰려고 따로 뺌
//   명령 이름 앞부분(접두어)만 게임마다 다름: 윷놀이 /YUT_, 할리갈리 /HG_
//   /X_FRIENDS                     초대할 수 있는 친구 목록 (찜했거나 1:1 대화한 사람, 접속 중, 같은 성별)
//   /X_INVITE 닉                   친구 초대 (파티가 없으면 내가 방장인 파티를 새로 만듦)
//   /X_ACCEPT 방장닉, /X_DECLINE 방장닉
//   /X_PARTY_LEAVE                 파티에서 나가기
//   /X_QUEUE                       (방장) 랜덤 매칭 시작. 대기열을 최대 인원짜리 자리로 나눠 담음
//   /X_CANCEL                      매칭 취소
//   /X_START_NOW                   (방장) 지금 인원으로 바로 시작. 최소 인원 이상일 때
//                                    매칭 전이면 파티(친구)만으로, 매칭 중이면 그 자리의 방장(제일 먼저 들어온 사람)이 모인 인원으로
// 시작은 게임 핸들러에 넘김 (Starter)
// 윷놀이: 자리가 4명으로 차면 바로 시작
// 할리갈리(13주차 수정): 2명째가 들어온 때부터 1분 안에 방장이 시작할 수 있고, 1분이 지나면 모인 인원으로 자동 시작
//   4명이 차면 그때부터 10초 안에 방장이 시작 안 하면 자동 시작
//   /X_QUEUED 모인인원 방장여부 남은초   남은초는 자동 시작까지 남은 시간 (해당 없으면 -1)
// 이 클래스는 자기 객체로 잠금. 게임 핸들러는 따로 잠그고, 여기서 게임 쪽은 잠금 없이 읽기만 함 (서로 기다리는 일 없게)

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class Matchmaker {

    // 같이 들어가는 묶음. 혼자 시작해도 1명짜리 파티를 만듦
    static class Party {
        UserService leader;
        final List<UserService> members = new ArrayList<>();
        final LinkedHashSet<UserService> pending = new LinkedHashSet<>(); // 초대했는데 아직 답 안 한 사람
        boolean queued;
        long readyAt;   // (자리의 첫 파티만) 자리에 최소 인원이 모인 시각. 0이면 아직
        long fullAt;    // (자리의 첫 파티만) 자리가 꽉 찬 시각. 0이면 아직

        Party(UserService leader) {
            this.leader = leader;
            members.add(leader);
        }
    }

    // 모인 사람으로 게임 시작 (people 순서대로 from에 원래 파티)
    interface Starter {
        void start(List<UserService> people, List<Party> from);
    }

    private final ServerContext ctx;
    final String prefix, label;  // "/YUT_", "윷놀이"
    final int min, max;          // 시작할 수 있는 인원
    private final Predicate<UserService> inGame; // 이 게임을 하는 중인지 (게임 핸들러가 잠금 없이 답함)
    private Starter starter;
    // 다른 스레드(다른 게임의 매칭)에서 잠금 없이 읽을 수 있게 ConcurrentHashMap. 바꾸는 건 이 객체 잠금 안에서만
    private final Map<UserService, Party> parties = new ConcurrentHashMap<>();
    private final List<Party> queue = new ArrayList<>(); // 먼저 들어온 순서
    private long waitMs;                                  // 최소 인원이 모인 뒤 자동 시작까지 (0이면 최대 인원이 찰 때까지 기다림, 윷놀이)
    private long fullWaitMs;                              // 꽉 찬 뒤 자동 시작까지 (0이면 차자마자 시작)

    Matchmaker(ServerContext ctx, String prefix, String label, int min, int max, Predicate<UserService> inGame) {
        this.ctx = ctx;
        this.prefix = prefix;
        this.label = label;
        this.min = min;
        this.max = max;
        this.inGame = inGame;
    }

    void setStarter(Starter s) {
        starter = s;
    }

    // 최소 인원이 모이고 ms가 지나면 모인 인원으로 시작, 꽉 차면 fullMs 뒤 시작. 0.5초마다 확인
    void setWait(long ms, long fullMs) {
        waitMs = ms;
        fullWaitMs = fullMs;
        java.util.concurrent.ScheduledExecutorService t = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, label + "-match");
            th.setDaemon(true);
            return th;
        });
        t.scheduleAtFixedRate(this::tick, 500, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private synchronized void tick() {
        java.util.Set<String> genders = new java.util.HashSet<>();
        for (Party p : queue) genders.add(p.leader.gender());
        long now = System.currentTimeMillis();
        for (String g : genders) {
            for (List<Party> bin : groups(g)) {
                Party first = bin.get(0);
                int n = count(bin);
                boolean full = n == max && first.fullAt > 0 && now - first.fullAt >= fullWaitMs;
                boolean waited = n >= min && first.readyAt > 0 && now - first.readyAt >= waitMs;
                if (full || waited) {
                    ctx.log(label + " 자동 시작 " + n + "명" + (full ? " (꽉 차고 " + fullWaitMs / 1000 + "초)" : " (" + waitMs / 1000 + "초)"));
                    start(bin);
                    queueState(g);
                    break; // 자리가 바뀌었으니 다음 확인 때 다시
                }
            }
        }
    }

    String[] commands() {
        String[] c = { "FRIENDS", "INVITE", "ACCEPT", "DECLINE", "PARTY_LEAVE", "QUEUE", "CANCEL", "START_NOW" };
        for (int i = 0; i < c.length; i++) c[i] = prefix + c[i];
        return c;
    }

    boolean handles(String cmd) {
        for (String c : commands()) if (c.equals(cmd)) return true;
        return false;
    }

    // 이 게임의 파티, 매칭, 게임 중 하나라도 들어가 있음 (다른 게임에서 바쁜지 볼 때 씀, 잠금 없이)
    boolean involved(UserService u) {
        return parties.containsKey(u) || inGame.test(u);
    }

    synchronized void handle(UserService u, String cmd, String arg) throws MatchException {
        switch (cmd.substring(prefix.length())) {
            case "FRIENDS":     u.WriteOne(prefix + "FRIENDS " + friendList(u)); break;
            case "INVITE":      invite(u, arg); break;
            case "ACCEPT":      accept(u, arg); break;
            case "DECLINE":     decline(u, arg); break;
            case "PARTY_LEAVE": leaveParty(u); break;
            case "QUEUE":       enqueue(u); break;
            case "CANCEL":      cancel(u); break;
            case "START_NOW":   startNow(u); break;
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

    // 이 게임에서 게임 중이거나 다른 파티에 묶여 있거나, 다른 미니게임을 하는 중이면 바쁨
    private boolean busy(UserService u) {
        if (inGame.test(u) || ctx.busyElsewhere(u, this)) return true;
        Party p = parties.get(u);
        return p != null && (p.queued || p.members.size() > 1 || !p.pending.isEmpty());
    }

    // 닉:가능|바쁨|초대중,...
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

    private UserService findUser(UserService me, String nick) throws MatchException {
        for (UserService o : ctx.usersCopy()) {
            if (o.nick().equals(nick) && o.gender().equals(me.gender())) return o; // 다른 성별은 없는 사람처럼
        }
        throw new MatchException("없는사용자");
    }

    // 내가 이 게임을 새로 시작할 수 있는지
    private void checkFree(UserService u) throws MatchException {
        if (inGame.test(u)) throw new MatchException("이미게임중");
        if (ctx.busyElsewhere(u, this)) throw new MatchException("다른게임중");
    }

    // ===================== 파티 =====================

    private Party myParty(UserService u) {
        return parties.computeIfAbsent(u, Party::new);
    }

    private void invite(UserService u, String nick) throws MatchException {
        checkFree(u);
        UserService target = findUser(u, nick);
        if (target == u) throw new MatchException("형식오류");
        if (!isFriend(u, target)) throw new MatchException("친구아님");
        Party p = myParty(u);
        if (p.leader != u) throw new MatchException("방장아님");
        if (p.queued) throw new MatchException("매칭중");
        if (p.members.contains(target) || p.pending.contains(target)) throw new MatchException("이미초대");
        if (busy(target)) throw new MatchException("상대바쁨");
        if (p.members.size() + p.pending.size() >= max) throw new MatchException("인원초과");
        p.pending.add(target);
        target.WriteOne(prefix + "INVITED " + u.nick());
        partyState(p);
    }

    private Party invitedBy(UserService u, String leaderNick) throws MatchException {
        for (Party p : new LinkedHashSet<>(parties.values())) {
            if (p.leader.nick().equals(leaderNick) && p.pending.contains(u)) return p;
        }
        throw new MatchException("초대만료");
    }

    private void accept(UserService u, String leaderNick) throws MatchException {
        checkFree(u);
        Party p = invitedBy(u, leaderNick);
        p.pending.remove(u);
        if (parties.get(u) != null) leaveParty(u); // 혼자 만들어 둔 파티가 있었으면 정리하고 넘어감
        p.members.add(u);
        parties.put(u, p);
        partyState(p);
    }

    private void decline(UserService u, String leaderNick) throws MatchException {
        Party p = invitedBy(u, leaderNick);
        p.pending.remove(u);
        p.leader.WriteOne(prefix + "DECLINED " + u.nick());
        partyState(p);
    }

    // 방장이 나가면 다음 사람이 방장. 매칭 중이었으면 매칭도 취소
    private void leaveParty(UserService u) {
        Party p = parties.remove(u);
        if (p == null) return;
        if (p.queued) unqueue(p);
        p.members.remove(u);
        u.WriteOne(prefix + "PARTY -");
        if (p.leader == u) {
            for (UserService inv : p.pending) inv.WriteOne(prefix + "INVITE_GONE " + u.nick());
            p.pending.clear();
            if (!p.members.isEmpty()) p.leader = p.members.get(0);
        }
        if (!p.members.isEmpty()) partyState(p);
    }

    // /X_PARTY 방장 멤버들 초대중인사람들
    private void partyState(Party p) {
        StringBuilder m = new StringBuilder(), w = new StringBuilder();
        for (UserService x : p.members) m.append(m.length() > 0 ? "," : "").append(x.nick());
        for (UserService x : p.pending) w.append(w.length() > 0 ? "," : "").append(x.nick());
        String msg = prefix + "PARTY " + p.leader.nick() + " " + m + " " + (w.length() == 0 ? "-" : w.toString());
        for (UserService x : p.members) x.WriteOne(msg);
    }

    // ===================== 매칭 =====================

    private void enqueue(UserService u) throws MatchException {
        checkFree(u);
        Party p = myParty(u);
        if (p.leader != u) throw new MatchException("방장아님");
        if (p.queued) throw new MatchException("매칭중");
        for (UserService inv : p.pending) inv.WriteOne(prefix + "INVITE_GONE " + u.nick()); // 답 안 한 초대는 취소
        p.pending.clear();
        p.queued = true;
        queue.add(p);
        partyState(p);
        ctx.log(label + " 매칭 시작 " + u.nick() + " (" + p.members.size() + "명)");
        match(u.gender());
    }

    private void cancel(UserService u) throws MatchException {
        Party p = parties.get(u);
        if (p == null || !p.queued) throw new MatchException("매칭안함");
        unqueue(p);
        for (UserService x : p.members) x.WriteOne(prefix + "CANCELED " + u.nick());
        if (p.members.size() == 1) parties.remove(u); // 혼자였으면 파티도 정리
        else partyState(p);
    }

    // 방장이 지금 인원으로 바로 시작
    //   매칭 전: 파티(친구)만으로 / 매칭 중: 그 자리의 방장(제일 먼저 들어온 파티의 방장)이 모인 인원으로
    private void startNow(UserService u) throws MatchException {
        checkFree(u);
        Party p = parties.get(u);
        if (p == null) throw new MatchException("인원부족"); // 혼자
        if (p.leader != u) throw new MatchException("방장아님");
        List<Party> bin = null;
        if (p.queued) {
            for (List<Party> b : groups(u.gender())) if (b.contains(p)) bin = b;
            if (bin == null || bin.get(0) != p) throw new MatchException("방장아님");
        } else {
            for (UserService inv : p.pending) inv.WriteOne(prefix + "INVITE_GONE " + u.nick()); // 답 안 한 초대는 취소
            p.pending.clear();
            bin = new ArrayList<>();
            bin.add(p);
        }
        if (count(bin) < min) throw new MatchException("인원부족");
        start(bin);
        queueState(u.gender());
    }

    private void unqueue(Party p) {
        p.queued = false;
        queue.remove(p);
        queueState(p.leader.gender());
    }

    // 대기열을 최대 인원짜리 자리로 나눠 봄. 먼저 온 파티부터, 들어갈 수 있는 첫 자리에 넣음
    private List<List<Party>> groups(String gender) {
        List<List<Party>> bins = new ArrayList<>();
        List<Integer> size = new ArrayList<>();
        for (Party p : queue) {
            if (!p.leader.gender().equals(gender)) continue;
            int n = p.members.size();
            int at = -1;
            for (int b = 0; b < bins.size(); b++) {
                if (size.get(b) + n <= max) {
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
                if (count(bin) == max && fullWaitMs == 0) { // 꽉 차면 바로 (윷놀이). 할리갈리는 tick에서 10초 뒤
                    start(bin);
                    again = true;
                    break;
                }
            }
        }
        queueState(gender);
    }

    // 매칭 중인 사람에게 "/X_QUEUED 모인인원 방장여부(1|0) 남은초". 방장은 그 자리에 제일 먼저 들어온 파티의 방장
    // 자리에 최소 인원이 모인 시각, 꽉 찬 시각도 여기서 기록 (줄면 다시 0)
    private void queueState(String gender) {
        long now = System.currentTimeMillis();
        for (List<Party> bin : groups(gender)) {
            int n = count(bin);
            Party first = bin.get(0);
            for (Party p : bin) {
                if (p == first) continue;
                p.readyAt = 0;
                p.fullAt = 0;
            }
            if (waitMs > 0 && n >= min) {
                if (first.readyAt == 0) first.readyAt = now;
            } else first.readyAt = 0;
            if (fullWaitMs > 0 && n == max) {
                if (first.fullAt == 0) first.fullAt = now;
            } else first.fullAt = 0;
            long left = -1;
            if (first.readyAt > 0) left = waitMs - (now - first.readyAt);
            if (first.fullAt > 0) left = Math.min(left < 0 ? Long.MAX_VALUE : left, fullWaitMs - (now - first.fullAt));
            long sec = left < 0 ? -1 : Math.max(0, (left + 999) / 1000);
            for (Party p : bin) {
                for (UserService x : p.members) x.WriteOne(prefix + "QUEUED " + n + " " + (p == first && x == p.leader ? 1 : 0) + " " + sec);
            }
        }
    }

    private void start(List<Party> pick) {
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
        starter.start(people, from);
    }

    // 연결이 끊김
    synchronized void disconnected(UserService u) {
        leaveParty(u);
        for (Party p : new LinkedHashSet<>(parties.values())) {
            if (p.pending.remove(u)) partyState(p);
        }
    }
}
