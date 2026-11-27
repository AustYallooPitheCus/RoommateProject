// RoommateServer.java
// 3-way 핸드셰이크부터 시작하는 동거생활 - 서버
// 수업 실습 코드(JavaChatServer, 다양한 프로토콜 추가 버전)를 바탕으로 수정
// 10주차: 가입(/JOIN) + 성별 구분 + 같은 성별끼리만 접속자 목록/채팅
// 11주차: 예외 처리 보강, 채팅방 여러 개(/ROOM_...)
// 11주차: 간편로그인(/LOGIN, 테스트 계정 20개) + 닉네임. 다른 사람에게는 닉네임만 보임
// 11주차: 앱 형식으로 바꿈 (프로토타입 영상 기준). 성별 대기실은 없애고
//         프로필/생활습관 설문(/SETUP), 룸메 둘러보기, 찜, 1:1 메시지 추가. 저장은 ServerStore
//         (랜덤매칭은 넣었다가 뺌. 홈에서 바로 둘러보기로)
// 11주차: 접속 상태 표시는 뺌. 채팅 + 버튼: 사진/동영상, 캡쳐(사진으로 보냄), 내 정보 보내기(룸메 신청용)
//         메시지에 종류를 붙임 (T 글자, I 내 정보, P 사진, V 동영상)
//         파일은 writeUTF로 못 보내서(최대 64KB) "명령 한 줄 + 바로 뒤에 바이트 그대로" 방식으로 주고받음
//           올리기   /FILE_UP 대상종류 대상 P|V 크기 파일이름  + 바이트(크기만큼)
//           받기     /FILE_GET 아이디 -> /FILE_DATA 아이디 크기 + 바이트
// 12주차: 서버 리팩토링. 이 파일에 다 들어 있던 걸 나눔 (기능은 그대로)
//   RoommateServer  서버 화면 + 접속 받기 (이 파일)
//   ServerContext   서버 전체가 같이 쓰는 상태 + 명령 -> 핸들러 표
//   UserService     사용자 1명 스레드: 읽기, 핸들러에 넘기기, 보내기, 정리
//   LoginHandler, MemberHandler, DmHandler, RoomHandler, FileHandler  기능별 명령 처리
//   Room            단체방 하나

import java.awt.EventQueue;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

public class RoommateServer extends JFrame {

    private static final long serialVersionUID = 1L;
    private JPanel contentPane;
    private JTextArea textArea;
    private JTextField txtPortNumber;
    private JButton btnServerStart;

    private ServerSocket socket;
    // 사용자 목록, 저장소, 명령 핸들러 (12주차에 이 화면 클래스에서 분리)
    private final ServerContext ctx = new ServerContext(this::AppendText);

    public static void main(String[] args) {
        UI.setup(); // 클라이언트랑 같은 테마로 (UI.java)
        EventQueue.invokeLater(() -> {
            RoommateServer frame = new RoommateServer();
            frame.setVisible(true);
            // 실행할 때 포트를 같이 주면 버튼 안 누르고 바로 시작 (테스트용)
            if (args.length > 0) frame.startServer(args[0]);
        });
    }

    public RoommateServer() {
        setTitle("Roommate Server");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 338, 386);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);
        contentPane.setLayout(null);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setBounds(12, 10, 300, 244);
        contentPane.add(scrollPane);

        textArea = new JTextArea();
        textArea.setEditable(false);
        scrollPane.setViewportView(textArea);

        JLabel lblPort = new JLabel("Port Number");
        lblPort.setBounds(12, 264, 87, 26);
        contentPane.add(lblPort);

        txtPortNumber = new JTextField("30000");
        txtPortNumber.setHorizontalAlignment(SwingConstants.CENTER);
        txtPortNumber.setBounds(111, 264, 199, 26);
        contentPane.add(txtPortNumber);

        btnServerStart = new JButton("Server Start");
        btnServerStart.addActionListener(e -> startServer(txtPortNumber.getText().trim()));
        btnServerStart.setBounds(12, 300, 300, 35);
        contentPane.add(btnServerStart);
    }

    private void startServer(String port) {
        try {
            socket = new ServerSocket(Integer.parseInt(port));
        } catch (IllegalArgumentException | IOException e1) {
            // 11주차 수정: 원래는 NumberFormatException만 잡아서 포트 70000 같은 범위 밖 숫자는 못 잡았음
            // ServerSocket은 범위 밖 포트에 IllegalArgumentException(언체크)을 던지고,
            // NumberFormatException도 IllegalArgumentException의 자식이라 이거 하나로 둘 다 잡힘
            AppendText("서버 시작 실패: " + e1.getMessage());
            return;
        }
        txtPortNumber.setText(port);
        AppendText("Roommate Server Running.. (port " + port + ")");
        ctx.store.load();
        btnServerStart.setText("Server Running..");
        btnServerStart.setEnabled(false);
        txtPortNumber.setEnabled(false);
        new AcceptServer().start();
    }

    // 접속만 받고 바로 UserService 스레드에 넘김
    class AcceptServer extends Thread {
        public void run() {
            while (true) {
                try {
                    AppendText("Waiting clients ...");
                    Socket client_socket = socket.accept();
                    AppendText("새 연결 " + client_socket.getRemoteSocketAddress());
                    // 원래는 생성자 안에서 첫 메시지(/login)를 readUTF()로 기다렸는데,
                    // 그러면 그 클라이언트가 아무것도 안 보내는 동안 accept()도 같이 멈춤.
                    // 그래서 첫 메시지 읽는 건 run() 안으로 옮김
                    new UserService(client_socket, ctx).start();
                } catch (IOException e) {
                    AppendText("!!!! accept 에러 발생... !!!!");
                    break;
                }
            }
        }
    }

    // 여러 스레드에서 부르기 때문에 화면 갱신은 EDT로 넘김
    public void AppendText(String str) {
        SwingUtilities.invokeLater(() -> {
            textArea.append(str + "\n");
            textArea.setCaretPosition(textArea.getText().length());
        });
    }
}
