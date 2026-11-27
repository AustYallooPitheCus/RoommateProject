// LoginScreen.java
// 첫 화면: 서버 주소 + 간편로그인 (10주차 시작 창을 앱 화면으로 옮김)
// 카카오는 아직 준비 중, 네이버만 동작

import java.awt.Color;
import java.awt.Font;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

public class LoginScreen extends AppFrame.Screen {

    private static final long serialVersionUID = 1L;
    private final JButton naver;
    private final JLabel status;

    public LoginScreen(AppFrame app) {
        super("home");
        int W = AppFrame.W;

        Avatar logo = new Avatar(2, 96);
        logo.setLocation((W - 96) / 2, 96);
        add(logo);
        JLabel title = UI.title("동거생활", 34f);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBounds(0, 206, W, 44);
        add(title);
        JLabel sub = UI.sub("같이 살 룸메, 여기서 찾아요");
        sub.setFont(UI.font(Font.PLAIN, 13f));
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        sub.setBounds(0, 252, W, 22);
        add(sub);

        JLabel lServer = UI.sub("서버");
        lServer.setBounds(60, 336, 100, 20);
        add(lServer);
        JTextField ip = new JTextField("127.0.0.1");
        ip.setHorizontalAlignment(SwingConstants.CENTER);
        ip.setBounds(56, 358, 190, 38);
        add(ip);
        JTextField port = new JTextField("30000");
        port.setHorizontalAlignment(SwingConstants.CENTER);
        port.setBounds(252, 358, 92, 38);
        add(port);

        JLabel lLogin = UI.sub("간편로그인");
        lLogin.setBounds(60, 414, 100, 20);
        add(lLogin);
        JButton kakao = UI.pill("카카오로 시작하기", UI.KAKAO, new Color(0x191919));
        kakao.setBounds(56, 436, 288, 46);
        add(kakao);
        kakao.addActionListener(e -> app.toast("카카오 로그인은 준비 중이에요. 네이버로 시작해 주세요"));

        naver = UI.pill("네이버로 시작하기", UI.NAVER, Color.WHITE);
        naver.setBounds(56, 490, 288, 46);
        add(naver);
        naver.addActionListener(e -> {
            naver.setEnabled(false);
            setStatus("서버 연결 중...", false);
            app.connect(ip.getText().trim(), port.getText().trim());
        });

        status = UI.sub(" ");
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setBounds(40, 548, W - 80, 24);
        add(status);

        UI.card(this, 36, 318, W - 72, 266);

        JLabel foot = UI.sub("로컬호스트쨩 / 네트워크프로그래밍 텀프로젝트");
        foot.setFont(UI.font(Font.PLAIN, 11f));
        foot.setHorizontalAlignment(SwingConstants.CENTER);
        foot.setBounds(0, 660, W, 20);
        add(foot);
    }

    private void setStatus(String msg, boolean error) {
        status.setText(msg);
        status.setForeground(error ? UI.ERROR : UI.TEXT_SUB);
    }

    // 연결 실패, 로그인 실패, 취소 -> 버튼 다시 누를 수 있게
    public void fail(String msg) {
        setStatus(msg, !msg.isBlank());
        naver.setEnabled(true);
    }

    @Override
    public void onShow() {
        naver.setEnabled(true);
    }
}
