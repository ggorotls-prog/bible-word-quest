package app.wordquest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// 화면은 앱 안의 game.html(assets, 빌드할 때 드라이브의 「말씀-낱말-탐험대.html」을 그대로 복사).
// 게임 파일은 고치지 않고, 앱이 문서 틀(doctype·charset·viewport)과 읽어주기 이어 주기(tts.js)만 앞에 붙여 띄운다.
// 소리(WebAudio)는 WebView가 그대로 낸다. 기록(localStorage)은 앱 안에 남는다(지우고 다시 깔면 사라짐).
// 뒤로 가기: 화면의 「뒤로」 버튼 → 첫 화면 → 첫 화면에서만 앱 닫힘(새 앱 규칙, 2026-11-11).
public class MainActivity extends Activity {
    private static final String TAG = "WordQuest";
    static volatile boolean visible;

    private WebView web;
    private boolean loaded, backFromSecurity, selftest;
    private TextToSpeech tts;
    private volatile boolean ttsReady;
    private volatile int ttsLang = -99;
    private String pendingSpeak;
    private float pendingRate = 1f, pendingPitch = 1f;
    private boolean warnedNoVoice;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 기록 앱 화면 값(브랜드 가이드 9절): 밝은 화면 고정
        int bg = Color.parseColor("#f6f5f1");
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        selftest = getIntent() != null && getIntent().getBooleanExtra("selftest", false);
        startTts();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#F6EBD3"));
        web.setFitsSystemWindows(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);      // localStorage: 달란트·별·이름이 앱을 다시 열어도 남는다
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false); // 효과음·음악
        s.setTextZoom(100);
        web.addJavascriptInterface(new Bridge(), "NativeTTS");
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) {
                loaded = true;
                if (selftest) runSelftest();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; } // 앱 밖으로 안 나감
        });
        web.loadDataWithBaseURL("file:///android_asset/", page(), "text/html", "utf-8", null);
        setContentView(web);

        UpdateJob.schedule(this);
        askNotifyOnce();
    }

    // 게임 앞에 문서 틀과 읽어주기 이어 주기를 붙인다(게임 글자는 그대로)
    private String page() {
        return "<!doctype html><html lang=\"ko\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,viewport-fit=cover\">"
                + "<script>" + asset("tts.js") + "</script>"
                + asset("game.html");
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            return o.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    // ---- 읽어주기: 폰의 음성 엔진 ----
    private void startTts() {
        tts = new TextToSpeech(getApplicationContext(), st -> {
            if (st == TextToSpeech.SUCCESS && tts != null) {
                ttsLang = tts.setLanguage(Locale.KOREAN);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {}
                    @Override public void onDone(String id) { js("window.__ttsDone && __ttsDone()"); }
                    @Override public void onError(String id) { js("window.__ttsDone && __ttsDone()"); }
                });
                ttsReady = true;
                String p = pendingSpeak;
                pendingSpeak = null;
                if (p != null) say(p, pendingRate, pendingPitch);
            }
            Log.i(TAG, "selftest tts init=" + st + " lang=" + ttsLang);
        });
    }

    private void say(String text, float rate, float pitch) {
        if (!ttsReady) { pendingSpeak = text; pendingRate = rate; pendingPitch = pitch; return; }
        if (ttsLang < 0) { warnNoVoice(); return; }
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);
        int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime());
        Log.i(TAG, "selftest tts speak=" + r);
    }

    // 한국어 목소리가 없을 때 한 번만 알린다
    private void warnNoVoice() {
        if (warnedNoVoice) return;
        warnedNoVoice = true;
        runOnUiThread(() -> Toast.makeText(this, "읽어주기 목소리(한국어)가 없어요. 설정 → 텍스트 음성 변환에서 한국어를 받아 주세요.", Toast.LENGTH_LONG).show());
        js("window.__ttsDone && __ttsDone()");
    }

    private class Bridge {
        @JavascriptInterface public void speak(String text, String lang, double rate, double pitch) {
            say(text, (float) rate, (float) pitch);
        }
        @JavascriptInterface public void stop() {
            pendingSpeak = null;
            if (tts != null) try { tts.stop(); } catch (Exception ignored) {}
        }
    }

    // 가상 폰 시험용(빌드의 smoke 단계): 읽어주기·소리·저장이 되는지 로그로 남긴다. 평소에는 안 돈다.
    private void runSelftest() {
        String code = "(function(){var r={};"
                + "try{r.ttsNative=String(speechSynthesis.speak).indexOf('NativeTTS')>=0}catch(e){r.ttsErr=String(e)}"
                + "try{var A=window.AudioContext||window.webkitAudioContext;var a=new A();r.audio=a.state;var o=a.createOscillator();o.connect(a.destination);o.start();o.stop(a.currentTime+.05)}catch(e){r.audioErr=String(e)}"
                + "try{r.saved=localStorage.getItem('wq_selftest');localStorage.setItem('wq_selftest','kept')}catch(e){r.lsErr=String(e)}"
                + "r.home=!!(document.getElementById('toShop')||document.querySelector('.pinpad'));"
                + "r.pin=!!document.querySelector('.pinpad');"
                + "try{var bb=JSON.parse(localStorage.getItem('mw2_bible')||'{}');r.seed=Object.keys(bb).filter(function(k){return bb[k]==='2026-11-10'}).length}catch(e){}"
                + "try{r.vis=localStorage.getItem('wq_vis');localStorage.setItem('wq_vis','');document.addEventListener('visibilitychange',function(){localStorage.setItem('wq_vis',(localStorage.getItem('wq_vis')||'')+document.visibilityState+'-')})}catch(e){}"
                + "try{speechSynthesis.speak(new SpeechSynthesisUtterance('말씀 낱말 탐험대'))}catch(e){r.speakErr=String(e)}"
                + "return JSON.stringify(r)})()";
        web.postDelayed(() -> web.evaluateJavascript(code, v -> Log.i(TAG, "selftest result " + v)), 3000);
    }

    private void js(String code) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(code, null); });
    }

    // 업데이트 알림이 보이게: 처음 한 번만 알림 허용을 묻는다(안드로이드 13부터)
    private void askNotifyOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33 || selftest) return;
        SharedPreferences p = getSharedPreferences("wq-app", MODE_PRIVATE);
        if (p.getBoolean("askedNotify", false)) return;
        p.edit().putBoolean("askedNotify", true).apply();
        if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        if (web != null) { web.onResume(); web.resumeTimers(); }
        Context app = getApplicationContext();

        // 앱을 열 때마다 새 버전이 있나 본다(업무 기록 앱과 같은 방식)
        boolean retry = backFromSecurity;
        backFromSecurity = false;
        new Thread(() -> {
            int latest = Updater.latest(app);
            if (latest <= BuildConfig.VERSION_CODE) return;
            if (!Updater.canInstall(this)) { runOnUiThread(this::askInstallPermission); return; }
            if (retry) { Updater.checkAndInstall(app, true, true); return; }
            if (!Updater.askedRecently(app, latest)) runOnUiThread(this::askUpdate);
        }).start();
    }

    // 앱을 내리면 읽어주기·음악을 멈춘다
    @Override
    protected void onPause() {
        visible = false;
        if (tts != null) try { tts.stop(); } catch (Exception ignored) {}
        // 게임 시간: 내리는 순간 남은 시간을 저장한다(게임의 saveTime). 내려가 있는 동안은 타이머가 멈춰 시간이 줄지 않는다.
        if (web != null && loaded) web.evaluateJavascript("try{typeof saveTime==='function'&&saveTime()}catch(e){}", null);
        if (web != null) { web.onPause(); web.pauseTimers(); }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) {} tts = null; }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    // 뒤로: 사이트의 appBack()이 있으면 먼저 → 화면의 「뒤로」 버튼 → 첫 화면으로 → 첫 화면이면 앱 닫힘
    @Override
    public void onBackPressed() {
        if (!loaded) { super.onBackPressed(); return; }
        // 비밀번호 화면·시간이 끝나 잠긴 화면에서는 뒤로 가기로 풀리지 않게 앱을 닫는다
        web.evaluateJavascript("(function(){try{if(window.appBack)return !!appBack();"
                + "var ab=document.querySelector('.admbox');if(ab){ab.remove();return true}"
                + "if(document.querySelector('.pinpad'))return false;"
                + "var b=document.getElementById('backBtn');if(b){b.click();return true}"
                + "if(typeof gated!=='undefined'&&gated)return false;"
                + "if(document.getElementById('toShop'))return false;"
                + "if(typeof home==='function'){home();return true}}catch(e){}return false})()", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    // 새 버전이 있을 때: 「업데이트」를 누르면 보안 화면을 연다. 자동 차단을 끄고 돌아오면 설치가 시작된다.
    private void askUpdate() {
        if (isFinishing()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("새 버전이 있어요")
                .setMessage("업데이트하려면 삼성 「자동 차단」을 잠깐 꺼야 해요.\n\n「업데이트」를 누르면 보안 화면이 열려요. 「자동 차단」을 끄고 뒤로 오면 업데이트가 시작돼요.\n(자동 차단은 30분 뒤 저절로 다시 켜져요)")
                .setPositiveButton("업데이트", (d, w) -> {
                    backFromSecurity = true;
                    openSecuritySettings();
                })
                .setNegativeButton("나중에", null)
                .show();
    }

    private void openSecuritySettings() {
        for (String action : new String[]{Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(action));
                return;
            } catch (Exception ignored) {}
        }
    }

    // 처음 한 번: 이 앱이 스스로 업데이트할 수 있게 「이 출처 허용」을 켜 달라고 한다
    private void askInstallPermission() {
        if (isFinishing()) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("새 버전이 있어요")
                .setMessage("앞으로 저절로 업데이트되게, 다음 화면에서 「이 출처 허용」을 켜 주세요. 한 번만 하면 돼요.")
                .setPositiveButton("켜러 가기", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("나중에", null)
                .show();
    }
}
