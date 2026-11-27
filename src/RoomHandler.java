// RoomHandler.java
// 단체방 (11주차에 만든 채팅방 기능, 12주차에 UserService에서 분리)
//   /ROOM_CREATE 방, /ROOM_ENTER 방, /ROOM_EXIT 방, /ROOM_MSG 방 내용, /ROOM_INFO 방 1|0
// 방 목록은 이 클래스만 가지고 있고, 바꿀 때는 전부 synchronized (rooms) 안에서
// 실패는 /ROOM_FAIL 이유

import java.util.ArrayList;
import java.util.LinkedHashMap;

public class RoomHandler implements CommandHandler {

    private final ServerContext ctx;
    private final MemberHandler members;
    // 방 이름 -> 방. 만든 순서대로 보이게 LinkedHashMap
    private final LinkedHashMap<String, Room> rooms = new LinkedHashMap<>();

    RoomHandler(ServerContext ctx, MemberHandler members) {
        this.ctx = ctx;
        this.members = members;
    }

    @Override
    public String[] commands() {
        return new String[] { "/ROOM_CREATE", "/ROOM_ENTER", "/ROOM_EXIT", "/ROOM_MSG", "/ROOM_INFO" };
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        try {
            if (args.length < 2) throw new RoomException("형식오류");
            String name = args[1];
            switch (cmd) {
                case "/ROOM_CREATE": create(u, name); break;
                case "/ROOM_ENTER":  enter(u, name); break;
                case "/ROOM_EXIT":   exit(u, name); break;
                case "/ROOM_MSG":    message(u, name, args.length == 3 ? args[2] : ""); break;
                case "/ROOM_INFO":   send(u, name, "I", members.infoCard(u, args.length == 3 ? args[2] : "0")); break;
            }
        } catch (RoomException | JoinException e) {
            u.WriteOne("/ROOM_FAIL " + e.getMessage());
        }
    }

    // 같은 성별 방만 "방이름:인원" 형식으로 이어 붙임 (입장할 때 LoginHandler도 씀)
    String roomListFor(String gender) {
        StringBuilder sb = new StringBuilder();
        synchronized (rooms) {
            for (Room r : rooms.values()) {
                if (!r.gender.equals(gender)) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(r.name).append(":").append(r.members.size());
            }
        }
        return sb.toString();
    }

    private void broadcastRoomList(String gender) {
        ctx.writeSameGender(gender, "/ROOM_LIST " + roomListFor(gender));
    }

    // 방 사람들에게만 전송. 복사본으로 돌림
    void sendToRoom(Room r, String msg) {
        ArrayList<UserService> copy;
        synchronized (rooms) {
            copy = new ArrayList<>(r.members);
        }
        for (UserService m : copy) m.WriteOne(msg);
    }

    // 내가 볼 수 있는 방 찾기. 다른 성별 방은 아예 없는 방처럼 대함 (있다는 것 자체를 알려주지 않으려고)
    private Room find(UserService u, String name) throws RoomException {
        Room r = rooms.get(name);
        if (r == null || !r.gender.equals(u.gender())) throw new RoomException("없는방");
        return r;
    }

    private void create(UserService u, String name) throws RoomException {
        if (name.isEmpty() || name.length() > 15 || name.contains(",") || name.contains(":"))
            throw new RoomException("방이름오류"); // , : 는 /ROOM_LIST 구분자라서 막음
        Room r;
        synchronized (rooms) {
            if (rooms.containsKey(name)) throw new RoomException("이미있는방");
            r = new Room(name, u.gender());
            r.members.add(u);
            rooms.put(name, r);
        }
        ctx.log("방 생성 [" + name + "] by " + u.nick());
        afterEnter(u, r);
    }

    private void enter(UserService u, String name) throws RoomException {
        Room r;
        synchronized (rooms) {
            r = find(u, name);
            if (r.members.contains(u)) throw new RoomException("이미들어간방");
            r.members.add(u);
        }
        afterEnter(u, r);
    }

    private void afterEnter(UserService u, Room r) {
        u.WriteOne("/ROOM_OK " + r.name);
        sendToRoom(r, "/ROOM_USERS " + r.name + " " + r.memberNames());
        sendToRoom(r, "/ROOM_NOTICE " + r.name + " " + u.nick() + "님이 들어왔습니다.");
        broadcastRoomList(u.gender());
    }

    private void exit(UserService u, String name) throws RoomException {
        Room r;
        boolean empty;
        synchronized (rooms) {
            r = find(u, name);
            if (!r.members.remove(u)) throw new RoomException("들어가지않은방");
            empty = r.members.isEmpty();
            if (empty) rooms.remove(name); // 마지막 사람이 나가면 방 삭제
        }
        if (empty) {
            ctx.log("방 삭제 [" + name + "]");
        } else {
            sendToRoom(r, "/ROOM_USERS " + r.name + " " + r.memberNames());
            sendToRoom(r, "/ROOM_NOTICE " + r.name + " " + u.nick() + "님이 나갔습니다.");
        }
        broadcastRoomList(u.gender());
    }

    // 내가 들어가 있는 방 (방에 메시지를 보낼 때 확인)
    Room joinedRoom(UserService u, String name) throws RoomException {
        synchronized (rooms) {
            Room r = find(u, name);
            if (!r.members.contains(u)) throw new RoomException("들어가지않은방");
            return r;
        }
    }

    private void message(UserService u, String name, String text) throws RoomException {
        Room r = joinedRoom(u, name);
        if (text.isEmpty()) return;
        sendToRoom(r, "/ROOM_MSG " + r.name + " " + u.nick() + " T " + text);
    }

    // 방에 글자 말고 다른 종류(I 내 정보, P 사진, V 동영상) 보내기. FileHandler도 이걸 부름
    void send(UserService u, String name, String kind, String content) throws RoomException {
        Room r = joinedRoom(u, name);
        sendToRoom(r, "/ROOM_MSG " + r.name + " " + u.nick() + " " + kind + " " + content);
    }

    // 연결이 끊길 때 들어가 있던 방에서 전부 빼기
    void leaveAll(UserService u) {
        ArrayList<String> mine = new ArrayList<>();
        synchronized (rooms) {
            for (Room r : rooms.values()) if (r.members.contains(u)) mine.add(r.name);
        }
        for (String name : mine) {
            try {
                exit(u, name);
            } catch (RoomException e) {
                // 그 사이에 이미 빠졌으면 그냥 넘어감
            }
        }
    }
}
