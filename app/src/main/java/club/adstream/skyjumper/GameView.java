package club.adstream.skyjumper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // рендер / логика
    private GameThread thread;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Player player;
    private LevelGenerator level;

    // реклама
    private AdsManager ads;
    public void setAds(AdsManager ads){ this.ads = ads; }

    // интерстициал: показать каждый N-й раз
    private int interEvery = 3;               // каждая 3-я смерть (3, 6, 9, …)
    private int deathCount = 0;               // счётчик смертей
    private long lastInterShown = 0L;         // когда последний раз показали
    private static final long INTER_GAP_MS = 90_000; // минимум 90 сек между показами

    // мир
    private final float worldW = 360f;
    private float worldH, pxPerUnit;

    // состояния
    private enum State { RUNNING, GAME_OVER, STORE, PAUSED, TUTORIAL }
    private State state = State.RUNNING;

    // счёт/монеты
    private float ascended = 0f;
    private int score = 0, bestScore = 0;
    private int coinsTotal = 0, runCoins = 0;
    private boolean doubledUsed = false, continueUsed = false;

    private SharedPreferences prefs;

    // текст
    private float textSmallPx, textBigPx;

    // кнопки
    private final RectF btnRetry = new RectF();
    private final RectF btnContinue = new RectF();
    private final RectF btnDouble = new RectF();
    private final RectF chipCoins = new RectF();
    private final RectF badgeShield = new RectF();
    private final RectF btnSound = new RectF();
    private final RectF btnVibe  = new RectF();
    private final RectF btnStore = new RectF();
    private final RectF btnPause = new RectF();
    private final RectF btnPResume = new RectF();
    private final RectF btnPRetry  = new RectF();
    private final RectF btnPStore  = new RectF();
    private final RectF btnStoreClose = new RectF();

    // магазин
    private final Skin[] skins = Skin.catalog();
    private boolean[] unlocked;
    private int selectedSkin = 0;

    // настройки
    private boolean soundEnabled = true;
    private boolean hapticEnabled = true;
    private boolean tutorialSeen = false;

    // инсеты
    private int insetTopPx=0, insetLeftPx=0, insetRightPx=0, insetBottomPx=0;

    // звук/вибро
    private SoundManager sound;
    private Vibrator vibrator;

    // баннер: показывать/прятать в зависимости от экрана
    private State lastBannerState = null;
    private void updateBannerVisibility() {
        if (ads == null || lastBannerState == state) return;
        boolean visible = (state == State.RUNNING || state == State.PAUSED
                || state == State.STORE || state == State.GAME_OVER);
        ads.setBannerVisible(visible);
        lastBannerState = state;
    }

    public GameView(Context c) {
        super(c);
        getHolder().addCallback(this);
        setFocusable(true);

        prefs = c.getSharedPreferences("skyjumper_prefs", Context.MODE_PRIVATE);
        bestScore      = prefs.getInt("best_score", 0);
        coinsTotal     = prefs.getInt("coins", 0);
        soundEnabled   = prefs.getBoolean("soundEnabled", true);
        hapticEnabled  = prefs.getBoolean("hapticEnabled", true);
        tutorialSeen   = prefs.getBoolean("tutorial_seen", false);

        // скины
        unlocked = new boolean[skins.length];
        for (int i=0;i<skins.length;i++) {
            unlocked[i] = skins[i].free || prefs.getBoolean("skin_unlocked_"+skins[i].id, false);
        }
        selectedSkin = prefs.getInt("skin_selected", 0);
        if (selectedSkin < 0 || selectedSkin >= skins.length || !unlocked[selectedSkin]) selectedSkin = 0;

        sound = new SoundManager(c);
        sound.setMuted(!soundEnabled);
        vibrator = getVibratorCompat(c);

        ViewCompat.setOnApplyWindowInsetsListener(this, (View v, WindowInsetsCompat insets) -> {
            Insets s = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
                            | WindowInsetsCompat.Type.navigationBars());
            insetTopPx = s.top; insetLeftPx = s.left; insetRightPx = s.right; insetBottomPx = s.bottom;
            return insets;
        });
    }

    private Vibrator getVibratorCompat(Context c) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                return vm != null ? vm.getDefaultVibrator() : null;
            } else {
                return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            }
        } catch (Exception e) { return null; }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        pxPerUnit = dm.widthPixels / worldW;
        worldH = dm.heightPixels / pxPerUnit;

        textSmallPx = 14f * dm.scaledDensity;
        textBigPx   = 22f * dm.scaledDensity;

        resetGame();
        thread = new GameThread(this);
        thread.start();
    }

    private void resetGame() {
        player = new Player(worldW / 2f, worldH - 80, worldW);
        player.setColor(skins[selectedSkin].color);

        level  = new LevelGenerator(worldW, worldH);

        float w = 90f, h = 8f;
        float px = player.x - w / 2f;
        float py = player.y + 12f;
        level.addStartPlatform(px, py, w, h);

        player.primeCoyote();
        state = tutorialSeen ? State.RUNNING : State.TUTORIAL;
        continueUsed = false;
        doubledUsed = false;
        ascended = 0f;
        score = 0;
        runCoins = 0;

        updateBannerVisibility();
    }

    public void update(float dt) {
        if (state != State.RUNNING) return;

        player.setGrounded(false);
        player.update(dt);
        boolean onGround = level.update(dt, player);
        if (onGround) player.setGrounded(true);

        if (level.consumeBoostedFlag()) { sound.playJump(); vibratePreset(1); }
        if (level.consumeShieldPicked()) { player.setShield(true); vibratePreset(0); }

        if (level.consumeKilledFlag()) {
            if (player.hasShield()) { player.setShield(false); vibratePreset(1); }
            else { sound.playDeath(); vibratePreset(2); gameOver(); return; }
        }

        int got = level.consumeCollectedCoins();
        if (got > 0) { runCoins += got; vibratePreset(0); sound.playCoin(); }

        float camTargetY = worldH * 0.35f;
        float dy = Math.max(0, camTargetY - player.y);
        if (dy > 0) {
            player.y += dy;
            level.shiftAll(dy);
            ascended += dy;
            score = Math.max(score, (int)ascended);
        }

        if (player.y > worldH + 40) { sound.playDeath(); vibratePreset(2); gameOver(); }
    }

    private void gameOver() {
        state = State.GAME_OVER;
        deathCount++; // <— учитываем смерть для частоты interstitial

        coinsTotal += runCoins;
        saveCoins();
        if (score > bestScore) {
            bestScore = score;
            prefs.edit().putInt("best_score", bestScore).apply();
        }

        float w = getWidth(), h = getHeight();
        float bw = w * 0.5f, bh = Math.max(100, h * 0.09f);
        float x = (w - bw) / 2f;
        float baseY = Math.max(h * 0.48f, insetTopPx + dp(80));
        btnContinue.set(x, baseY, x + bw, baseY + bh);
        btnDouble.set(  x, baseY + bh + dp(18), x + bw, baseY + 2*bh + dp(18));
        btnRetry.set(   x, baseY + 2*bh + dp(36), x + bw, baseY + 3*bh + dp(36));

        updateBannerVisibility();
    }

    private void doContinue() {
        if (continueUsed) return;
        continueUsed = true;
        state = State.RUNNING;
        player.y = worldH * 0.55f; player.vy = 0f;
        float w = 90f, h = 8f;
        float px = Math.max(10f, Math.min(worldW - w - 10f, player.x - w/2f));
        float py = player.y + 12f;
        level.addStartPlatform(px, py, w, h);
        player.primeCoyote();
        sound.playJump(); vibratePreset(1);
    }

    private void doDoubleCoins() {
        if (doubledUsed || runCoins == 0) return;
        coinsTotal += runCoins; saveCoins(); doubledUsed = true;
    }

    private void saveCoins()   { prefs.edit().putInt("coins", coinsTotal).apply(); }
    private void saveToggles() { prefs.edit().putBoolean("soundEnabled", soundEnabled).putBoolean("hapticEnabled", hapticEnabled).apply(); }
    private void saveSkins() {
        for (Skin s : skins) prefs.edit().putBoolean("skin_unlocked_"+s.id, unlocked[s.id]).apply();
        prefs.edit().putInt("skin_selected", selectedSkin).apply();
    }

    public void render() {
        Canvas c = null;
        try {
            c = getHolder().lockCanvas();
            if (c == null) return;

            updateBannerVisibility();

            c.drawColor(Color.rgb(11, 132, 193));
            level.draw(c, pxPerUnit, paint);
            player.draw(c, pxPerUnit, paint);

            if (state == State.STORE) drawStore(c);
            drawTopBar(c);
            if (state == State.GAME_OVER) drawGameOver(c);
            if (state == State.PAUSED)    drawPause(c);
            if (state == State.TUTORIAL)  drawTutorial(c);

        } finally {
            if (c != null) getHolder().unlockCanvasAndPost(c);
        }
    }

    // ---------- UI ----------

    private void drawTopBar(Canvas c) {
        final float pad = dp(10);
        final float chipH = dp(36);
        final float gap = dp(8);

        float top = insetTopPx + pad;
        float leftSafe  = insetLeftPx + pad;
        float rightSafe = getWidth() - insetRightPx - pad;

        btnPause.set(leftSafe, top, leftSafe + chipH, top + chipH);
        leftSafe += chipH + gap;

        String coinsTxt = coinsTotal + (runCoins>0 ? " (+"+runCoins+")" : "");
        paint.setTextSize(textBigPx * 0.95f);
        paint.setTextAlign(Paint.Align.LEFT);
        Rect bounds = new Rect();
        paint.getTextBounds(coinsTxt, 0, coinsTxt.length(), bounds);
        float chipW = chipH + dp(12) + bounds.width() + dp(12);
        chipCoins.set(rightSafe - chipW, top, rightSafe, top + chipH);
        rightSafe -= chipW + gap;

        if (player != null && player.hasShield()) {
            badgeShield.set(rightSafe - chipH, top, rightSafe, top + chipH);
            rightSafe -= chipH + gap;
        } else {
            badgeShield.set(0,0,0,0);
        }

        btnStore.set(rightSafe - chipH, top, rightSafe, top + chipH); rightSafe -= chipH + gap;
        btnSound.set(rightSafe - chipH, top, rightSafe, top + chipH); rightSafe -= chipH + gap;
        btnVibe.set(rightSafe - chipH, top, rightSafe, top + chipH);

        drawToggleChip(c, btnPause, (state==State.PAUSED) ? "▶️" : "⏸️");
        drawCoinsChip(c, chipCoins, coinsTxt);
        if (badgeShield.width() > 0) drawShieldBadge(c, badgeShield);
        drawToggleChip(c, btnStore, "🛍️");
        drawToggleChip(c, btnVibe,  hapticEnabled ? "📳" : "🚫");
        drawToggleChip(c, btnSound, soundEnabled  ? "🔊" : "🔇");

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(textBigPx);
        paint.setShadowLayer(4,0,0,0x66000000);
        c.drawText("Score: " + score, leftSafe, top + chipH*0.55f, paint);
        paint.setTextSize(textSmallPx);
        c.drawText("Best: " + bestScore, leftSafe, top + chipH*0.55f + dp(18), paint);
        paint.clearShadowLayer();
    }

    private void drawCoinsChip(Canvas c, RectF r, String txt) {
        paint.setColor(0x33222222);
        c.drawRoundRect(r, r.height()/2f, r.height()/2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x55FFFFFF);
        paint.setStrokeWidth(dp(1));
        c.drawRoundRect(r, r.height()/2f, r.height()/2f, paint);
        paint.setStyle(Paint.Style.FILL);

        float cx = r.left + r.height()/2f;
        float cy = r.centerY();
        float rr = r.height()*0.32f;
        paint.setColor(Color.rgb(255, 215, 0));
        c.drawCircle(cx, cy, rr, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.rgb(120, 90, 0));
        paint.setStrokeWidth(dp(2));
        c.drawCircle(cx, cy, rr, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(textBigPx * 0.95f);
        float textBase = r.centerY() + (textBigPx*0.95f)*0.35f;
        c.drawText(txt, cx + rr + dp(12), textBase, paint);
    }

    private void drawShieldBadge(Canvas c, RectF r) {
        paint.setColor(0x5533C5FF);
        c.drawRoundRect(r, r.height()/2f, r.height()/2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0xAAFFFFFF);
        paint.setStrokeWidth(dp(1));
        c.drawRoundRect(r, r.height()/2f, r.height()/2f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textBigPx);
        paint.setColor(Color.WHITE);
        c.drawText("🛡️", r.centerX(), r.centerY() + textBigPx*0.35f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawToggleChip(Canvas c, RectF r, String icon) {
        paint.setColor(0x33111111);
        c.drawRoundRect(r, dp(10), dp(10), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x55FFFFFF);
        paint.setStrokeWidth(dp(1));
        c.drawRoundRect(r, dp(10), dp(10), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textBigPx);
        c.drawText(icon, r.centerX(), r.centerY() + textBigPx*0.35f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawStore(Canvas c) {
        paint.setColor(0xCC000000);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        float pad = dp(10), size = dp(40), chipH = dp(36);
        float topY = insetTopPx + pad + chipH + pad;
        btnStoreClose.set(getWidth() - insetRightPx - pad - size, topY,
                getWidth() - insetRightPx - pad,         topY + size);
        paint.setColor(0x33FFFFFF);
        c.drawRoundRect(btnStoreClose, dp(10), dp(10), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x66FFFFFF);
        paint.setStrokeWidth(dp(1));
        c.drawRoundRect(btnStoreClose, dp(10), dp(10), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textBigPx);
        c.drawText("✖", btnStoreClose.centerX(), btnStoreClose.centerY() + textBigPx*0.35f, paint);
        paint.setTextAlign(Paint.Align.LEFT);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textBigPx * 1.2f);
        c.drawText("Store", getWidth()/2f, insetTopPx + dp(64), paint);
        paint.setTextSize(textBigPx);
        c.drawText("Coins: " + coinsTotal, getWidth()/2f, insetTopPx + dp(92), paint);

        int n = skins.length;
        int cols = Math.min(4, Math.max(2, n >= 6 ? 3 : 2));
        int rows = (int)Math.ceil(n / (float)cols);

        float padH = dp(14), padV = dp(12);
        float top = insetTopPx + dp(110);
        float bottom = getHeight() - insetBottomPx - dp(40);
        float availH = bottom - top;

        float cellH = Math.min(dp(120), (availH - padV*(rows-1)) / rows);
        float cellW = Math.min(dp(120), (getWidth() - insetLeftPx - insetRightPx - padH*(cols+1)) / cols);

        float y = top;
        int idx = 0;
        for (int r=0; r<rows; r++) {
            float x = insetLeftPx + padH;
            for (int ccol=0; ccol<cols && idx<n; ccol++, idx++) {
                RectF cell = new RectF(x, y, x + cellW, y + cellH);
                drawSkinCell(c, cell, idx);
                x += cellW + padH;
            }
            y += cellH + padV;
        }

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSmallPx);
        paint.setColor(0xFFCCCCCC);
        c.drawText("Tap skin: Buy/Select. Tap ✖ or 🛍️ to close.", getWidth()/2f, bottom + dp(24), paint);
    }

    private void drawSkinCell(Canvas c, RectF cell, int i) {
        Skin s = skins[i];
        boolean isUnlocked = unlocked[i];
        boolean isSelected = (i == selectedSkin);

        paint.setColor(0x22FFFFFF);
        c.drawRoundRect(cell, dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(isSelected ? 0xFF1E88E5 : 0x55FFFFFF);
        c.drawRoundRect(cell, dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.FILL);

        float cx = cell.centerX();
        float cy = cell.top + cell.height() * 0.45f;
        float rr = Math.min(cell.width(), cell.height()) * 0.22f;
        paint.setColor(s.color);
        c.drawCircle(cx, cy, rr, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x66000000);
        c.drawCircle(cx, cy, rr, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textSmallPx * 0.95f);
        paint.setColor(Color.WHITE);
        c.drawText(s.name, cx, cy + rr + dp(16), paint);

        String label;
        int bg = 0xFF1E88E5;
        if (isUnlocked) {
            label = isSelected ? "Selected" : "Use";
            bg = isSelected ? 0xFF2E7D32 : 0xFF1E88E5;
        } else if (s.free) {
            label = "Use";
            bg = 0xFF1E88E5;
        } else {
            label = "Buy " + s.price;
            bg = (coinsTotal >= s.price) ? 0xFFFB8C00 : 0xFF757575;
        }

        RectF btn = new RectF(cell.left + dp(10), cell.bottom - dp(38), cell.right - dp(10), cell.bottom - dp(10));
        paint.setColor(bg);
        c.drawRoundRect(btn, dp(10), dp(10), paint);
        paint.setTextSize(textSmallPx);
        paint.setColor(Color.WHITE);
        c.drawText(label, btn.centerX(), btn.centerY() + textSmallPx*0.35f, paint);
    }

    private void drawGameOver(Canvas c) {
        paint.setColor(0xAA000000);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(textBigPx * 1.2f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Game Over", getWidth()/2f, insetTopPx + dp(90), paint);

        paint.setTextSize(textBigPx);
        c.drawText("Score: " + score + "   Best: " + bestScore, getWidth()/2f, insetTopPx + dp(120), paint);
        c.drawText("Coins: +" + runCoins + "   Total: " + coinsTotal, getWidth()/2f, insetTopPx + dp(150), paint);

        paint.setTextAlign(Paint.Align.LEFT);
        drawButton(c, btnContinue, continueUsed ? "Continue (used)" : "Continue");
        drawButton(c, btnDouble,   doubledUsed  ? "x2 Coins (used)" : "x2 Coins");
        drawButton(c, btnRetry,    "Retry");
    }

    private void drawPause(Canvas c) {
        paint.setColor(0xAA000000);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(textBigPx * 1.2f);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Paused", getWidth()/2f, insetTopPx + dp(90), paint);

        float w = getWidth(), h = getHeight();
        float bw = w * 0.5f, bh = Math.max(100, h * 0.09f);
        float x = (w - bw) / 2f;
        float baseY = Math.max(h * 0.48f, insetTopPx + dp(80));
        btnPResume.set(x, baseY, x + bw, baseY + bh);
        btnPStore .set(x, baseY + bh + dp(18), x + bw, baseY + 2*bh + dp(18));
        btnPRetry .set(x, baseY + 2*bh + dp(36), x + bw, baseY + 3*bh + dp(36));

        paint.setTextAlign(Paint.Align.LEFT);
        drawButton(c, btnPResume, "Resume");
        drawButton(c, btnPStore,  "Store");
        drawButton(c, btnPRetry,  "Retry");
    }

    private void drawTutorial(Canvas c) {
        paint.setColor(0xAA000000);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);

        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(textBigPx * 1.2f);
        c.drawText("How to play", getWidth()/2f, insetTopPx + dp(90), paint);

        paint.setTextSize(textBigPx);
        c.drawText("Tap to jump • Drag to move", getWidth()/2f, insetTopPx + dp(130), paint);

        paint.setTextSize(textSmallPx);
        c.drawText("Avoid spikes • Collect coins", getWidth()/2f, insetTopPx + dp(160), paint);

        paint.setTextSize(textBigPx);
        c.drawText("Tap anywhere to start", getWidth()/2f, getHeight()*0.75f, paint);
    }

    private void drawButton(Canvas c, RectF r, String label) {
        paint.setColor(0xFF1E88E5);
        c.drawRoundRect(r, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0xFF0D47A1);
        c.drawRoundRect(r, dp(12), dp(12), paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.WHITE);
        paint.setTextSize(textBigPx);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() + textBigPx*0.35f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    // ---------- input ----------

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();

        // STORE
        if (state == State.STORE) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (btnStoreClose.contains(x,y) || btnStore.contains(x,y)) { state = State.RUNNING; updateBannerVisibility(); return true; }
                handleStoreTap(x, y);
            }
            return true;
        }

        // Туториал
        if (state == State.TUTORIAL) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (btnSound.contains(x, y)) { soundEnabled = !soundEnabled; sound.setMuted(!soundEnabled); saveToggles(); return true; }
                if (btnVibe.contains(x, y))  { hapticEnabled = !hapticEnabled; saveToggles(); return true; }
                if (btnStore.contains(x, y)) { state = State.STORE; updateBannerVisibility(); return true; }
                if (btnPause.contains(x,y))  { state = State.PAUSED; updateBannerVisibility(); return true; }
                tutorialSeen = true; prefs.edit().putBoolean("tutorial_seen", true).apply();
                state = State.RUNNING; updateBannerVisibility(); vibratePreset(0);
                return true;
            }
            return true;
        }

        // Пауза
        if (state == State.PAUSED) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (btnPResume.contains(x,y) || btnPause.contains(x,y)) { state = State.RUNNING; updateBannerVisibility(); return true; }
                if (btnPStore.contains(x,y)) { state = State.STORE; updateBannerVisibility(); return true; }
                if (btnPRetry.contains(x,y)) { resetGame(); return true; }
            }
            return true;
        }

        // чипы топ-бара
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (btnSound.contains(x, y)) { soundEnabled = !soundEnabled; sound.setMuted(!soundEnabled); saveToggles(); return true; }
            if (btnVibe.contains(x, y))  { hapticEnabled = !hapticEnabled; saveToggles(); return true; }
            if (btnStore.contains(x, y)) { state = State.STORE; updateBannerVisibility(); return true; }
            if (btnPause.contains(x, y)) { if (state==State.RUNNING) { state = State.PAUSED; updateBannerVisibility(); } return true; }
        }

        // Game Over
        if (state == State.GAME_OVER && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (btnRetry.contains(x, y)) {
                boolean freqOk = interEvery > 0 && (deathCount % interEvery == 0);
                boolean timeOk = (System.currentTimeMillis() - lastInterShown) >= INTER_GAP_MS;

                if (ads != null && freqOk && timeOk) {
                    ads.showInterstitial(() -> {
                        lastInterShown = System.currentTimeMillis();
                        resetGame();
                    });
                } else {
                    resetGame();
                }
                return true;
            }
            if (btnContinue.contains(x, y)) {
                if (!continueUsed) {
                    if (ads != null) ads.showRewarded(this::doContinue, () -> vibratePreset(0));
                    else doContinue();
                }
                return true;
            }
            if (btnDouble.contains(x, y)) {
                if (!doubledUsed && runCoins > 0) {
                    if (ads != null) ads.showRewarded(this::doDoubleCoins, () -> vibratePreset(0));
                    else doDoubleCoins();
                }
                return true;
            }
            return true;
        }

        // управление в игре
        float worldX = x / pxPerUnit;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                player.setTargetX(worldX);
                player.jump(); if (soundEnabled) sound.playJump(); vibratePreset(1);
                return true;
            case MotionEvent.ACTION_MOVE:
                player.setTargetX(worldX);
                return true;
            default:
                return true;
        }
    }

    private void handleStoreTap(float x, float y) {
        int n = skins.length;
        int cols = Math.min(4, Math.max(2, n >= 6 ? 3 : 2));
        int rows = (int)Math.ceil(n / (float)cols);

        float padH = dp(14), padV = dp(12);
        float top = insetTopPx + dp(110);
        float bottom = getHeight() - insetBottomPx - dp(40);
        float availH = bottom - top;

        float cellH = Math.min(dp(120), (availH - padV*(rows-1)) / rows);
        float cellW = Math.min(dp(120), (getWidth() - insetLeftPx - insetRightPx - padH*(cols+1)) / cols);

        float yy = top;
        int idx = 0;
        outer:
        for (int r=0; r<rows; r++) {
            float xx = insetLeftPx + padH;
            for (int c=0; c<cols && idx<n; c++, idx++) {
                RectF cell = new RectF(xx, yy, xx + cellW, yy + cellH);
                RectF btn = new RectF(cell.left + dp(10), cell.bottom - dp(38), cell.right - dp(10), cell.bottom - dp(10));
                if (cell.contains(x, y) || btn.contains(x, y)) {
                    onSkinCellClick(idx);
                    break outer;
                }
                xx += cellW + padH;
            }
            yy += cellH + padV;
        }
    }

    private void onSkinCellClick(int i) {
        Skin s = skins[i];
        if (unlocked[i] || s.free) {
            unlocked[i] = true; selectedSkin = i; player.setColor(s.color);
            saveSkins(); vibratePreset(0);
        } else {
            if (coinsTotal >= s.price) {
                coinsTotal -= s.price; unlocked[i] = true; selectedSkin = i; player.setColor(s.color);
                saveCoins(); saveSkins(); sound.playCoin(); vibratePreset(1);
            } else {
                vibratePreset(0);
            }
        }
    }

    private void vibratePreset(int preset){ // 0=tick,1=click,2=heavy
        if (!hapticEnabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                int eff = (preset==0) ? VibrationEffect.EFFECT_TICK
                        : (preset==1) ? VibrationEffect.EFFECT_CLICK
                        : VibrationEffect.EFFECT_HEAVY_CLICK;
                vibrator.vibrate(VibrationEffect.createPredefined(eff));
            } else if (Build.VERSION.SDK_INT >= 26) {
                int dur = (preset==0)? 22 : (preset==1)? 35 : 70;
                vibrator.vibrate(VibrationEffect.createOneShot(dur, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                long dur = (preset==0)? 28 : (preset==1)? 45 : 85;
                vibrator.vibrate(dur);
            }
        } catch (Exception ignored) {}
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    public void onPauseApp() { if (state == State.RUNNING) state = State.PAUSED; updateBannerVisibility(); }
    public void onResumeApp() { /* noop */ }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (thread != null) { thread.requestStop(); try { thread.join(); } catch (InterruptedException ignored) {} }
        if (sound != null) sound.release();
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hpx) {}
}
