// Room.java
// 단체방 하나 (11주차에 RoommateServer 안에 있던 걸 12주차에 밖으로 뺌)
// 만든 사람의 성별을 따라가서 같은 성별만 보이고 들어갈 수 있음
// members는 RoomHandler가 synchronized (rooms) 안에서만 바꿈

import java.util.Vector;

public class Room {

    final String name;
    final String gender;
    final Vector<UserService> members = new Vector<>();

    Room(String name, String gender) {
        this.name = name;
        this.gender = gender;
    }

    // "새벽요정,장지아" (/ROOM_USERS 용)
    String memberNames() {
        StringBuilder sb = new StringBuilder();
        for (UserService u : members) {
            if (sb.length() > 0) sb.append(",");
            sb.append(u.nick());
        }
        return sb.toString();
    }
}
