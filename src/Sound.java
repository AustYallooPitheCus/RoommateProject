// Sound.java
// 윷놀이 효과음, 배경음 (12주차)
// 프로젝트의 sounds 폴더에 있는 wav 파일을 씀 (fonts 폴더처럼 실행 위치 기준)
// 배경음은 BGM_나비보벳따우 한 곡(약 2분 52초), 효과음은 BGM_모음에서 잘라 씀
//   bgm.wav      게임 중 배경음 (반복)
//   match.wav    4명이 모여 게임 시작          (랭킹 쇼츠 터지는 효과음)
//   turn.wav     내 차례가 됨                  (카톡)
//   throw.wav    윷 던지기                     (BGM_윷소리 첫 번째 던지는 소리)
//   nak.wav      낙                            (투누누)
//   capture.wav  상대 말을 잡을 때             (찰싹)
//   hell.wav     지옥에 빠질 때                (삐뷰욱)
//   birth.wav    잉태로 말이 나올 때           (귀여운 헬로)
//   rank.wav     1등, 2등이 나올 때            (이겼다!)
//   bgm_halli.wav 할리갈리 배경음 (반복)        (13주차, BGM_배경음_할리갈리)
//   card.wav     할리갈리 카드 내기            (13주차, 슉쉭_모음 1분 34초 Track 25 "슉~ 쉭~")
//   bell.wav     할리갈리 종                   (13주차, 종소리_모음 30초 "오래 울리는 쇠종소리")
// 자바 기본 기능(javax.sound)은 mp3를 못 읽어서 wav로 바꿔서 넣음 (라이브러리 추가 없이)
// 소리 장치가 없거나 파일이 없으면 조용히 넘어감 (게임은 그대로 됨)

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;

public class Sound {

    private static final Map<String, Clip[]> clips = new HashMap<>(); // 효과음마다 2개씩 (빨리 연달아 나도 끊기지 않게)
    private static final Map<String, Integer> turn = new HashMap<>();
    private static final Map<String, Clip> bgms = new HashMap<>(); // 게임마다 배경음 (윷놀이 bgm, 할리갈리 bgm_halli)
    private static String playing = "bgm";                         // 지금(또는 마지막으로) 튼 배경음
    private static boolean muted, loaded;

    private static Clip open(String name, float gainDb) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new File("sounds", name + ".wav"))) {
            Clip c = AudioSystem.getClip();
            c.open(in);
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl fc = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                fc.setValue(Math.max(fc.getMinimum(), Math.min(fc.getMaximum(), gainDb))); // 장치가 지원하는 범위 안으로
            }
            return c;
        } catch (Exception | LinkageError e) {
            return null; // 파일이 없거나 소리 장치가 없음
        }
    }

    // 처음 쓸 때 한 번만 불러옴
    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        for (String n : new String[] { "match", "turn", "throw", "nak", "capture", "hell", "birth", "rank", "card", "bell" }) {
            clips.put(n, new Clip[] { open(n, -28f), open(n, -28f) }); // 효과음 (13주차: 두 번 줄임, -20dB -> -28dB)
        }
        bgms.put("bgm", open("bgm", -40.5f)); // 배경음은 작게 (13주차: 세 번 줄임, -14dB -> -24.5dB -> -32.5dB -> -40.5dB)
        bgms.put("bgm_halli", open("bgm_halli", -40.5f));
    }

    // 효과음 한 번
    public static void play(String name) {
        if (Boolean.getBoolean("sound.log")) System.out.println("소리 " + name); // 확인용 (-Dsound.log=true)
        load();
        if (muted) return;
        Clip[] cs = clips.get(name);
        if (cs == null) return;
        int i = turn.merge(name, 1, Integer::sum) % cs.length;
        Clip c = cs[i];
        if (c == null) return;
        c.stop();
        c.setFramePosition(0);
        c.start();
    }

    public static void startBgm() {
        startBgm("bgm");
    }

    // 배경음 틀기 (다른 게임 배경음은 끔)
    public static void startBgm(String name) {
        load();
        stopBgm();
        playing = name;
        Clip c = bgms.get(name);
        if (c == null || muted) return;
        c.setFramePosition(0);
        c.loop(Clip.LOOP_CONTINUOUSLY);
    }

    public static void stopBgm() {
        for (Clip c : bgms.values()) if (c != null) c.stop();
    }

    public static boolean isMuted() {
        return muted;
    }

    // 소리 끄기/켜기. 게임 중이면 배경음도 같이
    public static void setMuted(boolean m, boolean inGame) {
        muted = m;
        if (m) stopBgm();
        else if (inGame) startBgm(playing);
    }
}
