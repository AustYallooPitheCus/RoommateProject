// MemberHandler.java
// 룸메 찾기: 둘러보기, 한 명 보기, 찜, 내 프로필 수정 (12주차에 UserService에서 분리)
// 다른 핸들러도 쓰는 "같은 성별 회원인지 확인", "닉네임으로 회원 찾기", "내 정보 카드 만들기"가 여기 있음
// 실패는 /FAIL 명령 이유

import java.util.ArrayList;
import java.util.List;

public class MemberHandler implements CommandHandler {

    private final ServerStore store;

    MemberHandler(ServerStore store) {
        this.store = store;
    }

    @Override
    public String[] commands() {
        return new String[] { "/BROWSE", "/USER", "/LIKE", "/LIKES", "/PROFILE_EDIT" };
    }

    @Override
    public void handle(UserService u, String cmd, String[] args) {
        try {
            switch (cmd) {
                case "/BROWSE":       u.WriteOne("/BROWSE_LIST " + entries(u, candidates(u))); break;
                case "/USER":         u.WriteOne("/USER_INFO " + entry(u, memberByNick(u, args.length > 1 ? args[1] : ""))); break;
                case "/LIKE":         like(u, args); break;
                case "/LIKES":        likes(u); break;
                case "/PROFILE_EDIT": profileEdit(u, args); break;
            }
        } catch (JoinException e) {
            u.WriteOne("/FAIL " + cmd + " " + e.getMessage());
        }
    }

    // ===================== 다른 핸들러도 쓰는 것 =====================

    // 그 계정이 나랑 같은 성별의 다른 회원인지 (룸메는 같은 성별끼리만)
    boolean sameGenderMember(UserService u, String id) {
        AccountDB.Account a = AccountDB.find(id);
        return a != null && !id.equals(u.account().id) && a.gender().equals(u.gender())
                && store.getNick(id) != null && store.getProfile(id) != null;
    }

    // 닉네임으로 같은 성별 회원 찾기. 없거나 다른 성별이면 실패 (다른 성별도 없는 사람처럼)
    String memberByNick(UserService u, String nick) throws JoinException {
        String id = store.idByNick(nick);
        if (id == null || !sameGenderMember(u, id)) throw new JoinException("없는사용자");
        return id;
    }

    // 내 정보 카드 내용: 이름|학번|전화번호(안 보내면 -)
    // 클라이언트가 보낸 글자를 믿지 않고 서버에 저장된 정보로 만듦 (남의 학번을 사칭 못 하게)
    String infoCard(UserService u, String phoneFlag) throws JoinException {
        AccountDB.Account a = u.account();
        String sid = store.getProfile(a.id).studentId;
        if (sid.isEmpty()) sid = a.studentId; // 학번 넣기 전에 가입한 프로필이면 계정 DB 학번
        if (sid.isEmpty()) throw new JoinException("학번없음");
        return a.realName + "|" + sid + "|" + (phoneFlag.equals("1") ? a.phone() : "-");
    }

    // 설정, 수정에서 같이 쓰는 프로필 검사
    static Profile decodeProfile(String s) throws JoinException {
        Profile p;
        try {
            p = Profile.decode(s);
        } catch (IllegalArgumentException e) { // 숫자가 아니거나 범위 밖 (NumberFormatException 포함)
            throw new JoinException("설문형식");
        }
        if (p.studentId.isEmpty()) throw new JoinException("학번없음"); // 학번은 룸메 신청할 때 필요해서 꼭 받음
        return p;
    }

    // ===================== 목록 =====================

    // 목록 한 칸: 닉네임:프로필(비공개 항목은 -1, 학번 없음):궁합%:찜했는지
    private String entry(UserService u, String id) {
        Profile other = store.getProfile(id);
        int score = Profile.score(store.getProfile(u.account().id), other);
        return store.getNick(id) + ":" + other.encodePublic() + ":" + score + ":"
                + (store.isLiked(u.account().id, id) ? 1 : 0);
    }

    // 같은 성별 회원 전체, 궁합 높은 순
    private List<String> candidates(UserService u) {
        ArrayList<String> ids = new ArrayList<>();
        for (String id : store.members()) if (sameGenderMember(u, id)) ids.add(id);
        Profile me = store.getProfile(u.account().id);
        ids.sort((x, y) -> Profile.score(me, store.getProfile(y)) - Profile.score(me, store.getProfile(x)));
        return ids;
    }

    private String entries(UserService u, List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) sb.append(";");
            sb.append(entry(u, id));
        }
        return sb.toString();
    }

    // ===================== 찜, 프로필 수정 =====================

    // /LIKE 닉네임 1|0
    private void like(UserService u, String[] args) throws JoinException {
        if (args.length != 3) throw new JoinException("형식오류");
        String id = memberByNick(u, args[1]);
        boolean on = args[2].equals("1");
        store.setLike(u.account().id, id, on);
        u.WriteOne("/LIKE_OK " + args[1] + " " + (on ? 1 : 0));
    }

    private void likes(UserService u) {
        ArrayList<String> ids = new ArrayList<>();
        for (String id : candidates(u)) if (store.isLiked(u.account().id, id)) ids.add(id);
        u.WriteOne("/LIKES_LIST " + entries(u, ids));
    }

    // /PROFILE_EDIT 프로필  (메뉴 > 내 프로필 수정)
    private void profileEdit(UserService u, String[] args) throws JoinException {
        if (args.length != 2) throw new JoinException("형식오류");
        store.setProfile(u.account().id, decodeProfile(args[1]));
        u.WriteOne("/ME_INFO " + store.getProfile(u.account().id).encode());
    }
}
