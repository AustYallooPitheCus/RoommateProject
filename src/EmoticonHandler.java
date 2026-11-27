// EmoticonHandler.java
// 이모티콘 보내기 (12주차). 리팩토링으로 나눈 구조 위에 새로 붙인 첫 기능
//   /DM_EMO 닉네임 이모티콘    -> 1:1 대화에 E 종류 메시지   (실패 /FAIL /DM_EMO 이유)
//   /ROOM_EMO 방이름 이모티콘  -> 단체방에 E 종류 메시지      (실패 /ROOM_FAIL 이유)
// 실제 전달은 DmHandler.deliver, RoomHandler.send를 그대로 씀 (사진, 내 정보랑 같은 길)
// 서버에서 바꾼 건 이 파일 추가 + ServerContext에 등록 한 줄뿐

public class EmoticonHandler implements CommandHandler {

    private final MemberHandler members;
    private final DmHandler dms;
    private final RoomHandler rooms;

    EmoticonHandler(MemberHandler members, DmHandler dms, RoomHandler rooms) {
        this.members = members;
        this.dms = dms;
        this.rooms = rooms;
    }

    @Override
    public String[] commands() {
        return new String[] { "/DM_EMO", "/ROOM_EMO" };
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        if (cmd.equals("/DM_EMO")) {
            try {
                if (args.length != 3 || !Emoticon.valid(args[2])) throw new JoinException("형식오류");
                dms.deliver(u, members.memberByNick(u, args[1]), "E", args[2]);
            } catch (JoinException e) {
                u.WriteOne("/FAIL " + cmd + " " + e.getMessage());
            }
        } else {
            try {
                if (args.length != 3 || !Emoticon.valid(args[2])) throw new RoomException("형식오류");
                rooms.send(u, args[1], "E", args[2]);
            } catch (RoomException e) {
                u.WriteOne("/ROOM_FAIL " + e.getMessage());
            }
        }
    }
}
