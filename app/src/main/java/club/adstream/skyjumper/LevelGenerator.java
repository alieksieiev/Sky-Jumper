package club.adstream.skyjumper;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Random;

public class LevelGenerator {
    private final float W, H;
    private final ArrayList<Platform> plats = new ArrayList<>();
    private final ArrayList<Coin> coins = new ArrayList<>();
    private final ArrayList<Spike> spikes = new ArrayList<>();
    private final ArrayList<Spring> springs = new ArrayList<>();
    private final ArrayList<ShieldPickup> shields = new ArrayList<>();
    private final Random rnd = new Random();
    private float lastY;

    private float lastX;
    private float playerX;
    private float maxDx;
    private int sinceMoving = 0;

    private final float edgeMargin = 10f;

    private int coinsCollectedThisFrame = 0;
    private boolean killedThisFrame = false;
    private boolean boostedThisFrame = false;
    private boolean shieldPickedThisFrame = false;

    public LevelGenerator(float worldW, float worldH) {
        this.W = worldW; this.H = worldH;
        lastY = worldH - 30;
        lastX = W / 2f;
        maxDx = W * 0.35f;
        for (int i = 0; i < 22; i++) spawnNext();
    }

    /** true, если игрок стоит на платформе в этом кадре. */
    public boolean update(float dt, Player p) {
        coinsCollectedThisFrame = 0;
        killedThisFrame = false;
        boostedThisFrame = false;
        shieldPickedThisFrame = false;
        playerX = p.x;

        // Платформы
        for (Platform pl : plats) {
            pl.update(dt);
            if (!pl.oscillate && pl.vx != 0f) {
                if (pl.r.left < edgeMargin) {
                    pl.r.offset(edgeMargin - pl.r.left, 0);
                    pl.vx = Math.abs(pl.vx);
                }
                if (pl.r.right > W - edgeMargin) {
                    pl.r.offset(W - edgeMargin - pl.r.right, 0);
                    pl.vx = -Math.abs(pl.vx);
                }
            }
        }

        // Пружины
        for (int i = springs.size()-1; i >= 0; i--) {
            Spring s = springs.get(i);
            if (s.parent.r.top > H + 120) { springs.remove(i); continue; }
            s.update(dt);
            if (s.tryActivate(p)) {
                p.springBoost();
                boostedThisFrame = true;
            }
        }

        // Монеты
        for (int i = coins.size()-1; i >= 0; i--) {
            Coin c = coins.get(i);
            c.update(dt);
            c.x = clamp(c.x, c.r + 8f, W - c.r - 8f);
        }

        // Щиты
        for (int i = shields.size()-1; i >= 0; i--) {
            ShieldPickup sh = shields.get(i);
            if (sh.y > H + 150) { shields.remove(i); continue; }
            sh.update(dt);
        }

        // Платформа-коллизии
        boolean grounded = false;
        for (Platform pl : plats) {
            if (pl.collideFromTop(p)) {
                grounded = true;
                pl.onStepped();
            }
        }

        // Шипы = смерть
        for (int i = spikes.size()-1; i >= 0; i--) {
            Spike s = spikes.get(i);
            if (s.parent.r.top > H + 120) { spikes.remove(i); continue; }
            if (s.hits(p)) killedThisFrame = true;
        }

        // Сбор монет
        float pr = p.getRadius();
        for (int i = coins.size()-1; i >= 0; i--) {
            Coin coin = coins.get(i);
            float dx = coin.x - p.x, dy = coin.y - p.y;
            float rr = (coin.r + pr);
            if (dx*dx + dy*dy <= rr*rr) {
                coins.remove(i);
                coinsCollectedThisFrame++;
            }
        }

        // Подбор щита
        for (int i = shields.size()-1; i >= 0; i--) {
            ShieldPickup sh = shields.get(i);
            float dx = sh.x - p.x, dy = sh.y - p.y;
            float rr = (sh.r + pr);
            if (dx*dx + dy*dy <= rr*rr) {
                shields.remove(i);
                shieldPickedThisFrame = true;
            }
        }

        // Чистка/досоздание
        plats.removeIf(pl -> pl.r.top > H + 120);
        coins.removeIf(c -> c.y > H + 150);
        while (countAbove(-40) < 24) spawnNext();

        return grounded;
    }

    public boolean consumeKilledFlag()   { boolean k = killedThisFrame; killedThisFrame = false; return k; }
    public boolean consumeBoostedFlag()  { boolean b = boostedThisFrame; boostedThisFrame = false; return b; }
    public boolean consumeShieldPicked() { boolean s = shieldPickedThisFrame; shieldPickedThisFrame = false; return s; }
    public int consumeCollectedCoins()   { int n = coinsCollectedThisFrame; coinsCollectedThisFrame = 0; return n; }

    public void shiftAll(float dy) {
        for (Platform pl : plats) pl.r.offset(0, dy);
        for (Coin c : coins) c.y += dy;
        for (ShieldPickup sh : shields) sh.y += dy;
        // springs/spikes сидят на платформах — двигаются вместе автоматически
    }

    public void draw(Canvas c, float s, Paint p) {
        for (Platform pl : plats) pl.draw(c, s, p);
        for (Spike sp : spikes)   sp.draw(c, s, p);
        for (Spring sp : springs) sp.draw(c, s, p);
        for (Coin coin : coins)   coin.draw(c, s, p);
        for (ShieldPickup sh : shields) sh.draw(c, s, p);
    }

    private int countAbove(float y) { int c=0; for (Platform pl: plats) if (pl.r.top < y) c++; return c; }

    public void addStartPlatform(float x, float y, float w, float h) {
        Platform pl = new Platform();
        pl.set(x, y, w, h);
        plats.add(pl);
        lastX = x + w / 2f;
        lastY = Math.min(lastY, y);
        addCoinCluster(x + w/2f, y - 12f, 3, null);
    }

    private void spawnNext() {
        float t = difficulty();                 // 0..1
        float gap = lerp(24, 36, 1f - t);
        lastY -= gap;

        float w = rndRange(50, 90), h = 8;

        float desiredCenter = lastX + rndRange(-maxDx, maxDx);
        if (Math.abs(playerX - lastX) > maxDx * 0.8f) {
            float bias = (playerX > lastX ? 1f : -1f) * (maxDx * 0.6f);
            desiredCenter = lastX + bias + rndRange(-15, 15);
        }

        float center = clamp(desiredCenter, w/2f + edgeMargin, W - w/2f - edgeMargin);
        float x = center - w/2f;

        Platform pl = new Platform();
        pl.set(x, lastY, w, h);

        // движение (как раньше)
        float moveChance = 0.45f + 0.25f * t;
        boolean makeMoving = rnd.nextFloat() < moveChance || sinceMoving >= 2;

        if (makeMoving) {
            if (rnd.nextFloat() < 0.7f) {
                pl.oscillate = true;
                pl.baseCenterX = center;
                float maxAmp = Math.min(center - (w/2f + edgeMargin),
                        (W - w/2f - edgeMargin) - center);
                maxAmp = Math.max(12f, maxAmp);
                pl.amp = Math.min(maxAmp, rndRange(18, 42));
                float freq = rndRange(0.6f, 1.2f) * (1.0f + 1.2f*t);
                pl.omega = (float)(2 * Math.PI * freq);
                pl.phase = rndRange(0f, (float)(2*Math.PI));
            } else {
                float speed = rndRange(24, 48) * (1.0f + 1.0f*t);
                pl.vx = rnd.nextBoolean() ? speed : -speed;
            }
            sinceMoving = 0;
        } else {
            sinceMoving++;
        }

        // исчезающие/ломучие
        if (rnd.nextFloat() < (0.12f + 0.18f * t)) pl.vanishOnStep = true;
        if (rnd.nextFloat() < 0.12f) pl.breakable = true;

        plats.add(pl);
        lastX = center;

        // монеты
        if (rnd.nextFloat() < 0.45f)
            addCoinCluster(center, lastY - rndRange(8,16), rnd.nextInt(3)+3, pl);

        // шипы
        float spikeChance = 0.18f + 0.22f * t;
        if (rnd.nextFloat() < spikeChance) {
            int count = rnd.nextFloat() < 0.6f ? 1 : 2;
            float spikeW = 12f, spikeH = 10f;
            for (int i=0; i<count; i++) {
                float offset = (i==0)
                        ? rndRange(6f, w*0.35f)
                        : w - rndRange(6f, w*0.35f) - spikeW;
                spikes.add(new Spike(pl, offset, spikeW, spikeH));
            }
        }

        // пружины
        float springChance = 0.16f + 0.18f * t;
        if (rnd.nextFloat() < springChance) {
            float sw = 18f, sh = 12f;
            float off = rndRange(6f, w - sw - 6f);
            springs.add(new Spring(pl, off, sw, sh));
        }

        // щиты (редкие)
        float shieldChance = 0.08f + 0.07f * t;
        if (rnd.nextFloat() < shieldChance) {
            float r = 6f;
            float sx = clamp(center + rndRange(-22,22), r+8, W - r - 8);
            float sy = lastY - rndRange(12,18);
            shields.add(new ShieldPickup(sx, sy, r, pl));
        }
    }

    private void addCoinCluster(float cx, float cy, int n, Platform parent) {
        float r = 5f;
        for (int i=0;i<n;i++) {
            float ox = rndRange(-14, 14);
            float oy = rndRange(-6,  6);
            float x = clamp(cx + ox, r + 8, W - r - 8);
            Coin coin = new Coin(x, cy + oy, r);
            coin.parent = parent;
            coins.add(coin);
        }
    }

    private float difficulty() { return Math.min(1f, Math.max(0f, (H - lastY) / 5000f)); }
    private float rndRange(float a, float b){ return a + rnd.nextFloat()*(b-a); }
    private float lerp(float a, float b, float t){ return a + (b-a)*t; }
    private float clamp(float v, float lo, float hi){ return Math.max(lo, Math.min(hi, v)); }
}
