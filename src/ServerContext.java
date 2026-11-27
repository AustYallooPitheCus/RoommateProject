// ServerContext.java
// 서버 전체가 같이 쓰는 것들을 한곳에 모음 (12주차, 서버 리팩토링)
//   - 저장소(ServerStore), 서버 화면 로그
//   - 앱에 들어온 사용자 목록, 로그인 중인 계정 목록
//   - 명령 핸들러들 + "명령 이름 -> 핸들러" 표
// 원래는 이게 전부 RoommateServer(화면 클래스)의 필드였고 UserService가 안쪽 클래스라서 바로 꺼내 썼음.
// 화면과 상관없는 서버 상태를 화면 클래스에서 떼어낸 것 (계층 분리)

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.function.Consumer;

public class ServerContext {

    final ServerStore store;
    private final Consumer<String> log;

    // 앱에 들어온(닉네임, 프로필 설정까지 끝난) 사용자
    private final Vector<UserService> users = new Vector<>();
    // 로그인 중인 계정 (계정 아이디 -> 그 사용자). 같은 계정 두 번 로그인 막기, 1:1 메시지 전달에 씀
    private final HashMap<String, UserService> online = new HashMap<>();

    // 명령 핸들러들. 서로 필요한 것만 생성자로 넘겨줌
    final LoginHandler login;
    final MemberHandler members;
    final DmHandler dms;
    final RoomHandler rooms;
    final FileHandler files;
    final YutHandler yut;
    final HalliHandler halli;
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public ServerContext(Consumer<String> log) {
        this.log = log;
        store = new ServerStore(log);
        login = new LoginHandler(this);
        members = new MemberHandler(store);
        dms = new DmHandler(this, members);
        rooms = new RoomHandler(this, members);
        files = new FileHandler(this, members, dms, rooms);
        register(login);
        register(members);
        register(dms);
        register(rooms);
        register(files);
        register(new EmoticonHandler(members, dms, rooms)); // 12주차 이모티콘 (새 기능은 이렇게 한 줄로 붙임)
        yut = new YutHandler(this);
        register(yut); // 12주차 도파민 윷놀이 (게임 탭 매칭)
        halli = new HalliHandler(this);
        register(halli); // 13주차 죽음의 피흘리기 할리갈리
    }

    private void register(CommandHandler h) {
        for (String cmd : h.commands()) handlers.put(cmd, h);
    }

    // 명령을 맡은 핸들러 (없으면 null)
    CommandHandler handlerFor(String cmd) {
        return handlers.get(cmd);
    }

    void log(String msg) {
        log.accept(msg);
    }

    // ===================== 로그인 중인 계정 =====================

    // 이미 다른 연결에서 로그인 중이면 false
    boolean goOnline(String id, UserService u) {
        synchronized (online) {
            if (online.containsKey(id)) return false;
            online.put(id, u);
            return true;
        }
    }

    UserService online(String id) {
        synchronized (online) {
            return online.get(id);
        }
    }

    // 그 연결이 맞을 때만 뺌 (다시 로그인할 수 있게)
    void goOffline(String id, UserService u) {
        synchronized (online) {
            if (online.get(id) == u) online.remove(id);
        }
    }

    // ===================== 앱에 들어온 사용자 =====================

    // 닉네임을 정하고 목록에 넣는 걸 한 번에 (중간에 다른 스레드가 반쯤 된 상태를 보지 않게)
    int addUser(UserService u, String nick) {
        synchronized (users) {
            u.join(nick);
            users.add(u);
            return users.size();
        }
    }

    boolean removeUser(UserService u) {
        synchronized (users) {
            return users.remove(u);
        }
    }

    // 다른 미니게임에서 바쁜지 (파티, 매칭, 게임 중). 잠금 없이 읽기만 함
    boolean busyElsewhere(UserService u, Matchmaker me) {
        if (yut.mm != me && yut.mm.involved(u)) return true;
        if (halli != null && halli.mm != me && halli.mm.involved(u)) return true;
        return false;
    }

    // 사용자 목록 복사본 (윷놀이 친구 찾기용)
    java.util.List<UserService> usersCopy() {
        synchronized (users) {
            return new ArrayList<>(users);
        }
    }

    int userCount() {
        return users.size();
    }

    // 같은 성별인 사용자에게만 전송
    // 목록을 복사해서 돌림. 보내다가 누가 나가서 목록에서 빠져도 반복문이 안 꼬이게
    void writeSameGender(String gender, String msg) {
        ArrayList<UserService> copy;
        synchronized (users) {
            copy = new ArrayList<>(users);
        }
        for (UserService u : copy) {
            if (u.gender().equals(gender)) u.WriteOne(msg);
        }
    }
}
