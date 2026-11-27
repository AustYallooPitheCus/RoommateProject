// Member.java
// 클라이언트에서 다른 회원 한 명 (11주차)
// 서버가 보내는 목록 한 칸 "닉네임:프로필:궁합%:찜했는지"를 풀어서 담음

import java.util.ArrayList;
import java.util.List;

public class Member {

    public final String nick;
    public final Profile profile; // 비공개 학년/학과는 -1
    public final int score;
    public boolean liked;

    private Member(String nick, Profile profile, int score, boolean liked) {
        this.nick = nick;
        this.profile = profile;
        this.score = score;
        this.liked = liked;
    }

    public static Member parse(String entry) {
        String[] p = entry.split(":");
        return new Member(p[0], Profile.decodePublic(p[1]), Integer.parseInt(p[2]), p[3].equals("1"));
    }

    // "칸;칸;칸" -> 목록. 이상한 칸은 건너뜀
    public static List<Member> parseList(String data) {
        ArrayList<Member> r = new ArrayList<>();
        if (data == null || data.isEmpty()) return r;
        for (String e : data.split(";")) {
            try {
                r.add(parse(e));
            } catch (RuntimeException ex) {
                System.out.println("목록 칸 무시: " + e);
            }
        }
        return r;
    }
}
