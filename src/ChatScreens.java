// ChatScreens.java
// 채팅 쪽 화면들 (11주차)
//   ChatList  [1:1 대화] / [단체방] 탭. 1:1은 최근 대화 순, 단체방은 같은 성별 방 목록 + [방 만들기]
//   Dm        1:1 대화 (서버에 저장돼서 다시 들어와도 기록이 남음)
//   Room      단체방 채팅 (11주차1 RoomWindow를 앱 화면으로 옮김)
// 대화 화면 입력줄 왼쪽 + 버튼: 사진/동영상, 캡쳐, 내 정보 보내기 (1:1, 단체방 둘 다)
// 입력칸 안 오른쪽 회색 웃는 얼굴: 누르면 키보드 자리에 이모티콘 판 (12주차). 그림이 키보드로 바뀌고 다시 누르면 닫힘
//   이모티콘은 한 번 누르면 입력줄 위에 미리보기, 같은 걸 한 번 더 누르면(또는 전송 버튼) 보냄
// 제목줄 왼쪽 < : 뒤로 (12주차에 아래 탭바에서 옮김)

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.filechooser.FileNameExtensionFilter;

public class ChatScreens {

    static final int W = AppFrame.W, H = AppFrame.SCREEN_H;
    private static final SimpleDateFormat TODAY = new SimpleDateFormat("a h:mm", Locale.KOREAN);
    private static final SimpleDateFormat DAY = new SimpleDateFormat("M/d");

    static String when(long t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(t);
        boolean today = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return (today ? TODAY : DAY).format(new Date(t));
    }

    // 하얀 반투명 줄 (대화 목록, 방 목록 한 칸)
    static class Row extends JPanel {
        private static final long serialVersionUID = 1L;

        Row(int h) {
            setOpaque(false);
            setLayout(null);
            Dimension d = new Dimension(W - 60, h);
            setPreferredSize(d);
            setMaximumSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.fill(new RoundRectangle2D.Float(0, 2, getWidth() - 1, getHeight() - 6, 20, 20));
            g2.setColor(new Color(255, 255, 255, 235));
            g2.draw(new RoundRectangle2D.Float(0, 2, getWidth() - 1, getHeight() - 6, 20, 20));
            g2.dispose();
        }
    }

    // 목록, 알림에 보여줄 한 줄 (사진이면 "사진" 등)
    static String preview(String kind, String content) {
        switch (kind) {
            case "I": return "룸메 신청 정보";
            case "P": return "사진";
            case "V": return "동영상";
            case "E": return "이모티콘";
            default: return content.length() > 20 ? content.substring(0, 20) + "..." : content;
        }
    }

    // 입력칸 안 회색 그림. false면 웃는 얼굴(이모티콘 열기), true면 키보드(다시 글자 입력)
    static class ToggleIcon implements javax.swing.Icon {
        private final boolean keyboard;

        ToggleIcon(boolean keyboard) {
            this.keyboard = keyboard;
        }

        public int getIconWidth() {
            return 22;
        }

        public int getIconHeight() {
            return 22;
        }

        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            g2.translate(x, y);
            g2.setColor(new Color(0x9A95AE));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (keyboard) {
                g2.drawRoundRect(1, 5, 20, 13, 4, 4);
                for (int r = 0; r < 2; r++)
                    for (int k = 0; k < 4; k++) g2.fillRect(4 + k * 4, 8 + r * 3, 2, 2);
                g2.drawLine(7, 15, 15, 15); // 스페이스바
            } else {
                g2.drawOval(2, 2, 18, 18);
                g2.fillOval(7, 8, 3, 3);
                g2.fillOval(12, 8, 3, 3);
                g2.drawArc(6, 9, 10, 7, 200, 140);
            }
            g2.dispose();
        }
    }

    // + 눌렀을 때 나오는 동그란 버튼 하나
    static class TrayButton extends JComponent {
        private static final long serialVersionUID = 1L;
        private final String label, icon;
        private final Color color;

        TrayButton(String label, String icon, Color color, Runnable run) {
            this.label = label;
            this.icon = icon;
            this.color = color;
            Widgets.onClick(this, run);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Widgets.smooth(g2);
            int cx = getWidth() / 2, r = 24;
            g2.setColor(color);
            g2.fillOval(cx - r, 4, r * 2, r * 2);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cy = 4 + r;
            switch (icon) {
                case "photo": // 산 + 해
                    g2.drawRoundRect(cx - 12, cy - 9, 24, 18, 5, 5);
                    g2.drawPolyline(new int[] { cx - 10, cx - 3, cx + 2, cx + 5, cx + 10 }, new int[] { cy + 7, cy - 1, cy + 4, cy + 1, cy + 7 }, 5);
                    g2.fillOval(cx + 3, cy - 6, 5, 5);
                    break;
                case "capture": // 모서리 네 개
                    int s = 11, k = 5;
                    g2.drawPolyline(new int[] { cx - s, cx - s, cx - s + k }, new int[] { cy - s + k, cy - s, cy - s }, 3);
                    g2.drawPolyline(new int[] { cx + s - k, cx + s, cx + s }, new int[] { cy - s, cy - s, cy - s + k }, 3);
                    g2.drawPolyline(new int[] { cx - s, cx - s, cx - s + k }, new int[] { cy + s - k, cy + s, cy + s }, 3);
                    g2.drawPolyline(new int[] { cx + s - k, cx + s, cx + s }, new int[] { cy + s, cy + s, cy + s - k }, 3);
                    g2.drawLine(cx - 4, cy, cx + 4, cy);
                    g2.drawLine(cx, cy - 4, cx, cy + 4);
                    break;
                default: // 명함
                    g2.drawRoundRect(cx - 12, cy - 8, 24, 16, 4, 4);
                    g2.fillOval(cx - 8, cy - 4, 6, 6);
                    g2.drawLine(cx + 1, cy - 2, cx + 8, cy - 2);
                    g2.drawLine(cx + 1, cy + 3, cx + 6, cy + 3);
            }
            g2.setColor(UI.TEXT);
            g2.setFont(UI.font(Font.PLAIN, 11.5f));
            int tw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, cx - tw / 2, 4 + r * 2 + 18);
            g2.dispose();
        }
    }

    // 대화 화면 공통: 위 제목줄 + 말풍선 + 아래 입력줄 (+ 버튼, 입력칸, 전송)
    static abstract class Talk extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;
        protected final AppFrame app;
        protected ChatView chat;
        protected final JScrollPane scroll;
        protected final JTextField input = new JTextField();
        protected final JLabel title = UI.title("", 17f), sub = UI.sub("");
        private final JPanel holder = new JPanel(new java.awt.BorderLayout());
        private final JPanel tray;
        private final JPanel emoPanel; // 이모티콘 고르는 판 (12주차)
        private final JButton emoToggle; // 입력칸 안 이모티콘/키보드 그림
        private final JButton send;
        private boolean emoMode;          // 이모티콘 판이 떠 있는지
        private static final int EMO_H = 200;
        private final EmoticonView[] emoViews = new EmoticonView[Emoticon.IDS.length];
        private final JPanel preview;          // 이모티콘 미리보기 (대기실)
        private final EmoticonView previewView;
        private final JLabel previewHint = UI.title("", 15f);
        private String picked;                 // 미리보기 중인 이모티콘 (없으면 null)
        private final JButton plus;
        private boolean infoSent; // 이 대화에서 내가 내 정보 카드를 보낸 적 있는지 (기록 포함)
        protected int topY = 84;  // 말풍선 영역 위쪽

        // 하위 화면이 입력줄 위치(rowY)에 맞춰 추가 부품 자리를 잡을 때 (기본은 없음)
        protected void layoutExtra(int rowY) {
        }

        Talk(AppFrame app, int titleX) {
            super("chat");
            this.app = app;
            Widgets.BackButton back = new Widgets.BackButton(this::onBack);
            back.setLocation(18, 26);
            add(back);
            title.setBounds(titleX, 20, 260, 26);
            add(title);
            sub.setBounds(titleX, 46, 280, 18);
            add(sub);
            UI.card(this, 12, 10, W - 24, 64);

            holder.setOpaque(false);
            scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            UI.clear(scroll);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            scroll.setBounds(12, 84, W - 24, H - 84 - 66);
            add(scroll);
            newChat();

            // + 누르면 위로 올라오는 판 (카톡처럼)
            tray = sheet();
            TrayButton[] tbs = {
                new TrayButton("사진/동영상", "photo", new Color(0x7FC8A9), this::pickMedia),
                new TrayButton("캡쳐", "capture", new Color(0x8FB8F0), this::capture),
                new TrayButton("내 정보 보내기", "info", UI.LAVENDER, this::sendInfo),
            };
            int bw = (W - 24) / tbs.length;
            for (int i = 0; i < tbs.length; i++) {
                tbs[i].setBounds(i * bw, 10, bw, 80);
                tray.add(tbs[i]);
            }
            tray.setVisible(false);
            add(tray);
            setComponentZOrder(tray, 0);

            // 이모티콘 판: 폰에서 키보드가 올라오는 자리(화면 맨 아래)에 뜨고, 입력줄은 그 위로 올라감
            // 4개씩 두 줄. 한 번 누르면 미리보기(대기실), 같은 걸 한 번 더 누르면 전송. 판은 그대로
            emoPanel = sheet();
            emoPanel.setBounds(12, H - EMO_H, W - 24, EMO_H - 6);
            int ew = (W - 24 - 16) / 4;
            for (int i = 0; i < Emoticon.IDS.length; i++) {
                final String id = Emoticon.IDS[i];
                EmoticonView v = new EmoticonView(id, 62);
                int x = 8 + (i % 4) * ew, y = 10 + (i / 4) * 90;
                v.setLocation(x + (ew - 62) / 2, y);
                Widgets.onClick(v, () -> pickEmoticon(id));
                emoViews[i] = v;
                emoPanel.add(v);
                JLabel l = UI.sub(Emoticon.LABELS[i]);
                l.setHorizontalAlignment(JLabel.CENTER);
                l.setBounds(x, y + 62, ew, 18);
                emoPanel.add(l);
            }
            emoPanel.setVisible(false);
            add(emoPanel);

            // 이모티콘 미리보기: 입력줄 바로 위에 크게 (같은 걸 한 번 더 누르면 전송). 오른쪽 위 x로 취소
            preview = sheet();
            previewView = new EmoticonView(Emoticon.IDS[0], 92);
            previewView.setLocation((W - 24 - 92) / 2 - 60, 10);
            Widgets.onClick(previewView, () -> {
                if (picked != null) pickEmoticon(picked); // 미리보기 그림을 눌러도 전송
            });
            preview.add(previewView);
            previewHint.setBounds((W - 24) / 2 - 8, 44, 160, 22);
            preview.add(previewHint);
            JButton close = UI.soft("×");
            close.setMargin(new Insets(0, 0, 2, 0));
            close.setBounds(W - 24 - 40, 8, 30, 30);
            close.addActionListener(e -> clearPick());
            preview.add(close);
            preview.setVisible(false);
            add(preview);
            setComponentZOrder(preview, 0);

            plus = UI.soft("+");
            plus.setFont(UI.font(Font.BOLD, 20f));
            plus.setMargin(new Insets(0, 0, 2, 0));
            add(plus);
            plus.addActionListener(e -> showTray(!tray.isVisible()));

            // 입력칸 오른쪽 안의 회색 그림: 이모티콘(웃는 얼굴) <-> 키보드
            emoToggle = new JButton(new ToggleIcon(false));
            emoToggle.putClientProperty("JButton.buttonType", "toolBarButton");
            emoToggle.setFocusable(false);
            emoToggle.setToolTipText("이모티콘");
            emoToggle.addActionListener(e -> setEmoMode(!emoMode));
            input.putClientProperty("JTextField.trailingComponent", emoToggle);
            input.putClientProperty("JTextField.placeholderText", "메시지 입력");
            add(input);
            send = UI.pill("전송", UI.LAVENDER, Color.WHITE);
            send.setMargin(new Insets(0, 0, 0, 0));
            add(send);
            Runnable go = () -> {
                if (picked != null) { // 미리보기 중인 이모티콘이 있으면 같이 보냄 (카톡처럼)
                    sendEmoticon(picked);
                    clearPick();
                }
                String t = input.getText().trim();
                if (t.isEmpty()) return;
                sendText(t);
                input.setText("");
            };
            send.addActionListener(e -> go.run());
            input.addActionListener(e -> go.run());
            layoutBottom();
        }

        // + 누르면 올라오는 판 배경 (뒤 말풍선이 비치지 않게 바탕을 먼저 칠함)
        private static JPanel sheet() {
            return new UI.GlassPanel(22) {
                private static final long serialVersionUID = 1L;

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Widgets.smooth(g2);
                    g2.setColor(new Color(0xFBF9FF));
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 22, 22);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
        }

        // 입력줄 위치: 이모티콘 판이 떠 있으면 판 위로, 아니면 맨 아래
        // 말풍선 영역 높이도 같이 줄였다 늘림 (판이 말풍선을 가리지 않게)
        protected void layoutBottom() {
            int rowY = emoMode ? H - EMO_H - 48 : H - 56;
            plus.setBounds(14, rowY, 40, 40);
            input.setBounds(60, rowY, W - 148, 40);
            send.setBounds(W - 82, rowY, 66, 40);
            layoutExtra(rowY);
            scroll.setBounds(12, topY, W - 24, rowY - 10 - topY);
            tray.setBounds(12, rowY - 108, W - 24, 98);
            preview.setBounds(12, rowY - 122, W - 24, 112);
            revalidate();
            repaint();
            chat.scrollToEnd();
        }

        private void showTray(boolean on) {
            if (on) setEmoMode(false); // + 판이랑 이모티콘 판은 동시에 안 뜸
            tray.setVisible(on);
            plus.setText(on ? "×" : "+");
            repaint();
        }

        // 이모티콘 판 켜고 끄기. 켜면 그림이 키보드로 바뀌고, 다시 누르면 판이 닫히고 입력칸에 커서
        private void setEmoMode(boolean on) {
            if (emoMode == on) return;
            emoMode = on;
            if (on) showTray(false);
            emoPanel.setVisible(on);
            if (!on) clearPick();
            emoToggle.setIcon(new ToggleIcon(on));
            emoToggle.setToolTipText(on ? "키보드" : "이모티콘");
            layoutBottom();
            if (!on) input.requestFocusInWindow();
        }

        // 이모티콘 하나 누름: 처음이면 미리보기, 미리보기 중인 걸 또 누르면 전송
        private void pickEmoticon(String id) {
            if (id.equals(picked)) {
                sendEmoticon(id);
                clearPick();
                return;
            }
            picked = id;
            previewView.setId(id);
            previewHint.setText(Emoticon.LABELS[Emoticon.index(id)]);
            for (EmoticonView v : emoViews) v.setSelected(v.id().equals(id));
            preview.setVisible(true);
            repaint();
        }

        private void clearPick() {
            picked = null;
            if (preview == null) return;
            preview.setVisible(false);
            for (EmoticonView v : emoViews) if (v != null) v.setSelected(false);
            repaint();
        }

        // 내 닉네임이 로그인마다 달라서 대화창을 새로 만듦
        protected void newChat() {
            chat = new ChatView(app.myNick == null ? "" : app.myNick, 210);
            holder.removeAll();
            holder.add(chat, java.awt.BorderLayout.SOUTH); // 메시지가 적을 때는 아래쪽부터 (메신저처럼)
            holder.revalidate();
            if (tray != null) {
                showTray(false);
                setEmoMode(false);
            }
            infoSent = false;
        }

        // 받은 메시지를 종류에 맞는 말풍선으로
        protected void show(String sender, String kind, String content, long time) {
            switch (kind) {
                case "E": // 이모티콘은 말풍선 없이 그림만 크게
                    chat.addBubble(sender, new EmoticonView(content, 100), time);
                    break;
                case "I":
                    if (chat.isMine(sender)) infoSent = true;
                    chat.addBubble(sender, new Media.InfoCard(content, chat.isMine(sender)), time);
                    break;
                case "P": {
                    String[] f = content.split("\\|", 3); // 아이디|크기|이름
                    Media.ImageBubble b = new Media.ImageBubble(
                            () -> app.requestFile(f[0], d -> Media.viewImage(app, d, f[2])));
                    chat.addBubble(sender, b, time);
                    app.requestFile(f[0], d -> { // 사진은 바로 받아서 작게 보여줌
                        b.setImage(Media.read(d));
                        chat.scrollToEnd();
                    });
                    break;
                }
                case "V": {
                    String[] f = content.split("\\|", 3);
                    Media.FileBubble[] b = new Media.FileBubble[1];
                    b[0] = new Media.FileBubble(f[2], Long.parseLong(f[1]), () -> { // 동영상은 크니까 누를 때 받음
                        b[0].setHint("받는 중...");
                        app.requestFile(f[0], d -> {
                            b[0].setHint("눌러서 재생");
                            Media.openVideo(app, d, f[2]);
                        });
                    });
                    chat.addBubble(sender, b[0], time);
                    break;
                }
                default:
                    chat.addMessage(sender, content, time);
            }
        }

        private void pickMedia() {
            showTray(false);
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("사진, 동영상 (20MB까지)", concat(Media.IMAGE_EXT, Media.VIDEO_EXT)));
            if (fc.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
            File f = fc.getSelectedFile();
            if (!Media.isImage(f.getName()) && !Media.isVideo(f.getName())) {
                app.toast("사진이나 동영상만 보낼 수 있어요");
                return;
            }
            if (f.length() > AppFrame.MAX_FILE) {
                app.toast("20MB보다 큰 파일은 못 보내요");
                return;
            }
            try {
                app.sendFile(where(), f.getName().replace(" ", "_"), Files.readAllBytes(f.toPath()));
            } catch (IOException e) {
                app.toast("파일을 읽지 못했어요");
            }
        }

        private static String[] concat(String[] a, String[] b) {
            String[] r = Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, r, a.length, b.length);
            return r;
        }

        private void capture() {
            showTray(false);
            Media.capture(app, png -> app.sendFile(where(), "캡쳐_" + new SimpleDateFormat("MMdd_HHmmss").format(new Date()) + ".png", png));
        }

        private void sendInfo() {
            showTray(false);
            if (app.me == null) return;
            String sid = app.me.studentId.isEmpty() ? app.dbStudentId : app.me.studentId;
            if (sid.isEmpty()) {
                app.toast("메뉴 > 내 프로필 수정에서 학번을 먼저 넣어 주세요");
                return;
            }
            if (infoSent) { // 이 대화에 이미 내 정보 카드가 있으면 한 번 더 물어봄
                String who = this instanceof Room ? "이 방에는" : title.getText() + "님에게는";
                Object[] opts = { "다시 보내기", "취소" };
                int r = JOptionPane.showOptionDialog(app, who + " 이미 내 정보를 보냈어요.\n다시 보낼까요?", "내 정보 보내기",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[1]);
                if (r != 0) return;
            }
            Boolean phone = Media.askInfo(app, app.realName, sid, app.phone, this instanceof Room);
            if (phone != null) sendInfo(phone);
        }

        @Override
        public void onHide() {
            showTray(false);
            setEmoMode(false);
        }

        // 제목줄 < 버튼 (단체방은 여기서 방을 나감)
        protected void onBack() {
            app.goBack();
        }

        abstract void sendText(String t);

        abstract void sendInfo(boolean phone);

        abstract void sendEmoticon(String id);

        abstract String where(); // 파일 올릴 때 대상 ("dm 닉네임", "room 방이름")
    }

    // ===================== 채팅 목록 =====================
    public static class ChatList extends AppFrame.Screen {
        private static final long serialVersionUID = 1L;
        private final AppFrame app;
        private final JToggleButton tDm = seg("1:1 대화"), tRoom = seg("단체방");
        private final Screens.RowList list = new Screens.RowList();
        private final JButton make;
        private String dms = "", rooms = "";

        public ChatList(AppFrame app) {
            super("chat");
            this.app = app;
            header("채팅", null);
            ButtonGroup g = new ButtonGroup();
            g.add(tDm);
            g.add(tRoom);
            tDm.setSelected(true);
            tDm.setBounds(20, 66, (W - 48) / 2, 36);
            tRoom.setBounds(28 + (W - 48) / 2, 66, (W - 48) / 2, 36);
            add(tDm);
            add(tRoom);
            tDm.addActionListener(e -> onShow());
            tRoom.addActionListener(e -> onShow());

            make = UI.pill("+ 방 만들기", UI.LAVENDER, Color.WHITE);
            make.setMargin(new Insets(0, 0, 0, 0));
            make.setBounds(W - 130, 22, 110, 32);
            add(make);
            make.addActionListener(e -> {
                String name = JOptionPane.showInputDialog(app, "방 이름 (15자 이하)", "단체방 만들기", JOptionPane.PLAIN_MESSAGE);
                if (name == null) return;
                name = name.trim().replace(" ", "_"); // 방 이름은 명령어 칸 하나라 띄어쓰기 대신 _
                if (!name.isEmpty()) app.send("/ROOM_CREATE " + name);
            });
            list.scroll.setBounds(10, 112, W - 20, H - 120);
            add(list.scroll);
        }

        private static JToggleButton seg(String text) {
            JToggleButton b = new JToggleButton(text);
            b.setFont(UI.font(Font.BOLD, 13f));
            b.putClientProperty("JButton.buttonType", "roundRect");
            b.setFocusPainted(false);
            return b;
        }

        public void showRooms() {
            tRoom.setSelected(true);
        }

        @Override
        public void onShow() {
            make.setVisible(tRoom.isSelected());
            if (tDm.isSelected()) app.send("/DM_LIST"); // 방 목록은 서버가 바뀔 때마다 보내줌
            redraw();
        }

        public void setDms(String data) {
            dms = data;
            redraw();
        }

        public void setRooms(String data) {
            rooms = data;
            redraw();
        }

        private void redraw() {
            list.clear();
            if (tDm.isSelected()) {
                int n = 0;
                for (String e : dms.split(";")) {
                    String[] p = e.split(":", 5); // 닉:아바타:시간:종류:내용
                    if (p.length != 5) continue;
                    try {
                        list.add(dmRow(p[0], Integer.parseInt(p[1]), Long.parseLong(p[2]),
                                preview(p[3], URLDecoder.decode(p[4], StandardCharsets.UTF_8))));
                        n++;
                    } catch (IllegalArgumentException ex) {
                        System.out.println("대화 목록 칸 무시: " + e);
                    }
                }
                if (n == 0) list.empty("<html><center>아직 대화가 없어요<br>마음에 드는 룸메에게 메시지를 보내 보세요</center></html>");
            } else {
                int n = 0;
                for (String e : rooms.split(",")) {
                    String[] p = e.split(":");
                    if (p.length != 2) continue;
                    list.add(roomRow(p[0], p[1]));
                    n++;
                }
                if (n == 0) list.empty("<html><center>열린 방이 없어요<br>위의 [+ 방 만들기]로 먼저 열어 보세요</center></html>");
            }
            list.done();
        }

        private Row dmRow(String nick, int avatar, long time, String text) {
            Row r = new Row(70);
            Avatar a = new Avatar(avatar, 46);
            a.setLocation(12, 12);
            r.add(a);
            JLabel n = UI.title(nick, 14f);
            n.setBounds(70, 12, 180, 22);
            r.add(n);
            JLabel t = UI.sub(text.length() > 20 ? text.substring(0, 20) + "..." : text);
            t.setBounds(70, 36, 220, 20);
            r.add(t);
            JLabel w = UI.sub(when(time));
            w.setFont(UI.font(Font.PLAIN, 10.5f));
            w.setHorizontalAlignment(JLabel.RIGHT);
            w.setBounds(W - 60 - 90, 14, 76, 18);
            r.add(w);
            Widgets.onClick(r, () -> app.dm.open(nick, avatar));
            return r;
        }

        private Row roomRow(String name, String count) {
            Row r = new Row(62);
            JLabel n = UI.title(name, 14f);
            n.setBounds(20, 12, 220, 22);
            r.add(n);
            JLabel c = UI.sub(count + "명 참여 중");
            c.setBounds(20, 34, 200, 18);
            r.add(c);
            JButton in = UI.soft("입장");
            in.setMargin(new Insets(0, 0, 0, 0));
            in.setBounds(W - 60 - 82, 15, 66, 30);
            r.add(in);
            in.addActionListener(e -> app.room.enter(name));
            return r;
        }
    }

    // ===================== 1:1 대화 =====================
    public static class Dm extends Talk {
        private static final long serialVersionUID = 1L;
        private final Avatar avatar = new Avatar(0, 42);
        private String partner;
        private boolean loading;

        public Dm(AppFrame app) {
            super(app, 106);
            avatar.setLocation(54, 21);
            add(avatar);
            setComponentZOrder(avatar, 0);
        }

        public String partner() {
            return partner;
        }

        public void open(String nick, int av) {
            partner = nick;
            avatar.setIndex(av);
            title.setText(nick);
            sub.setText("1:1 대화");
            newChat();
            loading = true;
            chat.addNotice("대화 불러오는 중...");
            app.send("/DM_HISTORY " + nick);
            app.go("dm", true);
        }

        public void add(String from, String kind, String content, long time) {
            if (loading) { // 기록 오기 전에 새 메시지가 오면 "불러오는 중" 안내만 지움
                chat.clear();
                loading = false;
            }
            show(from, kind, content, time);
        }

        public void historyDone() {
            if (loading) {
                chat.clear();
                chat.addNotice(partner + "님과의 대화를 시작해 보세요");
                loading = false;
            }
            input.requestFocusInWindow();
        }

        @Override
        void sendText(String t) {
            app.send("/DM " + partner + " " + t);
        }

        @Override
        void sendInfo(boolean phone) {
            app.send("/DM_INFO " + partner + " " + (phone ? 1 : 0));
        }

        @Override
        void sendEmoticon(String id) {
            app.send("/DM_EMO " + partner + " " + id);
        }

        @Override
        String where() {
            return "dm " + partner;
        }
    }

    // ===================== 단체방 =====================
    // (12주차에 잠깐 방 안에서 윷놀이를 했는데, 게임은 게임 탭에서만 하기로 해서 다시 뺌)
    public static class Room extends Talk {
        private static final long serialVersionUID = 1L;
        private String name;

        public Room(AppFrame app) {
            super(app, 56);
        }

        public void open(String roomName) {
            if (name != null && !name.equals(roomName)) app.send("/ROOM_EXIT " + name); // 방은 한 번에 하나만
            name = roomName;
            title.setText(roomName);
            sub.setText("");
            newChat();
        }

        public void users(String room, String names) {
            if (!room.equals(name)) return;
            String[] ns = names.isEmpty() ? new String[0] : names.split(",");
            String s = ns.length + "명 / " + String.join(", ", ns);
            sub.setText(s.length() > 34 ? s.substring(0, 34) + "..." : s);
            sub.setToolTipText(names);
        }

        public void notice(String room, String text) {
            if (room.equals(name)) chat.addNotice(text);
        }

        public void message(String room, String sender, String kind, String content) {
            if (room.equals(name)) show(sender, kind, content, System.currentTimeMillis());
        }

        @Override
        void sendText(String t) {
            app.send("/ROOM_MSG " + name + " " + t);
        }

        @Override
        void sendInfo(boolean phone) {
            app.send("/ROOM_INFO " + name + " " + (phone ? 1 : 0));
        }

        @Override
        void sendEmoticon(String id) {
            app.send("/ROOM_EMO " + name + " " + id);
        }

        @Override
        String where() {
            return "room " + name;
        }

        // 12주차 수정: 원래는 다른 화면으로 가기만 해도 방에서 나갔는데, 아래 탭으로 잠깐 갔다 와도
        // 방에서 빠져버려서, 이제는 < 를 누를 때만 나감 (다른 방을 열 때도 전 방은 나감)
        @Override
        protected void onBack() {
            leave();
            app.goBack();
        }

        public void leave() {
            if (name != null) app.send("/ROOM_EXIT " + name);
            name = null;
        }

        public String name() {
            return name;
        }

        // 방 목록에서 방을 누름: 이미 들어가 있는 방이면 화면만 열고, 아니면 입장 요청
        public void enter(String roomName) {
            if (roomName.equals(name)) app.go("room", true);
            else app.send("/ROOM_ENTER " + roomName);
        }
    }
}
