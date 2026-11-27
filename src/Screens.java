// Screens.java
// 룸메 찾기 쪽 화면들 (11주차, 프로토타입 영상 순서대로)
//   Browse   홈. 같은 성별 회원 전체 (궁합 높은 순) + 검색, 상세 필터
//            (처음엔 홈 카드 + 랜덤매칭이 따로 있었는데 빼고 홈에서 바로 둘러보게 바꿈)
//   Detail   한 사람 프로필 자세히
//   Likes    내가 찜한 사람
//   Menu     내 프로필 수정, 환경설정, 문의사항, 로그아웃

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class Screens {

    static final int W = AppFrame.W;

    // 세로로 줄을 쌓는 스크롤 목록 (둘러보기, 찜, 채팅 목록에서 같이 씀)
    static class RowList {
        final JPanel box = new JPanel();
        final JScrollPane scroll;

        RowList() {
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setOpaque(false);
            JPanel holder = new JPanel(new java.awt.BorderLayout()); // 줄이 적어도 위에서부터 쌓이게
            holder.setOpaque(false);
            holder.add(box, java.awt.BorderLayout.NORTH);
            scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            UI.clear(scroll);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
        }

        void clear() {
            box.removeAll();
        }

        void add(JComponent c) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(6));
        }

        void empty(String msg) {
            JLabel l = UI.sub(msg);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setPreferredSize(new Dimension(W - 60, 120));
            l.setMaximumSize(new Dimension(W - 60, 120));
            add(l);
        }

        void done() {
            box.revalidate();
            box.repaint();
        }
    }

    // ===================== 홈 = 룸메 둘러보기 =====================
    // 같은 성별 회원 전체를 궁합 높은 순으로. 검색 + 상세 필터 (거르는 건 화면에서만)
    // 필터는 프로필 설정에서 고르는 선택지를 전부 그대로 고를 수 있음
    //   한 항목 안에서 여러 개 고르면 "그중 하나라도", 항목끼리는 "전부 만족"
    //   학년, 학과는 비공개로 해둔 사람도 있어서 [비공개] 칸을 따로 둠
    public static class Browse extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;

        // 필터 항목 하나 (학년, 학과, 설문 6개, MBTI 글자 4쌍)
        static class Filter {
            final String title; // null이면 제목 없이 바로 앞 항목 밑에 붙음 (MBTI 두 번째 줄부터)
            final String[] labels;
            final java.util.function.ToIntFunction<Profile> value; // 이 사람이 몇 번 칸인지
            final JToggleButton[] chips;

            Filter(String title, String[] labels, java.util.function.ToIntFunction<Profile> value) {
                this.title = title;
                this.labels = labels;
                this.value = value;
                chips = new JToggleButton[labels.length];
            }

            int selectedCount() {
                int n = 0;
                for (JToggleButton c : chips) if (c.isSelected()) n++;
                return n;
            }

            boolean pass(Profile p) {
                if (selectedCount() == 0) return true; // 아무것도 안 골랐으면 전부 통과
                int v = value.applyAsInt(p);
                return v >= 0 && v < chips.length && chips[v].isSelected();
            }
        }

        private final AppFrame app;
        private final Avatar myAvatar = new Avatar(0, 46);
        private final JLabel hello = UI.title("", 20f);
        private final JTextField search = new JTextField();
        private final JButton filterBtn, reset;
        private final JLabel summary = UI.sub("");
        private final RowList list = new RowList();
        private final JPanel sheet;
        private final JButton sheetClose;
        private final ArrayList<Filter> filters = new ArrayList<>();
        private List<Member> all = new ArrayList<>();
        private final ArrayList<Widgets.MemberRow> rows = new ArrayList<>();

        public Browse(AppFrame app) {
            super("home");
            this.app = app;
            myAvatar.setLocation(22, 18);
            add(myAvatar);
            hello.setBounds(80, 18, 290, 28);
            add(hello);
            JLabel sub = UI.sub("궁합 높은 순으로 보여줘요. 누르면 프로필이 나와요");
            sub.setBounds(80, 44, 300, 20);
            add(sub);

            search.putClientProperty("JTextField.placeholderText", "닉네임 검색");
            search.putClientProperty("JTextField.showClearButton", true);
            search.setBounds(20, 80, W - 132, 38);
            add(search);
            filterBtn = UI.pill("필터", UI.LAVENDER, Color.WHITE);
            filterBtn.setMargin(new Insets(0, 0, 0, 0));
            filterBtn.setBounds(W - 104, 80, 84, 38);
            add(filterBtn);
            filterBtn.addActionListener(e -> showSheet(!sheetVisible()));

            summary.setBounds(24, 124, W - 140, 22);
            add(summary);
            reset = UI.soft("필터 초기화");
            reset.setFont(UI.font(Font.BOLD, 11f));
            reset.setMargin(new Insets(0, 0, 0, 0));
            reset.setBounds(W - 110, 122, 90, 26);
            add(reset);
            reset.addActionListener(e -> clearFilters());

            list.scroll.setBounds(10, 152, W - 20, AppFrame.SCREEN_H - 158);
            add(list.scroll);

            // ---- 필터 항목 ----
            String[] grades = Arrays.copyOf(Profile.GRADES, Profile.GRADES.length + 1);
            grades[grades.length - 1] = "비공개";
            filters.add(new Filter("학년", grades, p -> p.grade < 0 ? Profile.GRADES.length : p.grade));
            String[] depts = Arrays.copyOf(Profile.DEPTS, Profile.DEPTS.length + 1);
            depts[depts.length - 1] = "비공개";
            filters.add(new Filter("학과", depts, p -> p.dept < 0 ? Profile.DEPTS.length : p.dept));
            for (int q = 0; q < 6; q++) {
                final int qq = q;
                filters.add(new Filter(Profile.QUESTIONS[q], Profile.OPTIONS[q], p -> p.answers[qq]));
            }
            String[][] letters = { { "E", "I" }, { "S", "N" }, { "T", "F" }, { "J", "P" } };
            for (int k = 0; k < 4; k++) {
                final int kk = k;
                filters.add(new Filter(k == 0 ? "MBTI" : null, letters[k],
                        p -> p.mbti.charAt(kk) == letters[kk][0].charAt(0) ? 0 : 1));
            }

            // ---- 필터 창 (목록 위에 덮어서 보여줌) ----
            sheet = new UI.GlassPanel(22) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(java.awt.Graphics g) {
                    g.setColor(new Color(0xFBF9FF)); // 뒤 목록이 비치지 않게 바탕을 먼저 칠함
                    ((java.awt.Graphics2D) g).setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 22, 22);
                    super.paintComponent(g);
                }
            };
            int sw = W - 24, sh = AppFrame.SCREEN_H - 132;
            sheet.setBounds(12, 124, sw, sh);
            JPanel inner = new JPanel(null);
            inner.setOpaque(false);
            int y = 14, x0 = 18, iw = sw - 18 - 28;
            for (Filter f : filters) {
                if (f.title != null) {
                    JLabel t = UI.title(f.title, 13f);
                    t.setBounds(x0, y, iw, 20);
                    inner.add(t);
                    y += 24;
                }
                for (int i = 0; i < f.labels.length; i++) {
                    f.chips[i] = chip(f.labels[i]);
                    f.chips[i].addActionListener(e -> rebuild());
                }
                if (f.title != null && !f.title.equals("MBTI")) {
                    y = flow(inner, f.chips, x0, iw, y) + 16;
                    continue;
                }
                // MBTI는 E/I, S/N, T/F, J/P 네 쌍을 한 줄에
                int k = filters.indexOf(f) - (filters.size() - 4);
                int pw = (iw - 3 * 12) / 4;
                for (int j = 0; j < 2; j++) {
                    f.chips[j].setBounds(x0 + k * (pw + 12) + j * (pw / 2), y, pw / 2, 30);
                    inner.add(f.chips[j]);
                }
                if (k == 3) y += 46;
            }
            inner.setPreferredSize(new Dimension(sw - 12, y + 6));
            JScrollPane sp = new JScrollPane(inner, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            UI.clear(sp);
            sp.getVerticalScrollBar().setUnitIncrement(18);
            sp.setBounds(4, 6, sw - 10, sh - 74);
            sheet.add(sp);
            JButton sheetReset = UI.soft("초기화");
            sheetReset.setBounds(16, sh - 60, 96, 42);
            sheet.add(sheetReset);
            sheetReset.addActionListener(e -> clearFilters());
            sheetClose = UI.pill("", UI.LAVENDER, Color.WHITE);
            sheetClose.setBounds(120, sh - 60, sw - 136, 42);
            sheet.add(sheetClose);
            sheetClose.addActionListener(e -> showSheet(false));
            sheet.setVisible(false);
            add(sheet);
            setComponentZOrder(sheet, 0);

            search.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    rebuild();
                }

                public void removeUpdate(DocumentEvent e) {
                    rebuild();
                }

                public void changedUpdate(DocumentEvent e) {
                    rebuild();
                }
            });
            rebuild();
        }

        private static JToggleButton chip(String text) {
            JToggleButton b = new JToggleButton(text);
            b.setFont(UI.font(Font.PLAIN, 12f));
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setFocusPainted(false);
            b.setMargin(new Insets(0, 4, 0, 4));
            return b;
        }

        // 글자 길이에 맞춰 왼쪽부터 놓고 줄이 넘치면 다음 줄로 (SetupScreen이랑 같은 방식)
        private static int flow(JPanel p, JToggleButton[] bs, int x0, int w, int y) {
            java.awt.FontMetrics fm = bs[0].getFontMetrics(bs[0].getFont());
            int x = x0;
            for (JToggleButton b : bs) {
                int bw = fm.stringWidth(b.getText()) + 28;
                if (x + bw > x0 + w) {
                    x = x0;
                    y += 38;
                }
                b.setBounds(x, y, bw, 30);
                p.add(b);
                x += bw + 6;
            }
            return y + 30;
        }

        private boolean sheetVisible() {
            return sheet.isVisible();
        }

        private void showSheet(boolean on) {
            sheet.setVisible(on);
            summary.setVisible(!on);
            reset.setVisible(!on && activeCount() > 0);
            repaint();
        }

        private int activeCount() {
            int n = 0;
            for (Filter f : filters) if (f.selectedCount() > 0) n++;
            return n;
        }

        private void clearFilters() {
            for (Filter f : filters) for (JToggleButton c : f.chips) c.setSelected(false);
            rebuild();
        }

        // 내 프로필 바뀌면 위쪽 인사도 바꿈
        public void refresh() {
            if (app.me == null) return;
            myAvatar.setIndex(app.me.avatar);
            hello.setText("안녕, " + app.myNick + "님");
        }

        @Override
        public void onShow() {
            refresh();
            app.send("/BROWSE");
        }

        @Override
        public void onHide() {
            showSheet(false);
        }

        public void setList(List<Member> ms) {
            all = ms;
            rebuild();
        }

        private void rebuild() {
            list.clear();
            rows.clear();
            String q = search.getText().trim();
            for (Member m : all) {
                if (!q.isEmpty() && !m.nick.contains(q)) continue;
                boolean ok = true;
                for (Filter f : filters) if (!f.pass(m.profile)) ok = false;
                if (!ok) continue;
                Widgets.MemberRow r = new Widgets.MemberRow(m, () -> app.detail.open(m.nick),
                        () -> app.send("/LIKE " + m.nick + " " + (m.liked ? 0 : 1)));
                rows.add(r);
                list.add(r);
            }
            if (rows.isEmpty()) list.empty(all.isEmpty() ? "아직 다른 회원이 없어요" : "조건에 맞는 회원이 없어요");
            list.done();

            int n = activeCount();
            filterBtn.setText(n > 0 ? "필터 " + n : "필터");
            summary.setText(n > 0 ? "필터 " + n + "개 적용 중 / " + rows.size() + "명" : "전체 " + rows.size() + "명");
            reset.setVisible(!sheet.isVisible() && n > 0);
            sheetClose.setText(rows.size() + "명 보기");
        }

        public void liked(String nick, boolean on) {
            for (Widgets.MemberRow r : rows) if (r.member.nick.equals(nick)) r.setLiked(on);
        }
    }

    // ===================== 프로필 자세히 =====================
    public static class Detail extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;
        private final AppFrame app;
        private final Widgets.MemberCard card = new Widgets.MemberCard();
        private final Widgets.Heart heart;
        private final JLabel status = UI.sub("");
        private Member m;

        public Detail(AppFrame app) {
            super("home");
            this.app = app;
            header("프로필", null, app::goBack);
            status.setBounds(48, 54, 300, 20);
            add(status);
            card.setBounds(20, 96, W - 40, 370);
            add(card);
            heart = new Widgets.Heart(30, () -> {
                if (m != null) app.send("/LIKE " + m.nick + " " + (m.liked ? 0 : 1));
            });
            heart.setLocation(W - 70, 434);
            add(heart);
            setComponentZOrder(heart, 0);
            JButton message = UI.pill("메시지 보내기", UI.LAVENDER, Color.WHITE);
            message.setBounds(20, 484, W - 40, 46);
            add(message);
            message.addActionListener(e -> {
                if (m != null) app.dm.open(m.nick, m.profile.avatar);
            });
        }

        // 목록에서 누르면 서버에 최신 정보 요청 -> /USER_INFO 오면 show
        public void open(String nick) {
            m = null;
            card.setVisible(false);
            heart.setVisible(false);
            status.setText("불러오는 중...");
            app.send("/USER " + nick);
            app.go("detail", true);
        }

        public void show(Member m) {
            this.m = m;
            card.show(m.nick, m.profile, m.score);
            card.setVisible(true);
            heart.setOn(m.liked);
            heart.setVisible(true);
            status.setText("");
        }

        public void liked(String nick, boolean on) {
            if (m != null && m.nick.equals(nick)) {
                m.liked = on;
                heart.setOn(on);
            }
        }
    }

    // ===================== 찜 =====================
    public static class Likes extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;
        private final AppFrame app;
        private final RowList list = new RowList();

        public Likes(AppFrame app) {
            super("likes");
            this.app = app;
            header("찜한 룸메", "하트를 누른 사람이 여기 모여요");
            list.scroll.setBounds(10, 88, W - 20, AppFrame.SCREEN_H - 96);
            add(list.scroll);
        }

        @Override
        public void onShow() {
            app.send("/LIKES");
        }

        public void setList(List<Member> ms) {
            list.clear();
            for (Member m : ms) {
                list.add(new Widgets.MemberRow(m, () -> app.detail.open(m.nick),
                        () -> app.send("/LIKE " + m.nick + " " + (m.liked ? 0 : 1))));
            }
            if (ms.isEmpty()) list.empty("아직 찜한 사람이 없어요");
            list.done();
        }

        // 여기서 하트를 끄면 목록에서 빠지게 다시 받아옴
        public void liked(String nick, boolean on) {
            if ("likes".equals(app.current())) app.send("/LIKES");
        }
    }

    // ===================== 게임 탭 (12주차) =====================
    // 게임 고르는 곳. 미니게임은 이 탭에서만 함 (윷놀이를 누르면 윷놀이 화면으로)
    public static class Games extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;

        public Games(AppFrame app) {
            super("game");
            header("미니게임", "같이 놀면서 룸메랑 친해져요");
            String[][] games = {
                { "yut", "도파민 윷놀이", "4명 / 랜덤 매칭, 친구 초대", "지금 가능" },
                { "halli", "죽음의 피흘리기 할리갈리", "2~4명 / 체력제, 페이크 액션", "14주차 오픈" },
                { "agree", "동의하기 게임", "혼자 / 가입할 때 했던 약관 퍼즐", "14주차 오픈" },
            };
            for (int i = 0; i < games.length; i++) {
                final String[] gm = games[i];
                Runnable click = gm[0].equals("yut") ? () -> app.go("yut", true)
                        : () -> app.toast(gm[1] + "은(는) 준비 중이에요 (" + gm[3] + ")");
                GameCard c = new GameCard(gm[0], gm[1], gm[2], gm[3], click);
                c.setBounds(20, 88 + i * 104, W - 40, 94);
                add(c);
            }
        }
    }

    // 게임 카드 한 장 (왼쪽에 그림, 오른쪽에 이름과 설명, 오른쪽 위에 오픈 예정 표시)
    static class GameCard extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String kind, name, desc, badge;

        GameCard(String kind, String name, String desc, String badge, Runnable onClick) {
            this.kind = kind;
            this.name = name;
            this.desc = desc;
            this.badge = badge;
            Widgets.onClick(this, onClick);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            Widgets.smooth(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 190));
            g2.fillRoundRect(0, 0, w - 1, h - 1, 22, 22);
            g2.setColor(new Color(255, 255, 255, 240));
            g2.drawRoundRect(0, 0, w - 2, h - 2, 22, 22);

            // 그림 칸
            Color[] bg = { new Color(0xFFE3B8), new Color(0xFFD1DF), new Color(0xD9D0FF) };
            int k = kind.equals("yut") ? 0 : kind.equals("halli") ? 1 : 2;
            g2.setColor(bg[k]);
            g2.fillRoundRect(14, 14, 66, 66, 18, 18);
            g2.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            if (k == 0) { // 윷가락 4개
                for (int i = 0; i < 4; i++) {
                    int x = 24 + i * 12;
                    g2.setColor(i == 0 ? new Color(0xC98A4B) : new Color(0xE8B27A));
                    g2.fillRoundRect(x, 24, 9, 46, 8, 8);
                    g2.setColor(new Color(0x8A5A2B));
                    if (i == 0) g2.drawLine(x + 2, 40, x + 7, 40); // 빽도 표시
                }
            } else if (k == 1) { // 종 + 과일
                g2.setColor(new Color(0xF5B700));
                g2.fillArc(30, 30, 34, 34, 0, 180);
                g2.fillRect(30, 46, 34, 6);
                g2.setColor(new Color(0xB08000));
                g2.fillOval(43, 24, 8, 8);
                g2.setColor(new Color(0xE5484D));
                g2.fillOval(24, 58, 12, 12);
                g2.setColor(new Color(0x2E9B74));
                g2.fillOval(58, 58, 12, 12);
            } else { // 체크 상자
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(30, 30, 34, 34, 8, 8);
                g2.setColor(UI.LAVENDER);
                g2.drawRoundRect(30, 30, 34, 34, 8, 8);
                g2.setStroke(new java.awt.BasicStroke(3.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] { 38, 45, 57 }, new int[] { 47, 55, 39 }, 3);
            }

            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(java.awt.Font.BOLD, 15f));
            g2.drawString(name, 94, 42);
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(java.awt.Font.PLAIN, 12f));
            g2.drawString(desc, 94, 64);
            // 오픈 예정 표시
            g2.setFont(UI.font(java.awt.Font.BOLD, 10.5f));
            int bw = g2.getFontMetrics().stringWidth(badge) + 14;
            g2.setColor(new Color(0xECE9FC));
            g2.fillRoundRect(w - bw - 12, 10, bw, 20, 20, 20);
            g2.setColor(UI.LAVENDER);
            g2.drawString(badge, w - bw - 5, 24);
            g2.dispose();
        }
    }

    // ===================== 메뉴 =====================
    public static class Menu extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;
        private final AppFrame app;
        private final Avatar avatar = new Avatar(0, 56);
        private final JLabel nick = UI.title("", 16f), info = UI.sub("");

        public Menu(AppFrame app) {
            super("menu");
            this.app = app;
            header("메뉴", null);
            avatar.setLocation(40, 84);
            add(avatar);
            nick.setBounds(110, 88, 220, 24);
            add(nick);
            info.setBounds(110, 112, 240, 20);
            add(info);
            UI.card(this, 20, 70, W - 40, 84);

            String[] items = { "내 프로필 수정", "환경설정", "문의사항", "로그아웃" };
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                JLabel l = UI.title(items[i], 15f);
                if (i == 3) l.setForeground(UI.ERROR);
                l.setBounds(44, 186 + i * 64, 260, 24);
                add(l);
                JLabel arrow = UI.sub(">");
                arrow.setBounds(W - 60, 186 + i * 64, 20, 24);
                add(arrow);
                JPanel hit = new JPanel(); // 줄 전체를 눌러도 되게
                hit.setOpaque(false);
                hit.setBounds(20, 172 + i * 64, W - 40, 52);
                Widgets.onClick(hit, () -> pick(idx));
                add(hit);
                setComponentZOrder(hit, 0);
                UI.card(this, 20, 172 + i * 64, W - 40, 56);
            }
            JLabel foot = UI.sub("동거생활 v0.11 / 로컬호스트쨩");
            foot.setFont(UI.font(Font.PLAIN, 11f));
            foot.setHorizontalAlignment(SwingConstants.CENTER);
            foot.setBounds(0, AppFrame.SCREEN_H - 40, W, 20);
            add(foot);
        }

        @Override
        public void onShow() {
            if (app.me == null) return;
            avatar.setIndex(app.me.avatar);
            nick.setText(app.myNick);
            info.setText(app.realName + " / " + (app.gender.equals("M") ? "남" : "여") + " / 네이버 로그인");
        }

        private void pick(int i) {
            switch (i) {
                case 0:
                    app.setup.openEdit();
                    app.go("setup", true);
                    break;
                case 1:
                    JOptionPane.showMessageDialog(app, "<html>알림, 테마 설정은 다음 주에 추가할 예정이에요.<br>"
                            + "지금은 글꼴(나눔스퀘어라운드)과 파스텔 테마만 있어요.</html>", "환경설정", JOptionPane.PLAIN_MESSAGE);
                    break;
                case 2:
                    JOptionPane.showMessageDialog(app, "<html>로컬호스트쨩 (9조)<br>김여진, 박수아<br><br>"
                            + "버그나 하고 싶은 말은 팀원에게 직접 알려 주세요!</html>", "문의사항", JOptionPane.PLAIN_MESSAGE);
                    break;
                default:
                    int r = JOptionPane.showConfirmDialog(app, "로그아웃할까요?", "로그아웃", JOptionPane.YES_NO_OPTION);
                    if (r == JOptionPane.YES_OPTION) app.logout();
            }
        }
    }
}
