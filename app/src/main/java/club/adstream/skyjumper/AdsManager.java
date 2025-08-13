package club.adstream.skyjumper;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;            // <— добавлено
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

public class AdsManager {
    private final Activity activity;
    private final String bannerId, interId, rewardId;

    private AdView banner;
    private @Nullable InterstitialAd interstitial;
    private @Nullable RewardedAd rewarded;

    public AdsManager(Activity a, String bannerId, String interId, String rewardId) {
        this.activity = a;
        this.bannerId = bannerId;
        this.interId = interId;
        this.rewardId = rewardId;
        MobileAds.initialize(a, status -> {});
        preloadInterstitial();
        preloadRewarded();
    }

    public void attachBanner(FrameLayout root) {
        banner = new AdView(activity);
        banner.setAdUnitId(bannerId);
        banner.setAdSize(AdSize.BANNER);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        banner.setLayoutParams(lp);
        root.addView(banner);
        banner.loadAd(new AdRequest.Builder().build());
    }

    /** Показ/скрытие баннера (для пустых экранов, туториала и т.п.) */
    public void setBannerVisible(boolean visible) {
        if (banner != null) banner.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void onResume(){ if (banner != null) banner.resume(); }
    public void onPause(){ if (banner != null) banner.pause(); }
    public void onDestroy(){ if (banner != null) banner.destroy(); }

    // ---------- Interstitial ----------
    private void preloadInterstitial() {
        InterstitialAd.load(activity, interId, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(InterstitialAd ad) { interstitial = ad; }
                    @Override public void onAdFailedToLoad(LoadAdError err) { interstitial = null; }
                });
    }

    public void showInterstitial(Runnable after) {
        if (interstitial == null) { preloadInterstitial(); after.run(); return; }
        interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                interstitial = null; preloadInterstitial(); after.run();
            }
            @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                interstitial = null; preloadInterstitial(); after.run();
            }
        });
        interstitial.show(activity);
    }

    // ---------- Rewarded ----------
    private void preloadRewarded() {
        RewardedAd.load(activity, rewardId, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; }
                    @Override public void onAdFailedToLoad(LoadAdError err) { rewarded = null; }
                });
    }

    public void showRewarded(Runnable onReward, @Nullable Runnable onUnavailable) {
        if (rewarded == null) {
            preloadRewarded();
            if (onUnavailable != null) onUnavailable.run();
            return;
        }
        rewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                rewarded = null; preloadRewarded();
            }
            @Override public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError adError) {
                rewarded = null; preloadRewarded();
            }
        });
        rewarded.show(activity, (RewardItem r) -> { if (onReward != null) onReward.run(); });
    }
}
