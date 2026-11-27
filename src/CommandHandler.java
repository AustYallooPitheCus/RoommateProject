// CommandHandler.java
// 서버 명령 처리 클래스들이 공통으로 따르는 규칙 (12주차, 서버 리팩토링)
// UserService는 명령 이름으로 담당 핸들러를 찾아서 handle()만 부름.
// 그래서 새 기능(예: 이모티콘)을 넣을 때 UserService를 고치지 않고 핸들러만 추가하면 됨 (인터페이스와 구현 분리)

public interface CommandHandler {

    // 이 핸들러가 맡는 명령들 (예: "/DM", "/DM_LIST")
    String[] commands();

    // 명령 하나 처리. 실패 응답(/FAIL 등)도 핸들러 안에서 보냄
    void handle(UserService u, String cmd, String[] args);

    // 앱에 들어오기 전(로그인, 설정 중)에도 받을 수 있는 명령인지
    default boolean allowedBeforeJoin() {
        return false;
    }
}
