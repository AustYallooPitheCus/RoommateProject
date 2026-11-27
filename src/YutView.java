// YutView.java
// 도파민 윷놀이 게임 판 (12주차). 게임 탭의 윷놀이 화면(YutScreen) 안에 들어감
//   위: 참가자 4명 칸. 이름 밑에 말 4개를 1x4로 (꽉 찬 원 = 대기, 빈 원 = 판 위, 금색 체크 = 완주)
//   가운데: 윷판 (출발점 0번이 오른쪽 아래)
//   아래: 윷가락, 알림, 남은 결과, [던지기], 내 말 4개 (대기 중인 말을 누르면 새 말을 올릴 칸이 보임)
// 서버가 판정한 결과(/YUT_...)를 받아서 그리기만 함. 내가 보내는 건 던지기, 옮기기뿐
// 서버는 판 정보를 동물 이름(토끼, 펭귄...)으로 보내고, /YUT_MATCH로 받은 표로 화면에 보일 이름을 바꿈
//   화면에는 모두 차례 순서대로 "플레이어1~4". 같은 파티 친구는 그 밑에 (닉네임), 나는 (나)
//
// 말 옮기기: 내 말(판 위 말이나 아래 대기 말)을 누르면 남은 결과마다 갈 칸이 반투명으로 뜨고, 그 칸을 누르면 옮김
// 모션 (12주차): 받은 메시지를 줄에 세워두고, 모션이 끝나야 다음 메시지를 화면에 반영함
//   던지기  윷가락 4개가 날아올랐다 떨어지고 결과 글자가 튀어나옴
//   이동    한 칸씩 콩콩 뛰어서 이동. 새 말은 내 말 자리에서 날아와 판에 올라감
//   잡기    도착하면서 상대 말을 밀쳐내고, 밀려난 말은 주인 자리로 날아감
//   지옥    불길이 솟으면서 말이 구덩이로 빠짐
//   잉태    우물에서 대기 말 하나가 튀어나와 업힘
//   완주    출발점을 지나 내 말 자리로 돌아가서 금색 체크

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;

public class YutView extends JPanel implements GameView {

    private static final long serialVersionUID = 1L;
    // 말 색 (판의 보라색 칸과 헷갈리지 않게 진한 색으로)
    static final Color[] PLAYER = { new Color(0x3D7BF0), new Color(0xE5508F), new Color(0x13A389), new Color(0xF29A12) };
    static final Color HELL = new Color(0xD9483B), BIRTH = new Color(0x2E9B74), GOLD = new Color(0xF2B829);
    static final int BOARD = 272;
    static final int PIECE_R = 12;                  // 판 위 말 반지름
    static final int FLY_MS = 900, ANIM_MS = 1500;  // 던지기: 날아가는 시간, 결과 글자까지 전체 시간
    static final int STEP_MS = 190, JUMP_MS = 380;  // 이동: 한 칸, 판 밖에서 날아오는 구간

    // 윷판 칸 좌표 (300 x 300 기준)
    static final int[][] XY = new int[29][];
    static {
        for (int i = 0; i <= 5; i++) XY[i] = new int[] { 280, 280 - 52 * i };
        for (int i = 6; i <= 10; i++) XY[i] = new int[] { 280 - 52 * (i - 5), 20 };
        for (int i = 11; i <= 15; i++) XY[i] = new int[] { 20, 20 + 52 * (i - 10) };
        for (int i = 16; i <= 19; i++) XY[i] = new int[] { 20 + 52 * (i - 15), 280 };
        int[][] d1 = { { 237, 63 }, { 193, 107 }, { 150, 150 }, { 107, 193 }, { 63, 237 } };
        for (int k = 0; k < 5; k++) XY[20 + k] = d1[k];
        XY[25] = new int[] { 63, 63 };
        XY[26] = new int[] { 107, 107 };
        XY[27] = new int[] { 193, 193 };
        XY[28] = new int[] { 237, 237 };
    }

    private final AppFrame app;
    private Runnable changed = () -> { };

    // ===== 상태 (서버 메시지로만 바뀜) =====
    private String gid, me = "";              // 게임 번호, 이 게임에서 내 이름(동물)
    private final Map<String, String> names = new LinkedHashMap<>(); // 동물 이름 -> 화면에 보일 이름
    private final List<String> players = new ArrayList<>();
    private final Set<String> out = new HashSet<>(); // 나간 사람 (색이 안 바뀌게 목록에서는 안 뺌)
    private boolean started;
    private final Map<String, int[]> pos = new LinkedHashMap<>();
    private final Map<String, int[]> prevs = new LinkedHashMap<>(); // 말마다 바로 전 칸 (미리보기, 이동 모션용)
    private final List<String> results = new ArrayList<>();
    private String current = "";
    private int throwsLeft;
    private int hellCell = YutGame.HELL, birthCell = YutGame.BIRTH;
    private String lastResult = "", sticks = "";
    private String log = "";
    private String endText, endSub = "";      // 끝났으면 "1등 했어요!" 같은 글, 그 아래 1등 2등
    private final Map<String, Integer> ranks = new LinkedHashMap<>(); // 말을 다 들여보낸 사람 -> 등수
    private boolean rankPopup;                // 내가 1등 했을 때 [계속 구경] [나가기] 카드
    private int seconds;
    private final Timer tick;

    // ===== 말 고르기 =====
    private Integer picked;                   // 고른 내 말이 있는 칸 (대기 말이면 WAIT, 안 골랐으면 null)
    private Integer follow;                   // 방금 옮긴 칸. 다음 /YUT_BOARD가 오면 그 말을 계속 골라둠

    // 반투명 미리보기 칸 하나
    static class Ghost {
        final int cell;                       // 도착 칸 (완주면 DONE)
        final String result;
        final int x, y;

        Ghost(int cell, String result, int x, int y) {
            this.cell = cell;
            this.result = result;
            this.x = x;
            this.y = y;
        }
    }

    // ===== 모션 =====
    // 메시지 줄. 모션이 도는 동안 온 메시지는 여기서 기다렸다가 차례로 반영
    private final ArrayDeque<String[]> queue = new ArrayDeque<>();
    private long busyUntil;                   // 이 시각까지는 모션 중
    private long animStart = -1;              // 던지기 모션 시작 시각 (-1이면 없음)
    private boolean throwResultSound;         // 던지기 결과 소리를 냈는지
    private MoveAnim mv;                      // 이동 모션 (없으면 null)
    private final Timer frame;

    // 이동 모션 하나 (/YUT_MOVED 하나)
    static class MoveAnim {
        String nick;
        int stepsDone = 0;                    // 소리: 지금까지 내려앉은 칸 수
        boolean capSound, hellSound, birthSound;
        final List<Integer> ids = new ArrayList<>();
        int to;
        final List<double[]> pts = new ArrayList<>();  // 지나가는 점 (처음 = 출발 점)
        final List<Integer> segMs = new ArrayList<>(); // 구간마다 걸리는 시간
        final List<Integer> hop = new ArrayList<>();   // 구간마다 뛰는 높이
        final List<String> victims = new ArrayList<>();          // 잡힌 사람
        final List<List<Integer>> victimIds = new ArrayList<>(); // 잡힌 말 번호
        boolean hell, done;
        int birthId = -1;
        long t0;
        int walk, cap, fx, total;
    }

    // ===== 부품 =====
    private final JButton throwBtn = UI.pill("던지기", UI.LAVENDER, Color.WHITE);
    private final JButton closeBtn = UI.pill("확인", UI.LAVENDER, Color.WHITE);
    private final JButton watchBtn = UI.pill("더 구경하기", UI.LAVENDER, Color.WHITE), leaveBtn = UI.soft("나가기");
    private int boardX, boardY, trayX, trayY;

    public YutView(AppFrame app) {
        this.app = app;
        setOpaque(false);
        setLayout(null);

        throwBtn.setFont(UI.font(Font.BOLD, 15f));
        add(throwBtn);
        add(closeBtn);
        closeBtn.setVisible(false);
        add(watchBtn);
        add(leaveBtn);
        watchBtn.addActionListener(e -> {
            rankPopup = false;
            refresh();
        });
        leaveBtn.addActionListener(e -> app.send("/YUT_LEAVE")); // 등수가 정해진 뒤 나가기는 게임에 영향 없음
        throwBtn.addActionListener(e -> app.send("/YUT_THROW"));
        closeBtn.addActionListener(e -> {
            endText = null;
            started = false;
            gid = null;
            changed.run();
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                click(e.getX(), e.getY());
            }
        });
        tick = new Timer(1000, e -> {
            if (seconds > 0) seconds--;
            repaint();
        });
        frame = new Timer(16, e -> {
            if (mv != null) moveSounds(mv);
            if (animStart >= 0 && !throwResultSound && animTime() >= FLY_MS) { // 윷가락이 떨어진 순간 결과 소리 (낙만)
                throwResultSound = true;
                if (lastResult.equals("낙")) Sound.play("nak");
            }
            if (!busy()) { // 모션 끝 -> 기다리던 메시지 반영
                animStart = -1;
                mv = null;
                ((Timer) e.getSource()).stop();
                drain();
            }
            repaint();
        });
    }

    public void onChange(Runnable r) {
        changed = r;
    }

    // 게임이 진행 중이거나, 끝나고 결과를 보여주는 중
    public boolean isStarted() {
        return started;
    }

    public boolean isOver() {
        return endText != null;
    }

    // 나는 이미 등수가 정해짐 (기권 대신 그냥 나가기)
    public boolean amRanked() {
        return ranks.containsKey(me);
    }

    // 로그아웃할 때
    public void reset() {
        gid = null;
        started = false;
        endText = null;
        players.clear();
        out.clear();
        ranks.clear();
        rankPopup = false;
        names.clear();
        pos.clear();
        prevs.clear();
        results.clear();
        queue.clear();
        picked = null;
        follow = null;
        log = "";
        tick.stop();
        frame.stop();
        Sound.stopBgm();
        busyUntil = 0;
        animStart = -1;
        mv = null;
        changed.run();
    }

    // 동물 이름 -> 화면에 보일 이름
    // 모두 차례 순서대로 "플레이어1~4". 알림 글에서 나는 "나"
    String show(String animal) {
        if (animal.equals(me)) return "나";
        return tag(animal);
    }

    private String tag(String animal) {
        int i = players.indexOf(animal);
        return i < 0 ? "플레이어" : "플레이어" + (i + 1);
    }

    // 플레이어N 밑에 붙는 작은 글: 나는 (나), 같은 파티 친구는 (닉네임), 모르는 사람은 없음
    private String sub(String animal) {
        if (animal.equals(me)) return "(나)";
        String n = names.get(animal);
        return n == null || n.equals("-") ? null : "(" + n + ")";
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        boardX = (w - BOARD) / 2;
        boardY = 74; // 위 참가자 칸이 두 줄(플레이어N, 친구 닉네임)이라 조금 내림
        int by = boardY + BOARD + 118;
        throwBtn.setBounds(12, by, (w - 36) / 2, 44);
        trayX = 24 + (w - 36) / 2;
        trayY = by;
        closeBtn.setBounds((w - 160) / 2, h / 2 + 30, 160, 42);
        leaveBtn.setBounds(w / 2 - 128, boardY + 200, 120, 42);
        watchBtn.setBounds(w / 2 + 8, boardY + 200, 120, 42);
    }

    // ===================== 서버 메시지 =====================

    // /YUT_MATCH 게임번호 내이름 이름=보일이름,...
    public void match(String rest) {
        String[] a = rest.split(" ", 3);
        gid = a[0];
        me = a[1];
        names.clear();
        queue.clear();
        for (String kv : a[2].split(",")) {
            int k = kv.indexOf('=');
            if (k > 0) names.put(kv.substring(0, k), kv.substring(k + 1));
        }
    }

    // cmd: /YUT_START 등, rest: 게임번호 뒤 부분. 줄에 세우고, 모션 중이 아니면 바로 반영
    public void onMessage(String cmd, String id, String rest) {
        if (gid == null || !gid.equals(id)) return; // 예전 게임 메시지
        queue.add(new String[] { cmd, rest });
        drain();
    }

    private boolean busy() {
        return System.currentTimeMillis() < busyUntil;
    }

    // 모션이 안 돌 때까지 줄에 있는 메시지를 차례로 반영
    private void drain() {
        while (!busy() && !queue.isEmpty()) {
            String[] m = queue.poll();
            apply(m[0], m[1]);
        }
        refresh();
    }

    private void startAnim(int ms) {
        busyUntil = System.currentTimeMillis() + ms;
        frame.restart();
    }

    private void apply(String cmd, String rest) {
        String[] a = rest.split(" ");
        switch (cmd) {
            case "/YUT_START": // 순서 말수 지옥 잉태
                started = true;
                Sound.startBgm();
                endText = null;
                players.clear();
                out.clear();
                ranks.clear();
                rankPopup = false;
                prevs.clear();
                picked = null;
                players.addAll(Arrays.asList(a[0].split(",")));
                hellCell = Integer.parseInt(a[2]);
                birthCell = Integer.parseInt(a[3]);
                log = "게임 시작! " + who(players.get(0)) + "부터";
                lastResult = "";
                sticks = "";
                current = "";
                break;
            case "/YUT_BOARD": // 닉:위치,...;... 남은결과
                pos.clear();
                for (String p : a[0].split(";")) {
                    String[] kv = p.split(":");
                    String[] ps = kv[1].split(",");
                    int[] v = new int[ps.length];
                    for (int i = 0; i < ps.length; i++) v[i] = Integer.parseInt(ps[i]);
                    pos.put(kv[0], v);
                    // 전 칸 표 크기 맞추기. 잡혔거나 지옥에 빠져서 대기로 간 말은 전 칸도 대기
                    int[] old = prevs.get(kv[0]);
                    int[] pv = new int[v.length];
                    for (int i = 0; i < v.length; i++) {
                        pv[i] = v[i] == YutGame.WAIT ? YutGame.WAIT : old != null && i < old.length ? old[i] : -2;
                    }
                    prevs.put(kv[0], pv);
                }
                setResults(a[1]);
                if (follow != null) {
                    picked = follow;
                    follow = null;
                }
                break;
            case "/YUT_TURN": // 닉 초
                if (!a[0].equals(current)) {
                    current = a[0];
                    throwsLeft = 1;
                    results.clear();
                    picked = null;
                    if (current.equals(me)) {
                        log = "내 차례예요! 윷을 던져요";
                        Sound.play("turn");
                    }
                }
                seconds = Integer.parseInt(a[1]);
                tick.restart();
                break;
            case "/YUT_THROWN": { // 닉 결과 윷가락 남은결과
                lastResult = a[1];
                sticks = a[2];
                throwsLeft--;
                if (a[1].equals("윷") || a[1].equals("모")) throwsLeft++;
                setResults(a[3]);
                String nakText = results.isEmpty() ? "! 차례가 끝나요" : "! 앞에 나온 " + String.join(", ", results) + "만 써요";
                log = show(a[0]) + ": " + a[1] + (a[1].equals("윷") || a[1].equals("모") ? "! 한 번 더" : a[1].equals("낙") ? nakText : "");
                animStart = System.currentTimeMillis();
                throwResultSound = false;
                Sound.play("throw");
                startAnim(ANIM_MS);
                break;
            }
            case "/YUT_MOVED": { // 닉 말들 출발 도착 결과 사건
                int from = Integer.parseInt(a[2]), to = Integer.parseInt(a[3]);
                mv = buildMove(a[0], a[1], from, to, a[4], a[5]);
                trackPrev(a[0], a[1], from, a[4]);
                StringBuilder sb = new StringBuilder(show(a[0]) + " " + a[4] + (a[1].contains(",") ? " (업어서 " + a[1].split(",").length + "개)" : ""));
                boolean sent = false;
                for (String ev : a[5].split("\\|")) {
                    if (ev.startsWith("잡기")) {
                        sb.append(" / ").append(show(ev.split(":")[1])).append(" 말을 잡았어요! 한 번 더");
                        if (a[0].equals(current)) throwsLeft++;
                    } else if (ev.equals("지옥")) {
                        sb.append(" / 불구덩이에 빠져서 출발점으로...");
                        sent = true;
                    } else if (ev.startsWith("잉태")) sb.append(" / 잉태! 우물에서 말이 하나 나왔어요");
                    else if (ev.equals("완주")) sb.append(" / 완주!");
                }
                log = sb.toString();
                // 내가 옮겼으면 그 말을 계속 골라둠 (이어서 다음 결과 칸이 보이게)
                if (a[0].equals(me)) follow = !sent && to >= 0 && to < XY.length ? Integer.valueOf(to) : null;
                picked = null;
                startAnim(mv.total);
                break;
            }
            case "/YUT_SKIP": // 닉 결과 이유
                log = a[1] + "는 판에 말이 없어서 넘어가요";
                break;
            case "/YUT_TIMEOUT":
                log = who(a[0]) + " 시간 초과! 자동으로 진행해요";
                break;
            case "/YUT_OUT":
                log = who(a[0]) + " 나갔어요";
                out.add(a[0]);
                pos.remove(a[0]);
                break;
            case "/YUT_RANK": // 이름 등수 (2등이 나올 때까지 게임은 계속)
                ranks.put(a[0], Integer.parseInt(a[1]));
                log = (a[0].equals(me) ? "내가" : show(a[0]) + "님이") + " " + a[1] + "등! 남은 사람끼리 2등을 가려요";
                if (a[0].equals(me)) rankPopup = true;
                Sound.play("rank");
                break;
            case "/YUT_END": // 1등,2등 이유
                tick.stop();
                Sound.stopBgm();
                if (a[1].equals("완주") || a[1].equals("기권")) Sound.play("rank");
                picked = null;
                rankPopup = false;
                if (a[1].equals("나감")) { // 내가 기권했거나, 등수가 정해진 뒤 나감
                    started = false;
                    endText = null;
                    gid = null;
                    queue.clear();
                } else if (a[1].equals("취소")) {
                    endText = "게임이 취소됐어요";
                    endSub = "";
                } else {
                    String[] r = a[0].split(",");
                    for (int i = 0; i < r.length; i++) ranks.put(r[i], i + 1);
                    Integer mine = ranks.get(me);
                    endText = mine == null ? "게임 끝!" : mine + "등 했어요!";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < r.length; i++) sb.append(i > 0 ? "  /  " : "").append(i + 1).append("등 ").append(show(r[i]));
                    endSub = sb + (a[1].equals("기권") ? "  (나머지가 나가서 끝)" : "");
                    log = "게임 끝";
                }
                break;
            default:
        }
    }

    // /YUT_MOVED 하나를 모션으로 바꿈. 지나가는 칸은 서버와 같은 YutGame.path로 계산
    private MoveAnim buildMove(String nick, String ids, int from, int to, String result, String events) {
        MoveAnim m = new MoveAnim();
        m.nick = nick;
        m.to = to;
        for (String s : ids.split(",")) m.ids.add(Integer.parseInt(s));
        int first = m.ids.get(0);
        int[] pv = prevs.get(nick);
        int before = pv != null && first < pv.length ? pv[first] : -2;
        List<Integer> path;
        try {
            path = YutGame.path(from, before, result);
        } catch (YutException e) {
            path = new ArrayList<>();
            path.add(to);
        }
        if (path.isEmpty() || path.get(path.size() - 1) != to) { // 혹시 계산이 다르면 도착 칸으로 바로
            path = new ArrayList<>();
            path.add(to);
        }
        m.pts.add(from == YutGame.WAIT ? slotPoint(nick, first) : pt(screen(from)));
        boolean fromTray = from == YutGame.WAIT;
        for (int i = 0; i < path.size(); i++) {
            int cell = path.get(i);
            if (cell == YutGame.DONE) {
                m.pts.add(pt(doneSpot()));
                m.segMs.add(STEP_MS);
                m.hop.add(12);
                m.pts.add(slotPoint(nick, first)); // 완주하면 내 말 자리로 돌아감
                m.segMs.add(JUMP_MS);
                m.hop.add(40);
                m.done = true;
            } else {
                m.pts.add(pt(screen(cell)));
                boolean jump = i == 0 && fromTray;
                m.segMs.add(jump ? JUMP_MS + 120 : STEP_MS);
                m.hop.add(jump ? 70 : 12);
            }
        }
        for (String ev : events.split("\\|")) {
            if (ev.startsWith("잡기:")) {
                String[] p = ev.split(":");
                m.victims.add(p[1]);
                List<Integer> v = new ArrayList<>();
                for (String s : p[2].split(",")) v.add(Integer.parseInt(s));
                m.victimIds.add(v);
            } else if (ev.equals("지옥")) m.hell = true;
            else if (ev.startsWith("잉태:")) m.birthId = Integer.parseInt(ev.substring(3));
        }
        for (int ms : m.segMs) m.walk += ms;
        m.cap = m.victims.isEmpty() ? 0 : 620;
        m.fx = m.hell ? 760 : m.birthId >= 0 ? 640 : 0;
        m.total = m.walk + m.cap + m.fx + 120;
        m.t0 = System.currentTimeMillis();
        return m;
    }

    // 이동 모션 중 소리: 한 칸 내려앉을 때마다 콩, 부딪혀 잡을 때, 불구덩이에 빠질 때
    private void moveSounds(MoveAnim m) {
        long e = System.currentTimeMillis() - m.t0;
        long acc = 0;
        int landed = 0;
        for (int ms : m.segMs) {
            acc += ms;
            if (e >= acc) landed++;
        }
        if (m.done) landed = Math.min(landed, m.segMs.size() - 1); // 내 자리로 날아 들어가는 구간은 콩 안 함
        m.stepsDone = landed; // 한 칸 소리는 뺌 (12주차 수정)
        if (!m.capSound && !m.victims.isEmpty() && e >= m.walk) {
            m.capSound = true;
            Sound.play("capture");
        }
        if (!m.hellSound && m.hell && e >= m.walk + m.cap) {
            m.hellSound = true;
            Sound.play("hell");
        }
        if (!m.birthSound && m.birthId >= 0 && e >= m.walk + m.cap) {
            m.birthSound = true;
            Sound.play("birth");
        }
    }

    private static double[] pt(int[] p) {
        return new double[] { p[0], p[1] };
    }

    // 옮긴 말들의 전 칸 기억 (서버와 같은 계산)
    private void trackPrev(String nick, String ids, int from, String result) {
        int[] pv = prevs.get(nick);
        if (pv == null) return;
        String[] list = ids.split(",");
        int first = Integer.parseInt(list[0]);
        int before = first < pv.length ? pv[first] : -2;
        int np;
        try {
            np = YutGame.dest(from, before, result)[1];
        } catch (YutException e) {
            return;
        }
        for (String s : list) {
            int i = Integer.parseInt(s);
            if (i < pv.length) pv[i] = np;
        }
    }

    private void setResults(String s) {
        results.clear();
        if (!s.equals("-")) results.addAll(Arrays.asList(s.split(",")));
    }

    // "내가", "플레이어3님이" 같은 말 앞부분
    private String who(String animal) {
        return animal.equals(me) ? "내가" : show(animal) + "님이";
    }

    private boolean myTurn() {
        return started && endText == null && current.equals(me);
    }

    private boolean canMove() {
        return myTurn() && throwsLeft <= 0 && !results.isEmpty() && !busy();
    }

    private long animTime() {
        return System.currentTimeMillis() - animStart;
    }

    // 그 칸(대기면 -1)에 있는 내 말 중 번호가 제일 작은 것
    private int firstPiece(String nick, int at) {
        int[] v = pos.get(nick);
        if (v == null) return -1;
        for (int i = 0; i < v.length; i++) if (v[i] == at) return i;
        return -1;
    }

    // 판 위에 내 말이 하나도 없는지 (그러면 대기 말을 자동으로 골라 줌)
    private boolean nothingOnBoard() {
        int[] v = pos.get(me);
        if (v == null) return true;
        for (int p : v) if (p >= 0 && p < XY.length) return false;
        return true;
    }

    private boolean onlyBack() {
        for (String r : results) if (!r.equals("빽도")) return false;
        return true;
    }

    private void refresh() {
        if (!canMove()) {
            if (!busy()) picked = null;
        } else {
            if (picked != null && firstPiece(me, picked) < 0) picked = null; // 그 칸에 내 말이 없어짐
            if (picked == null && nothingOnBoard() && firstPiece(me, YutGame.WAIT) >= 0 && !onlyBack()) picked = YutGame.WAIT;
        }
        boolean inGame = players.contains(me) && !out.contains(me) && !ranks.containsKey(me);
        throwBtn.setEnabled(myTurn() && throwsLeft > 0 && !busy());
        throwBtn.setVisible(started && endText == null && inGame);
        closeBtn.setVisible(endText != null);
        boolean popup = rankPopup && endText == null && !busy();
        watchBtn.setVisible(popup);
        leaveBtn.setVisible(popup);
        changed.run();
        repaint();
    }

    // ===================== 말 자리 =====================

    // 위쪽 참가자 칸에서 그 사람의 k번째 말 자리 (나는 아래 내 말 칸)
    private double[] slotPoint(String nick, int k) {
        if (nick.equals(me)) return trayPoint(k);
        int i = Math.max(0, players.indexOf(nick));
        int cw = (getWidth() - 16) / Math.max(1, players.size());
        return new double[] { 8 + i * cw + 15 + k * 14, 51 };
    }

    // 아래 내 말 칸 k번째 가운데
    private double[] trayPoint(int k) {
        return new double[] { trayX + 22 + k * 42, trayY + 22 };
    }

    // ===================== 미리보기 칸 =====================

    // 고른 말이 남은 결과마다 도착할 칸 (같은 결과는 한 번만)
    private List<Ghost> ghosts() {
        List<Ghost> list = new ArrayList<>();
        if (picked == null || !canMove()) return list;
        int at = picked;
        int id = firstPiece(me, at);
        if (id < 0) return list;
        int[] pv = prevs.get(me);
        int before = pv != null && id < pv.length ? pv[id] : -2;
        List<String> seen = new ArrayList<>();
        Map<Integer, Integer> sameCell = new LinkedHashMap<>();
        for (String r : results) {
            if (seen.contains(r)) continue;
            seen.add(r);
            if (r.equals("빽도") && at == YutGame.WAIT) continue; // 대기 중인 말은 뒤로 못 감
            int cell;
            try {
                cell = YutGame.dest(at, before, r)[0];
            } catch (YutException e) {
                continue;
            }
            int[] c = cell == YutGame.DONE ? doneSpot() : screen(cell);
            int k = sameCell.merge(cell, 1, Integer::sum) - 1; // 같은 칸이면 살짝 옆으로
            list.add(new Ghost(cell, r, c[0] + k * 14, c[1] - k * 14));
        }
        return list;
    }

    // 완주 미리보기 자리 (출발/도착 칸 오른쪽 바깥)
    private int[] doneSpot() {
        int[] s = screen(0);
        return new int[] { s[0] + 34, s[1] - 30 };
    }

    // ===================== 클릭 =====================

    private void click(int x, int y) {
        if (!canMove()) return;
        for (Ghost gh : ghosts()) { // 미리보기 칸을 누르면 그 결과로 옮김
            if (Math.hypot(x - gh.x, y - gh.y) > 15) continue;
            int id = firstPiece(me, picked);
            if (id >= 0) app.send("/YUT_MOVE " + id + " " + gh.result);
            return;
        }
        int[] mine = pos.get(me);
        if (mine != null) { // 아래 내 말 칸: 대기 중인 말을 누르면 새 말 올리기
            for (int k = 0; k < mine.length; k++) {
                double[] p = trayPoint(k);
                if (Math.hypot(x - p[0], y - p[1]) > 19) continue;
                if (mine[k] == YutGame.WAIT && !onlyBack()) {
                    picked = Integer.valueOf(YutGame.WAIT).equals(picked) ? null : YutGame.WAIT;
                    refresh();
                }
                return;
            }
        }
        for (int cell = 0; cell < XY.length; cell++) { // 판 위 내 말을 누르면 고르기 (다시 누르면 취소)
            int[] c = screen(cell);
            if (Math.hypot(x - c[0], y - c[1]) > 15) continue;
            if (firstPiece(me, cell) >= 0) {
                picked = Integer.valueOf(cell).equals(picked) ? null : cell;
                refresh();
            }
            return;
        }
    }

    private int[] screen(int cell) {
        double s = BOARD / 300.0;
        return new int[] { boardX + (int) (XY[cell][0] * s), boardY + (int) (XY[cell][1] * s) };
    }

    private Color colorOf(String nick) {
        int idx = players.indexOf(nick);
        return PLAYER[(idx >= 0 ? idx : 0) % PLAYER.length];
    }

    // ===================== 그리기 =====================

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Widgets.smooth(g2);
        int w = getWidth(), h = getHeight();
        g2.setColor(new Color(0xFBF9FF));
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 22, 22));
        g2.setColor(new Color(0xE6E1F5));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, 22, 22));

        drawChips(g2, w);
        drawBoard(g2);
        if (!busy()) drawGhosts(g2);
        if (mv != null) drawMove(g2);

        // ---- 윷가락 + 결과 + 알림 (던지기 모션 중에는 가려둠) ----
        boolean throwing = animStart >= 0;
        int y0 = boardY + BOARD + 8;
        if (!sticks.isEmpty() && !throwing) {
            for (int i = 0; i < 4; i++) drawStick(g2, 19 + i * 13, y0 + 20, 10, 40, 0, sticks.charAt(i) == '1', i == 0);
            g2.setColor(lastResult.equals("낙") ? HELL : UI.LAVENDER);
            g2.setFont(UI.font(Font.BOLD, 26f));
            g2.drawString(lastResult, 70, y0 + 30);
        }
        g2.setColor(UI.TEXT);
        g2.setFont(UI.font(Font.PLAIN, 12f));
        if (!throwing) drawWrapped(g2, log, 140, y0 + 14, w - 150);

        // 남은 결과
        int cy = y0 + 52;
        if (!results.isEmpty() && !throwing) {
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 11f));
            g2.drawString(current.equals(me) ? "쓸 결과" : "남은 결과", 14, cy + 19);
            int x = 70;
            for (String r : results) {
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(x, cy, 46, 28, 28, 28));
                g2.setColor(new Color(0xDAD5EE));
                g2.draw(new RoundRectangle2D.Float(x, cy, 46, 28, 28, 28));
                g2.setColor(UI.TEXT);
                g2.setFont(UI.font(Font.BOLD, 13f));
                int tw = g2.getFontMetrics().stringWidth(r);
                g2.drawString(r, x + (46 - tw) / 2, cy + 19);
                x += 52;
            }
            if (canMove()) {
                g2.setColor(UI.TEXT_SUB);
                g2.setFont(UI.font(Font.PLAIN, 10.5f));
                g2.drawString(picked == null ? "말을 누르면 갈 칸이 보여요" : "반투명 칸을 누르면 옮겨요", x + 4, cy + 18);
            }
        }

        if (started && players.contains(me) && endText == null) drawTray(g2, w);
        if (rankPopup && endText == null && !busy()) { // 내가 1등: 안내 + [나가기] [더 구경하기] 팝업
            g2.setColor(new Color(40, 30, 70, 90)); // 뒤 화면 어둡게
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 22, 22));
            float cx = w / 2f - 150, cyy = boardY + 30;
            g2.setColor(new Color(0, 0, 0, 30));
            g2.fill(new RoundRectangle2D.Float(cx, cyy + 4, 300, 232, 24, 24));
            g2.setColor(Color.WHITE);
            g2.fill(new RoundRectangle2D.Float(cx, cyy, 300, 232, 24, 24));
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(2f));
            g2.draw(new RoundRectangle2D.Float(cx, cyy, 300, 232, 24, 24));
            drawMedal(g2, w / 2.0, cyy + 54, 27, ranks.get(me));
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 24f));
            String t = ranks.get(me) + "등 했어요!";
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, cyy + 124);
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 12f));
            String t2 = "2등이 나올 때까지 게임은 계속돼요";
            g2.drawString(t2, (w - g2.getFontMetrics().stringWidth(t2)) / 2, cyy + 150);
        }
        if (throwing) drawThrow(g2);

        // 끝났을 때
        if (endText != null) {
            g2.setColor(new Color(255, 255, 255, 215));
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 22, 22));
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 24f));
            int tw = g2.getFontMetrics().stringWidth(endText);
            g2.drawString(endText, (w - tw) / 2, h / 2 - 20);
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 13f));
            tw = g2.getFontMetrics().stringWidth(endSub);
            g2.drawString(endSub, (w - tw) / 2, h / 2 + 8);
        }
        g2.dispose();
    }

    // 말 하나의 상태: 0 대기, 1 판 위, 2 완주. 이동 모션 중인 말은 판 위로 봄
    private int stateOf(String nick, int k) {
        int[] v = pos.get(nick);
        if (v == null || k >= v.length) return 0;
        if (mv != null && mv.nick.equals(nick) && mv.ids.contains(k)) // 완주 말은 내 자리에 도착한 뒤에 체크
            return mv.done && System.currentTimeMillis() - mv.t0 >= mv.walk ? 2 : 1;
        if (mv != null && mv.birthId == k && mv.nick.equals(nick)) return 1;
        if (v[k] == YutGame.WAIT) return 0;
        return v[k] == YutGame.DONE ? 2 : 1;
    }

    // ---- 위쪽 참가자 칸: 이름 + 말 4개 ----
    private void drawChips(Graphics2D g2, int w) {
        int cw = (w - 16) / Math.max(1, players.size());
        for (int i = 0; i < players.size(); i++) {
            String p = players.get(i);
            int x = 8 + i * cw;
            Color c = PLAYER[i % PLAYER.length];
            boolean gone = out.contains(p);
            boolean cur = p.equals(current) && endText == null;
            g2.setColor(cur ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 34) : new Color(255, 255, 255, 210));
            g2.fill(new RoundRectangle2D.Float(x, 6, cw - 6, 56, 14, 14));
            if (cur) {
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(x, 6, cw - 6, 56, 14, 14));
            }
            g2.setColor(gone ? UI.TEXT_SUB : UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 11f));
            g2.drawString(tag(p), x + 9, 22);
            String sb = sub(p);
            if (sb != null) { // (나), (친구 닉네임)
                g2.setColor(p.equals(me) ? colorOf(p) : UI.TEXT_SUB);
                float fs = 10f;
                g2.setFont(UI.font(Font.PLAIN, fs));
                while (fs > 8f && g2.getFontMetrics().stringWidth(sb) > cw - 16) g2.setFont(UI.font(Font.PLAIN, fs -= 0.5f));
                g2.drawString(sb, x + 9, 36);
            }
            Integer rk = ranks.get(p);
            if (rk != null) { // 등수 배지
                g2.setColor(rk == 1 ? GOLD : new Color(0xA9B4C2)); // 칸 테두리도 금색(2등은 은색)
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(x, 6, cw - 6, 56, 14, 14));
                drawMedal(g2, x + cw - 19, 19, 10.5, rk); // 등수 딱지
            }
            if (gone) {
                g2.setColor(UI.TEXT_SUB);
                g2.setFont(UI.font(Font.PLAIN, 10f));
                g2.drawString("나감", x + 9, 54);
                continue;
            }
            int[] v = pos.get(p);
            int n = v == null ? YutGame.START_PIECES : v.length;
            for (int k = 0; k < n; k++) {
                double[] s = slotPoint(p, k);
                if (p.equals(me)) s = new double[] { x + 15 + k * 14, 51 }; // 위 칸에도 내 말을 같이 보여줌
                drawSlot(g2, s[0], s[1], 5.5, c, stateOf(p, k), false);
            }
        }
        // 남은 시간 막대
        if (started && endText == null) {
            Color c = colorOf(current);
            g2.setColor(new Color(0xEAE6FA));
            g2.fillRoundRect(8, 66, w - 16, 4, 4, 4);
            g2.setColor(seconds <= 5 ? HELL : c);
            g2.fillRoundRect(8, 66, (int) ((w - 16) * seconds / (double) YutHandler.TURN_SECONDS), 4, 4, 4);
        }
    }

    // 말 칸 하나: 대기 = 꽉 찬 말, 판 위 = 점선 빈 원(글자 없이), 완주 = 금색 테두리 + 체크
    private void drawSlot(Graphics2D g2, double x, double y, double r, Color c, int state, boolean big) {
        if (state == 0) {
            drawToken(g2, x, y, r, c, 1, 1f);
        } else if (state == 1) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 30));
            g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 150));
            g2.setStroke(new BasicStroke(big ? 1.6f : 1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    big ? new float[] { 4f, 3f } : new float[] { 2f, 2f }, 0f));
            g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        } else {
            drawToken(g2, x, y, r, new Color(c.getRed(), c.getGreen(), c.getBlue()), 1, 0.45f);
            g2.setColor(GOLD);
            g2.setStroke(new BasicStroke(big ? 2.4f : 1.6f));
            g2.draw(new Ellipse2D.Double(x - r - 1, y - r - 1, r * 2 + 2, r * 2 + 2));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(big ? 2.6f : 1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Double ck = new Path2D.Double();
            ck.moveTo(x - r * 0.45, y);
            ck.lineTo(x - r * 0.1, y + r * 0.38);
            ck.lineTo(x + r * 0.5, y - r * 0.35);
            g2.draw(ck);
        }
    }

    // ---- 아래 내 말 4개 (대기 중인 말을 누르면 새 말 올리기) ----
    private void drawTray(Graphics2D g2, int w) {
        int[] v = pos.get(me);
        if (v == null) return;
        Color c = colorOf(me);
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(trayX, trayY, w - 12 - trayX, 44, 22, 22));
        g2.setColor(new Color(0xE2DDF2));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(trayX, trayY, w - 12 - trayX, 44, 22, 22));
        g2.setColor(UI.TEXT_SUB);
        g2.setFont(UI.font(Font.PLAIN, 10.5f));
        g2.drawString("내 말", trayX + 4, trayY - 5);
        boolean canPlace = canMove() && !onlyBack();
        int next = firstPiece(me, YutGame.WAIT);
        for (int k = 0; k < v.length; k++) {
            double[] p = trayPoint(k);
            int st = stateOf(me, k);
            if (st == 0 && canPlace) { // 올릴 수 있는 말 표시
                boolean on = Integer.valueOf(YutGame.WAIT).equals(picked) && k == next;
                g2.setColor(on ? c : new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
                g2.setStroke(on ? new BasicStroke(2.5f) : new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 3f, 3f }, 0f));
                g2.draw(new Ellipse2D.Double(p[0] - 18, p[1] - 18, 36, 36));
            }
            drawSlot(g2, p[0], p[1], 14, c, st, true);
        }
    }

    // 업힌 말: 숫자 대신 실제 개수만큼 작게 모아서 그림 (2개 나란히, 3개 삼각형, 4개 2x2)
    static void drawStack(Graphics2D g2, double x, double y, double r, Color c, int n, float alpha) {
        if (n <= 1) {
            drawToken(g2, x, y, r, c, 1, alpha);
            return;
        }
        double[][] off;
        double scale;
        if (n == 2) {
            off = new double[][] { { -0.55, 0 }, { 0.55, 0 } };
            scale = 0.78;
        } else if (n == 3) {
            off = new double[][] { { 0, -0.5 }, { -0.58, 0.4 }, { 0.58, 0.4 } };
            scale = 0.72;
        } else {
            off = new double[][] { { -0.55, -0.55 }, { 0.55, -0.55 }, { -0.55, 0.55 }, { 0.55, 0.55 } };
            scale = 0.66;
        }
        for (double[] o : off) drawToken(g2, x + o[0] * r, y + o[1] * r, r * scale, c, 1, alpha); // 위쪽 말부터 그려서 아래 말이 앞에 보이게
    }

    // 말 하나 (진한 색 + 흰 테두리 + 그림자). 판 칸과 헷갈리지 않게 굵게
    static void drawToken(Graphics2D g2, double x, double y, double r, Color c, int count, float alpha) {
        Composite oc = g2.getComposite();
        if (alpha < 1f) g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fill(new Ellipse2D.Double(x - r, y - r + r * 0.28, r * 2, r * 2));
        g2.setColor(c);
        g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g2.setColor(new Color(255, 255, 255, 70)); // 위쪽 빛
        g2.fill(new Ellipse2D.Double(x - r * 0.62, y - r * 0.82, r * 1.24, r * 0.75));
        g2.setColor(new Color(255, 255, 255, 140)); // 안쪽 고리
        g2.setStroke(new BasicStroke((float) Math.max(1, r / 8)));
        g2.draw(new Ellipse2D.Double(x - r * 0.52, y - r * 0.52, r * 1.04, r * 1.04));
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke((float) Math.max(1.2, r / 5)));
        g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
        g2.setColor(c.darker().darker());
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Ellipse2D.Double(x - r - 1.2, y - r - 1.2, r * 2 + 2.4, r * 2 + 2.4));
        if (count > 1) { // 업힌 개수
            double bx = x + r * 0.78, by = y - r * 0.78;
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Double(bx - 7.5, by - 7.5, 15, 15));
            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new Ellipse2D.Double(bx - 7.5, by - 7.5, 15, 15));
            g2.setFont(UI.font(Font.BOLD, 10f));
            String t = String.valueOf(count);
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, (float) (bx - tw / 2.0), (float) (by + 3.6));
        }
        g2.setComposite(oc);
    }

    // 고른 말 테두리 + 갈 수 있는 칸 (내 색 반투명 + 결과 글자)
    private void drawGhosts(Graphics2D g2) {
        if (picked == null || !canMove()) return;
        Color c = colorOf(me);
        if (picked >= 0) { // 고른 말 표시
            int[] s = screen(picked);
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Ellipse2D.Float(s[0] - 18, s[1] - 18, 36, 36));
        }
        for (Ghost gh : ghosts()) {
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 140));
            g2.fill(new Ellipse2D.Float(gh.x - 13, gh.y - 13, 26, 26));
            g2.setColor(c.darker());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 3f }, 0f));
            g2.draw(new Ellipse2D.Float(gh.x - 13, gh.y - 13, 26, 26));
            g2.setColor(Color.WHITE);
            String t = gh.cell == YutGame.DONE ? gh.result + "!" : gh.result;
            g2.setFont(UI.font(Font.BOLD, t.length() > 1 ? 9.5f : 11f));
            int tw = g2.getFontMetrics().stringWidth(t);
            g2.drawString(t, gh.x - tw / 2f, gh.y + 4);
            if (gh.cell == YutGame.DONE) {
                g2.setColor(c);
                g2.setFont(UI.font(Font.BOLD, 9f));
                g2.drawString("완주", gh.x - 9, gh.y - 16);
            }
        }
    }

    // ---- 이동 모션 ----
    private void drawMove(Graphics2D g2) {
        MoveAnim m = mv;
        long e = System.currentTimeMillis() - m.t0;
        Color c = colorOf(m.nick);
        double[] last = m.pts.get(m.pts.size() - 1);
        double[] before = m.pts.size() > 1 ? m.pts.get(m.pts.size() - 2) : last;
        double dx = last[0] - before[0], dy = last[1] - before[1], len = Math.hypot(dx, dy);
        if (len < 1) {
            dx = 1;
            dy = 0;
            len = 1;
        }
        dx /= len;
        dy /= len;

        // 잡힌 말: 도착 전에는 제자리, 부딪히면 밀려났다가 주인 자리로 날아감
        for (int vi = 0; vi < m.victims.size(); vi++) {
            String vn = m.victims.get(vi);
            Color vc = colorOf(vn);
            List<Integer> vids = m.victimIds.get(vi);
            if (e < m.walk) {
                drawStack(g2, last[0], last[1], PIECE_R, vc, vids.size(), 1f);
                continue;
            }
            double f = Math.min(1, (e - m.walk) / (double) Math.max(1, m.cap));
            for (int j = 0; j < vids.size(); j++) {
                double spread = (j - (vids.size() - 1) / 2.0) * 10;
                double bx = last[0] + dx * 20 - dy * spread, by = last[1] + dy * 20 + dx * spread;
                double x, y, r;
                if (f < 0.22) { // 밀려남
                    double q = f / 0.22;
                    x = last[0] + (bx - last[0]) * q;
                    y = last[1] + (by - last[1]) * q;
                    r = PIECE_R;
                } else { // 주인 자리로 날아감
                    double q = (f - 0.22) / 0.78;
                    double[] t = slotPoint(vn, vids.get(j));
                    x = bx + (t[0] - bx) * q;
                    y = by + (t[1] - by) * q - Math.sin(Math.PI * q) * 70;
                    r = PIECE_R * (1 - 0.5 * q);
                }
                if (f < 1) drawToken(g2, x, y, r, vc, 1, 1f);
            }
        }

        // 움직이는 말
        double x = last[0], y = last[1], r = PIECE_R;
        float alpha = 1f;
        int count = m.ids.size();
        if (e < m.walk) {
            long acc = 0;
            for (int i = 0; i < m.segMs.size(); i++) {
                int ms = m.segMs.get(i);
                if (e < acc + ms) {
                    double f = (e - acc) / (double) ms;
                    double ef = f < 0.5 ? 2 * f * f : 1 - Math.pow(-2 * f + 2, 2) / 2; // 천천히 출발, 천천히 멈춤
                    double[] a = m.pts.get(i), b = m.pts.get(i + 1);
                    x = a[0] + (b[0] - a[0]) * ef;
                    y = a[1] + (b[1] - a[1]) * ef - Math.sin(Math.PI * f) * m.hop.get(i);
                    if (i == 0 && m.hop.get(0) > 40) r = PIECE_R * (0.6 + 0.4 * f); // 내 말 자리에서 커지며 나옴
                    if (m.done && i == m.segMs.size() - 1) r = PIECE_R * (1 - 0.5 * f);
                    break;
                }
                acc += ms;
            }
        } else {
            if (m.done) return; // 완주한 말은 내 말 자리로 들어감
            if (!m.victims.isEmpty() && e < m.walk + 160) { // 부딪히며 밀고 들어감
                double q = (e - m.walk) / 160.0;
                x += dx * Math.sin(Math.PI * q) * 8;
                y += dy * Math.sin(Math.PI * q) * 8;
            }
            long fx0 = m.walk + m.cap;
            if (m.hell && e >= fx0) { // 불구덩이로 빠짐
                double f = Math.min(1, (e - fx0) / (double) m.fx);
                r = PIECE_R * (1 - 0.85 * f);
                y += 8 * f;
                alpha = (float) (1 - f);
                drawStack(g2, x, y, r, c, count, alpha);
                flameBurst(g2, last[0], last[1], f);
                return;
            }
            if (m.birthId >= 0 && e >= fx0) { // 우물에서 말이 튀어나옴
                double f = Math.min(1, (e - fx0) / (double) m.fx);
                drawStack(g2, x, y, r, c, count, 1f);
                for (int i = 0; i < 8; i++) { // 나뭇잎이 흩날림
                    double ang = Math.PI * 2 * i / 8 + 0.3;
                    double d = 12 + 26 * f;
                    AffineTransform old = g2.getTransform();
                    g2.translate(last[0] + Math.cos(ang) * d, last[1] + Math.sin(ang) * d);
                    g2.rotate(ang + f * 3);
                    int al = (int) ((1 - f) * 220);
                    g2.setColor(i % 2 == 0 ? new Color(62, 155, 98, al) : new Color(108, 196, 138, al));
                    g2.fill(new Ellipse2D.Double(-4.5, -2, 9, 4));
                    g2.setTransform(old);
                }
                double ny, nr;
                if (f < 0.55) { // 솟아오름
                    double q = f / 0.55;
                    ny = last[1] - 34 * Math.sin(q * Math.PI / 2);
                    nr = PIECE_R * (0.2 + 0.9 * q);
                } else { // 내려와서 업힘
                    double q = (f - 0.55) / 0.45;
                    ny = last[1] - 34 * (1 - q * q);
                    nr = PIECE_R * (1.1 - 0.1 * q);
                }
                if (f < 1) drawToken(g2, last[0], ny, nr, c, 1, 1f);
                else count++;
                return;
            }
        }
        drawStack(g2, x, y, r, c, count, alpha);
    }

    // 등수 딱지: 리본 두 줄 + 금색(1등) / 은색(2등) 메달
    static void drawMedal(Graphics2D g2, double cx, double cy, double r, int rank) {
        Color main = rank == 1 ? GOLD : new Color(0xB8C2CE), dark = rank == 1 ? new Color(0xC98A12) : new Color(0x7C8794);
        Path2D.Double left = new Path2D.Double(), right = new Path2D.Double();
        left.moveTo(cx - r * 0.55, cy + r * 0.3);
        left.lineTo(cx - r * 0.95, cy + r * 1.45);
        left.lineTo(cx - r * 0.55, cy + r * 1.25);
        left.lineTo(cx - r * 0.3, cy + r * 1.55);
        left.lineTo(cx - r * 0.05, cy + r * 0.5);
        left.closePath();
        right.moveTo(cx + r * 0.55, cy + r * 0.3);
        right.lineTo(cx + r * 0.95, cy + r * 1.45);
        right.lineTo(cx + r * 0.55, cy + r * 1.25);
        right.lineTo(cx + r * 0.3, cy + r * 1.55);
        right.lineTo(cx + r * 0.05, cy + r * 0.5);
        right.closePath();
        g2.setColor(new Color(0xE5508F));
        g2.fill(left);
        g2.setColor(new Color(0x3D7BF0));
        g2.fill(right);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fill(new Ellipse2D.Double(cx - r, cy - r + r * 0.15, r * 2, r * 2));
        g2.setColor(main);
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2.setColor(dark);
        g2.setStroke(new BasicStroke((float) Math.max(1, r / 9)));
        g2.draw(new Ellipse2D.Double(cx - r * 0.75, cy - r * 0.75, r * 1.5, r * 1.5));
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g2.setColor(Color.WHITE);
        String t = r > 16 ? String.valueOf(rank) : rank + "등";
        g2.setFont(UI.font(Font.BOLD, (float) (r > 16 ? r * 0.95 : r * 0.82)));
        int tw = g2.getFontMetrics().stringWidth(t);
        g2.drawString(t, (float) (cx - tw / 2.0), (float) (cy + r * (r > 16 ? 0.34 : 0.3)));
    }

    // 지옥에 빠질 때 불길이 크게 솟음
    private void flameBurst(Graphics2D g2, double cx, double cy, double f) {
        double s = 0.6 + Math.sin(Math.PI * Math.min(1, f * 1.3)) * 1.3;
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy + 2);
        g2.scale(s, s);
        Composite oc = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - f * 0.6)));
        flame(g2, -7, 4, 8, 18, new Color(0xF0602E));
        flame(g2, 7, 4, 8, 16, new Color(0xF0602E));
        flame(g2, 0, 4, 10, 26, new Color(0xE8452C));
        flame(g2, 0, 4, 6, 15, new Color(0xFFC23D));
        g2.setComposite(oc);
        g2.setTransform(old);
    }

    private void drawBoard(Graphics2D g2) {
        // 길
        g2.setColor(new Color(0xDCD6EE));
        g2.setStroke(new BasicStroke(3f));
        for (int i = 0; i < 20; i++) line(g2, i, (i + 1) % 20);
        line(g2, 5, 20); line(g2, 20, 21); line(g2, 21, 22); line(g2, 22, 23); line(g2, 23, 24); line(g2, 24, 15);
        line(g2, 10, 25); line(g2, 25, 26); line(g2, 26, 22); line(g2, 22, 27); line(g2, 27, 28); line(g2, 28, 0);
        // 칸 (지옥은 불구덩이, 잉태는 나뭇잎 우물 그림만)
        for (int cell = 0; cell < XY.length; cell++) {
            int[] c = screen(cell);
            boolean big = cell == 0 || cell == 5 || cell == 10 || cell == 15 || cell == 22;
            if (cell == hellCell) drawFirePit(g2, c[0], c[1]);
            else if (cell == birthCell) drawWell(g2, c[0], c[1]);
            else {
                int r = big ? 13 : 9;
                g2.setColor(big ? new Color(0xF1EEFB) : Color.WHITE);
                g2.fill(new Ellipse2D.Float(c[0] - r, c[1] - r, r * 2, r * 2));
                g2.setColor(big ? new Color(0xB9B0DD) : new Color(0xCFC9E3));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Float(c[0] - r, c[1] - r, r * 2, r * 2));
            }
        }
        int[] start = screen(0);
        g2.setColor(UI.TEXT_SUB);
        g2.setFont(UI.font(Font.PLAIN, 9.5f));
        g2.drawString("출발/도착", start[0] + 16, start[1] + 4); // 출발점(오른쪽 아래) 오른쪽 (아래는 알림 글 자리)

        // 말 (이동 모션 중인 말, 잡히는 말은 drawMove가 그림)
        for (Map.Entry<String, int[]> e : pos.entrySet()) {
            String nick = e.getKey();
            Color col = colorOf(nick);
            int[] v = e.getValue();
            Map<Integer, Integer> count = new LinkedHashMap<>();
            for (int k = 0; k < v.length; k++) {
                int p = v[k];
                if (p < 0 || p >= XY.length) continue;
                if (mv != null && nick.equals(mv.nick) && mv.ids.contains(k)) continue;
                if (mv != null && p == mv.to && mv.victims.contains(nick)) continue;
                count.merge(p, 1, Integer::sum);
            }
            for (Map.Entry<Integer, Integer> c : count.entrySet()) {
                int[] s = screen(c.getKey());
                drawStack(g2, s[0], s[1], PIECE_R, col, c.getValue(), 1f);
            }
        }
    }


    // 윷가락 하나. (cx, cy) 가운데, 각도 angle. flat이면 젖혀진 면(밝은 면 + X 무늬), 아니면 둥근 면
    static void drawStick(Graphics2D g2, double cx, double cy, double sw, double sh, double angle, boolean flat, boolean backMark) {
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        g2.rotate(angle);
        RoundRectangle2D.Double body = new RoundRectangle2D.Double(-sw / 2, -sh / 2, sw, sh, sw, sw);
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fill(new RoundRectangle2D.Double(-sw / 2 + 1.5, -sh / 2 + 2.5, sw, sh, sw, sw));
        if (flat) {
            g2.setColor(new Color(0xF3D9B1));
            g2.fill(body);
            g2.setColor(new Color(0xB07A45));
            g2.setStroke(new BasicStroke((float) Math.max(1, sw / 9)));
            double m = sw * 0.25;
            for (int k = -1; k <= 1; k++) { // X 무늬 3개
                double yy = k * sh * 0.27;
                g2.draw(new Line2D.Double(-m, yy - m, m, yy + m));
                g2.draw(new Line2D.Double(-m, yy + m, m, yy - m));
            }
            if (backMark) { // 빽도 표시 (빨간 점)
                g2.setColor(HELL);
                g2.fill(new Ellipse2D.Double(-sw * 0.18, -sh * 0.45, sw * 0.36, sw * 0.36));
            }
        } else {
            g2.setColor(new Color(0xB9824A));
            g2.fill(body);
            g2.setColor(new Color(255, 255, 255, 60)); // 둥근 면 빛
            g2.fill(new RoundRectangle2D.Double(-sw / 2 + sw * 0.2, -sh / 2 + 3, sw * 0.22, sh - 6, sw * 0.22, sw * 0.22));
        }
        g2.setColor(new Color(0x8A5A2B));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(body);
        g2.setTransform(old);
    }

    // 던지기 모션: 판 위로 윷가락 4개가 빙글빙글 날아올랐다 떨어지고, 결과 글자가 튀어나옴
    private void drawThrow(Graphics2D g2) {
        long t = animTime();
        double cx = boardX + BOARD / 2.0, floor = boardY + BOARD / 2.0 + 40;
        float fade = t > ANIM_MS - 250 ? (ANIM_MS - t) / 250f : 1f;
        fade = Math.max(0, Math.min(1, fade));
        g2.setColor(new Color(251, 249, 255, (int) (215 * fade)));
        g2.fill(new RoundRectangle2D.Float(boardX - 6, boardY - 6, BOARD + 12, BOARD + 12, 24, 24));
        java.awt.Composite oc = g2.getComposite();
        g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(fade));
        boolean nak = lastResult.equals("낙");
        for (int i = 0; i < 4; i++) {
            double p = Math.min(1, t / (double) (FLY_MS - (3 - i) * 40)); // 가락마다 살짝 다르게 떨어짐
            double up = Math.sin(Math.PI * p) * (150 + i * 12);
            double bounce = p >= 1 ? 0 : p > 0.82 ? Math.sin(Math.PI * (p - 0.82) / 0.18) * 14 : 0;
            double x = cx + (i - 1.5) * 34 + (1 - p) * (i - 1.5) * -18;
            if (nak && i == 3) x += p * 190; // 낙: 하나가 판 밖으로
            double y = floor - up - bounce;
            double spin = (1 - p) * (1 - p) * (i % 2 == 0 ? 1 : -1) * Math.PI * (3 + i);
            double angle = spin + (i - 1.5) * 0.12;
            boolean flat = p >= 0.85 ? sticks.charAt(i) == '1' : ((int) (p * 16 + i * 3) % 2 == 0);
            drawStick(g2, x, y, 18, 78, angle, flat, i == 0);
        }
        if (t > FLY_MS) { // 결과 글자 (커졌다가 제자리)
            double q = Math.min(1, (t - FLY_MS) / 180.0);
            double scale = q < 1 ? 0.4 + q * 0.9 : 1.3 - Math.min(0.3, (t - FLY_MS - 180) / 400.0);
            String txt = lastResult + "!";
            g2.setFont(UI.font(Font.BOLD, (float) (44 * scale)));
            int tw = g2.getFontMetrics().stringWidth(txt);
            int ty = (int) (boardY + 70);
            g2.setColor(new Color(255, 255, 255, 230));
            g2.drawString(txt, (int) (cx - tw / 2.0) + 2, ty + 2);
            g2.setColor(nak ? HELL : UI.LAVENDER);
            g2.drawString(txt, (int) (cx - tw / 2.0), ty);
        }
        g2.setComposite(oc);
    }

    private void drawWrapped(Graphics2D g2, String s, int x, int y, int maxW) {
        StringBuilder line = new StringBuilder();
        int ly = y;
        for (String word : s.split(" ")) {
            String t = line.length() == 0 ? word : line + " " + word;
            if (g2.getFontMetrics().stringWidth(t) > maxW && line.length() > 0) {
                g2.drawString(line.toString(), x, ly);
                ly += 16;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(t);
            }
        }
        g2.drawString(line.toString(), x, ly);
    }


    // 지옥 칸: 돌로 두른 불구덩이 + 불꽃 3개
    static void drawFirePit(Graphics2D g2, double cx, double cy) {
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy - 4); // 불꽃이 위로 솟으니 구덩이를 살짝 올려서 칸 가운데에 맞춤
        // 구덩이 (살짝 위에서 본 타원)
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fill(new Ellipse2D.Double(-16, 3, 32, 14));
        g2.setColor(new Color(0x4A2A1E));
        g2.fill(new Ellipse2D.Double(-16, 0, 32, 14));
        g2.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(0, 7), 13,
                new float[] { 0f, 0.6f, 1f }, new Color[] { new Color(0xFFD45C), new Color(0xF0602E), new Color(0x7A2615) }));
        g2.fill(new Ellipse2D.Double(-12, 2.5, 24, 9));
        // 구덩이 둘레 돌
        double[][] stones = { { -15, 6 }, { -10, 11 }, { -3, 13 }, { 4, 13 }, { 11, 11 }, { 15, 6 } };
        for (double[] st : stones) {
            g2.setColor(new Color(0x8C8479));
            g2.fill(new Ellipse2D.Double(st[0] - 3.5, st[1] - 2.5, 7, 5));
            g2.setColor(new Color(0x5E574F));
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new Ellipse2D.Double(st[0] - 3.5, st[1] - 2.5, 7, 5));
        }
        // 불꽃 (바깥 주황, 안쪽 노랑)
        flame(g2, -6, 7, 7, 15, new Color(0xF0602E));
        flame(g2, 6, 7, 7, 14, new Color(0xF0602E));
        flame(g2, 0, 7, 9, 22, new Color(0xE8452C));
        flame(g2, 0, 7, 5, 13, new Color(0xFFC23D));
        flame(g2, -6, 7, 3.5, 8, new Color(0xFFC23D));
        flame(g2, 6, 7, 3.5, 7, new Color(0xFFC23D));
        g2.setTransform(old);
    }

    // 아래가 둥글고 위가 뾰족한 불꽃 하나. (x, base)가 밑 가운데, w 너비, h 높이
    private static void flame(Graphics2D g2, double x, double base, double w, double h, Color c) {
        java.awt.geom.Path2D.Double f = new java.awt.geom.Path2D.Double();
        f.moveTo(x, base - h);
        f.curveTo(x + w * 0.2, base - h * 0.6, x + w * 0.75, base - h * 0.45, x + w * 0.5, base - h * 0.12);
        f.curveTo(x + w * 0.4, base, x - w * 0.4, base, x - w * 0.5, base - h * 0.12);
        f.curveTo(x - w * 0.75, base - h * 0.45, x - w * 0.1, base - h * 0.55, x, base - h);
        f.closePath();
        g2.setColor(c);
        g2.fill(f);
    }

    // 잉태 칸: 나뭇잎으로 둘러싸인 돌 우물
    static void drawWell(Graphics2D g2, double cx, double cy) {
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        // 나뭇잎 8장 (진한 잎, 연한 잎 번갈아)
        for (int i = 0; i < 8; i++) {
            AffineTransform t = g2.getTransform();
            g2.rotate(Math.PI * 2 * i / 8 + 0.2);
            java.awt.geom.Path2D.Double leaf = new java.awt.geom.Path2D.Double();
            leaf.moveTo(9, 0);
            leaf.quadTo(14, -6, 19, 0);
            leaf.quadTo(14, 6, 9, 0);
            leaf.closePath();
            g2.setColor(i % 2 == 0 ? new Color(0x3E9B62) : new Color(0x6CC48A));
            g2.fill(leaf);
            g2.setColor(new Color(0x2B6E45)); // 잎맥
            g2.setStroke(new BasicStroke(0.7f));
            g2.draw(new Line2D.Double(10, 0, 18, 0));
            g2.setTransform(t);
        }
        // 돌 테두리
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fill(new Ellipse2D.Double(-11, -9.5, 22, 22));
        g2.setColor(new Color(0xA7A39C));
        g2.fill(new Ellipse2D.Double(-11, -11, 22, 22));
        g2.setColor(new Color(0x77726B));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new Ellipse2D.Double(-11, -11, 22, 22));
        for (int i = 0; i < 8; i++) { // 돌 이음매
            double a = Math.PI * 2 * i / 8;
            g2.draw(new Line2D.Double(Math.cos(a) * 7.5, Math.sin(a) * 7.5, Math.cos(a) * 11, Math.sin(a) * 11));
        }
        // 물
        g2.setPaint(new java.awt.RadialGradientPaint(new java.awt.geom.Point2D.Double(0, 1), 8,
                new float[] { 0f, 1f }, new Color[] { new Color(0x7CC3F2), new Color(0x2F7FC1) }));
        g2.fill(new Ellipse2D.Double(-7.5, -7.5, 15, 15));
        g2.setColor(new Color(255, 255, 255, 170)); // 물빛
        g2.fill(new Ellipse2D.Double(-4.5, -5, 6, 2.6));
        g2.setTransform(old);
    }


    private void line(Graphics2D g2, int a, int b) {
        int[] p = screen(a), q = screen(b);
        g2.draw(new Line2D.Float(p[0], p[1], q[0], q[1]));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(376, 500);
    }
}
