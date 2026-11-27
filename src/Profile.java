// Profile.java
// 프로필 + 생활습관 설문 답 (11주차, 프로토타입 영상의 설문을 바탕으로)
// 서버(저장, 궁합 계산)랑 클라이언트(화면 표시)가 같이 씀
//
// 네트워크로 보낼 때는 한 줄 문자열로 바꿔서 보냄 (encode / decode)
//   아바타|학년|학년공개|학과|학과공개|생활패턴,흡연,청결,코골이,잠귀,냉난방|MBTI|학번
//   예) 3|2|1|0|0|0,0,1,0,1,2|INTP|2371380
// 학번은 다른 사람에게 절대 안 보냄 (encodePublic에서 뺌). "내 정보 보내기"를 할 때만 서버가 꺼내 씀

public class Profile {

    // ===== 설문 항목 (번호로 저장, 화면에는 아래 글자로 보여줌) =====
    public static final String[] QUESTIONS = { "생활패턴", "흡연", "청결", "코골이", "잠귀", "냉난방" };
    public static final String[][] OPTIONS = {
        { "아침형", "저녁형" },
        { "안 피워요", "안 피우지만 냄새 괜찮아요", "흡연자예요" },
        { "깔끔해요", "적당해요", "자유로워요" },
        { "거의 없어요", "가끔 골아요", "심한 편이에요" },
        { "아주 예민해요", "보통이에요", "상관없어요" },
        { "더위를 잘 타요", "추위를 잘 타요", "상관없어요" },
    };
    // 목록에 짧게 보여줄 때
    public static final String[][] SHORT = {
        { "아침형", "저녁형" },
        { "비흡연", "냄새 OK", "흡연" },
        { "깔끔", "적당", "자유" },
        { "코골이 없음", "코골이 가끔", "코골이 심함" },
        { "잠귀 예민", "잠귀 보통", "잠귀 무던" },
        { "더위 탐", "추위 탐", "온도 무던" },
    };
    public static final String[] GRADES = { "1학년", "2학년", "3학년", "4학년", "5학년", "6학년" };
    public static final String[] DEPTS = { "공학 계열", "IT/컴퓨터 계열", "인문 계열", "사회과학 계열",
                                           "경영/경제 계열", "디자인/예술 계열", "자연과학 계열", "기타" };
    public static final int AVATAR_COUNT = 8;

    // 궁합 계산할 때 항목별 비중 (합 100). 흡연, 청결을 조금 더 무겁게
    private static final int[] WEIGHT = { 15, 20, 20, 12, 12, 9 };
    private static final int MBTI_WEIGHT = 12;

    public int avatar;
    public int grade;          // 0~5 (1~6학년)
    public boolean gradePublic;
    public int dept;           // DEPTS 번호
    public boolean deptPublic;
    public int[] answers = new int[6];
    public String mbti = "INFP";
    public String studentId = ""; // 학번 7자리 (비공개)

    public String encode() {
        StringBuilder a = new StringBuilder();
        for (int i = 0; i < answers.length; i++) {
            if (i > 0) a.append(",");
            a.append(answers[i]);
        }
        return avatar + "|" + grade + "|" + (gradePublic ? 1 : 0) + "|" + dept + "|" + (deptPublic ? 1 : 0)
                + "|" + a + "|" + mbti + "|" + studentId;
    }

    // 형식이 틀리거나 범위를 벗어나면 IllegalArgumentException (서버에서 잡아서 실패 응답)
    public static Profile decode(String s) {
        String[] p = s.split("\\|");
        // 학번 칸이 없는 7칸짜리는 학번 넣기 전에 저장된 프로필이라 그대로 읽어줌
        if (p.length != 7 && p.length != 8) throw new IllegalArgumentException("칸 개수가 " + p.length + "개");
        Profile r = new Profile();
        r.avatar = range(p[0], AVATAR_COUNT);
        r.grade = range(p[1], GRADES.length);
        r.gradePublic = p[2].equals("1");
        r.dept = range(p[3], DEPTS.length);
        r.deptPublic = p[4].equals("1");
        String[] a = p[5].split(",");
        if (a.length != 6) throw new IllegalArgumentException("설문 답 개수가 " + a.length + "개");
        for (int i = 0; i < 6; i++) r.answers[i] = range(a[i], OPTIONS[i].length);
        if (!p[6].matches("[EI][SN][TF][JP]")) throw new IllegalArgumentException("MBTI 형식");
        r.mbti = p[6];
        if (p.length == 8) {
            if (!p[7].matches("\\d{7}")) throw new IllegalArgumentException("학번 형식");
            r.studentId = p[7];
        }
        return r;
    }

    private static int range(String s, int n) {
        int v = Integer.parseInt(s.trim()); // 숫자가 아니면 NumberFormatException (IllegalArgumentException의 자식)
        if (v < 0 || v >= n) throw new IllegalArgumentException("범위 밖 값 " + v);
        return v;
    }

    // 다른 사람이 볼 때 쓰는 encode (비공개 항목 -1). decode는 -1을 허용하지 않아서 따로 풂
    public String encodePublic() {
        return avatar + "|" + (gradePublic ? grade : -1) + "|" + (gradePublic ? 1 : 0) + "|" + (deptPublic ? dept : -1)
                + "|" + (deptPublic ? 1 : 0) + "|" + encode().split("\\|")[5] + "|" + mbti; // 학번은 안 넣음
    }

    public static Profile decodePublic(String s) {
        String[] p = s.split("\\|");
        Profile r = decode(p[0] + "|" + Math.max(0, Integer.parseInt(p[1])) + "|" + p[2] + "|"
                + Math.max(0, Integer.parseInt(p[3])) + "|" + p[4] + "|" + p[5] + "|" + p[6]);
        if (p[1].equals("-1")) r.grade = -1;
        if (p[3].equals("-1")) r.dept = -1;
        return r;
    }

    // ===== 궁합 % =====
    // 항목마다 같으면 만점, 한 칸 차이면 절반, 두 칸 차이면 0
    // 냉난방은 순서가 없어서: 같으면 만점, 한쪽이 상관없음이면 3/4, 더위 vs 추위면 0
    // MBTI는 글자 하나 같을 때마다 1/4
    public static int score(Profile a, Profile b) {
        double total = 0;
        for (int i = 0; i < 6; i++) {
            double part;
            if (i == 5) {
                if (a.answers[i] == b.answers[i]) part = 1;
                else if (a.answers[i] == 2 || b.answers[i] == 2) part = 0.75;
                else part = 0;
            } else {
                int d = Math.abs(a.answers[i] - b.answers[i]);
                part = d == 0 ? 1 : (d == 1 ? 0.5 : 0);
            }
            total += WEIGHT[i] * part;
        }
        int same = 0;
        for (int k = 0; k < 4; k++) if (a.mbti.charAt(k) == b.mbti.charAt(k)) same++;
        total += MBTI_WEIGHT * same / 4.0;
        return (int) Math.round(total);
    }

    // 화면용: "아침형 / 비흡연 / 깔끔"
    public String summary3() {
        return SHORT[0][answers[0]] + " / " + SHORT[1][answers[1]] + " / " + SHORT[2][answers[2]];
    }

    public String gradeDept() {
        String g = grade >= 0 ? GRADES[grade] : "학년 비공개";
        String d = dept >= 0 ? DEPTS[dept] : "학과 비공개";
        return g + " / " + d;
    }
}
