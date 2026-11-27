// HalliScreen.java
// 게임 탭 > 죽음의 피흘리기 할리갈리 (13주차). 2~4명
// 제목줄은 소리 버튼과 안 겹치게 짧게 "피흘리기 할리갈리" (게임 카드에는 전체 이름)
// 대기실(파티, 친구 초대, 랜덤 매칭)은 MatchScreen이 같이 처리. 여기는 판(HalliView)과 규칙만
//   카드 56장을 나눠 갖고 피는 3방울 (13주차 수정)
//   친구끼리 2~4명이면 방장이 [바로 시작], 랜덤 매칭은 2명 이상 모이고 10초 안에 안 차면 모인 인원으로 시작

public class HalliScreen extends MatchScreen {

    private static final long serialVersionUID = 1L;
    final HalliView view;

    public HalliScreen(AppFrame app) {
        this(app, new HalliView(app));
    }

    private HalliScreen(AppFrame app, HalliView v) {
        super(app, "halli", "/HG_", "할리갈리", "피흘리기 할리갈리", "2명부터 4명까지, 피 8방울 모으면 승리", HalliGame.MIN_PLAYERS, HalliGame.MAX_PLAYERS, v,
                new String[][] {
                    { "카드", "56장 나눠 가짐, 0장일 때 남이 종을 맞히면 탈락", null },
                    { "종", "같은 과일이 딱 5개면 종! 먼저 친 사람 피 +1, 카드도 가져감", null },
                    { "피", "3방울로 시작, 8방울 모으면 승리, 잘못 치면 -1, 0이면 탈락", "#D8263A" },
                    { "내는 척", "내 차례에 3번까지, 손만 종 쪽으로 움찔", null },
                    { "차례", "5초 안에 안 내면 자동, 카드가 0장이면 건너뜀", null },
                });
        view = v;
    }
}
