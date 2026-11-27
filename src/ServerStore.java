// ServerStore.java
// 서버가 파일에 저장해 두는 정보 모음 (11주차). 서버를 껐다 켜도 남아야 하는 것들
//   nicknames.txt  계정=닉네임
//   profiles.txt   계정=프로필(Profile.encode)
//   likes.txt      계정=찜한계정,찜한계정,...
//   dms.txt        보낸계정|받는계정|시간|종류|내용(URL 인코딩)   <- 1:1 메시지. 한 줄씩 뒤에 붙임
//                  종류: T 글자, I 내 정보(룸메 신청), P 사진, V 동영상 (종류 칸이 없는 옛날 줄은 T)
//   uploads/       사진, 동영상 파일 (파일 이름 = 아이디)
// 원래 RoommateServer 안에 있던 닉네임 저장 코드를 여기로 옮기고 나머지를 추가함
// 여러 사용자 스레드가 동시에 부르기 때문에 public 메소드는 전부 synchronized

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ServerStore {

    private static final Path NICK_FILE = Paths.get("nicknames.txt");
    private static final Path PROFILE_FILE = Paths.get("profiles.txt");
    private static final Path LIKE_FILE = Paths.get("likes.txt");
    private static final Path DM_FILE = Paths.get("dms.txt");

    // 1:1 메시지 하나 (계정 아이디로 저장. 화면에 보낼 때 닉네임으로 바꿈)
    public static class Dm {
        public final String from, to, kind, text;
        public final long time;

        Dm(String from, String to, long time, String kind, String text) {
            this.from = from;
            this.to = to;
            this.time = time;
            this.kind = kind;
            this.text = text;
        }
    }

    private final Map<String, String> nicknames = new LinkedHashMap<>();
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, LinkedHashSet<String>> likes = new LinkedHashMap<>();
    private final List<Dm> dms = new ArrayList<>();
    private final Consumer<String> log; // 서버 화면에 찍기

    public ServerStore(Consumer<String> log) {
        this.log = log;
    }

    // ===================== 읽기 / 쓰기 =====================

    public synchronized void load() {
        for (String line : readLines(NICK_FILE)) {
            String[] kv = split(line);
            if (kv != null) nicknames.put(kv[0], kv[1]);
        }
        for (String line : readLines(PROFILE_FILE)) {
            String[] kv = split(line);
            if (kv == null) continue;
            try {
                profiles.put(kv[0], Profile.decode(kv[1]));
            } catch (IllegalArgumentException e) {
                log.accept("프로필 줄 무시 (" + kv[0] + "): " + e.getMessage()); // 한 줄이 깨져도 나머지는 읽음
            }
        }
        for (String line : readLines(LIKE_FILE)) {
            String[] kv = split(line);
            if (kv == null) continue;
            LinkedHashSet<String> set = new LinkedHashSet<>();
            for (String t : kv[1].split(",")) if (!t.isEmpty()) set.add(t);
            likes.put(kv[0], set);
        }
        for (String line : readLines(DM_FILE)) {
            String[] p = line.split("\\|", 5); // 내용은 URL 인코딩이라 | 가 안 들어있음
            if (p.length != 4 && p.length != 5) continue;
            try {
                String kind = p.length == 5 ? p[3] : "T";
                dms.add(new Dm(p[0], p[1], Long.parseLong(p[2]), kind, URLDecoder.decode(p[p.length - 1], StandardCharsets.UTF_8)));
            } catch (IllegalArgumentException e) {
                log.accept("메시지 줄 무시: " + e.getMessage());
            }
        }
        if (profiles.isEmpty()) seed();
        log.accept("불러옴: 닉네임 " + nicknames.size() + ", 프로필 " + profiles.size() + ", 메시지 " + dms.size());
    }

    private List<String> readLines(Path p) {
        try {
            return Files.readAllLines(p, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return Collections.emptyList(); // 처음 실행
        } catch (IOException e) {
            log.accept(p + " 읽기 실패: " + e);
            return Collections.emptyList();
        }
    }

    private static String[] split(String line) {
        int i = line.indexOf('=');
        return i > 0 ? new String[] { line.substring(0, i), line.substring(i + 1) } : null;
    }

    private void writeMap(Path p, Map<String, String> map) {
        ArrayList<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
        try {
            Files.write(p, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.accept(p + " 저장 실패: " + e); // 저장이 안 돼도 지금 실행 중에는 그대로 씀
        }
    }

    private void saveNicknames() {
        writeMap(NICK_FILE, nicknames);
    }

    private void saveProfiles() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, Profile> e : profiles.entrySet()) m.put(e.getKey(), e.getValue().encode());
        writeMap(PROFILE_FILE, m);
    }

    private void saveLikes() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : likes.entrySet()) m.put(e.getKey(), String.join(",", e.getValue()));
        writeMap(LIKE_FILE, m);
    }

    // ===================== 닉네임 =====================

    public synchronized String getNick(String id) {
        return nicknames.get(id);
    }

    public synchronized String idByNick(String nick) {
        for (Map.Entry<String, String> e : nicknames.entrySet()) if (e.getValue().equals(nick)) return e.getKey();
        return null;
    }

    // 다른 계정이 쓰는 닉네임이면 JoinException
    public synchronized void setNick(String id, String nick) throws JoinException {
        String owner = idByNick(nick);
        if (owner != null && !owner.equals(id)) throw new JoinException("닉네임중복");
        nicknames.put(id, nick);
        saveNicknames();
    }

    // ===================== 프로필 =====================

    public synchronized Profile getProfile(String id) {
        return profiles.get(id);
    }

    public synchronized void setProfile(String id, Profile p) {
        profiles.put(id, p);
        saveProfiles();
    }

    // 닉네임이랑 프로필이 둘 다 있는 계정 (= 설정을 끝낸 회원)
    public synchronized List<String> members() {
        ArrayList<String> r = new ArrayList<>();
        for (String id : profiles.keySet()) if (nicknames.containsKey(id)) r.add(id);
        return r;
    }

    // ===================== 찜 =====================

    public synchronized boolean isLiked(String id, String target) {
        LinkedHashSet<String> s = likes.get(id);
        return s != null && s.contains(target);
    }

    public synchronized void setLike(String id, String target, boolean on) {
        LinkedHashSet<String> s = likes.computeIfAbsent(id, k -> new LinkedHashSet<>());
        if (on) s.add(target);
        else s.remove(target);
        saveLikes();
    }

    // ===================== 1:1 메시지 =====================

    public synchronized Dm addDm(String from, String to, String kind, String text) {
        Dm d = new Dm(from, to, System.currentTimeMillis(), kind, text);
        dms.add(d);
        String line = from + "|" + to + "|" + d.time + "|" + kind + "|" + URLEncoder.encode(text, StandardCharsets.UTF_8) + "\n";
        try {
            Files.write(DM_FILE, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.accept("메시지 저장 실패: " + e);
        }
        return d;
    }

    // 두 사람 사이 대화 전체 (오래된 것부터)
    public synchronized List<Dm> conversation(String a, String b) {
        ArrayList<Dm> r = new ArrayList<>();
        for (Dm d : dms) if ((d.from.equals(a) && d.to.equals(b)) || (d.from.equals(b) && d.to.equals(a))) r.add(d);
        return r;
    }

    // 나랑 대화한 사람별 마지막 메시지 (최근 대화가 앞으로)
    public synchronized List<Dm> lastPerPartner(String me) {
        LinkedHashMap<String, Dm> last = new LinkedHashMap<>();
        for (Dm d : dms) {
            if (!d.from.equals(me) && !d.to.equals(me)) continue;
            String other = d.from.equals(me) ? d.to : d.from;
            last.remove(other); // 다시 넣어서 순서를 맨 뒤로
            last.put(other, d);
        }
        ArrayList<Dm> r = new ArrayList<>(last.values());
        Collections.reverse(r);
        return r;
    }

    // ===================== 처음 실행할 때 기본 회원 =====================
    // 둘러보기, 랜덤매칭에 사람이 보이도록 테스트 계정 일부를 미리 "가입한 사람"으로 만들어 둠
    // naver08~10, naver18~20은 비워둬서 처음 로그인하는 흐름도 시연할 수 있게 함
    private void seed() {
        String[][] seed = {
            // 계정, 닉네임, 프로필
            { "naver01", "아침러너", "0|2|1|1|1|0,0,0,0,1,0|ESTJ" },
            { "naver02", "조용한곰", "1|1|1|3|0|0,0,1,0,0,2|ISFJ" },
            { "naver03", "밤샘코더", "2|3|1|1|1|1,1,2,1,2,1|INTP" },
            { "naver04", "깔끔쟁이", "3|0|1|4|1|0,0,0,0,0,0|ISTJ" },
            { "naver05", "고양이집사", "4|2|0|5|1|1,0,1,1,1,2|INFP" },
            { "naver06", "헬스보이", "5|1|1|0|1|0,2,1,2,2,0|ESTP" },
            { "naver07", "기타치는남자", "6|3|1|5|0|1,1,2,0,2,1|ENFP" },
            { "naver11", "새벽요정", "7|0|1|2|1|0,0,0,0,0,1|INFJ" },
            { "naver12", "정리왕", "0|3|1|4|1|0,0,0,0,1,0|ESTJ" },
            { "naver13", "야행성부엉이", "1|1|1|1|1|1,0,1,0,2,2|INTP" },
            { "naver14", "식물집사", "2|2|0|6|1|0,0,0,1,0,1|ISFP" },
            { "naver15", "커피중독", "3|0|1|5|1|1,1,1,0,1,0|ENFP" },
            { "naver16", "책읽는밤", "4|2|1|2|0|1,0,0,0,0,2|INFJ" },
            { "naver17", "뜨개질러", "5|1|1|7|1|0,0,1,0,1,1|ISFJ" },
        };
        for (String[] s : seed) {
            if (!nicknames.containsKey(s[0]) && idByNick(s[1]) == null) nicknames.put(s[0], s[1]);
            if (nicknames.containsKey(s[0])) profiles.put(s[0], Profile.decode(s[2] + "|" + AccountDB.find(s[0]).studentId)); // 학번은 계정 DB에서
        }
        saveNicknames();
        saveProfiles();
        log.accept("처음 실행이라 기본 회원 " + profiles.size() + "명을 만들었어요");
    }
}
