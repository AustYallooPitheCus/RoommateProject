// LoginHandler.java
// 간편로그인 + 처음 프로필 설정 + 앱 입장 (12주차에 UserService에서 분리)
//   /ACCOUNT_LIST            -> /ACCOUNT_LIST naver01:김*준,...
//   /LOGIN 계정아이디        -> /LOGIN_OK ... 또는 /LOGIN_FAIL 이유
//   /SETUP 닉네임 프로필     -> 앱 입장 또는 /SETUP_FAIL 이유
// 앱에 들어오기 전에도 받는 명령은 이 셋뿐

public class LoginHandler implements CommandHandler {

    private final ServerContext ctx;
    private final ServerStore store;

    LoginHandler(ServerContext ctx) {
        this.ctx = ctx;
        this.store = ctx.store;
    }

    @Override
    public String[] commands() {
        return new String[] { "/ACCOUNT_LIST", "/LOGIN", "/SETUP" };
    }

    @Override
    public boolean allowedBeforeJoin() {
        return true;
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        switch (cmd) {
            case "/ACCOUNT_LIST": sendAccountList(u); break;
            case "/LOGIN":        handleLogin(u, args); break;
            case "/SETUP":        handleSetup(u, args); break;
        }
    }

    // 계정 고르는 화면용. 실명은 가운데를 가려서 보냄
    private void sendAccountList(UserService u) {
        StringBuilder sb = new StringBuilder();
        for (AccountDB.Account a : AccountDB.ACCOUNTS) {
            if (sb.length() > 0) sb.append(",");
            sb.append(a.id).append(":").append(a.maskedName());
        }
        u.WriteOne("/ACCOUNT_LIST " + sb);
    }

    // 로그인 검사. 문제가 있으면 JoinException
    private void tryLogin(UserService u, String[] args) throws JoinException {
        if (u.account() != null) throw new JoinException("이미로그인");
        if (args.length != 2) throw new JoinException("형식오류");
        AccountDB.Account a = AccountDB.find(args[1]);
        if (a == null) throw new JoinException("없는계정");
        if (!ctx.goOnline(a.id, u)) throw new JoinException("이미접속중");
        u.login(a);
    }

    // /LOGIN 계정아이디
    //   -> /LOGIN_OK 실명 생년월일 성별 닉네임(없으면 -) 프로필있음(1/0) 전화번호 학번
    //   닉네임, 프로필이 다 있으면 바로 앱 입장. 아니면 클라이언트가 설정 화면을 끝내고 /SETUP을 보낼 때까지 기다림
    private void handleLogin(UserService u, String[] args) {
        try {
            tryLogin(u, args);
        } catch (JoinException e) {
            u.WriteOne("/LOGIN_FAIL " + e.getMessage());
            return;
        }
        AccountDB.Account a = u.account();
        String nick = store.getNick(a.id);
        boolean hasProfile = store.getProfile(a.id) != null;
        ctx.log("로그인 " + a.id + " (" + u.gender() + ") 닉네임 " + nick + ", 프로필 " + (hasProfile ? "있음" : "없음"));
        // 주민번호는 보내지 않음
        u.WriteOne("/LOGIN_OK " + a.realName + " " + a.birthDate() + " " + u.gender() + " "
                + (nick != null ? nick : "-") + " " + (hasProfile ? 1 : 0) + " " + a.phone() + " " + a.studentId);
        if (nick != null && hasProfile) enterApp(u, nick);
    }

    // 닉네임 규칙
    private void checkNick(String nick) throws JoinException {
        if (nick.isEmpty()) throw new JoinException("닉네임없음");
        if (nick.length() < 2 || nick.length() > 10) throw new JoinException("닉네임길이");
        for (String bad : new String[] { ",", ":", ";", "=", "|" })
            if (nick.contains(bad)) throw new JoinException("닉네임형식"); // 전부 목록, 저장 파일 구분자라서 막음
        if (nick.equals("-")) throw new JoinException("닉네임형식"); // "-"는 닉네임 없음 표시
    }

    // /SETUP 닉네임 프로필  (처음 로그인했을 때 설정 화면을 다 채우면 옴)
    private void trySetup(UserService u, String[] args) throws JoinException {
        if (u.account() == null) throw new JoinException("로그인먼저");
        if (u.isJoined()) throw new JoinException("이미입장");
        if (args.length != 3) throw new JoinException("형식오류");
        checkNick(args[1]);
        Profile p = MemberHandler.decodeProfile(args[2]);
        store.setNick(u.account().id, args[1]); // 다른 계정이 쓰는 닉네임이면 여기서 JoinException
        store.setProfile(u.account().id, p);
    }

    private void handleSetup(UserService u, String[] args) {
        try {
            trySetup(u, args);
        } catch (JoinException e) {
            u.WriteOne("/SETUP_FAIL " + e.getMessage());
            return;
        }
        enterApp(u, args[1]);
    }

    // 앱 입장: 내 정보와 단체방 목록을 보내줌
    private void enterApp(UserService u, String nick) {
        int count = ctx.addUser(u, nick);
        ctx.log("입장 " + nick + " (" + u.gender() + "), 현재 " + count + "명");
        u.WriteOne("/JOIN_OK " + nick + " " + u.gender());
        u.WriteOne("/ME_INFO " + store.getProfile(u.account().id).encode());
        u.WriteOne("/ROOM_LIST " + ctx.rooms.roomListFor(u.gender()));
    }
}
