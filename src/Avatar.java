// Avatar.java
// 프로필 아바타 (11주차). 이미지 파일 없이 동그란 얼굴을 직접 그림
// 번호(0~7)마다 배경색이랑 꾸밈(앞머리, 리본, 안경, 모자)이 달라짐
// 프로토타입 영상의 아바타는 학교 캐릭터라 그대로 쓸 수 없어서 새로 그림

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;

import javax.swing.JComponent;

public class Avatar extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final Color[] BG = {
        new Color(0xF8C8DC), new Color(0xD9D0FF), new Color(0xBFEBDF), new Color(0xC7E4FA),
        new Color(0xFFDDC2), new Color(0xFFF0B3), new Color(0xF9C5C0), new Color(0xD3DCEB),
    };
    private static final Color FACE = new Color(0xFFF4EC);
    private static final Color INK = new Color(0x4A4458);

    private int index;
    private boolean selected; // 설정 화면에서 고른 아바타 표시

    public Avatar(int index, int size) {
        this.index = index;
        setPreferredSize(new Dimension(size, size));
        setSize(size, size);
        setOpaque(false);
    }

    public void setIndex(int index) {
        this.index = index;
        repaint();
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float s = Math.min(getWidth(), getHeight());
        int i = Math.floorMod(index, BG.length);

        // 배경 원
        g2.setColor(BG[i]);
        g2.fill(new Ellipse2D.Float(0, 0, s, s));
        if (selected) {
            g2.setColor(UI.LAVENDER);
            g2.setStroke(new BasicStroke(Math.max(2f, s * 0.05f)));
            g2.draw(new Ellipse2D.Float(s * 0.03f, s * 0.03f, s * 0.94f, s * 0.94f));
        }

        // 얼굴
        float fx = s * 0.2f, fy = s * 0.24f, fw = s * 0.6f;
        g2.setColor(FACE);
        g2.fill(new Ellipse2D.Float(fx, fy, fw, fw));

        // 꾸밈 (얼굴 위)
        g2.setColor(INK);
        switch (i % 4) {
            case 1: // 앞머리
                g2.fill(new Arc2D.Float(fx - s * 0.02f, fy - s * 0.04f, fw + s * 0.04f, fw * 0.8f, 0, 180, Arc2D.CHORD));
                break;
            case 2: // 리본
                g2.setColor(new Color(0xE96B9A));
                float rx = fx + fw * 0.68f, ry = fy - s * 0.02f, r = s * 0.1f;
                g2.fill(new Ellipse2D.Float(rx - r, ry, r, r * 0.8f));
                g2.fill(new Ellipse2D.Float(rx + r * 0.2f, ry, r, r * 0.8f));
                g2.fill(new Ellipse2D.Float(rx - r * 0.2f, ry + r * 0.15f, r * 0.5f, r * 0.5f));
                break;
            case 3: // 모자
                g2.setColor(UI.LAVENDER);
                g2.fill(new Arc2D.Float(fx + fw * 0.08f, fy - s * 0.08f, fw * 0.84f, fw * 0.62f, 0, 180, Arc2D.CHORD));
                g2.fill(new Ellipse2D.Float(fx + fw * 0.42f, fy - s * 0.13f, s * 0.08f, s * 0.08f));
                break;
            default:
                break;
        }

        // 눈
        g2.setColor(INK);
        float ey = fy + fw * 0.45f, ew = s * 0.06f;
        if (i == 4 || i == 7) { // 안경
            g2.setStroke(new BasicStroke(Math.max(1.2f, s * 0.025f)));
            float gw = s * 0.15f;
            g2.draw(new Ellipse2D.Float(fx + fw * 0.12f, ey - gw * 0.45f, gw, gw));
            g2.draw(new Ellipse2D.Float(fx + fw * 0.88f - gw, ey - gw * 0.45f, gw, gw));
        }
        g2.fill(new Ellipse2D.Float(fx + fw * 0.27f - ew / 2, ey, ew, ew * 1.2f));
        g2.fill(new Ellipse2D.Float(fx + fw * 0.73f - ew / 2, ey, ew, ew * 1.2f));

        // 볼터치
        g2.setColor(new Color(255, 150, 170, 110));
        g2.fill(new Ellipse2D.Float(fx + fw * 0.08f, ey + ew * 1.4f, s * 0.1f, s * 0.06f));
        g2.fill(new Ellipse2D.Float(fx + fw * 0.92f - s * 0.1f, ey + ew * 1.4f, s * 0.1f, s * 0.06f));

        // 입
        g2.setColor(INK);
        g2.setStroke(new BasicStroke(Math.max(1.2f, s * 0.025f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float mw = s * 0.12f;
        g2.draw(new Arc2D.Float(fx + fw / 2 - mw / 2, ey + ew * 1.2f, mw, mw * 0.7f, 200, 140, Arc2D.OPEN));
        g2.dispose();
    }
}
