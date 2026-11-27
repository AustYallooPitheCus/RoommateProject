// HalliView.java
// 죽음의 피흘리기 할리갈리 판 (13주차). 게임 탭의 할리갈리 화면(HalliScreen) 안에 들어감
//   위: 참가자 칸 (플레이어N, 친구면 닉네임, 피 5방울)
//   가운데: 탁자. 나는 아래, 다른 사람은 왼쪽/위/오른쪽. 가운데 종
//   아래: 알림 글, [카드 내기] [내는 척 (남은 수)]
// 종은 가운데 종을 누르거나 스페이스바. 펼쳐진 카드 중 같은 과일이 합쳐서 딱 5개면 종!
// 모션: 손이 나와서 카드를 뒤집어 내려놓음 / 내는 척은 손만 움찔 / 종 치는 손 + 종 흔들림 / 피 -1 방울
// 서버가 판정한 결과(/HG_...)를 받아서 그리기만 함. 상태는 바로 바꾸고 모션은 그 위에 덧그림 (종 치기는 빨라야 해서 기다리지 않음)

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

public class HalliView extends JPanel implements GameView {

    private static final long serialVersionUID = 1L;
    static final Color[] PLAYER = YutView.PLAYER;
    static final Color BLOOD = new Color(0xD8263A), TABLE = new Color(0xEEF6EF), TABLE_EDGE = new Color(0xCFE3D2);
    static final int CARD_W = 62, CARD_H = 84;
    static final int FLIP_MS = 380, FAKE_MS = 560, RING_MS = 520, ROUND_MS = 480, HURT_MS = 900;

    private final AppFrame app;
    private Runnable changed = () -> { };

    // ===== 상태 (서버 메시지로만 바뀜) =====
    private String gid, me = "";
    private final Map<String, String> names = new LinkedHashMap<>();   // 동물 이름 -> 친구 닉네임 또는 -
    private final List<String> players = new ArrayList<>();
    private final Map<String, Integer> hp = new HashMap<>();
    private final Map<String, int[]> top = new HashMap<>();             // 맨 위 카드 {과일, 개수}
    private final Map<String, Integer> cards = new HashMap<>();         // 아직 안 낸 카드 수
    private final Set<String> out = new HashSet<>();                    // 탈락했거나 나감
    private boolean started;
    private int maxHp = HalliGame.WIN_HP, myFakes = HalliGame.FAKES; // maxHp: 핏방울 칸 수 (모으면 이기는 수)
    private String current = "", log = "";
    private String endText, endSub = "", endNote = "";
    private boolean outPopup;                                           // 내가 탈락: [나가기] [더 구경하기]
    private boolean bellOpen;                                           // 맞은 종 정산 중 (잠깐 카드 못 냄)
    private int seconds;
    private final Timer tick;

    // ===== 모션 (시작 시각과 함께 쌓아두고 그림) =====
    static class Anim {
        final String kind, who;   // flip, fake, ring, round, hurt
        final long t0 = System.currentTimeMillis();
        int[] oldCard;            // flip: 뒤집기 전에 있던 카드
        String result = "";       // ring: 맞음, 늦음, 틀림
        Map<String, int[]> cards; // round: 치워지는 카드

        Anim(String kind, String who) {
            this.kind = kind;
            this.who = who;
        }

        long age() {
            return System.currentTimeMillis() - t0;
        }
    }

    private final List<Anim> anims = new ArrayList<>();
    private final Timer frame;

    // ===== 부품 =====
    private final JButton flipBtn = UI.pill("카드 내기", UI.LAVENDER, Color.WHITE);
    private final JButton fakeBtn = UI.soft("내는 척 (3)");
    private final JButton closeBtn = UI.pill("확인", UI.LAVENDER, Color.WHITE);
    private final JButton watchBtn = UI.pill("더 구경하기", UI.LAVENDER, Color.WHITE), leaveBtn = UI.soft("나가기");
    private static final int CX = 188, CY = 238; // 탁자 가운데 (종 자리)

    public HalliView(AppFrame app) {
        this.app = app;
        setOpaque(false);
        setLayout(null);
        flipBtn.setFont(UI.font(Font.BOLD, 15f));
        add(flipBtn);
        add(fakeBtn);
        add(closeBtn);
        add(watchBtn);
        add(leaveBtn);
        closeBtn.setVisible(false);
        flipBtn.addActionListener(e -> app.send("/HG_FLIP"));
        fakeBtn.addActionListener(e -> app.send("/HG_FAKE"));
        closeBtn.addActionListener(e -> {
            endText = null;
            started = false;
            gid = null;
            changed.run();
        });
        watchBtn.addActionListener(e -> {
            outPopup = false;
            refresh();
        });
        leaveBtn.addActionListener(e -> app.send("/HG_LEAVE"));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { // 종은 누르는 순간 (빨라야 하니까 clicked 말고 pressed)
                if (Math.hypot(e.getX() - CX, e.getY() - CY) <= 38) ring();
            }
        });
        // 스페이스바 = 종 (이 화면이 보일 때만)
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "halliRing");
        getActionMap().put("halliRing", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (isShowing()) ring();
            }
        });
        tick = new Timer(1000, e -> {
            if (seconds > 0) seconds--;
            repaint();
        });
        frame = new Timer(16, e -> {
            anims.removeIf(a -> a.age() > 1200);
            if (anims.isEmpty()) ((Timer) e.getSource()).stop();
            repaint();
        });
    }

    private boolean alive() {
        return started && endText == null && players.contains(me) && !out.contains(me);
    }

    private void ring() {
        if (alive()) app.send("/HG_RING");
    }

    private void animate(Anim a) {
        anims.add(a);
        frame.start();
    }

    @Override
    public void onChange(Runnable r) {
        changed = r;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public boolean isOver() {
        return endText != null;
    }

    @Override
    public boolean amRanked() {
        return out.contains(me);
    }

    @Override
    public void reset() {
        gid = null;
        started = false;
        endText = null;
        outPopup = false;
        players.clear();
        names.clear();
        hp.clear();
        top.clear();
        out.clear();
        anims.clear();
        log = "";
        tick.stop();
        frame.stop();
        Sound.stopBgm();
        changed.run();
    }

    // 모두 차례 순서대로 "플레이어1~4". 알림 글에서 나는 "나"
    private String tag(String animal) {
        int i = players.indexOf(animal);
        return i < 0 ? "플레이어" : "플레이어" + (i + 1);
    }

    private String show(String animal) {
        return animal.equals(me) ? "나" : tag(animal);
    }

    private String sub(String animal) {
        if (animal.equals(me)) return "(나)";
        String n = names.get(animal);
        return n == null || n.equals("-") ? null : "(" + n + ")";
    }

    private Color colorOf(String animal) {
        int i = players.indexOf(animal);
        return PLAYER[(i < 0 ? 0 : i) % PLAYER.length];
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        super.setBounds(x, y, w, h);
        flipBtn.setBounds(12, 470, (w - 36) / 2, 46);
        fakeBtn.setBounds(24 + (w - 36) / 2, 470, (w - 36) / 2, 46);
        closeBtn.setBounds((w - 160) / 2, h / 2 + 46, 160, 42);
        leaveBtn.setBounds(w / 2 - 128, 300, 120, 42);
        watchBtn.setBounds(w / 2 + 8, 300, 120, 42);
    }

    // ===================== 서버 메시지 =====================

    // /HG_MATCH 게임번호 내이름 이름=닉|-,...
    @Override
    public void match(String rest) {
        String[] a = rest.split(" ", 3);
        gid = a[0];
        me = a[1];
        names.clear();
        for (String kv : a[2].split(",")) {
            int k = kv.indexOf('=');
            if (k > 0) names.put(kv.substring(0, k), kv.substring(k + 1));
        }
    }

    @Override
    public void onMessage(String cmd, String id, String rest) {
        if (gid == null || !gid.equals(id)) return; // 예전 게임 메시지
        String[] a = rest.split(" ");
        switch (cmd) {
            case "/HG_START": // 순서 피 페이크수 목표피
                started = true;
                endText = null;
                outPopup = false;
                bellOpen = false;
                players.clear();
                players.addAll(Arrays.asList(a[0].split(",")));
                int startHp = Integer.parseInt(a[1]);
                maxHp = a.length > 3 ? Integer.parseInt(a[3]) : startHp;
                myFakes = Integer.parseInt(a[2]);
                hp.clear();
                for (String p : players) hp.put(p, startHp);
                top.clear();
                cards.clear();
                out.clear();
                anims.clear();
                current = "";
                log = "게임 시작! 같은 과일이 딱 5개면 종을 쳐요";
                Sound.startBgm("bgm_halli");
                break;
            case "/HG_BOARD": // 이름:피:과일,개수 또는 -:남은카드;...
                for (String p : a[0].split(";")) {
                    String[] f = p.split(":");
                    hp.put(f[0], Integer.parseInt(f[1]));
                    if (f.length > 3) cards.put(f[0], Integer.parseInt(f[3]));
                    if (f[2].equals("-")) top.remove(f[0]);
                    else {
                        String[] c = f[2].split(",");
                        top.put(f[0], new int[] { Integer.parseInt(c[0]), Integer.parseInt(c[1]) });
                    }
                }
                break;
            case "/HG_TURN": // 이름 초 (이름이 - 면 아무도 낼 카드가 없음, 종만 칠 수 있음)
                if (!a[0].equals(current) && a[0].equals(me)) Sound.play("turn");
                if (a[0].equals("-") && !current.equals("-")) log = "모두 카드를 다 냈어요. 5개면 종! 아니면 곧 끝나요";
                current = a[0];
                seconds = Integer.parseInt(a[1]);
                tick.restart();
                break;
            case "/HG_FLIP": { // 이름 과일 개수 남은카드
                if (a.length > 3) cards.put(a[0], Integer.parseInt(a[3]));
                Anim an = new Anim("flip", a[0]);
                an.oldCard = top.get(a[0]);
                top.put(a[0], new int[] { Integer.parseInt(a[1]), Integer.parseInt(a[2]) });
                animate(an);
                Sound.play("card");
                break;
            }
            case "/HG_FAKE": // 이름 (손만 움찔)
                animate(new Anim("fake", a[0]));
                break;
            case "/HG_FAKES": // 내 남은 내는 척
                myFakes = Integer.parseInt(a[0]);
                break;
            case "/HG_RING": { // 이름 결과 피
                Anim an = new Anim("ring", a[0]);
                an.result = a[1];
                animate(an);
                Sound.play("bell");
                if (a[1].equals("틀림")) {
                    hp.put(a[0], Integer.parseInt(a[2]));
                    animate(new Anim("hurt", a[0]));
                    log = show(a[0]) + (a[0].equals(me) ? "는" : "님이") + " 5개가 아닌데 쳐서 피 -1";
                    Sound.play("nak");
                } else if (a[1].equals("맞음")) {
                    bellOpen = true;
                    log = show(a[0]) + (a[0].equals(me) ? "가" : "님이") + " 제일 먼저 쳤어요!";
                }
                break;
            }
            case "/HG_ROUND": { // 이긴사람 깎인사람들
                bellOpen = false;
                Anim an = new Anim("round", a[0]);
                an.cards = new HashMap<>(top);
                animate(an);
                List<String> losers = a[1].equals("-") ? new ArrayList<>() : Arrays.asList(a[1].split(","));
                for (String l : losers) animate(new Anim("hurt", l));
                if (losers.contains(me)) Sound.play("capture");
                animate(new Anim("heal", a[0]));
                log = show(a[0]) + (a[0].equals(me) ? "가" : "님이") + " 먼저 쳤어요! 피 +1, 카드도 가져가요";
                break;
            }
            case "/HG_OUT": // 이름 (피 0으로 탈락하거나 나감)
                out.add(a[0]);
                top.remove(a[0]);
                log = show(a[0]) + (a[0].equals(me) ? "는 탈락했어요" : "님 탈락");
                if (a[0].equals(me)) outPopup = true;
                break;
            case "/HG_END": // 등수순서 이유
                tick.stop();
                outPopup = false;
                Sound.stopBgm();
                if (a[1].equals("나감")) {
                    started = false;
                    endText = null;
                    gid = null;
                } else {
                    String[] r = a[0].split(",");
                    int mine = Arrays.asList(r).indexOf(me);
                    endText = mine == 0 ? "끝까지 살아남았어요!" : mine > 0 ? (mine + 1) + "등 했어요" : "게임 끝!";
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < r.length && i < 4; i++) sb.append(i > 0 ? "  /  " : "").append(i + 1).append("등 ").append(show(r[i]));
                    endSub = sb.toString();
                    endNote = a[1].equals("카드소진") ? "카드가 다 떨어져서 끝" : a[1].equals("목표") ? r[0].equals(me) ? "피 " + maxHp + "방울을 먼저 모았어요" : show(r[0]) + "님이 피 " + maxHp + "방울을 먼저 모았어요" : "";
                    if (mine == 0) Sound.play("rank");
                }
                break;
            default:
                return;
        }
        refresh();
    }

    private void refresh() {
        boolean myTurn = alive() && current.equals(me) && !bellOpen;
        boolean noCards = cards.getOrDefault(me, 1) == 0;
        flipBtn.setEnabled(myTurn && !noCards);
        flipBtn.setText(noCards ? "카드 없음" : "카드 내기");
        fakeBtn.setEnabled(myTurn && myFakes > 0);
        fakeBtn.setText("내는 척 (" + myFakes + ")");
        flipBtn.setVisible(started && endText == null && !out.contains(me));
        fakeBtn.setVisible(flipBtn.isVisible());
        closeBtn.setVisible(endText != null);
        boolean popup = outPopup && endText == null;
        watchBtn.setVisible(popup);
        leaveBtn.setVisible(popup);
        changed.run();
        repaint();
    }

    // ===================== 자리 =====================

    // 나를 아래로 두고 시계방향으로: 2명 아래/위, 3명 아래/왼쪽/오른쪽, 4명 아래/왼쪽/위/오른쪽
    private int[] seatOf(String animal) {
        int n = players.size(), i = players.indexOf(animal), m = Math.max(0, players.indexOf(me));
        int rel = ((i - m) % n + n) % n;
        int[][] spots = { { 0, 1 }, { -1, 0 }, { 0, -1 }, { 1, 0 } }; // 아래, 왼쪽, 위, 오른쪽 (밖으로 향하는 방향)
        int[] pick = n == 2 ? new int[] { 0, 2 } : n == 3 ? new int[] { 0, 1, 3 } : new int[] { 0, 1, 2, 3 };
        int[] d = spots[pick[rel]];
        int x = CX + d[0] * 112, y = CY + d[1] * 104;
        return new int[] { x, y, d[0], d[1] };
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

        // 탁자
        g2.setColor(TABLE);
        g2.fill(new RoundRectangle2D.Float(10, 76, w - 20, 324, 30, 30));
        g2.setColor(TABLE_EDGE);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(10, 76, w - 20, 324, 30, 30));

        // 카드 자리, 카드
        for (String p : players) {
            int[] s = seatOf(p);
            boolean gone = out.contains(p);
            g2.setColor(new Color(255, 255, 255, gone ? 90 : 160));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 4f }, 0f));
            g2.draw(new RoundRectangle2D.Float(s[0] - CARD_W / 2f, s[1] - CARD_H / 2f, CARD_W, CARD_H, 10, 10));
            int left = cards.getOrDefault(p, 0);
            if (!gone && cards.containsKey(p)) { // 아직 안 낸 카드 더미 (위, 아래 자리는 카드 왼쪽, 옆 자리는 카드 위)
                double px = s[2] == 0 ? s[0] - CARD_W / 2.0 - 26 : s[0];
                double py = s[2] == 0 ? s[1] + 10 : s[1] - CARD_H / 2.0 - 26;
                drawDeck(g2, px, py, left);
            }
            int[] card = top.get(p);
            Anim flip = find("flip", p);
            if (flip != null) {
                java.awt.Shape oc = g2.getClip();
                g2.clip(new RoundRectangle2D.Float(10, 76, w - 20, 324, 30, 30));
                drawFlip(g2, p, s, flip);
                g2.setClip(oc);
            }
            else if (card != null && !roundAnimating()) drawCard(g2, s[0], s[1], card, 1, 1f);
            // 자리 이름
            g2.setColor(gone ? UI.TEXT_SUB : colorOf(p));
            g2.setFont(UI.font(Font.BOLD, 10.5f));
            String label = gone ? tag(p) + " 탈락" : (p.equals(me) ? "나" : tag(p));
            int tw = g2.getFontMetrics().stringWidth(label);
            int ly = s[3] < 0 ? s[1] - CARD_H / 2 - 6 : s[1] + CARD_H / 2 + 14;
            if (s[3] > 0) ly = s[1] - CARD_H / 2 - 6; // 나는 카드 위에
            g2.drawString(label, s[0] - tw / 2f, ly);
            if (p.equals(current) && started && endText == null && !gone) { // 지금 차례 표시
                g2.setColor(new Color(colorOf(p).getRed(), colorOf(p).getGreen(), colorOf(p).getBlue(), 120));
                g2.setStroke(new BasicStroke(2.6f));
                g2.draw(new RoundRectangle2D.Float(s[0] - CARD_W / 2f - 4, s[1] - CARD_H / 2f - 4, CARD_W + 8, CARD_H + 8, 13, 13));
            }
        }

        drawRound(g2);
        drawBell(g2);
        java.awt.Shape oldClip = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(10, 76, w - 20, 324, 30, 30)); // 손은 탁자 안에서만 보이게
        for (Anim a : new ArrayList<>(anims)) {
            if (a.kind.equals("fake")) drawFakeHand(g2, a);
            if (a.kind.equals("ring")) drawRingHand(g2, a);
        }
        g2.setClip(oldClip);
        for (Anim a : new ArrayList<>(anims)) if (a.kind.equals("hurt") || a.kind.equals("heal")) drawHurt(g2, a);

        // 알림 글
        g2.setColor(UI.TEXT);
        g2.setFont(UI.font(Font.PLAIN, 12.5f));
        int tw = g2.getFontMetrics().stringWidth(log);
        g2.drawString(log, Math.max(14, (w - tw) / 2), 432);
        if (alive()) {
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 10.5f));
            String t = "종: 가운데 종을 누르거나 스페이스바";
            g2.drawString(t, (w - g2.getFontMetrics().stringWidth(t)) / 2, 456);
        }

        if (outPopup && endText == null) drawPopup(g2, w, h, "탈락했어요", "피를 다 흘렸어요. 끝날 때까지 구경할 수 있어요");
        if (endText != null) {
            g2.setColor(new Color(255, 255, 255, 220));
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 22, 22));
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 24f));
            int ew = g2.getFontMetrics().stringWidth(endText);
            g2.drawString(endText, (w - ew) / 2, h / 2 - 20);
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 13f));
            float fs = 13f; // 순위 줄이 길면 글자를 줄여서 한 줄에
            while (fs > 9.5f && g2.getFontMetrics().stringWidth(endSub) > w - 24) g2.setFont(UI.font(Font.PLAIN, fs -= 0.5f));
            ew = g2.getFontMetrics().stringWidth(endSub);
            g2.drawString(endSub, (w - ew) / 2, h / 2 + 8);
            g2.setFont(UI.font(Font.PLAIN, 12f));
            ew = g2.getFontMetrics().stringWidth(endNote);
            g2.drawString(endNote, (w - ew) / 2, h / 2 + 28);
        }
        g2.dispose();
    }

    private Anim find(String kind, String who) {
        for (int i = anims.size() - 1; i >= 0; i--) {
            Anim a = anims.get(i);
            if (a.kind.equals(kind) && a.who.equals(who)) return a;
        }
        return null;
    }

    private boolean roundAnimating() {
        Anim r = find("round", anyRound());
        return r != null && r.age() < ROUND_MS;
    }

    private String anyRound() {
        for (Anim a : anims) if (a.kind.equals("round")) return a.who;
        return "";
    }

    // ---- 위 참가자 칸: 플레이어N, (닉), 피 방울 ----
    private void drawChips(Graphics2D g2, int w) {
        int n = Math.max(1, players.size());
        int cw = (w - 16) / n;
        for (int i = 0; i < players.size(); i++) {
            String p = players.get(i);
            int x = 8 + i * cw;
            Color c = PLAYER[i % PLAYER.length];
            boolean gone = out.contains(p);
            boolean cur = p.equals(current) && endText == null && !gone;
            g2.setColor(cur ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 34) : new Color(255, 255, 255, 210));
            g2.fill(new RoundRectangle2D.Float(x, 6, cw - 6, 60, 14, 14));
            if (cur) {
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(x, 6, cw - 6, 60, 14, 14));
            }
            g2.setColor(gone ? UI.TEXT_SUB : UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 11f));
            g2.drawString(tag(p), x + 9, 22);
            // 남은 카드 수는 숫자로 안 보여줌 (탁자 위 카드 더미 두께로만, 13주차 수정)
            String sb = sub(p);
            if (sb != null) {
                g2.setColor(p.equals(me) ? c : UI.TEXT_SUB);
                g2.setFont(UI.font(Font.PLAIN, 10f));
                g2.drawString(sb, x + 9, 36);
            }
            // 피 방울 (남은 만큼 빨강, 잃은 만큼 빈 방울)
            int life = hp.getOrDefault(p, 0);
            Anim hurt = find("hurt", p);
            double shake = hurt != null && hurt.age() < 400 ? Math.sin(hurt.age() / 25.0) * 2 : 0;
            double dw = Math.min(14, (cw - 18) / (double) maxHp);
            for (int k = 0; k < maxHp; k++) drawDrop(g2, x + 13 + k * dw + shake, 53, dw * 0.42, k < life);
        }
        if (started && endText == null) {
            Color c = colorOf(current);
            g2.setColor(new Color(0xEAE6FA));
            g2.fillRoundRect(8, 69, w - 16, 4, 4, 4);
            g2.setColor(seconds <= 2 ? BLOOD : c);
            g2.fillRoundRect(8, 69, (int) ((w - 16) * seconds / (double) HalliHandler.TURN_SECONDS), 4, 4, 4);
        }
    }

    // 피 한 방울 (꽉 찬 빨강 또는 빈 테두리)
    static void drawDrop(Graphics2D g2, double cx, double cy, double r, boolean full) {
        Path2D.Double d = new Path2D.Double();
        d.moveTo(cx, cy - r * 1.9);
        d.curveTo(cx + r * 0.3, cy - r * 1.1, cx + r, cy - r * 0.6, cx + r, cy + r * 0.1);
        d.curveTo(cx + r, cy + r * 0.75, cx + r * 0.55, cy + r, cx, cy + r);
        d.curveTo(cx - r * 0.55, cy + r, cx - r, cy + r * 0.75, cx - r, cy + r * 0.1);
        d.curveTo(cx - r, cy - r * 0.6, cx - r * 0.3, cy - r * 1.1, cx, cy - r * 1.9);
        d.closePath();
        if (full) {
            g2.setColor(BLOOD);
            g2.fill(d);
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fill(new Ellipse2D.Double(cx - r * 0.55, cy - r * 0.2, r * 0.4, r * 0.55));
        } else {
            g2.setColor(new Color(0xE3DDEB));
            g2.fill(d);
            g2.setColor(new Color(0xC7BFD3));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(d);
        }
    }

    // ---- 카드 ----
    // 과일 카드 앞면 (scaleX는 뒤집을 때 0~1)
    static void drawCard(Graphics2D g2, double cx, double cy, int[] card, double scaleX, float alpha) {
        Composite oc = g2.getComposite();
        if (alpha < 1f) g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        g2.scale(Math.max(0.02, scaleX), 1);
        g2.setColor(new Color(0, 0, 0, 30));
        g2.fill(new RoundRectangle2D.Double(-CARD_W / 2.0 + 1.5, -CARD_H / 2.0 + 3, CARD_W, CARD_H, 10, 10));
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Double(-CARD_W / 2.0, -CARD_H / 2.0, CARD_W, CARD_H, 10, 10));
        g2.setColor(new Color(0xD9D3E8));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Double(-CARD_W / 2.0, -CARD_H / 2.0, CARD_W, CARD_H, 10, 10));
        double[][] pos = pips(card[1]);
        for (double[] p : pos) drawFruit(g2, card[0], p[0] * CARD_W / 2 * 0.62, p[1] * CARD_H / 2 * 0.7, 10);
        g2.setTransform(old);
        g2.setComposite(oc);
    }

    // 작은 카드 뒷면 (남은 카드 더미)
    static void drawMini(Graphics2D g2, double cx, double cy) {
        g2.setColor(new Color(0, 0, 0, 28)); // 살짝 그림자
        g2.fill(new RoundRectangle2D.Double(cx - 12, cy - 15.5, 26, 34, 6, 6));
        g2.setColor(new Color(0x7B6BE8));
        g2.fill(new RoundRectangle2D.Double(cx - 13, cy - 17, 26, 34, 6, 6));
        g2.setColor(new Color(255, 255, 255, 150));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(cx - 13, cy - 17, 26, 34, 6, 6));
        g2.draw(new RoundRectangle2D.Double(cx - 9, cy - 13, 18, 26, 4, 4)); // 뒷면 안쪽 테두리
    }

    // 남은 카드 더미. 숫자 대신 두께로 (정확한 장수는 안 알려줌)
    //   4장마다 한 겹씩 비스듬히 쌓음 (최소 2겹, 최대 8겹)
    //   3장 이하는 한 장씩 부채꼴로 펼쳐서 "거의 없음"이 보이게
    //   0장이면 점선 빈 자리만
    static final int CARDS_PER_LAYER = 4, MAX_LAYERS = 8;

    static void drawDeck(Graphics2D g2, double cx, double cy, int left) {
        if (left <= 0) {
            g2.setColor(new Color(0x7B6BE8));
            g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 3f, 3f }, 0f));
            g2.draw(new RoundRectangle2D.Double(cx - 13, cy - 17, 26, 34, 6, 6));
            return;
        }
        if (left <= 3) {
            double[] ang = left == 1 ? new double[] { 0 } : left == 2 ? new double[] { -9, 9 } : new double[] { -14, 0, 14 };
            for (double a : ang) {
                AffineTransform old = g2.getTransform();
                g2.translate(cx + a * 0.45, cy + 14); // 아래쪽 가운데를 기준으로 돌림
                g2.rotate(Math.toRadians(a));
                drawMini(g2, 0, -14);
                g2.setTransform(old);
            }
            return;
        }
        // 4장 이상은 최소 2겹 (1장짜리 한 장과 헷갈리지 않게)
        int layers = Math.max(2, Math.min(MAX_LAYERS, (left + CARDS_PER_LAYER - 1) / CARDS_PER_LAYER));
        for (int k = 0; k < layers; k++) drawMini(g2, cx + k * 1.8, cy - k * 1.8); // 아래 겹부터 그림
    }

    // 카드 뒷면
    static void drawBack(Graphics2D g2, double cx, double cy, double scaleX) {
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        g2.scale(Math.max(0.02, scaleX), 1);
        g2.setColor(new Color(0x8B7CF6));
        g2.fill(new RoundRectangle2D.Double(-CARD_W / 2.0, -CARD_H / 2.0, CARD_W, CARD_H, 10, 10));
        g2.setColor(new Color(255, 255, 255, 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new RoundRectangle2D.Double(-CARD_W / 2.0 + 5, -CARD_H / 2.0 + 5, CARD_W - 10, CARD_H - 10, 7, 7));
        drawDrop(g2, 0, 2, 9, true);
        g2.setTransform(old);
    }

    // 개수별 과일 자리 (-1~1)
    static double[][] pips(int n) {
        switch (n) {
            case 1: return new double[][] { { 0, 0 } };
            case 2: return new double[][] { { -0.5, -0.5 }, { 0.5, 0.5 } };
            case 3: return new double[][] { { -0.55, -0.6 }, { 0, 0 }, { 0.55, 0.6 } };
            case 4: return new double[][] { { -0.5, -0.55 }, { 0.5, -0.55 }, { -0.5, 0.55 }, { 0.5, 0.55 } };
            default: return new double[][] { { -0.55, -0.62 }, { 0.55, -0.62 }, { 0, 0 }, { -0.55, 0.62 }, { 0.55, 0.62 } };
        }
    }

    // 과일 그림: 0 딸기, 1 바나나, 2 라임, 3 자두
    static void drawFruit(Graphics2D g2, int fruit, double x, double y, double r) {
        AffineTransform old = g2.getTransform();
        g2.translate(x, y);
        switch (fruit) {
            case 0: { // 딸기: 아래가 뾰족한 빨강 + 씨 + 초록 꼭지
                Path2D.Double b = new Path2D.Double();
                b.moveTo(0, r * 1.05);
                b.curveTo(-r * 1.1, r * 0.2, -r * 1.05, -r * 0.75, 0, -r * 0.6);
                b.curveTo(r * 1.05, -r * 0.75, r * 1.1, r * 0.2, 0, r * 1.05);
                g2.setColor(new Color(0xE23A4E));
                g2.fill(b);
                g2.setColor(new Color(0xFFE08A));
                double[][] seeds = { { -0.4, -0.15 }, { 0.4, -0.15 }, { 0, 0.15 }, { -0.25, 0.45 }, { 0.25, 0.45 } };
                for (double[] s : seeds) g2.fill(new Ellipse2D.Double(s[0] * r - 1, s[1] * r - 1.3, 2, 2.6));
                g2.setColor(new Color(0x3FA14F));
                for (int i = -1; i <= 1; i++) g2.fill(new Ellipse2D.Double(i * r * 0.38 - r * 0.25, -r * 0.95, r * 0.5, r * 0.45));
                break;
            }
            case 1: { // 바나나: 노란 초승달
                Path2D.Double b = new Path2D.Double();
                b.moveTo(-r * 1.0, -r * 0.55);
                b.quadTo(-r * 0.6, r * 1.05, r * 1.05, r * 0.35);
                b.quadTo(-r * 0.15, r * 0.45, -r * 0.65, -r * 0.75);
                b.closePath();
                g2.setColor(new Color(0xF5CD2F));
                g2.fill(b);
                g2.setColor(new Color(0xC99A12));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(b);
                g2.setColor(new Color(0x6B4F1D));
                g2.fill(new Ellipse2D.Double(-r * 1.05, -r * 0.8, r * 0.35, r * 0.35));
                break;
            }
            case 2: { // 라임: 초록 동그라미 단면
                g2.setColor(new Color(0x5DAA2E));
                g2.fill(new Ellipse2D.Double(-r, -r, r * 2, r * 2));
                g2.setColor(new Color(0xC9EB8E));
                g2.fill(new Ellipse2D.Double(-r * 0.78, -r * 0.78, r * 1.56, r * 1.56));
                g2.setColor(new Color(0x86C24A));
                g2.setStroke(new BasicStroke(1f));
                for (int i = 0; i < 6; i++) {
                    double ang = Math.PI * i / 3;
                    g2.draw(new java.awt.geom.Line2D.Double(0, 0, Math.cos(ang) * r * 0.75, Math.sin(ang) * r * 0.75));
                }
                break;
            }
            default: { // 자두: 보라 동그라미 + 잎
                g2.setColor(new Color(0x7B3FA0));
                g2.fill(new Ellipse2D.Double(-r, -r * 0.85, r * 2, r * 1.9));
                g2.setColor(new Color(255, 255, 255, 90));
                g2.fill(new Ellipse2D.Double(-r * 0.6, -r * 0.55, r * 0.5, r * 0.45));
                g2.setColor(new Color(0x3FA14F));
                g2.fill(new Ellipse2D.Double(r * 0.05, -r * 1.25, r * 0.75, r * 0.4));
                break;
            }
        }
        g2.setTransform(old);
    }

    // ---- 손 ----
    // 손 그림: (x, y)가 손바닥 가운데, (dx, dy)는 손가락이 향하는 방향
    static void drawHand(Graphics2D g2, double x, double y, double dx, double dy, Color sleeve) {
        AffineTransform old = g2.getTransform();
        g2.translate(x, y);
        g2.rotate(Math.atan2(dy, dx) - Math.PI / 2); // 손가락이 +y 쪽을 보게 그린 걸 돌림
        g2.setColor(sleeve); // 소매
        g2.fill(new RoundRectangle2D.Double(-13, -46, 26, 30, 8, 8));
        g2.setColor(new Color(0xF6D2B5)); // 손바닥
        g2.fill(new RoundRectangle2D.Double(-12, -20, 24, 24, 12, 12));
        for (int i = 0; i < 4; i++) g2.fill(new RoundRectangle2D.Double(-11 + i * 6, -2, 5, 13 - Math.abs(i - 1.5) * 2, 5, 5)); // 손가락
        g2.fill(new RoundRectangle2D.Double(-17, -14, 7, 12, 6, 6)); // 엄지
        g2.setColor(new Color(0xD9A98A));
        g2.setStroke(new BasicStroke(0.9f));
        g2.draw(new RoundRectangle2D.Double(-12, -20, 24, 24, 12, 12));
        g2.setTransform(old);
    }

    // 카드 내기: 손이 뒷면 카드를 들고 나와서 내려놓고(뒤집힘) 돌아감
    private void drawFlip(Graphics2D g2, String p, int[] s, Anim a) {
        double f = Math.min(1, a.age() / (double) FLIP_MS);
        double rx = s[0] + s[2] * 70, ry = s[1] + s[3] * 70; // 손이 쉬는 자리 (탁자 밖)
        if (a.oldCard != null) drawCard(g2, s[0], s[1], a.oldCard, 1, 1f);
        int[] card = top.get(p);
        double hx, hy;
        if (f < 0.5) { // 들고 나옴
            double q = f / 0.5;
            double e = 1 - Math.pow(1 - q, 2);
            double cx = rx + (s[0] - rx) * e, cy = ry + (s[1] - ry) * e;
            drawBack(g2, cx, cy, 1);
            hx = cx + s[2] * 18;
            hy = cy + s[3] * 18;
        } else { // 뒤집어 놓고 돌아감
            double q = (f - 0.5) / 0.5;
            double flipX = Math.abs(Math.cos(Math.PI * Math.min(1, q * 1.6)));
            if (card != null) {
                if (q * 1.6 < 0.5) drawBack(g2, s[0], s[1], flipX);
                else drawCard(g2, s[0], s[1], card, flipX, 1f);
            }
            hx = s[0] + s[2] * 18 + (rx - s[0]) * q;
            hy = s[1] + s[3] * 18 + (ry - s[1]) * q;
        }
        if (f < 1) drawHand(g2, hx, hy, -s[2], -s[3], colorOf(p));
    }

    // 내는 척: 손이 반쯤 나왔다가 움찔하고 들어감 (카드는 안 나옴)
    private void drawFakeHand(Graphics2D g2, Anim a) {
        if (!players.contains(a.who)) return;
        int[] s = seatOf(a.who);
        double f = Math.min(1, a.age() / (double) FAKE_MS);
        if (f >= 1) return;
        double rx = s[0] + s[2] * 70, ry = s[1] + s[3] * 70;
        // 종 쪽으로 2/3쯤 나왔다가 움찔하고 들어감 (13주차 수정: 카드 자리 말고 종을 향해서)
        double reach = Math.sin(Math.PI * f) * (2 / 3.0) + Math.sin(f * Math.PI * 6) * 0.03;
        double dx = CX - rx, dy = CY - ry, len = Math.hypot(dx, dy);
        double ux = dx / len, uy = dy / len;
        double x = rx + dx * reach, y = ry + dy * reach;
        drawBack(g2, x + ux * 14, y + uy * 14, 1); // 들고 있는 것처럼 보이는 카드
        drawHand(g2, x - ux * 4, y - uy * 4, ux, uy, colorOf(a.who));
    }

    // 종 치기: 손이 종으로 뻗었다 돌아감 + 결과 글자
    private void drawRingHand(Graphics2D g2, Anim a) {
        if (!players.contains(a.who)) return;
        int[] s = seatOf(a.who);
        double f = Math.min(1, a.age() / (double) RING_MS);
        if (f < 1) {
            double reach = f < 0.3 ? f / 0.3 : 1 - (f - 0.3) / 0.7;
            double rx = s[0] + s[2] * 40, ry = s[1] + s[3] * 40;
            double x = rx + (CX - rx) * reach * 0.86, y = ry + (CY - ry) * reach * 0.86;
            drawHand(g2, x, y, CX - rx, CY - ry, colorOf(a.who));
        }
        if (a.age() < 1100) { // 결과 글자
            String t = a.result.equals("맞음") ? "땡! 1등" : a.result.equals("틀림") ? "틀림 -1" : "늦음";
            Color c = a.result.equals("맞음") ? new Color(0x2E9B74) : a.result.equals("틀림") ? BLOOD : UI.TEXT_SUB;
            g2.setFont(UI.font(Font.BOLD, 14f));
            int tw = g2.getFontMetrics().stringWidth(t);
            float y = (float) (s[1] - s[3] * 0 - CARD_H / 2.0 - 22 - a.age() / 60.0);
            if (s[3] < 0) y = (float) (s[1] + CARD_H / 2.0 + 30 - a.age() / 60.0);
            g2.setColor(new Color(255, 255, 255, 230));
            g2.fill(new RoundRectangle2D.Float(s[0] - tw / 2f - 8, y - 15, tw + 16, 21, 21, 21));
            g2.setColor(c);
            g2.drawString(t, s[0] - tw / 2f, y);
        }
    }

    // 피 -1: 자리에서 핏방울이 톡 떨어짐
    private void drawHurt(Graphics2D g2, Anim a) {
        if (!players.contains(a.who) || a.age() > HURT_MS) return;
        int[] s = seatOf(a.who);
        double f = a.age() / (double) HURT_MS;
        Composite oc = g2.getComposite();
        g2.setComposite(AlphaComposite.SrcOver.derive((float) Math.max(0, 1 - f)));
        boolean heal = a.kind.equals("heal"); // 피 +1: 방울이 위로 떠오름, -1: 아래로 떨어짐
        drawDrop(g2, s[0] + CARD_W / 2.0 + 8, s[1] - 20 + (heal ? -f * 28 : f * 34), 7, true);
        g2.setColor(heal ? new Color(0x2E9B74) : BLOOD);
        g2.setFont(UI.font(Font.BOLD, 15f));
        g2.drawString(heal ? "+1" : "-1", (float) (s[0] + CARD_W / 2.0 + 18), (float) (s[1] - 18 - f * 12));
        g2.setComposite(oc);
    }

    // 맞은 종 정산: 펼쳐진 카드가 전부 이긴 사람 쪽으로 쓸려감
    private void drawRound(Graphics2D g2) {
        for (Anim a : new ArrayList<>(anims)) {
            if (!a.kind.equals("round") || a.age() > ROUND_MS || !players.contains(a.who)) continue;
            int[] w = seatOf(a.who);
            double f = a.age() / (double) ROUND_MS;
            double e = f * f;
            for (Map.Entry<String, int[]> c : a.cards.entrySet()) {
                if (!players.contains(c.getKey())) continue;
                int[] s = seatOf(c.getKey());
                double x = s[0] + (w[0] + w[2] * 40 - s[0]) * e, y = s[1] + (w[1] + w[3] * 40 - s[1]) * e;
                drawCard(g2, x, y, c.getValue(), 1 - f * 0.4, (float) (1 - f));
            }
        }
    }

    // 가운데 종 (누가 치면 흔들림)
    private void drawBell(Graphics2D g2) {
        double wob = 0;
        for (Anim a : anims) if (a.kind.equals("ring") && a.age() < 500) wob = Math.sin(a.age() / 30.0) * (1 - a.age() / 500.0) * 0.25;
        AffineTransform old = g2.getTransform();
        g2.translate(CX, CY + 8);
        g2.rotate(wob);
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fill(new Ellipse2D.Double(-34, 14, 68, 14));
        g2.setColor(new Color(0x9C7A2A)); // 받침
        g2.fill(new RoundRectangle2D.Double(-32, 6, 64, 14, 12, 12));
        Path2D.Double dome = new Path2D.Double();
        dome.moveTo(-28, 8);
        dome.curveTo(-28, -30, 28, -30, 28, 8);
        dome.closePath();
        g2.setPaint(new java.awt.GradientPaint(-20, -24, new Color(0xFFE27A), 20, 8, new Color(0xE0A92A)));
        g2.fill(dome);
        g2.setColor(new Color(0xB98420));
        g2.setStroke(new BasicStroke(1.4f));
        g2.draw(dome);
        g2.setColor(new Color(0x8A6418)); // 누르는 꼭지
        g2.fill(new Ellipse2D.Double(-6, -27, 12, 9));
        g2.setColor(new Color(255, 255, 255, 150));
        g2.fill(new Ellipse2D.Double(-17, -17, 9, 12));
        g2.setTransform(old);
    }

    private void drawPopup(Graphics2D g2, int w, int h, String title, String sub) {
        g2.setColor(new Color(40, 30, 70, 90));
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 22, 22));
        float x = w / 2f - 150, y = 150;
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Float(x, y, 300, 210, 24, 24));
        g2.setColor(BLOOD);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new RoundRectangle2D.Float(x, y, 300, 210, 24, 24));
        for (int k = 0; k < 3; k++) drawDrop(g2, w / 2.0 - 22 + k * 22, y + 50, 8, false);
        g2.setColor(UI.TEXT);
        g2.setFont(UI.font(Font.BOLD, 22f));
        g2.drawString(title, (w - g2.getFontMetrics().stringWidth(title)) / 2, y + 100);
        g2.setColor(UI.TEXT_SUB);
        g2.setFont(UI.font(Font.PLAIN, 11.5f));
        g2.drawString(sub, (w - g2.getFontMetrics().stringWidth(sub)) / 2, y + 126);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(376, 540);
    }
}
