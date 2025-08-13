package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class Coin {
    public float x, y, r;
    public float vx = 0f;
    public Platform parent = null;

    public Coin(float x, float y, float r) { this.x=x; this.y=y; this.r=r; }

    public void update(float dt){
        if (parent != null) x += parent.getDeltaXLastFrame();
        else x += vx * dt;
    }

    public void draw(Canvas c, float s, Paint p) {
        p.setColor(Color.rgb(255, 215, 0));
        c.drawCircle(x*s, y*s, r*s, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(120, 90, 0));
        c.drawCircle(x*s, y*s, r*s, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255, 240, 120));
        float rr = r*0.5f;
        c.drawCircle(x*s, (y-rr)*s, (r*0.18f)*s, p);
        c.drawCircle((x-rr)*s, (y+rr*0.1f)*s, (r*0.18f)*s, p);
        c.drawCircle((x+rr)*s, (y+rr*0.1f)*s, (r*0.18f)*s, p);
    }
}
