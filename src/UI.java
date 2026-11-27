// UI.java
// 화면 디자인 모음 (11주차, 팀원 UI/UX 시안: 파스텔 무지개빛 + 반투명 유리 카드)
// FlatLaf(오픈소스 Swing 테마 라이브러리, https://github.com/JFormDesigner/FlatLaf) 위에 색, 둥근 모서리, 글꼴을 덮어씀
// 배경 그라데이션, 유리 카드는 FlatLaf에 없어서 paintComponent로 직접 그림

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatLightLaf;

public class UI {

    // ===== 색 (시안에서 뽑은 것) =====
    public static final Color LAVENDER = new Color(0x8B7CF6);  // 메인 (버튼, 강조)
    public static final Color LAVENDER_SOFT = new Color(0xE6E1FF); // 선택된 줄 배경
    public static final Color MINT = new Color(0x5FCFB8);
    public static final Color PINK = new Color(0xF4A6C6);
    public static final Color SKY = new Color(0xA9D6F5);
    public static final Color PEACH = new Color(0xFFD9B8);
    public static final Color TEXT = new Color(0x3B3650);      // 기본 글자
    public static final Color TEXT_SUB = new Color(0x8A85A0);  // 안내 문구
    public static final Color ERROR = new Color(0xE0567A);
    public static final Color NAVER = new Color(0x03C75A);
    public static final Color KAKAO = new Color(0xFEE500);

    // ===== 글씨체 =====
    // 바꾸고 싶으면 여기 이름만 바꾸면 됨. 글꼴 파일(.ttf/.otf)을 프로젝트의 fonts 폴더에 넣으면
    // 컴퓨터에 설치 안 해도 불러옴 (발표용 다른 컴퓨터에서도 똑같이 보이게)
    // 이름이 없거나 못 찾으면 맑은 고딕 -> 그 외 한글 글꼴 순으로 대신 씀
    // 지금은 나눔스퀘어라운드 (fonts 폴더에 NanumSquareRoundR.ttf, NanumSquareRoundB.ttf)
    // 이 파일들은 보통 굵기와 굵은 글씨가 서로 다른 글꼴 이름으로 잡혀서 굵은 글씨용 이름을 따로 둠
    public static String FONT_NAME = "NanumSquareRound Regular";   // 본문
    public static String BOLD_FONT_NAME = "NanumSquareRound Bold"; // 굵은 글씨. null이면 본문 글꼴을 굵게
    public static String TITLE_FONT_NAME = null;                   // 제목 ("동거생활" 등). null이면 굵은 글씨와 같음
    private static Font titleBase = null;
    private static Font boldBase = null;

    // 프로그램 시작할 때 한 번 (서버, 클라이언트 main 첫 줄)
    public static void setup() {
        FlatLightLaf.setup();
        loadFontFiles();
        Font base = pickFont(FONT_NAME, 13);
        boldBase = findFont(BOLD_FONT_NAME, 13);
        titleBase = findFont(TITLE_FONT_NAME, 13);
        UIManager.put("defaultFont", new FontUIResource(base));

        UIManager.put("Button.arc", 999);          // 알약 모양 버튼
        UIManager.put("Component.arc", 14);        // 입력창, 콤보박스
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", LAVENDER);
        UIManager.put("Component.borderColor", new Color(0xDAD5EE));
        UIManager.put("Button.borderColor", new Color(0xDAD5EE));
        UIManager.put("Button.background", Color.WHITE);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", LAVENDER_SOFT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("List.selectionInactiveBackground", LAVENDER_SOFT);
        UIManager.put("List.selectionInactiveForeground", TEXT);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumb", new Color(0xCFC8F0));
        UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        UIManager.put("RadioButton.icon.selectedBorderColor", LAVENDER);
        UIManager.put("RadioButton.icon.centerDiameter", 8);
        UIManager.put("OptionPane.background", Color.WHITE);
        UIManager.put("Panel.background", Color.WHITE);
        // 설문 선택지 (JToggleButton): 고르면 보라색
        UIManager.put("ToggleButton.background", Color.WHITE);
        UIManager.put("ToggleButton.foreground", TEXT);
        UIManager.put("ToggleButton.selectedBackground", LAVENDER);
        UIManager.put("ToggleButton.selectedForeground", Color.WHITE);
        UIManager.put("ToggleButton.borderColor", new Color(0xDAD5EE));
        UIManager.put("CheckBox.icon.checkmarkColor", Color.WHITE);
        UIManager.put("CheckBox.icon.selectedBackground", LAVENDER);
        UIManager.put("CheckBox.icon.selectedBorderColor", LAVENDER);
    }

    // fonts 폴더의 글꼴 파일을 자바에 등록 (설치 안 해도 이름으로 쓸 수 있게)
    private static void loadFontFiles() {
        File[] files = new File("fonts").listFiles();
        if (files == null) return; // 폴더가 없으면 그냥 설치된 글꼴만 씀
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (!n.endsWith(".ttf") && !n.endsWith(".otf")) continue;
            try {
                ge.registerFont(Font.createFont(Font.TRUETYPE_FONT, f));
            } catch (FontFormatException | IOException e) {
                System.out.println("글꼴 파일 읽기 실패: " + f.getName() + " (" + e.getMessage() + ")");
            }
        }
    }

    // 정확히 그 이름의 글꼴이 있으면 그것, 없으면 null (대신 쓸 글꼴은 안 찾음)
    private static Font findFont(String name, int size) {
        if (name == null) return null;
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        return have.contains(name) ? new Font(name, Font.PLAIN, size) : null;
    }

    // 원하는 글꼴 -> 맑은 고딕 -> 그 외 한글 글꼴 순서로 있는 것 하나
    private static Font pickFont(String wanted, int size) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String name : new String[] { wanted, "맑은 고딕", "Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK KR" }) {
            if (name != null && have.contains(name)) return new Font(name, Font.PLAIN, size);
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, size);
    }

    // 지금 컴퓨터에서 쓸 수 있는 한글 글꼴 이름 목록 (콘솔 출력). 이름을 FONT_NAME에 그대로 복사해서 쓰면 됨
    public static void main(String[] args) {
        loadFontFiles();
        for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (new Font(name, Font.PLAIN, 12).canDisplayUpTo("가나다") == -1) System.out.println(name);
        }
    }

    public static Font font(int style, float size) {
        // 굵은 글씨 전용 파일이 있으면 그걸 쓰고, 없으면 기본 글꼴을 굵게
        if ((style & Font.BOLD) != 0 && boldBase != null) return boldBase.deriveFont(Font.PLAIN, size);
        return UIManager.getFont("defaultFont").deriveFont(style, size);
    }

    // ===== 배경: 파스텔 그라데이션 + 흐릿한 색 덩어리 몇 개 =====
    public static class GradientPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        public GradientPanel() {
            setLayout(null);
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, new Color(0xFCE7F1), w, h, new Color(0xDDEBFB)));
            g2.fillRect(0, 0, w, h);
            blob(g2, w * 0.15f, h * 0.10f, Math.max(w, h) * 0.45f, PINK, 120);
            blob(g2, w * 0.95f, h * 0.30f, Math.max(w, h) * 0.45f, new Color(0xC6B8FF), 120);
            blob(g2, w * 0.30f, h * 1.00f, Math.max(w, h) * 0.50f, MINT, 80);
            blob(g2, w * 0.90f, h * 0.95f, Math.max(w, h) * 0.35f, PEACH, 90);
            g2.dispose();
        }

        private void blob(Graphics2D g2, float cx, float cy, float r, Color c, int alpha) {
            Color in = new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
            Color out = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);
            g2.setPaint(new RadialGradientPaint(cx, cy, r, new float[] { 0f, 1f }, new Color[] { in, out }));
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    // ===== 유리 카드: 반투명 흰색 + 위쪽 빛 반사 + 흰 테두리 + 옅은 그림자 =====
    public static class GlassPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final int arc;

        public GlassPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
            setLayout(null);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // 그림자 (아래로 살짝)
            for (int i = 0; i < 6; i++) {
                g2.setColor(new Color(120, 100, 180, 6));
                g2.fillRoundRect(i / 2, 3 + i / 2, w - i, h - 3 - i / 2, arc, arc);
            }
            // 반투명 흰 판
            g2.setColor(new Color(255, 255, 255, 165));
            g2.fillRoundRect(0, 0, w - 1, h - 4, arc, arc);
            // 위쪽 빛 반사
            g2.setComposite(AlphaComposite.SrcOver.derive(0.6f));
            g2.setPaint(new GradientPaint(0, 0, new Color(255, 255, 255, 200), 0, h / 3f, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(0, 0, w - 1, h / 3, arc, arc);
            g2.setComposite(AlphaComposite.SrcOver);
            // 테두리
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(255, 255, 255, 230));
            g2.drawRoundRect(0, 0, w - 2, h - 5, arc, arc);
            g2.dispose();
        }
    }

    // parent 안의 (x, y, w, h) 자리에 유리 카드를 깔아줌. 이미 있는 컴포넌트들 뒤로 보냄
    // (null 레이아웃이라 나중에 add한 게 앞에 그려지는데, 카드는 맨 뒤여야 해서 z순서를 맨 끝으로)
    public static GlassPanel card(Container parent, int x, int y, int w, int h) {
        GlassPanel p = new GlassPanel(22);
        p.setBounds(x, y, w, h);
        parent.add(p);
        parent.setComponentZOrder(p, parent.getComponentCount() - 1);
        return p;
    }

    // 색 있는 알약 버튼 (주 버튼)
    public static JButton pill(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFont(font(Font.BOLD, 13f));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        return b;
    }

    // 하얀 알약 버튼 (보조 버튼)
    public static JButton soft(String text) {
        JButton b = new JButton(text);
        b.setBackground(Color.WHITE);
        b.setForeground(LAVENDER);
        b.setFont(font(Font.BOLD, 12f));
        b.setFocusPainted(false);
        return b;
    }

    // 스크롤 영역을 투명하게 (뒤에 깔린 유리 카드가 보이게)
    public static void clear(JScrollPane sp) {
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.setBorder(BorderFactory.createEmptyBorder());
        Component v = sp.getViewport().getView();
        if (v instanceof JComponent) ((JComponent) v).setOpaque(false);
    }

    public static JLabel title(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(titleBase != null ? titleBase.deriveFont(Font.PLAIN, size) : font(Font.BOLD, size));
        l.setForeground(TEXT);
        return l;
    }

    public static JLabel sub(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_SUB);
        l.setFont(font(Font.PLAIN, 12f));
        return l;
    }

    // 목록 한 줄 높이를 넉넉하게, 선택된 줄만 연보라 배경
    public static void styleList(JList<?> list) {
        list.setOpaque(false);
        list.setFixedCellHeight(30);
        list.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, value, index, selected, false);
                c.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                c.setOpaque(selected);
                c.setBackground(LAVENDER_SOFT);
                c.setForeground(TEXT);
                return c;
            }
        });
    }
}
