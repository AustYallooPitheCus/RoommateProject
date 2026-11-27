// Emoticon.java
// 이모티콘 목록 (12주차, 임시로 8개). 서버랑 클라이언트가 같이 씀
// 네트워크로는 이름(hi, love ...)만 보내고, 그림은 클라이언트가 EmoticonView로 직접 그림
// 그림 파일을 주고받지 않으니까 글자 메시지처럼 가볍게 보낼 수 있음

public class Emoticon {

    public static final String[] IDS = { "hi", "love", "laugh", "cry", "angry", "wow", "sleepy", "thanks" };
    public static final String[] LABELS = { "안녕", "좋아", "ㅋㅋㅋ", "엉엉", "흥", "헉", "졸려", "고마워" };

    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    public static boolean valid(String id) {
        return index(id) >= 0;
    }
}
