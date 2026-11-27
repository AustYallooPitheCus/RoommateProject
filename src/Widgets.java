// Widgets.java
// 앱 화면에서 여러 번 쓰는 작은 부품들 (11주차)
//   Heart       찜 하트 (누르면 켜고 끔)
//   ScoreRing   궁합 % 동그라미
//   NavBar      아래 탭바 (뒤로, 홈, 찜, 채팅, 메뉴)
//   MemberRow   목록 한 줄 (아바타, 닉네임, 요약, 궁합, 하트)
//   MemberCard  한 사람 자세히 (랜덤매칭, 프로필 보기)

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class Widgets {

    static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    // 클릭하면 run 실행 (JComponent에 손가락 커서까지)
    static void onClick(JComponent c, Runnable run) {
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                run.run();
            }
        });
    }

    static Path2D heartPath(float x, float y, float w) {
        Path2D p = new Path2D.Float();
        float h = w * 0.9f;
        p.moveTo(x + w / 2, y + h);
        p.curveTo(x - w * 0.1f, y + h * 0.55f, x + w * 0.05f, y - h * 0.05f, x + w / 2, y + h * 0.28f);
        p.curveTo(x + w * 0.95f, y - h * 0.05f, x + w * 1.1f, y + h * 0.55f, x + w / 2, y + h);
        p.closePath();
        return p;
    }

    // ===================== 찜 하트 =====================
    public static class Heart extends JComponent {
        private static final long serialVersionUID = 1L;
        private boolean on;

        public Heart(int size, Runnable onToggle) {
            setPreferredSize(new Dimension(size, size));
            setSize(size, size);
            onClick(this, onToggle);
        }

        public void setOn(boolean on) {
            this.on = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            float w = Math.min(getWidth(), getHeight()) * 0.8f;
            Path2D p = heartPath((getWidth() - w) / 2, (getHeight() - w * 0.9f) / 2, w);
            if (on) {
                g2.setColor(new Color(0xF06292));
                g2.fill(p);
            } else {
                g2.setColor(new Color(0xF4A6C6));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(p);
            }
            g2.dispose();
        }
    }

    // ===================== 궁합 % 동그라미 =====================
    public static class ScoreRing extends JComponent {
        private static final long serialVersionUID = 1L;
        private int score;

        public ScoreRing(int size) {
            setPreferredSize(new Dimension(size, size));
            setSize(size, size);
        }

        public void setScore(int score) {
            this.score = score;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            float s = Math.min(getWidth(), getHeight()), t = s * 0.1f;
            g2.setStroke(new BasicStroke(t, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(0xEAE6FA));
            g2.draw(new Arc2D.Float(t / 2, t / 2, s - t, s - t, 0, 360, Arc2D.OPEN));
            g2.setPaint(new GradientPaint(0, 0, UI.PINK, s, s, UI.LAVENDER));
            g2.draw(new Arc2D.Float(t / 2, t / 2, s - t, s - t, 90, -360f * score / 100, Arc2D.OPEN));
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, s * 0.26f));
            String txt = score + "%";
            int w = g2.getFontMetrics().stringWidth(txt);
            g2.drawString(txt, (s - w) / 2, s / 2 + g2.getFontMetrics().getAscent() / 2.6f);
            g2.setFont(UI.font(Font.PLAIN, s * 0.12f));
            g2.setColor(UI.TEXT_SUB);
            int w2 = g2.getFontMetrics().stringWidth("궁합");
            g2.drawString("궁합", (s - w2) / 2, s * 0.32f);
            g2.dispose();
        }
    }

    // ===================== 뒤로 버튼 (화면 왼쪽 위 <) =====================
    public static class BackButton extends JComponent {
        private static final long serialVersionUID = 1L;

        public BackButton(Runnable run) {
            setPreferredSize(new Dimension(32, 32));
            setSize(32, 32);
            setToolTipText("뒤로");
            onClick(this, run);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            g2.setColor(UI.TEXT);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Float();
            p.moveTo(cx + 4, cy - 8);
            p.lineTo(cx - 4, cy);
            p.lineTo(cx + 4, cy + 8);
            g2.draw(p);
            g2.dispose();
        }
    }

    // ===================== 아래 탭바 =====================
    public static class NavBar extends JPanel {
        private static final long serialVersionUID = 1L;
        // 12주차: "뒤로"는 탭이 아니라 동작이라 화면 왼쪽 위(<)로 옮기고, 그 자리에 게임 탭
        public static final String[] KEYS = { "home", "likes", "chat", "game", "menu" };
        private static final String[] NAMES = { "홈", "찜", "채팅", "게임", "메뉴" };
        private String active = "home";
        private boolean chatDot = false; // 새 메시지 표시

        public NavBar(Consumer<String> onTap) {
            setOpaque(false);
            setPreferredSize(new Dimension(400, 66));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = e.getX() * KEYS.length / Math.max(1, getWidth());
                    if (i >= 0 && i < KEYS.length) onTap.accept(KEYS[i]);
                }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public void setActive(String key) {
            active = key;
            repaint();
        }

        public void setChatDot(boolean on) {
            chatDot = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 215));
            g2.fillRect(0, 0, w, h);
            g2.setColor(new Color(0xE6E1F5));
            g2.drawLine(0, 0, w, 0);
            float cell = w / (float) KEYS.length;
            for (int i = 0; i < KEYS.length; i++) {
                boolean on = KEYS[i].equals(active);
                Color c = on ? UI.LAVENDER : new Color(0xA6A1BD);
                float cx = cell * i + cell / 2, cy = 24;
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                drawIcon(g2, KEYS[i], cx, cy, on);
                g2.setFont(UI.font(on ? Font.BOLD : Font.PLAIN, 10.5f));
                int tw = g2.getFontMetrics().stringWidth(NAMES[i]);
                g2.drawString(NAMES[i], cx - tw / 2f, 52);
                if (KEYS[i].equals("chat") && chatDot) {
                    g2.setColor(new Color(0xF06292));
                    g2.fillOval((int) cx + 8, (int) cy - 14, 8, 8);
                }
            }
            g2.dispose();
        }

        private void drawIcon(Graphics2D g2, String key, float x, float y, boolean on) {
            switch (key) {
                case "game": { // 주사위
                    RoundRectangle2D r = new RoundRectangle2D.Float(x - 9, y - 9, 18, 18, 6, 6);
                    if (on) g2.fill(r);
                    else g2.draw(r);
                    Color keep = g2.getColor(); // 아래 글자 색은 그대로 두려고 기억
                    g2.setColor(on ? Color.WHITE : keep);
                    for (float[] d : new float[][] { { -4, -4 }, { 4, 4 }, { 0, 0 } })
                        g2.fill(new java.awt.geom.Ellipse2D.Float(x + d[0] - 1.7f, y + d[1] - 1.7f, 3.4f, 3.4f));
                    g2.setColor(keep);
                    break;
                }
                case "home": {
                    Path2D p = new Path2D.Float();
                    p.moveTo(x - 9, y - 1);
                    p.lineTo(x, y - 9);
                    p.lineTo(x + 9, y - 1);
                    p.moveTo(x - 6, y - 3);
                    p.lineTo(x - 6, y + 8);
                    p.lineTo(x + 6, y + 8);
                    p.lineTo(x + 6, y - 3);
                    g2.draw(p);
                    break;
                }
                case "likes": {
                    Path2D p = heartPath(x - 9, y - 8, 18);
                    if (on) g2.fill(p);
                    else g2.draw(p);
                    break;
                }
                case "chat": {
                    RoundRectangle2D r = new RoundRectangle2D.Float(x - 10, y - 8, 20, 14, 8, 8);
                    g2.draw(r);
                    Path2D t = new Path2D.Float();
                    t.moveTo(x - 4, y + 6);
                    t.lineTo(x - 7, y + 10);
                    t.lineTo(x + 1, y + 6);
                    g2.draw(t);
                    break;
                }
                default: { // menu
                    for (int k = -1; k <= 1; k++) g2.draw(new java.awt.geom.Line2D.Float(x - 9, y + k * 6, x + 9, y + k * 6));
                }
            }
        }
    }

    // ===================== 목록 한 줄 =====================
    public static class MemberRow extends JPanel {
        private static final long serialVersionUID = 1L;
        public final Member member;
        private final Heart heart;

        public MemberRow(Member m, Runnable onOpen, Runnable onHeart) {
            this.member = m;
            setOpaque(false);
            setLayout(null);
            Dimension d = new Dimension(340, 70);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);

            Avatar av = new Avatar(m.profile.avatar, 48);
            av.setLocation(10, 11);
            add(av);
            JLabel nick = UI.title(m.nick, 14f);
            nick.setBounds(70, 12, 180, 22);
            add(nick);
            JLabel sum = UI.sub(m.profile.summary3() + " / " + m.profile.mbti);
            sum.setBounds(70, 36, 200, 20);
            add(sum);
            JLabel score = new JLabel(m.score + "%");
            score.setFont(UI.font(Font.BOLD, 15f));
            score.setForeground(UI.LAVENDER);
            score.setHorizontalAlignment(SwingConstants.RIGHT);
            score.setBounds(250, 22, 46, 24);
            add(score);
            heart = new Heart(26, onHeart);
            heart.setLocation(304, 22);
            heart.setOn(m.liked);
            add(heart);
            onClick(this, onOpen);
        }

        public void setLiked(boolean on) {
            member.liked = on;
            heart.setOn(on);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.fill(new RoundRectangle2D.Float(0, 2, getWidth() - 1, getHeight() - 6, 20, 20));
            g2.setColor(new Color(255, 255, 255, 235));
            g2.draw(new RoundRectangle2D.Float(0, 2, getWidth() - 1, getHeight() - 6, 20, 20));
            g2.dispose();
        }
    }

    // ===================== 한 사람 자세히 =====================
    public static class MemberCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Avatar avatar = new Avatar(0, 84);
        private final JLabel nick = UI.title("", 20f);
        private final JLabel info = UI.sub("");
        private final ScoreRing ring = new ScoreRing(84);
        private final JLabel[] values = new JLabel[7];

        public MemberCard() {
            setOpaque(false);
            setLayout(null);
            avatar.setLocation(24, 22);
            add(avatar);
            nick.setBounds(122, 30, 130, 28);
            add(nick);
            info.setBounds(122, 60, 140, 40);
            add(info);
            ring.setLocation(244, 22);
            add(ring);
            String[] labels = { "생활패턴", "흡연", "청결", "코골이", "잠귀", "냉난방", "MBTI" };
            for (int i = 0; i < 7; i++) {
                JLabel l = UI.sub(labels[i]);
                l.setBounds(30, 128 + i * 32, 80, 24);
                add(l);
                values[i] = new JLabel("");
                values[i].setFont(UI.font(Font.BOLD, 13f));
                values[i].setForeground(UI.TEXT);
                values[i].setBounds(120, 128 + i * 32, 210, 24);
                add(values[i]);
            }
        }

        public void show(String nickName, Profile p, int score) {
            avatar.setIndex(p.avatar);
            nick.setText(nickName);
            info.setText("<html>" + p.gradeDept().replace(" / ", "<br>") + "</html>");
            ring.setScore(score);
            ring.setVisible(score >= 0);
            for (int i = 0; i < 6; i++) values[i].setText(Profile.OPTIONS[i][p.answers[i]]);
            values[6].setText(p.mbti);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight();
            g2.setColor(new Color(255, 255, 255, 175));
            g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 26, 26));
            g2.setColor(new Color(0xEDE9FA));
            g2.drawLine(24, 116, w - 24, 116);
            g2.setColor(new Color(255, 255, 255, 235));
            g2.setStroke(new BasicStroke(1.3f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, 26, 26));
            g2.dispose();
        }
    }
}
