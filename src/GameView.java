// GameView.java
// 미니게임 판 화면이 공통으로 가져야 하는 것 (13주차). MatchScreen(대기실)이 판을 바꿔 끼울 수 있게
//   윷놀이 YutView, 할리갈리 HalliView

public interface GameView {
    boolean isStarted();          // 게임이 진행 중이거나 결과를 보여주는 중

    boolean isOver();             // 결과 화면

    boolean amRanked();           // 나는 이미 등수가 정해졌거나 탈락함 (기권 대신 그냥 나가기)

    void reset();                 // 로그아웃할 때

    void match(String rest);      // /X_MATCH 게임 내이름 이름표

    void onMessage(String cmd, String id, String rest);

    void onChange(Runnable r);
}
