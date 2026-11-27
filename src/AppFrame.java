// AppFrame.java
// 앱 창 하나에 화면을 갈아 끼우는 방식 (11주차, 프로토타입 영상처럼 폰 앱 모양으로)
//   위: 지금 화면 (로그인, 프로필 설정, 홈(룸메 둘러보기), 프로필, 찜, 채팅, 1:1 대화, 단체방, 메뉴)
//   아래: 탭바 (뒤로, 홈, 찜, 채팅, 메뉴)
// 10주차 대기실 창(RoommateClientView)이랑 방 창(RoomWindow)은 이 창 안의 화면으로 들어옴
//
// 서버 메시지는 ListenNetwork 스레드가 읽어서 onMessage로 넘김
// 화면을 건드리는 건 Swing 스레드에서만 해야 해서 invokeLater로 넘김

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class AppFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    public static final int W = 400, SCREEN_H = 654, NAV_H = 66;
    public static final int MAX_FILE = 20 * 1024 * 1024; // 서버랑 같게

    // 화면 하나. 보일 때마다 onShow가 불림
    public static class Screen extends JPanel {
        private static final long serialVersionUID = 1L;
        public final String tab; // 탭바에서 켜질 칸 (home, likes, chat, menu)

        public Screen(String tab) {
            this.tab = tab;
            setOpaque(false);
            setLayout(null);
            setBounds(0, 0, W, SCREEN_H);
        }

        public void onShow() {
        }

        public void onHide() {
        }

        // 화면 위쪽 제목
        protected void header(String title, String sub) {
            header(title, sub, null);
        }

        // back이 있으면 제목 왼쪽에 < 버튼 (안으로 들어간 화면용)
        protected void header(String title, String sub, Runnable back) {
            int x = 24;
            if (back != null) {
                Widgets.BackButton b = new Widgets.BackButton(back);
                b.setLocation(12, 22);
                add(b);
                x = 48;
            }
            JLabel t = UI.title(title, 22f);
            t.setBounds(x, 22, 340, 32);
            add(t);
            if (sub != null) {
                JLabel s = UI.sub(sub);
                s.setBounds(x, 54, 340, 20);
                add(s);
            }
        }
    }

    // ===== 로그인 정보 (서버가 /LOGIN_OK, /JOIN_OK, /ME_INFO로 알려줌) =====
    public String realName, birth, gender, myNick, phone = "-", dbStudentId = "";
    public Profile me;

    private final JPanel root = new UI.GradientPanel();
    private final Widgets.NavBar nav;
    private final JLabel toast = new JLabel("", SwingConstants.CENTER);
    private final Timer toastTimer;
    private final Map<String, Screen> screens = new HashMap<>();
    private final ArrayDeque<String> back = new ArrayDeque<>();
    private String current;
    private final UI.GlassPanel inviteBar = new UI.GlassPanel(20) {
        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            Widgets.smooth(g2);
            g2.setColor(new Color(0xFCFBFF)); // 뒤 화면 글자가 비치지 않게
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 5, 20, 20);
            g2.setColor(UI.LAVENDER);
            g2.fillRoundRect(0, 8, 5, getHeight() - 21, 4, 4);
            g2.dispose();
            super.paintComponent(g);
        }
    };
    private final JLabel inviteText = UI.title("", 13.5f);
    private String inviter;

    final LoginScreen login;
    final SetupScreen setup;
    final Screens.Browse browse;
    final Screens.Detail detail;
    final Screens.Likes likes;
    final ChatScreens.ChatList chatList;
    final ChatScreens.Dm dm;
    final ChatScreens.Room room;
    final Screens.Menu menu;
    final Screens.Games games;
    final YutScreen yut;

    // ===== 네트워크 =====
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    // 사진, 동영상: 한 번 받은 건 다시 안 받게 아이디별로 기억. 받는 중이면 다 받은 다음 할 일을 모아둠
    private final Map<String, byte[]> files = new HashMap<>();
    private final Map<String, List<Consumer<byte[]>>> waiting = new HashMap<>();

    public AppFrame() {
        setTitle("동거생활");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        root.setPreferredSize(new Dimension(W, SCREEN_H + NAV_H));
        setContentPane(root);

        toast.setOpaque(true);
        toast.setBackground(new Color(0x3B3650));
        toast.setForeground(Color.WHITE);
        toast.setFont(UI.font(Font.BOLD, 12f));
        toast.setVisible(false);
        toast.putClientProperty("FlatLaf.style", "arc: 999");
        root.add(toast);
        toastTimer = new Timer(2200, e -> toast.setVisible(false));
        toastTimer.setRepeats(false);

        nav = new Widgets.NavBar(this::onTab);
        nav.setBounds(0, SCREEN_H, W, NAV_H);
        root.add(nav);

        login = add(new LoginScreen(this), "login");
        setup = add(new SetupScreen(this), "setup");
        browse = add(new Screens.Browse(this), "home"); // 홈 = 룸메 둘러보기
        detail = add(new Screens.Detail(this), "detail");
        likes = add(new Screens.Likes(this), "likes");
        chatList = add(new ChatScreens.ChatList(this), "chat");
        dm = add(new ChatScreens.Dm(this), "dm");
        room = add(new ChatScreens.Room(this), "room");
        menu = add(new Screens.Menu(this), "menu");
        games = add(new Screens.Games(this), "game");
        yut = add(new YutScreen(this), "yut");

        // 윷놀이 초대 알림 (어느 화면에 있든 위쪽에 뜸)
        inviteBar.setBounds(12, 10, W - 24, 62);
        inviteBar.setVisible(false);
        inviteText.setBounds(16, 10, 230, 20);
        inviteBar.add(inviteText);
        JLabel isub = UI.sub("4명이 모이면 바로 시작해요");
        isub.setBounds(16, 30, 230, 18);
        inviteBar.add(isub);
        JButton no = UI.soft("거절"), yes = UI.pill("수락", UI.LAVENDER, Color.WHITE);
        no.setMargin(new java.awt.Insets(0, 0, 0, 0));
        yes.setMargin(new java.awt.Insets(0, 0, 0, 0));
        no.setBounds(W - 24 - 124, 15, 54, 30);
        yes.setBounds(W - 24 - 64, 15, 54, 30);
        no.addActionListener(e -> {
            send("/YUT_DECLINE " + inviter);
            inviteBar.setVisible(false);
        });
        yes.addActionListener(e -> {
            send("/YUT_ACCEPT " + inviter);
            inviteBar.setVisible(false);
            if (!"yut".equals(current)) go("yut", true);
        });
        inviteBar.add(no);
        inviteBar.add(yes);
        root.add(inviteBar);
        root.setComponentZOrder(inviteBar, 0);

        pack();
        setLocationRelativeTo(null);
        go("login", false);
    }

    private <T extends Screen> T add(T s, String key) {
        s.setVisible(false);
        root.add(s);
        screens.put(key, s);
        return s;
    }

    // ===================== 화면 이동 =====================

    // push=true면 지금 화면을 뒤로가기 목록에 넣음
    public void go(String key, boolean push) {
        if (key.equals(current)) {
            screens.get(key).onShow();
            return;
        }
        if (current != null) {
            if (push) back.push(current);
            screens.get(current).onHide();
            screens.get(current).setVisible(false);
        }
        current = key;
        Screen s = screens.get(key);
        boolean outside = key.equals("login") || key.equals("setup") && setup.isFirstTime();
        nav.setVisible(!outside);
        s.setSize(W, outside ? SCREEN_H + NAV_H : SCREEN_H);
        s.setVisible(true);
        nav.setActive(s.tab);
        if (key.equals("chat")) nav.setChatDot(false);
        s.onShow();
        root.repaint();
    }

    // 탭바: 홈, 찜, 채팅, 게임, 메뉴는 뒤로가기 목록을 비우고 이동
    // (뒤로는 12주차부터 화면 왼쪽 위 < 버튼. 탭 첫 화면에는 없고 안으로 들어간 화면에만 있음)
    private void onTab(String key) {
        back.clear();
        go(key, false);
    }

    public void goBack() {
        if (back.isEmpty()) {
            if (!"home".equals(current)) go("home", false);
            return;
        }
        go(back.pop(), false);
    }

    public String current() {
        return current;
    }

    public void toast(String msg) {
        toast.setText(msg);
        int w = toast.getFontMetrics(toast.getFont()).stringWidth(msg) + 36;
        toast.setBounds((W - w) / 2, SCREEN_H - 52, w, 34);
        root.setComponentZOrder(toast, 0);
        toast.setVisible(true);
        toastTimer.restart();
    }

    public void chatDot() {
        if (!"chat".equals(current)) nav.setChatDot(true);
    }

    // ===================== 네트워크 =====================

    // [네이버로 시작하기] -> 연결 (오래 걸릴 수 있어서 스레드에서)
    public void connect(String ip, String port) {
        new Thread(() -> {
            try {
                Socket s = new Socket(ip, Integer.parseInt(port));
                socket = s;
                dis = new DataInputStream(s.getInputStream());
                dos = new DataOutputStream(s.getOutputStream());
                new ListenNetwork(s, dis).start();
                send("/ACCOUNT_LIST");
            } catch (IllegalArgumentException | IOException e) { // 포트가 숫자가 아님, 서버 없음
                SwingUtilities.invokeLater(() -> login.fail("연결 실패: " + e.getMessage()));
            }
        }).start();
    }

    public synchronized void send(String msg) {
        if (dos == null) return;
        try {
            dos.writeUTF(msg);
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> toast("서버로 보내지 못했어요"));
        }
    }

    // ===================== 사진, 동영상 주고받기 =====================

    // 파일 받기. 이미 받은 거면 바로, 아니면 /FILE_GET 보내고 도착하면 then 실행
    public void requestFile(String id, Consumer<byte[]> then) {
        byte[] d = files.get(id);
        if (d != null) {
            then.accept(d);
            return;
        }
        List<Consumer<byte[]>> list = waiting.get(id);
        if (list == null) {
            list = new ArrayList<>();
            waiting.put(id, list);
            send("/FILE_GET " + id); // 처음 부탁할 때만 서버에 요청
        }
        list.add(then);
    }

    private void onFile(String id, byte[] data) {
        files.put(id, data);
        List<Consumer<byte[]>> list = waiting.remove(id);
        if (list != null) for (Consumer<byte[]> c : list) c.accept(data);
    }

    // 파일 올리기. where = "dm 닉네임" 또는 "room 방이름"
    // 크면 오래 걸려서 스레드에서 보냄 (send랑 같은 락이라 보내는 중에 다른 명령이 끼어들지 않음)
    public void sendFile(String where, String name, byte[] data) {
        if (data.length > MAX_FILE) {
            toast("20MB보다 큰 파일은 못 보내요");
            return;
        }
        String kind = Media.isVideo(name) ? "V" : "P";
        if (data.length > 1024 * 1024) toast("보내는 중... (" + Media.sizeText(data.length) + ")");
        new Thread(() -> {
            synchronized (this) {
                if (dos == null) return;
                try {
                    dos.writeUTF("/FILE_UP " + where + " " + kind + " " + data.length + " " + name);
                    dos.write(data);
                    dos.flush();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> toast("파일을 보내지 못했어요"));
                }
            }
        }).start();
    }

    public void logout() {
        send("/EXIT");
        closeSocket();
        room.leave(); // 소켓은 이미 닫혀서 보내는 건 없고 화면 상태만 정리 (서버는 연결이 끊기면 알아서 방에서 뺌)
        yut.reset();
        inviteBar.setVisible(false);
        back.clear();
        myNick = null;
        me = null;
        files.clear();
        waiting.clear();
        go("login", false);
    }

    private void closeSocket() {
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            // 이미 닫힘
        }
        socket = null;
        dos = null;
    }

    class ListenNetwork extends Thread {
        private final Socket s;
        private final DataInputStream in;

        ListenNetwork(Socket s, DataInputStream in) {
            this.s = s;
            this.in = in;
        }

        public void run() {
            try {
                while (true) {
                    String msg = in.readUTF();
                    if (msg.startsWith("/FILE_DATA ")) { // 명령 뒤에 바이트가 붙어옴. 여기서 바로 다 읽어야 다음 명령이 안 꼬임
                        String[] a = msg.split(" ");
                        byte[] data = new byte[Integer.parseInt(a[2])];
                        in.readFully(data);
                        SwingUtilities.invokeLater(() -> onFile(a[1], data));
                        continue;
                    }
                    SwingUtilities.invokeLater(() -> onMessage(msg));
                }
            } catch (EOFException e) {
                lost("서버가 연결을 끊었어요");
            } catch (IOException e) {
                lost("서버 연결이 끊어졌어요");
            }
        }

        private void lost(String why) {
            SwingUtilities.invokeLater(() -> {
                if (socket != s) return; // 로그아웃해서 내가 닫은 거면 조용히
                closeSocket();
                back.clear();
                go("login", false);
                login.fail(why);
            });
        }
    }

    // ===================== 서버 메시지 처리 =====================

    void onMessage(String msg) {
        String[] p = msg.split(" ", 2);
        String cmd = p[0], rest = p.length > 1 ? p[1] : "";
        try {
            switch (cmd) {
                case "/ACCOUNT_LIST": pickAccount(rest); break;
                case "/LOGIN_FAIL":
                    closeSocket();
                    login.fail("로그인 실패: " + reasonText(rest));
                    break;
                case "/LOGIN_OK": {
                    // 실명 생년월일 성별 닉네임(-) 프로필(1/0)
                    String[] a = rest.split(" ");
                    realName = a[0];
                    birth = a[1];
                    gender = a[2];
                    phone = a.length > 5 ? a[5] : "-";
                    dbStudentId = a.length > 6 ? a[6] : "";
                    if (a[3].equals("-") || a[4].equals("0")) { // 처음 온 사람 -> 프로필 설정
                        setup.openFirst(a[3].equals("-") ? "" : a[3]);
                        go("setup", false);
                    }
                    break;
                }
                case "/SETUP_FAIL": setup.error(reasonText(rest)); break;
                case "/JOIN_OK": myNick = rest.split(" ")[0]; break;
                case "/ME_INFO": {
                    boolean first = me == null;
                    me = Profile.decode(rest);
                    if (first) {
                        back.clear();
                        go("home", false);
                        toast(myNick + "님 반가워요!");
                    } else if ("setup".equals(current)) {
                        goBack();
                        toast("프로필을 저장했어요");
                    }
                    browse.refresh();
                    break;
                }
                case "/BROWSE_LIST": browse.setList(Member.parseList(rest)); break;
                case "/USER_INFO": detail.show(Member.parse(rest)); break;
                case "/LIKE_OK": {
                    String[] a = rest.split(" ");
                    boolean on = a[1].equals("1");
                    browse.liked(a[0], on);
                    detail.liked(a[0], on);
                    likes.liked(a[0], on);
                    toast(on ? a[0] + "님을 찜했어요" : "찜을 풀었어요");
                    break;
                }
                case "/LIKES_LIST": likes.setList(Member.parseList(rest)); break;
                case "/DM": {
                    // 보낸닉 받는닉 시간 종류 내용
                    String[] a = rest.split(" ", 5);
                    String other = a[0].equals(myNick) ? a[1] : a[0];
                    if ("dm".equals(current) && other.equals(dm.partner())) dm.add(a[0], a[3], a[4], Long.parseLong(a[2]));
                    else if (!a[0].equals(myNick)) {
                        chatDot();
                        if ("chat".equals(current)) chatList.onShow();
                        else toast(a[0] + ": " + ChatScreens.preview(a[3], a[4]));
                    }
                    break;
                }
                case "/DM_HISTORY_END": dm.historyDone(); break;
                case "/DM_LIST": chatList.setDms(rest); break;
                case "/FAIL": {
                    String[] a = rest.split(" ", 2);
                    toast(reasonText(a.length > 1 ? a[1] : rest));
                    break;
                }
                case "/ROOM_LIST": chatList.setRooms(rest); break;
                case "/ROOM_OK": room.open(rest); go("room", true); break;
                case "/ROOM_USERS": {
                    String[] a = rest.split(" ", 2);
                    room.users(a[0], a.length > 1 ? a[1] : "");
                    break;
                }
                case "/ROOM_NOTICE": {
                    String[] a = rest.split(" ", 2);
                    room.notice(a[0], a.length > 1 ? a[1] : "");
                    break;
                }
                case "/ROOM_MSG": {
                    String[] a = rest.split(" ", 4); // 방 보낸닉 종류 내용
                    if (a.length == 4) room.message(a[0], a[1], a[2], a[3]);
                    break;
                }
                case "/ROOM_FAIL": toast(reasonText(rest)); break;
                // ===== 도파민 윷놀이 (12주차, 게임 탭에서만) =====
                case "/YUT_PARTY": yut.party(rest); break;
                case "/YUT_QUEUED": yut.queued(rest); break;
                case "/YUT_CANCELED": yut.canceled(rest); break;
                case "/YUT_FRIENDS": yut.friends(rest); break;
                case "/YUT_MATCH": yut.match(rest); break;
                case "/YUT_START":
                case "/YUT_BOARD":
                case "/YUT_TURN":
                case "/YUT_THROWN":
                case "/YUT_MOVED":
                case "/YUT_SKIP":
                case "/YUT_TIMEOUT":
                case "/YUT_OUT":
                case "/YUT_RANK": // 12주차 수정: 이게 빠져 있어서 1등 딱지, 1등 팝업이 안 떴음
                case "/YUT_END": yut.onGame(cmd, rest); break;
                case "/YUT_INVITED": // 방장닉
                    inviter = rest.trim();
                    inviteText.setText(inviter + "님이 윷놀이에 초대했어요");
                    inviteBar.setVisible(true);
                    root.setComponentZOrder(inviteBar, 0);
                    root.repaint();
                    break;
                case "/YUT_INVITE_GONE":
                    if (rest.trim().equals(inviter) && inviteBar.isVisible()) {
                        inviteBar.setVisible(false);
                        toast(inviter + "님의 초대가 취소됐어요");
                    }
                    break;
                case "/YUT_DECLINED": toast(rest.trim() + "님이 초대를 거절했어요"); break;
                case "/YUT_FAIL": toast(reasonText(rest.trim())); break;
                default:
                    System.out.println("모르는 메시지: " + msg);
            }
        } catch (RuntimeException e) { // 메시지 형식이 이상해도 앱은 계속 돌아가게
            System.out.println("메시지 처리 오류 [" + msg + "] " + e);
        }
    }

    // "naver01:김*준,naver02:이*윤,..." -> 고르면 /LOGIN, 취소하면 연결 끊기
    private void pickAccount(String data) {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String item : data.split(",")) {
            String[] kv = item.split(":");
            if (kv.length == 2) model.addElement(kv[1] + "  (" + kv[0] + ")");
        }
        JList<String> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setVisibleRowCount(10);
        UI.styleList(list);
        int r = JOptionPane.showConfirmDialog(this, new JScrollPane(list), "네이버 아이디로 로그인 (테스트 계정)",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r == JOptionPane.OK_OPTION && list.getSelectedValue() != null) {
            String v = list.getSelectedValue();
            send("/LOGIN " + v.substring(v.indexOf('(') + 1, v.indexOf(')')));
        } else {
            send("/EXIT");
            closeSocket();
            login.fail(" ");
        }
    }

    // 서버 실패 이유 -> 화면에 보여줄 말
    static String reasonText(String reason) {
        switch (reason) {
            case "이미접속중": return "그 계정은 이미 접속 중이에요";
            case "없는계정": return "없는 계정이에요";
            case "닉네임없음": return "닉네임을 입력해 주세요";
            case "닉네임길이": return "닉네임은 2~10자로 해 주세요";
            case "닉네임띄어쓰기": return "닉네임에 띄어쓰기는 못 써요";
            case "닉네임형식": return "닉네임에 , : ; = | 는 못 써요";
            case "닉네임중복": return "이미 다른 사람이 쓰는 닉네임이에요";
            case "설문형식": return "설문 답이 이상해요. 다시 골라 주세요";
            case "없는사용자": return "찾을 수 없는 사용자예요";
            case "형식오류": return "보낸 형식이 잘못됐어요";
            case "방이름오류": return "방 이름은 15자 이하, , : 없이 해 주세요";
            case "이미있는방": return "같은 이름의 방이 이미 있어요";
            case "없는방": return "없는 방이에요";
            case "이미들어간방": return "이미 들어가 있는 방이에요";
            case "들어가지않은방": return "들어가 있지 않은 방이에요";
            case "학번없음": return "학번을 입력해 주세요 (메뉴 > 내 프로필 수정)";
            case "게임없음": return "진행 중인 윷놀이가 없어요";
            case "이미게임중": return "이미 게임 중이에요";
            case "인원초과": return "4명까지만 같이 할 수 있어요";
            case "방장아님": return "방장만 할 수 있어요";
            case "참가안함": return "참가한 사람만 할 수 있어요";
            case "친구아님": return "찜했거나 1:1 대화한 사람만 초대할 수 있어요";
            case "매칭중": return "매칭 중에는 할 수 없어요";
            case "매칭안함": return "매칭 중이 아니에요";
            case "이미초대": return "이미 초대한 사람이에요";
            case "상대바쁨": return "그 친구는 지금 다른 게임 중이에요";
            case "초대만료": return "이미 끝난 초대예요";
            case "내차례아님": return "내 차례가 아니에요";
            case "던질기회없음": return "던질 기회가 없어요. 말을 옮겨요";
            case "먼저던지기": return "먼저 윷을 던져요";
            case "없는결과": return "그 결과는 없어요";
            case "없는말": return "없는 말이에요";
            case "못움직이는말": return "그 말은 못 움직여요";
            case "없는파일": return "서버에 파일이 없어요";
            default: return reason;
        }
    }
}
