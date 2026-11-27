// DmHandler.java
// 1:1 대화 (12주차에 UserService에서 분리)
//   /DM 닉네임 내용, /DM_HISTORY 닉네임, /DM_LIST, /DM_INFO 닉네임 1|0
// 메시지는 ServerStore가 dms.txt에 저장. 상대가 접속 중이면 바로 전달
// 실패는 /FAIL 명령 이유

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class DmHandler implements CommandHandler {

    private final ServerContext ctx;
    private final ServerStore store;
    private final MemberHandler members;

    DmHandler(ServerContext ctx, MemberHandler members) {
        this.ctx = ctx;
        this.store = ctx.store;
        this.members = members;
    }

    @Override
    public String[] commands() {
        return new String[] { "/DM", "/DM_HISTORY", "/DM_LIST", "/DM_INFO" };
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        try {
            switch (cmd) {
                case "/DM":         dm(u, args); break;
                case "/DM_HISTORY": history(u, args); break;
                case "/DM_LIST":    list(u); break;
                case "/DM_INFO":    info(u, args); break;
            }
        } catch (JoinException e) {
            u.WriteOne("/FAIL " + cmd + " " + e.getMessage());
        }
    }

    private String line(ServerStore.Dm d) {
        return "/DM " + store.getNick(d.from) + " " + store.getNick(d.to) + " " + d.time + " " + d.kind + " " + d.text;
    }

    // 저장하고 보낸 사람, 받는 사람(접속 중이면)에게 /DM 보낸닉 받는닉 시간 종류 내용
    // 종류: T 글자, I 내 정보, P 사진, V 동영상 (파일은 FileHandler가 이걸 불러서 보냄)
    void deliver(UserService u, String to, String kind, String content) {
        String line = line(store.addDm(u.account().id, to, kind, content));
        u.WriteOne(line);
        UserService target = ctx.online(to);
        if (target != null && target.isJoined()) target.WriteOne(line); // 접속 안 했으면 다음에 대화 목록에서 봄
    }

    // /DM 닉네임 내용
    private void dm(UserService u, String[] args) throws JoinException {
        if (args.length != 3 || args[2].trim().isEmpty()) throw new JoinException("형식오류");
        deliver(u, members.memberByNick(u, args[1]), "T", args[2].trim());
    }

    // /DM_INFO 닉네임 전화번호포함(1/0)
    private void info(UserService u, String[] args) throws JoinException {
        if (args.length != 3) throw new JoinException("형식오류");
        String to = members.memberByNick(u, args[1]);
        deliver(u, to, "I", members.infoCard(u, args[2]));
        ctx.log("내 정보 보내기 " + u.nick() + " -> " + args[1] + (args[2].equals("1") ? " (전화번호 포함)" : ""));
    }

    // /DM_HISTORY 닉네임 -> /DM ... 여러 줄 + /DM_HISTORY_END 닉네임
    private void history(UserService u, String[] args) throws JoinException {
        if (args.length != 2) throw new JoinException("형식오류");
        String other = members.memberByNick(u, args[1]);
        for (ServerStore.Dm d : store.conversation(u.account().id, other)) u.WriteOne(line(d));
        u.WriteOne("/DM_HISTORY_END " + args[1]);
    }

    // /DM_LIST -> /DM_LIST 닉:아바타:시간:종류:마지막내용(URL 인코딩);...
    private void list(UserService u) {
        String me = u.account().id;
        StringBuilder sb = new StringBuilder();
        for (ServerStore.Dm d : store.lastPerPartner(me)) {
            String other = d.from.equals(me) ? d.to : d.from;
            if (!members.sameGenderMember(u, other)) continue;
            if (sb.length() > 0) sb.append(";");
            sb.append(store.getNick(other)).append(":").append(store.getProfile(other).avatar).append(":")
              .append(d.time).append(":").append(d.kind).append(":")
              .append(URLEncoder.encode(d.text, StandardCharsets.UTF_8));
        }
        u.WriteOne("/DM_LIST " + sb);
    }
}
