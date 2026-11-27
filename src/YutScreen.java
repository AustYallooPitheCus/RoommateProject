// YutScreen.java
// 게임 탭 > 도파민 윷놀이 (12주차)
// 윷놀이는 이 화면에서만 함. 무조건 4명
//   대기: 자리 4칸(나, 초대한 친구, 빈자리) + 오른쪽 위 [친구 초대] + 아래 [시작]
//         시작을 누르면 빈자리만 같은 성별 랜덤(익명) 매칭으로 채움. 4명이 다 친구면 바로 시작
//   매칭 중: "상대를 찾는 중" + [매칭 취소]
//   게임 중: YutView (판). 오른쪽 위 버튼은 [기권]
// 친구 = 내가 찜한 사람 + 1:1 대화한 사람 중 접속 중인 사람 (서버가 /YUT_FRIENDS로 골라서 줌)

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

public class YutScreen extends AppFrame.Screen {

    private static final long serialVersionUID = 1L;
    private static final int W = AppFrame.W, H = AppFrame.SCREEN_H;

    private final AppFrame app;
    final YutView view;

    // ===== 파티 상태 (서버 /YUT_PARTY 로만 바뀜) =====
    private String leader;                              // null이면 아직 파티 없음 (나 혼자)
    private final List<String> members = new ArrayList<>(), pending = new ArrayList<>();
    private boolean queued;
    private int waiting;                                // 매칭 대기 중인 사람 수 (같은 성별 전체)
    private int dots;

    // ===== 부품 =====
    private final JButton topBtn = UI.soft("친구 초대");
    private final JPanel lobby = new JPanel(null);
    private final Seat[] seats = new Seat[4];
    private final JLabel hint = UI.sub("");
    private final JButton mainBtn = UI.pill("시작하기", UI.LAVENDER, Color.WHITE);
    private final JButton subBtn = UI.soft("파티 나가기");
    private final FriendPicker picker;
    private final Timer anim;

    public YutScreen(AppFrame app) {
        super("game");
        this.app = app;
        view = new YutView(app);
        header("도파민 윷놀이", "4명이 모이면 시작해요", app::goBack);

        topBtn.setMargin(new Insets(0, 0, 0, 0));
        topBtn.setBounds(W - 24 - 92, 28, 92, 34);
        add(topBtn);
        topBtn.addActionListener(e -> {
            if (view.isStarted() && !view.isOver()) app.send("/YUT_LEAVE"); // 게임 중이면 기권 (등수가 정해졌으면 그냥 나가기)
            else openPicker();
        });

        // ---- 대기 화면 ----
        lobby.setOpaque(false);
        lobby.setBounds(0, 84, W, H - 84);
        add(lobby);
        JLabel t = UI.title("같이 할 4명", 15f);
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
        Rules rules = new Rules();
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
            if (queued) app.send("/YUT_CANCEL");
            else app.send("/YUT_QUEUE");
        });
        subBtn.addActionListener(e -> app.send("/YUT_PARTY_LEAVE"));

        // ---- 게임 판 ----
        view.setBounds(12, 84, W - 24, H - 92);
        add(view);
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

    // /YUT_PARTY 방장 멤버들 초대중  또는  /YUT_PARTY -
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
        if (picker.isVisible()) app.send("/YUT_FRIENDS"); // 초대 상태가 바뀌었으니 목록도 다시
    }

    // /YUT_QUEUED 대기인원
    public void queued(String n) {
        queued = true;
        waiting = Integer.parseInt(n.trim());
        anim.start();
        refresh();
    }

    public void canceled(String nick) {
        queued = false;
        anim.stop();
        if (!nick.equals(app.myNick)) app.toast(nick + "님이 매칭을 취소했어요");
        refresh();
    }

    // /YUT_MATCH 게임번호 내이름 이름표
    public void match(String rest) {
        queued = false;
        anim.stop();
        leader = null; // 게임에 들어가면 파티는 끝 (서버도 같음)
        members.clear();
        pending.clear();
        picker.setVisible(false);
        view.match(rest);
        if (!"yut".equals(app.current())) {
            app.toast("윷놀이 상대를 찾았어요!");
            app.go("yut", true);
        }
    }

    // /YUT_START 게임번호 ... 등
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
        app.send("/YUT_FRIENDS");
    }

    // ===================== 화면 갱신 =====================

    private void refresh() {
        boolean game = view.isStarted();
        view.setVisible(game);
        lobby.setVisible(!game);
        topBtn.setText(game && !view.isOver() ? (view.amRanked() ? "나가기" : "기권") : "친구 초대");
        topBtn.setVisible(!(game && view.isOver()));
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
        for (int i = 0; i < 4; i++) {
            if (i < list.size()) seats[i].set(list.get(i)[0], list.get(i)[1]);
            else seats[i].set(null, queued ? "찾는 중" + ".".repeat(dots) : "빈자리");
        }

        int have = leader == null ? 1 : members.size();
        int empty = 4 - have;
        if (queued) {
            hint.setText("상대를 찾는 중이에요" + ".".repeat(dots) + "  (지금 대기 " + waiting + "명)");
            mainBtn.setText("매칭 취소");
            mainBtn.setBackground(new Color(0xB7B0D0));
            mainBtn.setEnabled(true);
        } else if (amLeader()) {
            hint.setText(empty == 0 ? "4명이 다 모였어요. 바로 시작할 수 있어요"
                    : "빈자리 " + empty + "명은 랜덤으로 채워요" + (pending.isEmpty() ? "" : " (답 안 한 초대는 취소돼요)"));
            mainBtn.setText(empty == 0 ? "바로 시작" : "시작하기");
            mainBtn.setBackground(UI.LAVENDER);
            mainBtn.setEnabled(true);
        } else {
            hint.setText(leader + "님이 시작하길 기다리는 중이에요");
            mainBtn.setText("시작하기");
            mainBtn.setBackground(new Color(0xB7B0D0));
            mainBtn.setEnabled(false);
        }
        subBtn.setVisible(leader != null && members.size() > 1);
        repaint();
    }

    // ===================== 자리 한 칸 =====================

    class Seat extends JComponent {
        private static final long serialVersionUID = 1L;
        private final int index;
        private String nick, tag = "빈자리";

        Seat(int index) {
            this.index = index;
            Widgets.onClick(this, () -> {
                if (nick == null && !queued) openPicker(); // 빈자리를 눌러도 초대
            });
        }

        void set(String nick, String tag) {
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
                g2.setColor(filled ? c : new Color(0xDAD5EE));
                g2.fill(new Ellipse2D.Float(14, 24, 40, 40));
                g2.setColor(Color.WHITE);
                g2.setFont(UI.font(Font.BOLD, 16f));
                String first = nick.substring(0, 1);
                int tw = g2.getFontMetrics().stringWidth(first);
                g2.drawString(first, 34 - tw / 2f, 50);
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
            String[][] rows = {
                { "말", "한 사람당 4개, 출발점을 지나가야 완주" },
                { "지옥", "불구덩이에 멈추면 그 말은 출발 전으로" },
                { "잉태", "우물에 멈추면 대기 말 하나가 나와서 업힘" },
                { "낙", "가끔(5%) 윷이 판 밖으로, 차례 끝" },
                { "시간", "한 차례 20초, 넘기면 자동으로 진행" },
            };
            for (int i = 0; i < rows.length; i++) {
                int y = 50 + i * 20;
                g2.setColor(i == 1 ? YutView.HELL : i == 2 ? YutView.BIRTH : UI.LAVENDER);
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
                    b.addActionListener(ev -> app.send("/YUT_INVITE " + p[0]));
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
}
