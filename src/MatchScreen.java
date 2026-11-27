// MatchScreen.java
// 미니게임 대기실 + 판 (12주차 윷놀이 화면을 13주차에 할리갈리도 같이 쓰게 뺌). YutScreen, HalliScreen이 물려받음
//   대기: 자리 4칸(나, 초대한 친구, 빈자리) + 오른쪽 위 [친구 초대] + 아래 [시작]
//         시작을 누르면 빈자리를 같은 성별 랜덤 매칭으로 채움. 최대 인원이 차면 바로 시작
//         최소 인원 이상 모인 파티(친구끼리)는 방장이 [바로 시작]으로 매칭 없이 시작할 수 있음
//         할리갈리 랜덤 매칭: 먼저 들어온 사람이 방장. 2명부터 [지금 시작], 2명째부터 1분 지나면 자동 시작,
//         4명이 차면 10초 뒤 자동 시작 (남은 초를 보여줌)
//   매칭 중: "N명 모였어요" + [매칭 취소]
//   게임 중: 판(GameView). 오른쪽 위 버튼은 [기권] (탈락, 등수가 정해졌으면 [나가기])
// 친구 = 내가 찜한 사람 + 1:1 대화한 사람 중 접속 중인 사람 (서버가 /X_FRIENDS로 골라서 줌)
// 서버 명령 앞부분(접두어)만 게임마다 다름: /YUT_, /HG_

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public abstract class MatchScreen extends AppFrame.Screen {

    private static final long serialVersionUID = 1L;
    private static final int W = AppFrame.W, H = AppFrame.SCREEN_H;

    protected final AppFrame app;
    private final GameView view;
    private final JComponent viewComp;
    final String key, prefix, gameName; // 화면 이름("yut"), 명령 앞부분("/YUT_"), "윷놀이"
    final int min, max;
    private long autoAt;                                // 매칭 중 자동 시작 예정 시각 (0이면 없음)
    private boolean host;                               // 매칭 중 내 자리의 방장 (제일 먼저 들어온 사람)

    // ===== 파티 상태 (서버 /X_PARTY 로만 바뀜) =====
    private String leader;                              // null이면 아직 파티 없음 (나 혼자)
    private final List<String> members = new ArrayList<>(), pending = new ArrayList<>();
    private boolean queued;
    private int waiting;                                // 매칭 중 내 자리(4명짜리)에 지금 모인 사람 수 (우리 파티 포함)
    private int dots;

    // ===== 부품 =====
    private final JButton topBtn = UI.soft("친구 초대");
    private final JButton soundBtn = new JButton(); // 소리 켜기/끄기 (게임 중에만 보임)
    private final JPanel lobby = new JPanel(null);
    private final Seat[] seats = new Seat[4];
    private final JLabel hint = UI.sub("");
    private final JButton mainBtn = UI.pill("시작하기", UI.LAVENDER, Color.WHITE);
    private final JButton subBtn = UI.soft("파티 나가기");
    private final FriendPicker picker;
    private final Timer anim;

    protected <V extends JComponent & GameView> MatchScreen(AppFrame app, String key, String prefix, String gameName,
            String title, String sub, int min, int max, V v, String[][] ruleRows) {
        super("game");
        this.app = app;
        this.key = key;
        this.prefix = prefix;
        this.gameName = gameName;
        this.min = min;
        this.max = max;
        view = v;
        viewComp = v;
        header(title, sub, app::goBack);

        topBtn.setMargin(new Insets(0, 0, 0, 0));
        topBtn.setBounds(W - 24 - 92, 28, 92, 34);
        add(topBtn);
        soundBtn.setIcon(new SpeakerIcon());
        soundBtn.setToolTipText("소리 켜기/끄기");
        soundBtn.setBackground(Color.WHITE);
        soundBtn.setFocusPainted(false);
        soundBtn.setMargin(new Insets(0, 0, 0, 0));
        soundBtn.setBounds(W - 24 - 92 - 44, 28, 38, 34);
        soundBtn.addActionListener(e -> {
            Sound.setMuted(!Sound.isMuted(), view.isStarted() && !view.isOver());
            soundBtn.repaint();
        });
        add(soundBtn);
        topBtn.addActionListener(e -> {
            if (view.isStarted() && !view.isOver()) app.send(prefix + "LEAVE"); // 게임 중이면 기권 (등수가 정해졌거나 탈락했으면 그냥 나가기)
            else openPicker();
        });

        // ---- 대기 화면 ----
        lobby.setOpaque(false);
        lobby.setBounds(0, 84, W, H - 84);
        add(lobby);
        JLabel t = UI.title(min == max ? "같이 할 " + max + "명" : "같이 할 " + min + "~" + max + "명", 15f);
        t.setBounds(24, 8, 200, 22);
        lobby.add(t);
        JLabel s = UI.sub("친구를 초대하지 않으면 전부 랜덤으로 만나요");
        s.setBounds(24, 30, 340, 18);
        lobby.add(s);
        int sw = (W - 48 - 10) / 2;
        for (int i = 0; i < 4; i++) {
            seats[i] = new Seat(i);
            seats[i].setBounds(24 + (i % 2) * (sw + 10), 58 + (i / 2) * 98, sw, 88);
            lobby.add(seats[i]);
        }
        Rules rules = new Rules(ruleRows);
        rules.setBounds(24, 262, W - 48, 150);
        lobby.add(rules);

        hint.setHorizontalAlignment(SwingConstants.CENTER);
        hint.setBounds(24, H - 84 - 128, W - 48, 20);
        lobby.add(hint);
        mainBtn.setFont(UI.font(Font.BOLD, 15f));
        mainBtn.setBounds(24, H - 84 - 102, W - 48, 48);
        lobby.add(mainBtn);
        subBtn.setBounds(24, H - 84 - 48, W - 48, 36);
        lobby.add(subBtn);
        mainBtn.addActionListener(e -> {
            if (canStartQueued()) app.send(prefix + "START_NOW"); // 매칭 자리의 방장: 모인 인원으로 지금 시작
            else if (queued) app.send(prefix + "CANCEL");
            else if (partySize() >= min) app.send(prefix + "START_NOW"); // 친구끼리 바로 시작
            else app.send(prefix + "QUEUE");
        });
        subBtn.addActionListener(e -> {
            if (canFillRandom()) app.send(prefix + "QUEUE"); // 친구 2~3명 + 빈자리는 랜덤으로
            else if (canStartQueued()) app.send(prefix + "CANCEL");
            else app.send(prefix + "PARTY_LEAVE");
        });

        // ---- 게임 판 ----
        viewComp.setBounds(12, 84, W - 24, H - 92);
        add(viewComp);
        view.onChange(this::refresh);

        // ---- 친구 고르기 (위에 덮는 카드) ----
        picker = new FriendPicker();
        picker.setBounds(20, 76, W - 40, 420);
        picker.setVisible(false);
        add(picker);
        setComponentZOrder(picker, 0);

        anim = new Timer(450, e -> {
            dots = (dots + 1) % 4;
            if (queued) refresh();
        });
        refresh();
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void onHide() {
        picker.setVisible(false);
    }

    private boolean amLeader() {
        return leader == null || leader.equals(app.myNick);
    }

    // ===================== 서버 메시지 =====================

    // /X_PARTY 방장 멤버들 초대중  또는  /X_PARTY -
    public void party(String rest) {
        String[] a = rest.split(" ");
        members.clear();
        pending.clear();
        if (a[0].equals("-")) {
            leader = null;
            queued = false;
        } else {
            leader = a[0];
            members.addAll(Arrays.asList(a[1].split(",")));
            if (a.length > 2 && !a[2].equals("-")) pending.addAll(Arrays.asList(a[2].split(",")));
        }
        refresh();
        if (picker.isVisible()) app.send(prefix + "FRIENDS"); // 초대 상태가 바뀌었으니 목록도 다시
    }

    // /X_QUEUED 모인인원 방장여부 남은초 (랜덤으로 들어온 사람이 생기면 빈자리가 하나씩 채워짐)
    public void queued(String rest) {
        String[] a = rest.trim().split(" ");
        queued = true;
        waiting = Integer.parseInt(a[0]);
        int left = a.length > 2 ? Integer.parseInt(a[2]) : -1;
        autoAt = left >= 0 ? System.currentTimeMillis() + left * 1000L : 0;
        host = a.length > 1 && a[1].equals("1");
        anim.start();
        refresh();
    }

    public void canceled(String nick) {
        queued = false;
        anim.stop();
        if (!nick.equals(app.myNick)) app.toast(nick + "님이 매칭을 취소했어요");
        refresh();
    }

    // /X_MATCH 게임번호 내이름 이름표
    public void match(String rest) {
        queued = false;
        anim.stop();
        leader = null; // 게임에 들어가면 파티는 끝 (서버도 같음)
        members.clear();
        pending.clear();
        picker.setVisible(false);
        view.match(rest);
        Sound.play("match");
        if (!key.equals(app.current())) {
            app.toast(gameName + " 상대를 찾았어요!");
            app.go(key, true);
        }
    }

    // 이 게임으로 온 서버 메시지 (AppFrame이 접두어를 보고 넘겨줌). 대기실 것 말고는 판으로
    public void route(String cmd, String rest) {
        switch (cmd.substring(prefix.length())) {
            case "PARTY": party(rest); return;
            case "QUEUED": queued(rest); return;
            case "CANCELED": canceled(rest); return;
            case "FRIENDS": friends(rest); return;
            case "MATCH": match(rest); return;
            case "INVITED": app.showInvite(this, rest.trim()); return;
            case "INVITE_GONE": app.inviteGone(rest.trim()); return;
            case "DECLINED": app.toast(rest.trim() + "님이 초대를 거절했어요"); return;
            case "FAIL": app.toast(app.reasonText(rest.trim())); return;
            default: onGame(cmd, rest);
        }
    }

    // /X_START 게임번호 ... 등
    public void onGame(String cmd, String rest) {
        String[] a = rest.split(" ", 2);
        view.onMessage(cmd, a[0], a.length > 1 ? a[1] : "");
    }

    public void friends(String data) {
        picker.fill(data);
    }

    public void reset() {
        leader = null;
        members.clear();
        pending.clear();
        queued = false;
        anim.stop();
        picker.setVisible(false);
        view.reset();
    }

    private void openPicker() {
        if (!amLeader()) {
            app.toast("방장만 초대할 수 있어요");
            return;
        }
        if (queued) {
            app.toast("매칭 중에는 초대할 수 없어요");
            return;
        }
        picker.fill(null);
        picker.setVisible(true);
        app.send(prefix + "FRIENDS");
    }

    // ===================== 화면 갱신 =====================

    private int partySize() {
        return leader == null ? 1 : members.size();
    }

    // 방장이고, 친구가 최소 인원 이상이지만 최대보다 적음 -> [바로 시작] 또는 [빈자리 랜덤으로 채우기]
    private boolean canFillRandom() {
        return !queued && amLeader() && partySize() >= min && partySize() < max;
    }

    // 매칭 중 내가 자리의 방장이고 최소 인원 이상 모임 -> [지금 시작 (N명)] (할리갈리)
    private boolean canStartQueued() {
        return queued && host && min < max && waiting >= min;
    }

    private void refresh() {
        boolean game = view.isStarted();
        viewComp.setVisible(game);
        lobby.setVisible(!game);
        topBtn.setText(game && !view.isOver() ? (view.amRanked() ? "나가기" : "기권") : "친구 초대");
        topBtn.setVisible(!(game && view.isOver()));
        soundBtn.setVisible(game);
        topBtn.setEnabled(game || (amLeader() && !queued));
        if (game) {
            repaint();
            return;
        }

        // 자리: 방장이 첫 칸, 그다음 멤버, 초대 중, 빈자리
        List<String[]> list = new ArrayList<>();
        if (leader == null) list.add(new String[] { app.myNick == null ? "나" : app.myNick, "나 / 방장" });
        else for (String m : members) {
            String tag = m.equals(app.myNick) ? "나" : "친구";
            if (m.equals(leader)) tag += " / 방장";
            list.add(new String[] { m, tag });
        }
        for (String p : pending) list.add(new String[] { p, "초대 중" });
        if (queued) { // 매칭 중: 랜덤으로 들어온 사람만큼 빈자리를 채워서 보여줌 (누군지는 게임에서도 안 알려줌)
            int found = Math.min(max, waiting) - list.size();
            for (int i = 0; i < found; i++) list.add(new String[] { "랜덤 플레이어", RANDOM });
        }
        for (int i = 0; i < 4; i++) {
            if (i < list.size()) seats[i].set(list.get(i)[0], list.get(i)[1]);
            else seats[i].set(null, queued ? "찾는 중" + ".".repeat(dots) : "빈자리");
        }

        int have = partySize();
        int empty = max - have;
        if (queued) {
            int n = Math.min(max, waiting);
            long sec = autoAt > 0 ? Math.max(0, (autoAt - System.currentTimeMillis() + 999) / 1000) : -1;
            String more;
            if (min < max && waiting < min) more = min + "명부터 시작돼요";
            else if (sec >= 0) more = (canStartQueued() ? "" : "방장이 시작하거나 ") + sec + "초 뒤 자동 시작";
            else more = "나머지를 찾는 중" + ".".repeat(dots);
            hint.setText(n + "/" + max + "명 모였어요. " + more);
            if (canStartQueued()) { // 자리의 방장: 모인 인원으로 지금 시작
                mainBtn.setText("지금 시작 (" + n + "명)");
                mainBtn.setBackground(UI.LAVENDER);
            } else {
                mainBtn.setText("매칭 취소");
                mainBtn.setBackground(new Color(0xB7B0D0));
            }
            mainBtn.setEnabled(true);
        } else if (amLeader() && have >= min) { // 친구끼리 최소 인원 이상: 바로 시작
            hint.setText(empty == 0 ? max + "명이 다 모였어요. 바로 시작할 수 있어요"
                    : "친구끼리 바로 시작하거나 빈자리를 랜덤으로 채워요");
            mainBtn.setText("바로 시작 (" + have + "명)");
            mainBtn.setBackground(UI.LAVENDER);
            mainBtn.setEnabled(true);
        } else if (amLeader()) {
            hint.setText("빈자리 " + empty + "명은 랜덤으로 채워요" + (pending.isEmpty() ? "" : " (답 안 한 초대는 취소돼요)"));
            mainBtn.setText("시작하기");
            mainBtn.setBackground(UI.LAVENDER);
            mainBtn.setEnabled(true);
        } else {
            hint.setText(leader + "님이 시작하길 기다리는 중이에요");
            mainBtn.setText("시작하기");
            mainBtn.setBackground(new Color(0xB7B0D0));
            mainBtn.setEnabled(false);
        }
        subBtn.setText(canFillRandom() ? "빈자리 랜덤으로 채우기" : canStartQueued() ? "매칭 취소" : "파티 나가기");
        subBtn.setVisible(canFillRandom() || canStartQueued() || (!queued && leader != null && members.size() > 1));
        repaint();
    }

    // ===================== 자리 한 칸 =====================

    static final String RANDOM = "입장했어요"; // 랜덤 매칭으로 들어온 자리 표시

    class Seat extends JComponent {
        private static final long serialVersionUID = 1L;
        private final int index;
        private String nick, tag = "빈자리";
        private long popAt;                     // 랜덤 플레이어가 들어온 시각 (톡 튀어나오는 모션)
        private final Timer pop = new Timer(16, e -> {
            repaint();
            if (System.currentTimeMillis() - popAt > 420) ((Timer) e.getSource()).stop();
        });

        Seat(int index) {
            this.index = index;
            Widgets.onClick(this, () -> {
                if (nick == null && !queued) openPicker(); // 빈자리를 눌러도 초대
            });
        }

        void set(String nick, String tag) {
            if (RANDOM.equals(tag) && !RANDOM.equals(this.tag)) { // 새로 채워진 자리
                popAt = System.currentTimeMillis();
                pop.restart();
            }
            this.nick = nick;
            this.tag = tag;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            int w = getWidth(), h = getHeight();
            boolean filled = nick != null && !tag.equals("초대 중");
            Color c = YutView.PLAYER[index];
            if (nick == null) { // 빈자리: 점선 테두리
                g2.setColor(new Color(255, 255, 255, 110));
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 20, 20));
                g2.setColor(new Color(0xC9C2E4));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5f, 5f }, 0f));
                g2.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, 20, 20));
                g2.setColor(new Color(0xC9C2E4));
                g2.setFont(UI.font(Font.BOLD, 22f));
                g2.drawString("?", 22, 52);
                g2.setColor(UI.TEXT_SUB);
                g2.setFont(UI.font(Font.BOLD, 12.5f));
                g2.drawString(tag, 52, 40);
                g2.setFont(UI.font(Font.PLAIN, 10.5f));
                g2.drawString(queued ? "랜덤 매칭" : "누르면 친구 초대", 52, 58);
            } else {
                g2.setColor(new Color(255, 255, 255, filled ? 200 : 130));
                g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 20, 20));
                g2.setColor(filled ? new Color(c.getRed(), c.getGreen(), c.getBlue(), 120) : new Color(0xDAD5EE));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, 20, 20));
                if (RANDOM.equals(tag)) { // 랜덤 플레이어: 사람 모양, 들어올 때 톡 커짐
                    double t = Math.min(1, (System.currentTimeMillis() - popAt) / 420.0);
                    double sc = t < 1 ? 1 + 2.2 * Math.pow(t - 1, 3) + 1.2 * Math.pow(t - 1, 2) : 1; // 살짝 넘쳤다가 제자리
                    java.awt.geom.AffineTransform old = g2.getTransform();
                    g2.translate(34, 44);
                    g2.scale(sc, sc);
                    g2.setColor(c);
                    g2.fill(new Ellipse2D.Float(-20, -20, 40, 40));
                    g2.setColor(new Color(255, 255, 255, 230));
                    g2.fill(new Ellipse2D.Float(-6.5f, -13, 13, 13));
                    g2.fill(new java.awt.geom.Arc2D.Float(-12, 1, 24, 22, 0, 180, java.awt.geom.Arc2D.PIE));
                    g2.setTransform(old);
                } else {
                    g2.setColor(filled ? c : new Color(0xDAD5EE));
                    g2.fill(new Ellipse2D.Float(14, 24, 40, 40));
                    g2.setColor(Color.WHITE);
                    g2.setFont(UI.font(Font.BOLD, 16f));
                    String first = nick.substring(0, 1);
                    int tw = g2.getFontMetrics().stringWidth(first);
                    g2.drawString(first, 34 - tw / 2f, 50);
                }
                g2.setColor(UI.TEXT);
                float fs = 13f;
                g2.setFont(UI.font(Font.BOLD, fs));
                while (fs > 10f && g2.getFontMetrics().stringWidth(nick) > w - 72) g2.setFont(UI.font(Font.BOLD, fs -= 0.5f));
                g2.drawString(nick, 62, 40);
                g2.setColor(tag.equals("초대 중") ? UI.TEXT_SUB : c);
                g2.setFont(UI.font(Font.PLAIN, 11f));
                g2.drawString(tag.equals("초대 중") ? "초대 중" + ".".repeat(1 + dots % 3) : tag, 62, 58);
            }
            g2.dispose();
        }
    }

    // ===================== 규칙 카드 =====================

    static class Rules extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String[][] rows; // { 항목, 설명, 색(#RRGGBB 또는 null) }

        Rules(String[][] rows) {
            this.rows = rows;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 165));
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 20, 20));
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 13f));
            g2.drawString("이번 판 규칙", 16, 26);
            for (int i = 0; i < rows.length; i++) {
                int y = 50 + i * 20;
                g2.setColor(rows[i].length > 2 && rows[i][2] != null ? Color.decode(rows[i][2]) : UI.LAVENDER);
                g2.setFont(UI.font(Font.BOLD, 11.5f));
                g2.drawString(rows[i][0], 16, y);
                g2.setColor(UI.TEXT_SUB);
                g2.setFont(UI.font(Font.PLAIN, 11.5f));
                g2.drawString(rows[i][1], 56, y);
            }
            g2.dispose();
        }
    }

    // ===================== 친구 고르기 =====================

    class FriendPicker extends UI.GlassPanel {
        private static final long serialVersionUID = 1L;
        private final Screens.RowList list = new Screens.RowList();

        // 뒤에 대기 화면이 비치지 않게 흰 바탕을 먼저 깔고 유리 효과
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            g2.setColor(new Color(0xFCFBFF));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 5, 22, 22));
            g2.dispose();
            super.paintComponent(g);
        }

        FriendPicker() {
            super(22);
            JLabel t = UI.title("친구 초대", 17f);
            t.setBounds(20, 16, 200, 26);
            add(t);
            JLabel s = UI.sub("찜했거나 1:1 대화한 사람 중 접속한 친구");
            s.setBounds(20, 42, 300, 18);
            add(s);
            JButton close = UI.soft("닫기");
            close.setMargin(new Insets(0, 0, 0, 0));
            close.setBounds(W - 40 - 20 - 56, 18, 56, 30);
            close.addActionListener(e -> setVisible(false));
            add(close);
            list.scroll.setBounds(6, 70, W - 52, 336);
            add(list.scroll);
        }

        // 닉:가능|바쁨|초대중,...  null이면 불러오는 중
        void fill(String data) {
            list.clear();
            int n = 0;
            if (data == null) {
                list.empty("불러오는 중...");
            } else if (!data.equals("-")) {
                for (String e : data.split(",")) {
                    String[] p = e.split(":");
                    if (p.length != 2) continue;
                    ChatScreens.Row r = new ChatScreens.Row(54);
                    JLabel name = UI.title(p[0], 13.5f);
                    name.setBounds(16, 8, 200, 20);
                    r.add(name);
                    JLabel st = UI.sub(p[1].equals("가능") ? "초대할 수 있어요" : p[1].equals("초대중") ? "초대했어요. 답을 기다리는 중" : "다른 게임 중이에요");
                    st.setBounds(16, 28, 220, 16);
                    r.add(st);
                    JButton b = p[1].equals("가능") ? UI.pill("초대", UI.LAVENDER, Color.WHITE) : UI.soft(p[1].equals("초대중") ? "대기" : "바쁨");
                    b.setMargin(new Insets(0, 0, 0, 0));
                    b.setEnabled(p[1].equals("가능"));
                    b.setBounds(W - 60 - 76, 12, 62, 30);
                    b.addActionListener(ev -> app.send(prefix + "INVITE " + p[0]));
                    r.add(b);
                    list.add(r);
                    n++;
                }
            }
            if (data != null && n == 0)
                list.empty("<html><center>지금 초대할 수 있는 친구가 없어요<br>찜하거나 1:1 대화한 사람이 접속하면 보여요</center></html>");
            list.done();
        }
    }

    // 스피커 그림 (소리 끄면 X)
    static class SpeakerIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            g2.translate(x, y);
            g2.setColor(UI.LAVENDER);
            java.awt.geom.Path2D.Double sp = new java.awt.geom.Path2D.Double();
            sp.moveTo(2, 6);
            sp.lineTo(6, 6);
            sp.lineTo(11, 2);
            sp.lineTo(11, 16);
            sp.lineTo(6, 12);
            sp.lineTo(2, 12);
            sp.closePath();
            g2.fill(sp);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (Sound.isMuted()) {
                g2.drawLine(14, 6, 19, 12);
                g2.drawLine(19, 6, 14, 12);
            } else {
                g2.draw(new java.awt.geom.Arc2D.Double(9, 5, 7, 8, -55, 110, java.awt.geom.Arc2D.OPEN));
                g2.draw(new java.awt.geom.Arc2D.Double(9, 2, 12, 14, -55, 110, java.awt.geom.Arc2D.OPEN));
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 21;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    // 게임 판
    protected GameView view() {
        return view;
    }
}
