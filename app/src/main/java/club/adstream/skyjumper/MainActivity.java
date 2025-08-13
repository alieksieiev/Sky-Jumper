package club.adstream.skyjumper;

import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.ump.ConsentDebugSettings;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

public class MainActivity extends AppCompatActivity {
    private GameView gameView;
    private AdsManager ads;
    private FrameLayout root;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Корневой контейнер
        root = new FrameLayout(this);
        setContentView(root);

        // Игра (без рекламы до получения статуса согласия)
        gameView = new GameView(this);
        root.addView(gameView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Старт UMP: запрашиваем статус и, если нужно, показываем форму
        requestConsentThenInitAds();
    }

    /** 1) Запрос статуса UMP  2) при необходимости показать форму  3) после этого инициализировать Ads */
    private void requestConsentThenInitAds() {
        // (опционально) дебаг-настройки — закомментируй в релизе
        ConsentDebugSettings debugSettings = new ConsentDebugSettings.Builder(this)
                // .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                // .addTestDeviceHashedId("YOUR_TEST_DEVICE_HASH")
                .build();

        ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings)
                .setTagForUnderAgeOfConsent(false)
                .build();

        ConsentInformation consentInfo = UserMessagingPlatform.getConsentInformation(this);
        consentInfo.requestConsentInfoUpdate(
                this,
                params,
                () -> {
                    // Форма может быть доступна — загрузим и при необходимости покажем
                    if (consentInfo.isConsentFormAvailable()) {
                        loadAndMaybeShowForm(consentInfo);
                    } else {
                        initAds(); // форма недоступна — можно грузить рекламу
                    }
                },
                formError -> {
                    // Не удалось обновить статус — продолжаем без формы
                    initAds();
                }
        );
    }

    private void loadAndMaybeShowForm(ConsentInformation consentInfo) {
        UserMessagingPlatform.loadConsentForm(
                this,
                (ConsentForm form) -> {
                    // Если согласие требуется — показываем форму
                    if (consentInfo.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED) {
                        form.show(
                                this,
                                formError -> {
                                    // После закрытия формы пробуем снова (статус мог измениться)
                                    if (consentInfo.getConsentStatus() == ConsentInformation.ConsentStatus.OBTAINED
                                            || consentInfo.getConsentStatus() == ConsentInformation.ConsentStatus.NOT_REQUIRED) {
                                        initAds();
                                    } else {
                                        // Если всё ещё REQUIRED и пользователь закрыл форму — всё равно инициализируем без персонализации
                                        initAds();
                                    }
                                }
                        );
                    } else {
                        // REQUIRED не требуется — сразу инициализируем рекламу
                        initAds();
                    }
                },
                formError -> {
                    // Не удалось загрузить форму — инициализируем без неё
                    initAds();
                }
        );
    }

    /** Инициализируем AdsManager и прикрепляем баннер. Вызывается ОДИН раз после UMP. */
    private void initAds() {
        if (ads != null) return; // защитимся от повторов
        ads = new AdsManager(
                this,
                getString(R.string.ad_unit_banner),
                getString(R.string.ad_unit_interstitial),
                getString(R.string.ad_unit_rewarded)
        );
        gameView.setAds(ads);     // дать игре доступ к interstitial/rewarded
        ads.attachBanner(root);   // показать баннер
    }

    @Override protected void onPause() {
        super.onPause();
        if (gameView != null) gameView.onPauseApp();
        if (ads != null) ads.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.onResumeApp();
        if (ads != null) ads.onResume();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (ads != null) ads.onDestroy();
    }
}
