// EmoticonView.java
// 이모티콘 그림 (12주차, 임시 8종). 이미지 파일 없이 Graphics2D로 직접 그림
// 동글동글한 떡 모양 캐릭터에 표정만 바꿈. 아바타(Avatar.java)랑 같은 느낌으로
// 100 x 100 칸 기준으로 그리고 크기에 맞춰 늘리고 줄임

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;

public class EmoticonView extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final Color[] BODY = {
        new Color(0xFFE3B8), new Color(0xFFD1DF), new Color(0xFFF0A8), new Color(0xCFE6FF),
        new Color(0xFFC9B8), new Color(0xE2D9FF), new Color(0xD6EFE4), new Color(0xFFE0EC),
    };
    private static final Color INK = new Color(0x4A4458);
    private static final Color BLUSH = new Color(255, 140, 160, 120);

    private int index;
    private boolean selected; // 이모티콘 판에서 미리보기로 고른 것

    public EmoticonView(String id, int size) {
        index = Math.max(0, Emoticon.index(id));
        setPreferredSize(new Dimension(size, size));
        setSize(size, size);
        setOpaque(false);
    }

    public String id() {
        return Emoticon.IDS[index];
    }

    public void setId(String id) {
        index = Math.max(0, Emoticon.index(id));
        repaint();
    }

    public void setSelected(boolean on) {
        selected = on;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        if (selected) { // 고른 이모티콘 뒤에 연보라 바탕
            g2.setColor(UI.LAVENDER_SOFT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.setColor(UI.LAVENDER);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
        }
        double s = Math.min(getWidth(), getHeight()) / 100.0;
        g2.scale(s, s);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (index == 7) g2.rotate(Math.toRadians(-12), 50, 60); // 고마워: 꾸벅

        // 몸 (아래가 조금 넓은 떡 모양) + 그림자
        g2.setColor(new Color(0, 0, 0, 25));
        g2.fill(new Ellipse2D.Double(18, 84, 64, 10));
        g2.setColor(BODY[index]);
        Path2D body = new Path2D.Double();
        body.moveTo(14, 70);
        body.curveTo(10, 30, 30, 16, 50, 16);
        body.curveTo(70, 16, 90, 30, 86, 70);
        body.curveTo(84, 88, 16, 88, 14, 70);
        body.closePath();
        g2.fill(body);
        g2.setColor(BODY[index].darker());
        g2.setStroke(new BasicStroke(2f));
        g2.draw(body);
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // 볼터치 (화남은 빨갛게)
        g2.setColor(index == 4 ? new Color(255, 90, 90, 110) : BLUSH);
        g2.fill(new Ellipse2D.Double(22, 56, 14, 8));
        g2.fill(new Ellipse2D.Double(64, 56, 14, 8));

        g2.setColor(INK);
        switch (index) {
            case 0: hi(g2); break;
            case 1: love(g2); break;
            case 2: laugh(g2); break;
            case 3: cry(g2); break;
            case 4: angry(g2); break;
            case 5: wow(g2); break;
            case 6: sleepy(g2); break;
            default: thanks(g2); break;
        }
        g2.dispose();
    }

    private void dotEyes(Graphics2D g2) {
        g2.fill(new Ellipse2D.Double(34, 44, 7, 9));
        g2.fill(new Ellipse2D.Double(59, 44, 7, 9));
    }

    private void smile(Graphics2D g2, double w) {
        g2.draw(new Arc2D.Double(50 - w / 2, 52, w, w * 0.7, 200, 140, Arc2D.OPEN));
    }

    // 안녕: 웃는 얼굴 + 손 흔들기
    private void hi(Graphics2D g2) {
        dotEyes(g2);
        smile(g2, 12);
        g2.setColor(BODY[0]);
        g2.fill(new Ellipse2D.Double(80, 30, 16, 16));
        g2.setColor(BODY[0].darker());
        g2.setStroke(new BasicStroke(2f));
        g2.draw(new Ellipse2D.Double(80, 30, 16, 16));
        g2.setColor(INK);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(84, 20, 14, 12, 20, 90, Arc2D.OPEN));
        g2.draw(new Arc2D.Double(90, 26, 10, 10, -40, 90, Arc2D.OPEN));
    }

    private static Path2D heart(double x, double y, double w) {
        Path2D p = new Path2D.Double();
        double h = w * 0.9;
        p.moveTo(x + w / 2, y + h);
        p.curveTo(x - w * 0.1, y + h * 0.55, x + w * 0.05, y - h * 0.05, x + w / 2, y + h * 0.28);
        p.curveTo(x + w * 0.95, y - h * 0.05, x + w * 1.1, y + h * 0.55, x + w / 2, y + h);
        p.closePath();
        return p;
    }

    // 좋아: 하트 눈 + 떠다니는 하트
    private void love(Graphics2D g2) {
        g2.setColor(new Color(0xF0507A));
        g2.fill(heart(30, 42, 14));
        g2.fill(heart(56, 42, 14));
        g2.fill(heart(74, 8, 12));
        g2.setColor(new Color(0xF48FB1));
        g2.fill(heart(14, 14, 9));
        g2.setColor(INK);
        smile(g2, 14);
    }

    // ㅋㅋㅋ: ^^ 눈 + 크게 벌린 입 + 글자
    private void laugh(Graphics2D g2) {
        g2.draw(new Arc2D.Double(31, 44, 12, 10, 20, 140, Arc2D.OPEN));
        g2.draw(new Arc2D.Double(57, 44, 12, 10, 20, 140, Arc2D.OPEN));
        g2.setColor(new Color(0xE0607E));
        g2.fill(new Arc2D.Double(40, 50, 20, 18, 180, 180, Arc2D.CHORD));
        g2.setColor(INK);
        g2.draw(new Arc2D.Double(40, 50, 20, 18, 180, 180, Arc2D.CHORD));
        g2.setFont(UI.font(Font.BOLD, 16f));
        g2.drawString("ㅋㅋ", 66, 22);
    }

    // 엉엉: 감은 눈 + 눈물 줄기
    private void cry(Graphics2D g2) {
        g2.draw(new Arc2D.Double(31, 42, 12, 10, 200, 140, Arc2D.OPEN));
        g2.draw(new Arc2D.Double(57, 42, 12, 10, 200, 140, Arc2D.OPEN));
        g2.setColor(new Color(0x6FB6F2));
        g2.fill(new RoundRectangle2D.Double(33, 52, 7, 26, 7, 7));
        g2.fill(new RoundRectangle2D.Double(60, 52, 7, 26, 7, 7));
        g2.setColor(INK);
        g2.draw(new Arc2D.Double(44, 62, 12, 8, 20, 140, Arc2D.OPEN)); // 삐죽 입
    }

    // 흥: 화난 눈썹 + 씩씩 표시
    private void angry(Graphics2D g2) {
        g2.draw(new Line2D.Double(30, 38, 42, 43));
        g2.draw(new Line2D.Double(70, 38, 58, 43));
        g2.fill(new Ellipse2D.Double(34, 46, 7, 7));
        g2.fill(new Ellipse2D.Double(59, 46, 7, 7));
        g2.draw(new QuadCurve2D.Double(43, 66, 50, 60, 57, 66));
        g2.setColor(new Color(0xE5484D));
        g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double cx = 80, cy = 20;
        for (int k = 0; k < 4; k++) { // 빨간 화남 표시
            double a = Math.toRadians(45 + k * 90);
            g2.draw(new Line2D.Double(cx + Math.cos(a) * 3, cy + Math.sin(a) * 3, cx + Math.cos(a) * 9, cy + Math.sin(a) * 9));
        }
    }

    // 헉: 동그란 눈 + O 입 + 느낌표
    private void wow(Graphics2D g2) {
        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(29, 38, 16, 18));
        g2.fill(new Ellipse2D.Double(55, 38, 16, 18));
        g2.setColor(INK);
        g2.setStroke(new BasicStroke(2.2f));
        g2.draw(new Ellipse2D.Double(29, 38, 16, 18));
        g2.draw(new Ellipse2D.Double(55, 38, 16, 18));
        g2.fill(new Ellipse2D.Double(34, 44, 7, 8));
        g2.fill(new Ellipse2D.Double(60, 44, 7, 8));
        g2.draw(new Ellipse2D.Double(45, 60, 10, 12));
        g2.setFont(UI.font(Font.BOLD, 22f));
        g2.drawString("!", 80, 26);
    }

    // 졸려: 감은 눈 + 작은 입 + Zz
    private void sleepy(Graphics2D g2) {
        g2.draw(new Line2D.Double(31, 49, 43, 49));
        g2.draw(new Line2D.Double(57, 49, 69, 49));
        g2.fill(new Ellipse2D.Double(46, 60, 8, 6));
        g2.setFont(UI.font(Font.BOLD, 13f));
        g2.drawString("Z", 72, 24);
        g2.setFont(UI.font(Font.BOLD, 10f));
        g2.drawString("z", 84, 14);
    }

    // 고마워: 꾸벅(몸 기울임은 위에서) + 반짝이
    private void thanks(Graphics2D g2) {
        g2.draw(new Arc2D.Double(31, 44, 12, 10, 200, 140, Arc2D.OPEN));
        g2.draw(new Arc2D.Double(57, 44, 12, 10, 200, 140, Arc2D.OPEN));
        smile(g2, 10);
        g2.setColor(new Color(0xF5B700));
        star(g2, 84, 22, 7);
        star(g2, 16, 30, 5);
    }

    private void star(Graphics2D g2, double x, double y, double r) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y - r);
        p.quadTo(x, y, x + r, y);
        p.quadTo(x, y, x, y + r);
        p.quadTo(x, y, x - r, y);
        p.quadTo(x, y, x, y - r);
        g2.fill(p);
    }
}
