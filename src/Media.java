// Media.java
// 채팅 + 버튼으로 보내는 것들 (11주차)
//   ImageBubble  사진 말풍선 (처음엔 "불러오는 중", 서버에서 받으면 작은 그림으로)
//   FileBubble   동영상 말풍선 (파일 이름, 크기. 누르면 컴퓨터 기본 플레이어로 열기)
//   InfoCard     내 정보 카드 (룸메 신청용: 이름, 학번, 전화번호는 보낸 사람이 골랐을 때만)
//   viewImage    사진 크게 보기 + 저장
//   capture      화면 영역 잘라서 PNG로 (윈도우 캡처 도구처럼 드래그)
//   askInfo      내 정보 보내기 확인 창 (전화번호 포함할지 고르기)

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class Media {

    public static final String[] IMAGE_EXT = { "jpg", "jpeg", "png", "gif", "bmp" };
    public static final String[] VIDEO_EXT = { "mp4", "mov", "avi", "mkv", "wmv", "webm" };

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    static boolean isImage(String name) {
        for (String e : IMAGE_EXT) if (e.equals(ext(name))) return true;
        return false;
    }

    static boolean isVideo(String name) {
        for (String e : VIDEO_EXT) if (e.equals(ext(name))) return true;
        return false;
    }

    static String sizeText(long b) {
        if (b < 1024 * 1024) return Math.max(1, b / 1024) + "KB";
        return String.format("%.1fMB", b / 1024.0 / 1024.0);
    }

    static void round(Graphics2D g2, int w, int h, Color fill, Color line) {
        Widgets.smooth(g2);
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 18, 18));
        if (line != null) {
            g2.setColor(line);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 2, h - 2, 18, 18));
        }
    }

    // ===================== 사진 말풍선 =====================
    public static class ImageBubble extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int MAX = 180;
        private Image img;
        private String status = "사진 불러오는 중...";

        public ImageBubble(Runnable onClick) {
            setPreferredSize(new Dimension(MAX, 120));
            Widgets.onClick(this, onClick);
        }

        public void setImage(BufferedImage b) {
            if (b == null) {
                status = "사진을 열 수 없어요";
                repaint();
                return;
            }
            int w = Math.min(MAX, b.getWidth());
            int h = (int) Math.round(b.getHeight() * (double) w / b.getWidth());
            if (h > 240) { // 너무 긴 세로 사진은 높이 기준으로
                h = 240;
                w = (int) Math.round(b.getWidth() * 240.0 / b.getHeight());
            }
            img = b.getScaledInstance(Math.max(1, w), Math.max(1, h), Image.SCALE_SMOOTH);
            setPreferredSize(new Dimension(Math.max(1, w), Math.max(1, h)));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            if (img == null) {
                round(g2, w, h, new Color(0xF1EEFB), new Color(0xE4DFF5));
                g2.setColor(UI.TEXT_SUB);
                g2.setFont(UI.font(Font.PLAIN, 11f));
                int sw = g2.getFontMetrics().stringWidth(status);
                g2.drawString(status, (w - sw) / 2, h / 2 + 4);
            } else {
                Widgets.smooth(g2);
                g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 16, 16)); // 모서리 둥글게 잘라서 (setClip을 쓰면 스크롤 영역 밖까지 그려져서 clip으로)
                g2.drawImage(img, 0, 0, this);
            }
            g2.dispose();
        }
    }

    // ===================== 동영상 말풍선 =====================
    public static class FileBubble extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String name, size;
        private String hint = "눌러서 재생";

        public FileBubble(String name, long size, Runnable onClick) {
            this.name = name.length() > 18 ? name.substring(0, 15) + "..." + ext(name) : name;
            this.size = sizeText(size);
            setPreferredSize(new Dimension(200, 62));
            Widgets.onClick(this, onClick);
        }

        public void setHint(String h) {
            hint = h;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            round(g2, getWidth(), getHeight(), Color.WHITE, new Color(0xE4DFF5));
            // 재생 버튼 동그라미
            g2.setColor(UI.LAVENDER);
            g2.fillOval(12, 13, 36, 36);
            Path2D tri = new Path2D.Float();
            tri.moveTo(26, 22);
            tri.lineTo(26, 40);
            tri.lineTo(39, 31);
            tri.closePath();
            g2.setColor(Color.WHITE);
            g2.fill(tri);
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.BOLD, 12f));
            g2.drawString(name, 58, 27);
            g2.setColor(UI.TEXT_SUB);
            g2.setFont(UI.font(Font.PLAIN, 10.5f));
            g2.drawString("동영상 / " + size + " / " + hint, 58, 45);
            g2.dispose();
        }
    }

    // ===================== 내 정보 카드 =====================
    // content: 이름|학번|전화번호(-면 안 보냄)
    public static class InfoCard extends JPanel {
        private static final long serialVersionUID = 1L;

        public InfoCard(String content, boolean mine) {
            setOpaque(false);
            setLayout(null);
            String[] p = content.split("\\|", 3);
            boolean phone = p.length == 3 && !p[2].equals("-");
            int h = phone ? 120 : 96;
            setPreferredSize(new Dimension(210, h));

            JLabel head = new JLabel(mine ? "내 정보를 보냈어요" : "룸메 신청 정보가 왔어요");
            head.setFont(UI.font(Font.BOLD, 12.5f));
            head.setForeground(Color.WHITE);
            head.setBounds(14, 8, 190, 22);
            add(head);
            String[][] rows = { { "이름", p.length > 0 ? p[0] : "" }, { "학번", p.length > 1 ? p[1] : "" },
                                { "전화번호", phone ? p[2] : null } };
            int y = 44;
            for (String[] r : rows) {
                if (r[1] == null) continue;
                JLabel k = UI.sub(r[0]);
                k.setBounds(14, y, 60, 20);
                add(k);
                JLabel v = new JLabel(r[1]);
                v.setFont(UI.font(Font.BOLD, 13f));
                v.setForeground(UI.TEXT);
                v.setBounds(76, y, 130, 20);
                add(v);
                y += 24;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            int w = getWidth(), h = getHeight();
            round(g2, w, h, Color.WHITE, new Color(0xD9D0FF));
            g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 18, 18));
            g2.setColor(UI.LAVENDER);
            g2.fillRect(0, 0, w, 36);
            g2.dispose();
        }
    }

    // ===================== 사진 크게 보기 =====================
    public static void viewImage(JFrame owner, byte[] data, String name) {
        BufferedImage b = read(data);
        if (b == null) {
            JOptionPane.showMessageDialog(owner, "사진을 열 수 없어요");
            return;
        }
        int max = 620;
        double s = Math.min(1.0, Math.min(max / (double) b.getWidth(), max / (double) b.getHeight()));
        JLabel pic = new JLabel(new ImageIcon(b.getScaledInstance((int) (b.getWidth() * s), (int) (b.getHeight() * s), Image.SCALE_SMOOTH)));
        pic.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JDialog d = new JDialog(owner, name, true);
        JPanel p = new JPanel(new java.awt.BorderLayout());
        p.add(pic, java.awt.BorderLayout.CENTER);
        JButton save = UI.soft("저장");
        save.addActionListener(e -> save(d, data, name));
        JPanel bottom = new JPanel();
        bottom.add(save);
        p.add(bottom, java.awt.BorderLayout.SOUTH);
        d.setContentPane(p);
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }

    static void save(java.awt.Component parent, byte[] data, String name) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File(name));
        if (fc.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.write(fc.getSelectedFile().toPath(), data);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "저장하지 못했어요: " + e.getMessage());
        }
    }

    static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException e) {
            return null;
        }
    }

    // 동영상: 임시 파일로 저장해서 컴퓨터에 깔린 기본 프로그램으로 열기
    public static void openVideo(JFrame owner, byte[] data, String name) {
        try {
            File f = File.createTempFile("roommate_", "." + ext(name));
            f.deleteOnExit();
            Files.write(f.toPath(), data);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) Desktop.getDesktop().open(f);
            else save(owner, data, name); // 열 수 없는 환경이면 저장이라도
        } catch (IOException e) {
            JOptionPane.showMessageDialog(owner, "동영상을 열 수 없어요: " + e.getMessage());
        }
    }

    // ===================== 캡쳐 =====================
    // 1. 앱 창을 잠깐 숨기고 2. 지금 모니터를 통째로 찍어서 3. 어둡게 덮은 창을 띄움
    // 4. 드래그한 부분만 밝게 보이다가 손을 떼면 그 부분을 PNG로 만들어 done에 넘김 (ESC나 클릭만 하면 취소)
    public static void capture(JFrame owner, Consumer<byte[]> done) {
        GraphicsConfiguration gc = owner.getGraphicsConfiguration();
        Rectangle screen = gc.getBounds();
        owner.setVisible(false);
        Timer t = new Timer(350, e -> { // 창이 사라질 시간을 조금 줌
            BufferedImage shot;
            try {
                shot = new Robot(gc.getDevice()).createScreenCapture(screen);
            } catch (Exception ex) {
                owner.setVisible(true);
                JOptionPane.showMessageDialog(owner, "화면을 찍을 수 없어요: " + ex.getMessage());
                return;
            }
            new Picker(owner, screen, shot, done).setVisible(true);
        });
        t.setRepeats(false);
        t.start();
    }

    static class Picker extends JWindow {
        private static final long serialVersionUID = 1L;
        private int x0, y0, x1, y1;
        private boolean dragging;

        Picker(JFrame owner, Rectangle screen, BufferedImage shot, Consumer<byte[]> done) {
            setBounds(screen);
            setAlwaysOnTop(true);
            JComponent c = new JComponent() {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    // 화면 크기와 찍힌 그림 크기가 다를 수 있어서(배율 설정) 창 크기에 맞춰 그림
                    g2.drawImage(shot, 0, 0, getWidth(), getHeight(), null);
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                    g2.setColor(Color.BLACK);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setComposite(AlphaComposite.SrcOver);
                    Rectangle r = sel();
                    if (r.width > 0 && r.height > 0) {
                        g2.setClip(r);
                        g2.drawImage(shot, 0, 0, getWidth(), getHeight(), null);
                        g2.setClip(null);
                        g2.setColor(UI.LAVENDER);
                        g2.setStroke(new BasicStroke(2f));
                        g2.draw(r);
                    }
                    String tip = "보낼 부분을 드래그하세요 (ESC 취소)";
                    g2.setFont(UI.font(Font.BOLD, 15f));
                    int tw = g2.getFontMetrics().stringWidth(tip);
                    g2.setColor(new Color(0, 0, 0, 150));
                    g2.fillRoundRect((getWidth() - tw) / 2 - 16, 24, tw + 32, 36, 18, 18);
                    g2.setColor(Color.WHITE);
                    g2.drawString(tip, (getWidth() - tw) / 2, 48);
                    g2.dispose();
                }
            };
            c.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            setContentPane(c);
            MouseAdapter m = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    x0 = x1 = e.getX();
                    y0 = y1 = e.getY();
                    dragging = true;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    x1 = e.getX();
                    y1 = e.getY();
                    c.repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!dragging) return;
                    dragging = false;
                    Rectangle r = sel();
                    finish(owner);
                    if (r.width < 5 || r.height < 5) return; // 클릭만 하면 취소
                    double sx = shot.getWidth() / (double) getWidth(), sy = shot.getHeight() / (double) getHeight();
                    BufferedImage part = shot.getSubimage((int) (r.x * sx), (int) (r.y * sy),
                            Math.max(1, (int) (r.width * sx)), Math.max(1, (int) (r.height * sy)));
                    try {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        ImageIO.write(part, "png", out);
                        done.accept(out.toByteArray());
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(owner, "캡쳐를 만들지 못했어요");
                    }
                }
            };
            c.addMouseListener(m);
            c.addMouseMotionListener(m);
            c.setFocusable(true);
            c.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) finish(owner);
                }
            });
            addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowGainedFocus(java.awt.event.WindowEvent e) {
                    c.requestFocusInWindow();
                }
            });
        }

        private Rectangle sel() {
            return new Rectangle(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
        }

        private void finish(JFrame owner) {
            dispose();
            owner.setVisible(true);
            owner.toFront();
        }
    }

    // ===================== 내 정보 보내기 확인 =====================
    // 이름, 학번은 룸메 신청에 꼭 필요해서 항상 보내고, 전화번호만 고름
    // 보내기를 누르면 전화번호 포함 여부(true/false), 취소하면 null
    public static Boolean askInfo(JFrame owner, String name, String studentId, String phone, boolean group) {
        JPanel p = new JPanel(null);
        p.setPreferredSize(new Dimension(300, group ? 196 : 172));
        JLabel t = UI.sub("<html>룸메 신청에 필요한 정보를 보내요.<br>이름, 학번은 꼭 보내고 전화번호는 골라요.</html>");
        t.setBounds(0, 0, 300, 36);
        p.add(t);
        String[][] rows = { { "이름", name }, { "학번", studentId } };
        int y = 48;
        for (String[] r : rows) {
            JCheckBox fixed = new JCheckBox(r[0], true);
            fixed.setEnabled(false); // 항상 보냄
            fixed.setBounds(0, y, 100, 26);
            p.add(fixed);
            JLabel v = new JLabel(r[1]);
            v.setFont(UI.font(Font.BOLD, 13f));
            v.setBounds(110, y, 190, 26);
            p.add(v);
            y += 32;
        }
        JCheckBox ph = new JCheckBox("전화번호", false);
        ph.setBounds(0, y, 100, 26);
        p.add(ph);
        JLabel v = new JLabel(phone);
        v.setFont(UI.font(Font.BOLD, 13f));
        v.setBounds(110, y, 190, 26);
        p.add(v);
        y += 34;
        if (group) {
            JLabel warn = new JLabel("단체방에 있는 사람 모두에게 보여요");
            warn.setFont(UI.font(Font.BOLD, 12f));
            warn.setForeground(UI.ERROR);
            warn.setHorizontalAlignment(SwingConstants.LEFT);
            warn.setBounds(0, y, 300, 22);
            p.add(warn);
        }
        Object[] opts = { "보내기", "취소" };
        int r = JOptionPane.showOptionDialog(owner, p, "내 정보 보내기", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, opts, opts[0]);
        return r == 0 ? ph.isSelected() : null;
    }
}
