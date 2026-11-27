// SetupScreen.java
// 프로필 설정 화면 (11주차, 프로토타입 영상의 설문 화면을 바탕으로)
//   처음 로그인: 아바타, 닉네임, 학번, 학년/학과(공개 여부), 생활습관 6개, MBTI -> /SETUP 닉네임 프로필
//   학번은 아무에게도 안 보이고 채팅에서 "내 정보 보내기"를 할 때만 보내짐
//   메뉴 > 내 프로필 수정: 닉네임 빼고 전부 수정 -> /PROFILE_EDIT 프로필
// 이름, 생년월일, 성별은 네이버 로그인 정보라 보여주기만 하고 못 바꿈
// 영상은 청결을 사진으로, 코골이를 소리로 골랐는데 여기서는 글자 선택지로 바꿈

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

public class SetupScreen extends AppFrame.Screen {

    private static final long serialVersionUID = 1L;
    private static final int CW = AppFrame.W - 10; // 안쪽 패널 너비 (스크롤바 자리 빼고)
    private static final int X = 40, IW = CW - 80; // 카드 안쪽 시작 x, 너비

    private final AppFrame app;
    private final JPanel body = new JPanel(null);
    private final JScrollPane scroll;
    private final JLabel head, headSub;
    private final JTextField tName = new JTextField(), tBirth = new JTextField(), tGender = new JTextField(), tPhone = new JTextField();
    private final JTextField tSid = new JTextField();
    private final JTextField tNick = new JTextField();
    private final JLabel nickHint;
    private final Avatar[] avatars = new Avatar[Profile.AVATAR_COUNT];
    private final JComboBox<String> cGrade = new JComboBox<>(Profile.GRADES);
    private final JComboBox<String> cDept = new JComboBox<>(Profile.DEPTS);
    private final JCheckBox pGrade = new JCheckBox("공개"), pDept = new JCheckBox("공개");
    private final JToggleButton[][] options = new JToggleButton[6][];
    private final ButtonGroup[] groups = new ButtonGroup[6];
    private final JToggleButton[][] mbti = new JToggleButton[4][2];
    private final JLabel error;
    private final JButton submit;
    private int avatar = 0;
    private boolean firstTime = true;
    private final Widgets.BackButton back;

    public SetupScreen(AppFrame app) {
        super("menu");
        this.app = app;
        body.setOpaque(false);

        int y = 22;
        head = UI.title("프로필 설정", 22f);
        head.setBounds(24, y, 300, 32);
        body.add(head);
        back = new Widgets.BackButton(app::goBack); // 메뉴 > 프로필 수정으로 들어왔을 때만 보임
        back.setLocation(12, y);
        back.setVisible(false);
        body.add(back);
        headSub = UI.sub("처음 한 번만 하면 돼요. 룸메 찾을 때 이걸로 궁합을 봐요");
        headSub.setBounds(24, y + 32, CW - 40, 20);
        body.add(headSub);
        y += 70;

        // ---- 카드 1: 네이버 로그인 정보 (못 바꿈) ----
        int top = y;
        y += 16;
        body.add(at(UI.sub("네이버 로그인으로 받은 정보"), X, y, IW, 20));
        y += 26;
        y = lockedRow("이름", tName, y);
        y = lockedRow("생년월일", tBirth, y);
        y = lockedRow("성별", tGender, y);
        y = lockedRow("전화번호", tPhone, y);
        JLabel lock = UI.sub("성별은 주민등록번호 기준이라 바꿀 수 없어요");
        lock.setFont(UI.font(Font.PLAIN, 11f));
        body.add(at(lock, X, y, IW, 18));
        y += 30;
        UI.card(body, 20, top, CW - 40, y - top);
        y += 14;

        // ---- 카드 2: 아바타 + 닉네임 ----
        top = y;
        y += 16;
        body.add(at(UI.title("아바타", 14f), X, y, IW, 22));
        y += 28;
        int gap = (IW - 4 * 58) / 3;
        for (int i = 0; i < avatars.length; i++) {
            final int idx = i;
            avatars[i] = new Avatar(i, 58);
            avatars[i].setLocation(X + (i % 4) * (58 + gap), y + (i / 4) * 66);
            Widgets.onClick(avatars[i], () -> pickAvatar(idx));
            body.add(avatars[i]);
        }
        y += 136;
        body.add(at(UI.title("닉네임", 14f), X, y, IW, 22));
        y += 26;
        tNick.putClientProperty("JTextField.placeholderText", "다른 사람에게 보일 이름");
        body.add(at(tNick, X, y, IW, 36));
        y += 40;
        nickHint = UI.sub("2~10자, 띄어쓰기 없이");
        body.add(at(nickHint, X, y, IW, 18));
        y += 30;
        body.add(at(UI.title("학번", 14f), X, y, IW, 22));
        y += 26;
        tSid.putClientProperty("JTextField.placeholderText", "예) 2371380");
        // 숫자만, 7글자까지만 들어가게 (붙여넣기도 걸러짐)
        ((AbstractDocument) tSid.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
                replace(fb, off, 0, str, a);
            }

            @Override
            public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
                String digits = str == null ? "" : str.replaceAll("[^0-9]", "");
                int room = 7 - (fb.getDocument().getLength() - len);
                if (digits.length() > room) digits = digits.substring(0, Math.max(0, room));
                super.replace(fb, off, len, digits, a);
            }
        });
        body.add(at(tSid, X, y, IW, 36));
        y += 40;
        JLabel sidHint = UI.sub("<html>숫자 7자리. 다른 사람에게는 안 보이고<br>채팅에서 [내 정보 보내기]를 할 때만 보내져요</html>");
        body.add(at(sidHint, X, y, IW, 34));
        y += 46;
        UI.card(body, 20, top, CW - 40, y - top);
        y += 14;

        // ---- 카드 3: 학년, 학과 (공개 여부) ----
        top = y;
        y += 16;
        body.add(at(UI.title("학년 / 학과", 14f), X, y, IW, 22));
        y += 28;
        body.add(at(cGrade, X, y, IW - 74, 34));
        body.add(at(pGrade, X + IW - 66, y, 66, 34));
        y += 42;
        body.add(at(cDept, X, y, IW - 74, 34));
        body.add(at(pDept, X + IW - 66, y, 66, 34));
        pGrade.setOpaque(false);
        pDept.setOpaque(false);
        y += 40;
        body.add(at(UI.sub("공개를 끄면 다른 사람에게는 비공개로 보여요"), X, y, IW, 18));
        y += 30;
        UI.card(body, 20, top, CW - 40, y - top);
        y += 14;

        // ---- 카드 4: 생활습관 설문 ----
        top = y;
        y += 16;
        body.add(at(UI.title("생활습관", 14f), X, y, IW, 22));
        y += 30;
        for (int q = 0; q < 6; q++) {
            body.add(at(UI.sub(Profile.QUESTIONS[q]), X, y, IW, 18));
            y += 22;
            groups[q] = new ButtonGroup();
            options[q] = new JToggleButton[Profile.OPTIONS[q].length];
            for (int o = 0; o < options[q].length; o++) {
                options[q][o] = toggle(Profile.OPTIONS[q][o]);
                groups[q].add(options[q][o]);
            }
            y = flow(options[q], y) + 14;
        }
        body.add(at(UI.sub("MBTI"), X, y, IW, 18));
        y += 22;
        String[][] letters = { { "E", "I" }, { "S", "N" }, { "T", "F" }, { "J", "P" } };
        int mw = (IW - 3 * 10) / 4;
        for (int k = 0; k < 4; k++) {
            ButtonGroup g = new ButtonGroup();
            for (int j = 0; j < 2; j++) {
                mbti[k][j] = toggle(letters[k][j]);
                g.add(mbti[k][j]);
                body.add(at(mbti[k][j], X + k * (mw + 10) + j * (mw / 2), y, mw / 2, 32));
            }
        }
        y += 48;
        UI.card(body, 20, top, CW - 40, y - top);
        y += 16;

        error = UI.sub(" ");
        error.setForeground(UI.ERROR);
        body.add(at(error, 24, y, CW - 48, 20));
        y += 24;
        submit = UI.pill("시작하기", UI.LAVENDER, Color.WHITE);
        body.add(at(submit, 20, y, CW - 40, 46));
        submit.addActionListener(e -> submit());
        y += 70;

        body.setPreferredSize(new Dimension(CW, y));
        scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        UI.clear(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll);
        pickAvatar(0);
    }

    private static <T extends java.awt.Component> T at(T c, int x, int y, int w, int h) {
        c.setBounds(x, y, w, h);
        return c;
    }

    private int lockedRow(String label, JTextField f, int y) {
        body.add(at(new JLabel(label), X, y, 70, 30));
        f.setEnabled(false);
        body.add(at(f, X + 74, y, IW - 74, 30));
        return y + 36;
    }

    private JToggleButton toggle(String text) {
        JToggleButton b = new JToggleButton(text);
        b.setFont(UI.font(Font.PLAIN, 12f));
        b.putClientProperty("JButton.buttonType", "roundRect");
        b.setFocusPainted(false);
        b.setMargin(new Insets(0, 4, 0, 4));
        return b;
    }

    // 선택지를 글자 길이에 맞춰 왼쪽부터 놓고, 줄이 넘치면 다음 줄로
    private int flow(JToggleButton[] bs, int y) {
        FontMetrics fm = bs[0].getFontMetrics(bs[0].getFont());
        int x = X;
        for (JToggleButton b : bs) {
            int w = fm.stringWidth(b.getText()) + 30;
            if (x + w > X + IW) {
                x = X;
                y += 40;
            }
            body.add(at(b, x, y, w, 32));
            x += w + 8;
        }
        return y + 32;
    }

    private void pickAvatar(int i) {
        avatar = i;
        for (int k = 0; k < avatars.length; k++) avatars[k].setSelected(k == i);
    }

    public boolean isFirstTime() {
        return firstTime;
    }

    private void fillLocked() {
        tName.setText(app.realName);
        tBirth.setText(app.birth);
        tGender.setText(app.gender.equals("M") ? "남자" : "여자");
        tPhone.setText(app.phone);
    }

    // 처음 로그인 (닉네임이 이미 있으면 채워둠)
    public void openFirst(String nick) {
        firstTime = true;
        back.setVisible(false);
        head.setLocation(24, head.getY());
        headSub.setLocation(24, headSub.getY());
        head.setText("프로필 설정");
        headSub.setText("처음 한 번만 하면 돼요. 룸메 찾을 때 이걸로 궁합을 봐요");
        fillLocked();
        tNick.setText(nick);
        tNick.setEnabled(true);
        nickHint.setText("2~10자, 띄어쓰기 없이");
        tSid.setText(app.dbStudentId); // 테스트 계정 DB에 있는 학번을 미리 채워둠 (고칠 수 있음)
        pickAvatar(0);
        cGrade.setSelectedIndex(0);
        cDept.setSelectedIndex(0);
        pGrade.setSelected(true);
        pDept.setSelected(true);
        for (ButtonGroup g : groups) g.clearSelection();
        for (JToggleButton[] pair : mbti) pair[0].setSelected(true);
        submit.setText("시작하기");
        error.setText(" ");
        top();
    }

    // 메뉴 > 내 프로필 수정 (지금 값으로 채워둠)
    public void openEdit() {
        firstTime = false;
        back.setVisible(true);
        head.setLocation(48, head.getY());
        headSub.setLocation(48, headSub.getY());
        head.setText("내 프로필 수정");
        headSub.setText("바꾸면 궁합 %도 다시 계산돼요");
        fillLocked();
        tNick.setText(app.myNick);
        tNick.setEnabled(false);
        nickHint.setText("닉네임은 가입할 때 정하면 바꿀 수 없어요");
        Profile p = app.me;
        tSid.setText(p.studentId.isEmpty() ? app.dbStudentId : p.studentId);
        pickAvatar(p.avatar);
        cGrade.setSelectedIndex(p.grade);
        cDept.setSelectedIndex(p.dept);
        pGrade.setSelected(p.gradePublic);
        pDept.setSelected(p.deptPublic);
        for (int q = 0; q < 6; q++) options[q][p.answers[q]].setSelected(true);
        String letters = "ESTJ";
        for (int k = 0; k < 4; k++) mbti[k][p.mbti.charAt(k) == letters.charAt(k) ? 0 : 1].setSelected(true);
        submit.setText("저장하기");
        error.setText(" ");
        top();
    }

    private void top() {
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    public void error(String msg) {
        error.setText(msg);
        submit.setEnabled(true);
    }

    private void submit() {
        Profile p = new Profile();
        p.avatar = avatar;
        p.grade = cGrade.getSelectedIndex();
        p.gradePublic = pGrade.isSelected();
        p.dept = cDept.getSelectedIndex();
        p.deptPublic = pDept.isSelected();
        for (int q = 0; q < 6; q++) {
            int sel = -1;
            for (int o = 0; o < options[q].length; o++) if (options[q][o].isSelected()) sel = o;
            if (sel < 0) {
                error(Profile.QUESTIONS[q] + " 항목을 골라 주세요");
                return;
            }
            p.answers[q] = sel;
        }
        StringBuilder m = new StringBuilder();
        for (JToggleButton[] pair : mbti) m.append(pair[0].isSelected() ? pair[0].getText() : pair[1].getText());
        p.mbti = m.toString();
        p.studentId = tSid.getText().trim();
        if (!p.studentId.matches("\\d{7}")) {
            error("학번은 숫자 7자리로 넣어 주세요");
            return;
        }

        if (firstTime) {
            String nick = tNick.getText().trim();
            if (nick.isEmpty()) {
                error("닉네임을 입력해 주세요");
                return;
            }
            if (nick.contains(" ")) {
                error("닉네임에 띄어쓰기는 못 써요");
                return;
            }
            error.setText(" ");
            app.send("/SETUP " + nick + " " + p.encode()); // 나머지 검사(길이, 중복)는 서버가
        } else {
            error.setText(" ");
            app.send("/PROFILE_EDIT " + p.encode());
        }
    }

    @Override
    public void setSize(int w, int h) {
        super.setSize(w, h);
        if (scroll != null) scroll.setBounds(0, 0, w, h);
    }
}
