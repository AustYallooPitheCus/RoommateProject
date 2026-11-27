// RoommateClientMain.java
// 클라이언트 시작 (실행 설정 RoommateClientMain.launch가 이 클래스를 가리켜서 이름은 그대로 둠)
// 11주차: 로그인 창 -> 대기실 창 -> 방 창으로 따로 뜨던 걸 앱 창(AppFrame) 하나로 합침
// 처음 수업 코드(JavaChatClientMain)를 바탕으로 수정

import java.awt.EventQueue;

public class RoommateClientMain {

    public static void main(String[] args) {
        UI.setup(); // FlatLaf + 우리 색/글꼴 (UI.java)
        EventQueue.invokeLater(() -> new AppFrame().setVisible(true));
    }
}
