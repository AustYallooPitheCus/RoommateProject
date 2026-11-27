// ChatView.java
// 카톡처럼 말풍선으로 보여주는 채팅 화면 (11주차, JTextArea 대신 사용)
//   내 메시지: 오른쪽, 라벤더 말풍선
//   다른 사람: 왼쪽, 흰 말풍선 + 위에 닉네임 (같은 사람이 연달아 보내면 닉네임은 한 번만)
//   알림(입장, 퇴장 등): 가운데 작은 회색 글씨
//   사진, 동영상, 내 정보 카드도 addBubble로 같은 자리에 넣음 (ChatScreens.Talk에서 만듦)
// 1:1 대화랑 단체방에서 같이 씀. 메소드는 전부 EDT에서 불러야 함

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class ChatView extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("a h:mm", Locale.KOREAN); // 오후 9:01

    private final String me;          // 내 닉네임 (내 메시지인지 구분)
    private final int maxBubble;      // 말풍선 최대 너비. 넘으면 줄바꿈
    private String lastSender = null; // 바로 앞 메시지를 보낸 사람 (닉네임 반복 표시 안 하려고)

    public ChatView(String me, int maxBubble) {
        this.me = me;
        this.maxBubble = maxBubble;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(6, 4, 6, 4));
    }

    // 채팅 메시지 하나
    public void addMessage(String sender, String text) {
        addMessage(sender, text, System.currentTimeMillis());
    }

    // 시간까지 받는 버전 (1:1 대화 기록은 서버에 저장된 시간으로 보여줌)
    public void addMessage(String sender, String text, long millis) {
        boolean mine = sender.equals(me);
        addBubble(sender, new Bubble(text, mine ? UI.LAVENDER : Color.WHITE, mine ? Color.WHITE : UI.TEXT), millis);
    }

    public boolean isMine(String sender) {
        return sender.equals(me);
    }

    // 말풍선 자리에 아무 컴포넌트나 (사진, 파일, 내 정보 카드). 닉네임, 시간 붙이는 건 글자 메시지랑 같음
    public void addBubble(String sender, JComponent bubble, long millis) {
        boolean mine = sender.equals(me);
        JLabel time = new JLabel(TIME.format(new Date(millis)));
        time.setFont(UI.font(Font.PLAIN, 10f));
        time.setForeground(UI.TEXT_SUB);

        JPanel line = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 0));
        line.setOpaque(false);
        if (mine) {
            line.add(time); // 내 말풍선은 시간이 왼쪽
            line.add(bubble);
        } else {
            line.add(bubble);
            line.add(time);
        }

        Box col = Box.createVerticalBox();
        if (!mine && !sender.equals(lastSender)) {
            JLabel name = new JLabel(sender);
            name.setFont(UI.font(Font.BOLD, 11f));
            name.setForeground(UI.TEXT_SUB);
            name.setBorder(new EmptyBorder(4, 6, 2, 0));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            col.add(name);
        }
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(line);

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(col, mine ? BorderLayout.EAST : BorderLayout.WEST);
        lastSender = sender;
        addRow(row, 4);
    }

    // 대화 내용 전부 지우기 (다른 사람이랑 대화를 새로 열 때)
    public void clear() {
        removeAll();
        lastSender = null;
        revalidate();
        repaint();
    }

    // 입장, 퇴장 같은 안내
    public void addNotice(String text) {
        JLabel l = new JLabel(text);
        l.setFont(UI.font(Font.PLAIN, 11f));
        l.setForeground(UI.TEXT_SUB);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        row.setOpaque(false);
        row.add(l);
        lastSender = null; // 안내 뒤에는 닉네임 다시 표시
        addRow(row, 2);
    }

    private void addRow(JComponent row, int gap) {
        // 처음엔 최대 높이를 넣을 때 높이로 고정했었는데, 사진은 나중에 도착해서 커지니까
        // 줄 높이가 그대로라 아래 메시지랑 겹쳐 보였음 -> 그때그때 지금 높이를 돌려주는 칸으로 감쌈
        JPanel fit = new JPanel(new BorderLayout()) {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        fit.setOpaque(false);
        fit.setAlignmentX(Component.LEFT_ALIGNMENT);
        fit.add(row, BorderLayout.CENTER);
        add(fit);
        add(Box.createVerticalStrut(gap));
        revalidate();
        repaint();
        // 레이아웃이 끝난 다음에 맨 아래로 스크롤
        scrollToEnd();
    }

    // 사진이 늦게 도착해서 높이가 바뀌었을 때도 부름
    public void scrollToEnd() {
        revalidate();
        SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0, getHeight() - 1, 1, 1)));
    }

    // 둥근 배경을 직접 그리는 라벨. 길면 html width로 줄바꿈
    class Bubble extends JLabel {
        private static final long serialVersionUID = 1L;
        private final Color bg;

        Bubble(String text, Color bg, Color fg) {
            this.bg = bg;
            setForeground(fg);
            setBorder(new EmptyBorder(7, 12, 7, 12));
            String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            int w = getFontMetrics(getFont()).stringWidth(text);
            // html의 px는 자바 화면에서 1.33배쯤 크게 그려져서 나눠서 넣음 (안 그러면 시간 글자가 잘림)
            if (w > maxBubble) setText("<html><div style='width:" + (int) (maxBubble / 1.33) + "px'>" + safe + "</div></html>");
            else setText("<html>" + safe + "</html>");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (bg.equals(Color.WHITE)) { // 흰 말풍선은 테두리를 살짝 넣어야 배경이랑 구분됨
                g2.setColor(new Color(0xE4DFF5));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(bg);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 16, 16);
            } else {
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---- 스크롤 영역 안에서 가로는 항상 창 너비에 맞추기 (가로 스크롤 안 생기게) ----
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
        return r.height - 32;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
