package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

public class Spring {
    public final Platform parent;
    public final float offset; // смещение от левого края платформы
    public final float width;
    public final float height;

    private float cooldown = 0f; // чтобы не триггерить каждый кадр

    public Spring(Platform parent, float offsetFromLeft, float width, float height) {
        this.parent = parent;
        this.offset = offsetFromLeft;
        this.width = width;
        this.height = height;
    }

    private RectF rect() {
        float left = parent.r.left + offset;
        return new RectF(left, parent.r.top - height, left + width, parent.r.top);
    }

    public void update(float dt) {
        if (cooldown > 0f) cooldown -= dt;
    }

    /** true, если активировали пружину в этом кадре */
    public boolean tryActivate(Player p) {
        if (cooldown > 0f) return false;
        RectF sr = rect();
        RectF pb = p.bounds();
        // активируется при падении сверху
        if (p.vy > 0 && RectF.intersects(pb, sr)) {
            cooldown = 0.4f;
            return true;
        }
        return false;
    }

    public void draw(Canvas c, float s, Paint p) {
        RectF r = rect();
        float l = r.left*s, t = r.top*s, rr = r.right*s, bb = r.bottom*s;

        // основание
        p.setColor(Color.rgb(60,60,60));
        c.drawRect(l, bb - 3, rr, bb, p);

        // пружина (полоски)
        p.setColor(Color.rgb(180, 210, 255));
        float step = Math.max(2f, (rr - l) / 6f);
        for (float x = l + 1; x < rr - 1; x += step) {
            c.drawLine(x, bb - 3, x + step * 0.6f, t, p);
        }
    }
}
