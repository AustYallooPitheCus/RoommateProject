// JoinException.java
// 가입 검사에서 실패했을 때 던지는 예외 (11주차 추가)
// 수업 예제(NegativeAgeException)처럼 직접 정의한 예외
// 가입 실패는 "충분히 일어날 수 있고, 부른 쪽이 꼭 대응해야 하는" 상황이라 체크 예외(Exception 상속)로 만듦
// -> handleJoin()에서 catch를 빼먹으면 컴파일이 안 되니까 실패 응답을 깜빡할 일이 없음

public class JoinException extends Exception {

    private static final long serialVersionUID = 1L;

    // message에는 클라이언트에게 그대로 보낼 실패 이유를 넣음 (띄어쓰기 없이)
    public JoinException(String reason) {
        super(reason);
    }
}
