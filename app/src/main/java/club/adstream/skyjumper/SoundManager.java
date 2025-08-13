package club.adstream.skyjumper;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

public class SoundManager {
    private final SoundPool sp;
    private int sJump = 0, sCoin = 0, sDeath = 0;
    private boolean muted = false;

    public SoundManager(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        sp = new SoundPool.Builder().setAudioAttributes(aa).setMaxStreams(4).build();

        try { sJump  = sp.load(ctx, R.raw.jump, 1); }  catch (Exception ignored) {}
        try { sCoin  = sp.load(ctx, R.raw.coin, 1); }  catch (Exception ignored) {}
        try { sDeath = sp.load(ctx, R.raw.death, 1); } catch (Exception ignored) {}
    }

    public void setMuted(boolean m) { muted = m; }
    public boolean isMuted() { return muted; }

    public void playJump()  { if (!muted && sJump  != 0) sp.play(sJump, 1f, 1f, 1, 0, 1f); }
    public void playCoin()  { if (!muted && sCoin  != 0) sp.play(sCoin, 1f, 1f, 1, 0, 1f); }
    public void playDeath() { if (!muted && sDeath != 0) sp.play(sDeath,1f, 1f, 1, 0, 1f); }

    public void release() { sp.release(); }
}
