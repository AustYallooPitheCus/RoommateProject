// RoomException.java
// 채팅방 만들기/들어가기/나가기/보내기에서 실패했을 때 던지는 예외 (11주차 추가)
// JoinException이랑 같은 방식: 메시지에 실패 이유를 넣고, 받는 쪽에서 /ROOM_FAIL 이유 로 보냄

public class RoomException extends Exception {

    private static final long serialVersionUID = 1L;

    public RoomException(String reason) {
        super(reason);
    }
}
