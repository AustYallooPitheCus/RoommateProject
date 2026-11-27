// FileHandler.java
// 사진, 동영상 주고받기 (11주차에 만들고 12주차에 UserService에서 분리)
// writeUTF는 최대 64KB라서 파일은 "명령 한 줄 + 바로 뒤에 바이트 그대로" 방식
//   올리기  /FILE_UP dm|room 대상 P|V 크기 파일이름  + 바이트(크기만큼)
//   받기    /FILE_GET 아이디 -> /FILE_DATA 아이디 크기 + 바이트
// 올린 파일은 uploads/아이디 로 저장하고, 1:1 대화나 단체방에 P/V 종류 메시지로 알림

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

public class FileHandler implements CommandHandler {

    static final long MAX_FILE = 20L * 1024 * 1024; // 사진, 동영상 한 개 최대 20MB
    static final Path UPLOAD_DIR = Paths.get("uploads");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ServerContext ctx;
    private final MemberHandler members;
    private final DmHandler dms;
    private final RoomHandler rooms;

    FileHandler(ServerContext ctx, MemberHandler members, DmHandler dms, RoomHandler rooms) {
        this.ctx = ctx;
        this.members = members;
        this.dms = dms;
        this.rooms = rooms;
    }

    // /FILE_UP은 바이트를 같이 읽어야 해서 UserService.run이 upload()를 직접 부름. 여기엔 받기만
    @Override
    public String[] commands() {
        return new String[] { "/FILE_GET" };
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        try {
            get(u, args);
        } catch (JoinException e) {
            u.WriteOne("/FAIL " + cmd + " " + e.getMessage());
        }
    }

    // /FILE_UP dm|room 대상 P|V 크기 파일이름 바로 뒤에 바이트가 크기만큼 따라옴
    // 대상이 이상해도 바이트는 꼭 다 읽어야 다음 명령을 제대로 읽을 수 있어서, 먼저 다 읽고 나서 검사함
    // 크기 자체가 이상하면 몇 바이트를 건너뛸지 몰라서 IOException -> 연결을 끊음
    void upload(UserService u, String msg) throws IOException {
        String[] a = msg.split(" ", 6);
        long size = a.length == 6 ? parseSize(a[4]) : -1;
        if (size < 0 || size > MAX_FILE) throw new IOException("파일 크기 이상 " + (a.length > 4 ? a[4] : ""));
        byte[] data = new byte[(int) size];
        u.in().readFully(data);
        String kind = a[3];
        String name = a[5].replace("|", "_");
        if (!u.isJoined() || (!kind.equals("P") && !kind.equals("V"))) {
            u.WriteOne("/FAIL /FILE_UP 형식오류");
            return;
        }
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(RANDOM.nextInt(1 << 30), 36);
        Files.createDirectories(UPLOAD_DIR);
        Files.write(UPLOAD_DIR.resolve(id), data);
        ctx.log("파일 받음 " + name + " (" + size / 1024 + "KB) from " + u.nick() + " -> " + a[1] + " " + a[2]);
        String content = id + "|" + size + "|" + name;
        try {
            if (a[1].equals("dm")) dms.deliver(u, members.memberByNick(u, a[2]), kind, content);
            else rooms.send(u, a[2], kind, content);
        } catch (JoinException e) {
            u.WriteOne("/FAIL /FILE_UP " + e.getMessage());
        } catch (RoomException e) {
            u.WriteOne("/ROOM_FAIL " + e.getMessage());
        }
    }

    private long parseSize(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // /FILE_GET 아이디 -> /FILE_DATA 아이디 크기 + 바이트
    private void get(UserService u, String[] args) throws JoinException {
        if (args.length != 2 || !args[1].matches("[0-9a-z]{1,20}")) throw new JoinException("형식오류"); // ../ 같은 경로 못 넣게
        byte[] data;
        try {
            data = Files.readAllBytes(UPLOAD_DIR.resolve(args[1]));
        } catch (IOException e) {
            throw new JoinException("없는파일");
        }
        u.WriteBytes("/FILE_DATA " + args[1] + " " + data.length, data);
    }
}
