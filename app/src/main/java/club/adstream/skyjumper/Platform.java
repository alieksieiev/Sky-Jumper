package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

public class Platform {
    public final RectF r = new RectF();
    public boolean breakable = false;

    // исчезающая после касания
    public boolean vanishOnStep = false;
    private boolean vanishing = false;
    private float vanishTimer = 0f;      // сек до исчезновения

    // движение
    public float vx = 0f;                 // ping-pong по краям, если ≠ 0
    public boolean oscillate = false;     // синус
    public float baseCenterX = 0f;
    public float amp = 0f;
    public float omega = 0f;
    public float phase = 0f;

    private float lastDx = 0f;

    public void set(float x, float y, float w, float h) { r.set(x, y, x + w, y + h); }

    public void onStepped() {
        if (breakable) {
            // мгновенно «ломаемся»
            r.offset(0, 9999);
        } else if (vanishOnStep && !vanishing) {
            vanishing = true;
            vanishTimer = 0.9f; // сколько живёт после касания
        }
    }

    public void update(float dt) {
        lastDx = 0f;

        // движение
        if (oscillate) {
            float prevCenter = (r.left + r.right) * 0.5f;
            phase += omega * dt;
            float newCenter = baseCenterX + amp * (float)Math.sin(phase);
            float dx = newCenter - prevCenter;
            r.offset(dx, 0);
            lastDx = dx;
        } else if (vx != 0f) {
            float dx = vx * dt;
            r.offset(dx, 0);
            lastDx = dx;
        }

        // исчезновение
        if (vanishing) {
            vanishTimer -= dt;
            if (vanishTimer <= 0f) {
                r.offset(0, 9999);
                vanishing = false;
            }
        }
    }

    public float getDeltaXLastFrame() { return lastDx; }

    public boolean collideFromTop(Player p) {
        RectF pb = p.bounds();
        if (RectF.intersects(pb, r) && p.vy > 0 && (pb.bottom - p.vy) <= r.top) {
            p.y  = r.top - (pb.height()/2f);
            p.vy = 0f;
            return true;
        }
        return false;
    }

    public void draw(Canvas c, float s, Paint p) {
        // альфа мерцает во время исчезновения
        int saveAlpha = p.getAlpha();
        if (vanishing) {
            float t = Math.max(0f, Math.min(1f, vanishTimer / 0.9f));
            float flicker = (float)(0.6 + 0.4*Math.sin(40*(1-t)));
            int a = (int)(255 * (0.25f + 0.75f*t*flicker));
            p.setAlpha(a);
        }

        p.setColor(breakable ? Color.rgb(90,170,90) : Color.rgb(70,200,120));
        c.drawRoundRect(r.left*s, r.top*s, r.right*s, r.bottom*s, 8, 8, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.rgb(20,40,60));
        c.drawRoundRect(r.left*s, r.top*s, r.right*s, r.bottom*s, 8, 8, p);
        p.setStyle(Paint.Style.FILL);

        p.setAlpha(saveAlpha);
    }
}
