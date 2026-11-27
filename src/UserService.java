// UserService.java
// 사용자 1명당 1개 도는 스레드 (12주차에 RoommateServer 안쪽 클래스에서 밖으로 뺌)
// 11주차까지는 로그인, 둘러보기, 찜, 1:1 대화, 단체방, 파일 처리가 전부 이 클래스 안에 있었음 (약 560줄).
// 지금은 이 클래스가 하는 일을 네 가지로 줄임
//   1. 메시지 읽기 (run)
//   2. 명령 이름으로 담당 핸들러를 찾아서 넘기기 (handleMessage)
//   3. 보내기 (WriteOne, WriteBytes)
//   4. 연결 정리 (close)
// 명령마다 실제 처리는 LoginHandler, MemberHandler, DmHandler, RoomHandler, FileHandler가 함

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class UserService extends Thread {

    private final ServerContext ctx;
    private final Socket client_socket;
    private DataInputStream dis;
    private DataOutputStream dos;

    private String UserName = "";            // 닉네임. 다른 사람에게 보이는 건 이것뿐
    private String Gender = "";              // "M" 또는 "F". 로그인한 계정의 주민번호로 정해지고 바꿀 수 없음
    private volatile boolean joined = false; // 닉네임, 프로필 설정까지 끝나서 앱에 들어왔는지
    private AccountDB.Account account = null; // 로그인한 계정 (null이면 아직 로그인 전)

    public UserService(Socket client_socket, ServerContext ctx) {
        this.client_socket = client_socket;
        this.ctx = ctx;
        try {
            dis = new DataInputStream(client_socket.getInputStream());
            dos = new DataOutputStream(client_socket.getOutputStream());
        } catch (IOException e) {
            ctx.log("userService error");
        }
    }

    // ===== 핸들러들이 읽는 정보 (바꾸는 건 아래 두 메소드로만) =====

    AccountDB.Account account() {
        return account;
    }

    String gender() {
        return Gender;
    }

    String nick() {
        return UserName;
    }

    boolean isJoined() {
        return joined;
    }

    // 파일 올리기는 명령 줄 뒤에 바이트가 붙어와서 FileHandler가 직접 읽음
    DataInputStream in() {
        return dis;
    }

    // 로그인 성공 (LoginHandler만 부름). 성별은 주민번호에서 정해지고 이후로 바꾸는 명령은 없음
    void login(AccountDB.Account a) {
        account = a;
        Gender = a.gender();
    }

    // 앱 입장 (ServerContext.addUser 안에서만 부름)
    void join(String nick) {
        UserName = nick;
        joined = true;
    }

    // ===================== 보내기 =====================

    // 한 소켓에 여러 스레드가 동시에 쓰면 메시지가 섞일 수 있어서 synchronized
    public synchronized void WriteOne(String msg) {
        try {
            dos.writeUTF(msg);
        } catch (IOException e) {
            ctx.log("dos.write() error (" + UserName + ")");
            close();
        }
    }

    // 명령 한 줄 + 바이트. WriteOne이랑 같은 락이라 중간에 다른 메시지가 끼어들지 않음
    public synchronized void WriteBytes(String head, byte[] data) {
        try {
            dos.writeUTF(head);
            dos.write(data);
            dos.flush();
        } catch (IOException e) {
            ctx.log("dos.write() error (" + UserName + ")");
            close();
        }
    }

    // ===================== 받기 =====================

    public void run() {
        // 11주차 수정: 어떤 이유로 끝나든(연결 끊김, 처리 중 언체크 예외) finally에서 close()가 꼭 불리게 함
        try {
            while (true) {
                String msg = dis.readUTF().trim();
                ctx.log("recv: " + msg);
                if (msg.startsWith("/FILE_UP ")) { // 뒤에 바이트가 붙어오는 명령이라 따로
                    ctx.files.upload(this, msg);
                    continue;
                }
                if (!handleMessage(msg)) break; // /EXIT면 false
            }
        } catch (IOException e) {
            ctx.log("연결 끊김 " + (joined ? UserName : client_socket.getRemoteSocketAddress()));
        } catch (RuntimeException e) {
            ctx.log("사용자 처리 중 오류 (" + UserName + "): " + e);
        } finally {
            close();
        }
    }

    // 메시지 하나 처리. 연결을 계속 유지하면 true
    private boolean handleMessage(String msg) {
        // 채팅 내용에 띄어쓰기가 있어도 안 잘리게 최대 3조각까지만 나눔
        String[] args = msg.split(" ", 3);
        String cmd = args[0];
        if (cmd.equals("/EXIT")) return false;

        CommandHandler h = ctx.handlerFor(cmd);
        // 앱에 들어오기 전에는 로그인, 설정 관련 명령만 받음
        if (!joined && (h == null || !h.allowedBeforeJoin())) {
            WriteOne("/ERROR 로그인먼저");
            return true;
        }
        if (h == null) {
            WriteOne("/ERROR 모르는명령 " + cmd);
            return true;
        }
        h.handle(this, cmd, args);
        return true;
    }

    // 연결 정리. 여러 번 불려도 한 번만 처리
    void close() {
        boolean removed = ctx.removeUser(this);
        if (account != null) ctx.goOffline(account.id, this);
        try {
            client_socket.close();
        } catch (IOException e) {
            // 이미 닫힘
        }
        if (removed) {
            ctx.rooms.leaveAll(this);
            ctx.yut.disconnected(this); // 윷놀이 매칭, 파티, 게임에서 빠지기
            ctx.halli.disconnected(this); // 할리갈리도
            ctx.log("퇴장 " + UserName + ", 현재 " + ctx.userCount() + "명");
        }
    }
}
