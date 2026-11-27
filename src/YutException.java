// YutException.java
// 윷놀이 명령이 규칙에 안 맞을 때 (12주차). 메시지는 /YUT_FAIL 방 이유 의 이유로 그대로 나감
// JoinException, RoomException이랑 같은 방식의 체크 예외

public class YutException extends Exception {

    private static final long serialVersionUID = 1L;

    public YutException(String reason) {
        super(reason);
    }
}
