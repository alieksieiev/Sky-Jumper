package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class ShieldPickup {
    public float x, y, r;
    public Platform parent; // двигается вместе с платформой

    public ShieldPickup(float x, float y, float r, Platform parent) {
        this.x=x; this.y=y; this.r=r; this.parent=parent;
    }

    public void update(float dt){
        if (parent != null) x += parent.getDeltaXLastFrame();
    }

    public void draw(Canvas c, float s, Paint p) {
        // голубой шар с кольцом
        p.setColor(Color.rgb(70, 200, 255));
        c.drawCircle(x*s, y*s, r*s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(Color.WHITE);
        c.drawCircle(x*s, y*s, r*s*1.2f, p);
        p.setStyle(Paint.Style.FILL);
    }
}
