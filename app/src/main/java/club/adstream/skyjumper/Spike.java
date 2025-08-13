package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

public class Spike {
    public final Platform parent;
    public final float offset; // смещение от левого края платформы
    public final float width;
    public final float height;

    public Spike(Platform parent, float offsetFromLeft, float width, float height) {
        this.parent = parent;
        this.offset = offsetFromLeft;
        this.width = width;
        this.height = height;
    }

    private RectF rect() {
        float left = parent.r.left + offset;
        return new RectF(left, parent.r.top - height, left + width, parent.r.top);
    }

    public boolean hits(Player p) {
        RectF spike = rect();
        RectF pb = p.bounds();
        // Аппроксимация: пересечение с прямоугольником шипа
        return RectF.intersects(spike, pb);
    }

    public void draw(Canvas c, float s, Paint p) {
        RectF r = rect();
        float l = r.left*s, t = r.top*s, rr = r.right*s, bb = r.bottom*s;
        float mid = (l + rr) * 0.5f;

        Path tri = new Path();
        tri.moveTo(l, bb);
        tri.lineTo(mid, t);
        tri.lineTo(rr, bb);
        tri.close();

        p.setColor(Color.rgb(200,90,70));
        c.drawPath(tri, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.rgb(60,30,20));
        c.drawPath(tri, p);
        p.setStyle(Paint.Style.FILL);
    }
}
