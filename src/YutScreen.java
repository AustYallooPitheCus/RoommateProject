// YutScreen.java
// 게임 탭 > 도파민 윷놀이 (12주차). 무조건 4명
// 대기실(파티, 친구 초대, 랜덤 매칭)은 13주차에 MatchScreen으로 빼서 할리갈리와 같이 씀. 여기는 윷놀이 판과 규칙만

public class YutScreen extends MatchScreen {

    private static final long serialVersionUID = 1L;
    final YutView view;

    public YutScreen(AppFrame app) {
        this(app, new YutView(app));
    }

    private YutScreen(AppFrame app, YutView v) {
        super(app, "yut", "/YUT_", "윷놀이", "도파민 윷놀이", "4명이 모이면 시작해요", YutGame.MAX_PLAYERS, YutGame.MAX_PLAYERS, v,
                new String[][] {
                    { "말", "한 사람당 4개, 출발점을 지나가야 완주", null },
                    { "지옥", "불구덩이에 멈추면 그 말은 출발 전으로", "#D9483B" },
                    { "잉태", "우물에 멈추면 대기 말 하나가 나와서 업힘", "#2E9B74" },
                    { "낙", "5%, 앞에 나온 결과만 쓰고 없으면 차례 끝", null },
                    { "시간", "한 차례 20초, 넘기면 자동으로 진행", null },
                });
        view = v;
    }
}
