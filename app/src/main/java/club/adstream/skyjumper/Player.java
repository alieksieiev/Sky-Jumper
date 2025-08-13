package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

public class Player {
    public float x, y, vy, vx;
    private final float radius = 8f;

    private float gravity = 9.5f;
    private float jumpImpulse = -8.8f;

    private boolean grounded = false;
    private float coyoteTime = 0.08f;
    private float coyoteTimer = 0f;

    private final float worldW;
    private float targetX;
    private float moveSpeed = 120f;

    private int bodyColor = Color.rgb(255,140,0);

    // щит
    private boolean shield = false;

    public Player(float x, float y, float worldW) {
        this.x = x; this.y = y; this.worldW = worldW; this.targetX = x;
    }

    public float getRadius() { return radius; }
    public void setColor(int color){ this.bodyColor = color; }

    public boolean hasShield(){ return shield; }
    public void setShield(boolean v){ shield = v; }

    public void setGrounded(boolean g){ grounded = g; if (g) coyoteTimer = coyoteTime; }
    public void primeCoyote(){ grounded=false; coyoteTimer=coyoteTime; }

    public void jump(){
        if (grounded || coyoteTimer > 0f) {
            vy = jumpImpulse; grounded = false; coyoteTimer = 0f;
        }
    }

    /** Ультрапрыжок от пружины */
    public void springBoost() {
        vy = -12.5f;            // заметно сильнее обычного
        grounded = false;
        coyoteTimer = 0f;
    }

    public void setTargetX(float tx){
        float minX = radius, maxX = worldW - radius;
        if (tx < minX) tx = minX; if (tx > maxX) tx = maxX;
        targetX = tx;
    }

    public void update(float dt){
        vy += gravity * dt; y += vy;
        float dx = targetX - x, step = moveSpeed * dt;
        if (Math.abs(dx) > step) x += Math.signum(dx)*step; else x = targetX;
        if (!grounded && coyoteTimer > 0f) coyoteTimer -= dt;
    }

    public RectF bounds(){ return new RectF(x - radius, y - radius, x + radius, y + radius); }

    public void draw(Canvas c, float s, Paint p){
        // щит (ореол)
        if (shield) {
            p.setColor(0x6633C5FF);
            c.drawCircle(x*s, y*s, radius*s*1.45f, p);
        }

        p.setColor(bodyColor);
        c.drawCircle(x*s, y*s, radius*s, p);
        p.setColor(Color.BLACK);
        float r = radius*s*0.18f;
        c.drawCircle((x*s - r*6), (y*s - r*2), r, p);
        c.drawCircle((x*s + r*2), (y*s - r*2), r, p);
    }
}
