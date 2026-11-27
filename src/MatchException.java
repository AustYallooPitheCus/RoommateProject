// MatchException.java
// 미니게임 사람 모으기(Matchmaker)에서 쓰는 실패. 메시지가 그대로 /X_FAIL 이유 로 감 (13주차)

public class MatchException extends Exception {
    private static final long serialVersionUID = 1L;

    public MatchException(String reason) {
        super(reason);
    }
}
