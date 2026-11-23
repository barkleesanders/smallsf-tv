package com.barklee.smallsftv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

/**
 * Small SF TV — a fullscreen, keep-awake WebView wrapper for
 * https://smallsf.com/ on Fire TV Cube / Android TV, with a remote-driven
 * cursor so the site's own controls (Layers, Places, zoom…) are usable
 * from the couch.
 *
 * Default view: Mission Rock / Mission Bay, ~1.2 km across.
 * Override without rebuilding:
 *   adb shell am start -n com.barklee.smallsftv/.MainActivity -e url "https://smallsf.com/?at=37.79530,-122.39360,400m"
 *
 * Remote map:
 *   D-pad        move the cursor (hold to accelerate)
 *   OK           tap at the cursor
 *   OK + D-pad   hold OK and move to drag / pan the map
 *   Fast-forward zoom in      Rewind   zoom out
 *   Menu         open/close the Layers panel (cursor jumps to it)
 *   Play/Pause   reload the page
 *   Back         first press closes any open panel, second press exits
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_URL =
            "https://smallsf.com/?at=37.77530,-122.38860,1200m";
    private static final long AUTO_RELOAD_MS = 6L * 60L * 60L * 1000L;
    private static final long ERROR_RETRY_MS = 30L * 1000L;
    private static final long CURSOR_HIDE_MS = 5000L;

    /** Simple ring cursor drawn over the WebView. */
    private static class CursorView extends View {
        private float x = -1, y = -1;
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        CursorView(android.content.Context c) {
            super(c);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(4f);
            ring.setColor(Color.parseColor("#FF7A59"));
            dot.setStyle(Paint.Style.FILL);
            dot.setColor(Color.WHITE);
            setVisibility(GONE);
        }
        void place(float nx, float ny) {
            x = nx; y = ny;
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            if (x < 0) return;
            canvas.drawCircle(x, y, 26f, ring);
            canvas.drawCircle(x, y, 5f, dot);
        }
    }

    private WebView web;
    private CursorView cursor;
    private float curX = -1, curY = -1;
    private boolean dragging = false;
    private long touchDownTime = 0;
    private long lastBackMs = 0;
    private String homeUrl = DEFAULT_URL;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoReload = new Runnable() {
        @Override public void run() {
            if (web != null) web.reload();
            handler.postDelayed(this, AUTO_RELOAD_MS);
        }
    };
    private final Runnable hideCursor = () -> { if (cursor != null) cursor.setVisibility(View.GONE); };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterImmersive();

        Intent intent = getIntent();
        if (intent != null) {
            String extra = intent.getStringExtra("url");
            if (extra != null && extra.startsWith("https://smallsf.com")) {
                homeUrl = extra;
            } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
                Uri data = intent.getData();
                if ("smallsf.com".equals(data.getHost())) homeUrl = data.toString();
            }
        }

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        web.setFocusable(false);
        web.setFocusableInTouchMode(false);
        web.setBackgroundColor(Color.parseColor("#10222a"));
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " SmallSF-TV/1.2 (Fire TV; Android TV)");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.requestFocus();
                view.evaluateJavascript(
                        "(function(){var c=document.querySelector('#map canvas')||document.body;"
                      + "if(c){c.setAttribute('tabindex','0');c.focus();}})();", null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) {
                    handler.postDelayed(() -> { if (web != null) web.loadUrl(homeUrl); }, ERROR_RETRY_MS);
                }
            }
        });

        web.loadUrl(homeUrl);
        handler.postDelayed(autoReload, AUTO_RELOAD_MS);
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
              | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    // ---- cursor helpers ----

    private void ensureCursor() {
        if (curX < 0) {
            curX = web.getWidth() / 2f;
            curY = web.getHeight() / 2f;
        }
        cursor.place(curX, curY);
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, CURSOR_HIDE_MS);
    }

    private void moveCursor(float dx, float dy) {
        ensureCursor();
        curX = Math.max(0, Math.min(web.getWidth() - 1, curX + dx));
        curY = Math.max(0, Math.min(web.getHeight() - 1, curY + dy));
        cursor.place(curX, curY);
        if (dragging) dispatchTouch(MotionEvent.ACTION_MOVE);
    }

    private void dispatchTouch(int action) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) touchDownTime = now;
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, curX, curY, 0);
        ev.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(ev);
        ev.recycle();
    }

    private void js(String script) {
        if (web != null) web.evaluateJavascript(script, null);
    }

    /** Zoom via the site's own camera (window.__cam.zoomStep, the same call its
     *  own zoom buttons make), falling back to its +/- keyboard shortcut. */
    private void zoom(boolean in) {
        String f = in ? "1.6" : "(1/1.6)";
        String key = in ? "+" : "-";
        js("(function(){var c=document.querySelector('#map');"
         + "if(window.__cam&&window.OpenSeadragon&&c){"
         + "window.__cam.zoomStep(" + f + ",new OpenSeadragon.Point(c.clientWidth/2,c.clientHeight/2));return;}"
         + "var e=new KeyboardEvent('keydown',{key:'" + key + "',bubbles:true});"
         + "document.dispatchEvent(e);window.dispatchEvent(e);})();");
        ensureCursor();
    }

    /** Toggle the Layers pop-over and park the cursor on the Layers button. */
    private void toggleLayers() {
        web.evaluateJavascript(
                "(function(){var els=document.querySelectorAll('button,[role=button],a');"
              + "for(var i=0;i<els.length;i++){var e=els[i];"
              + "if((e.textContent||'').trim()==='Layers'){e.click();"
              + "var r=e.getBoundingClientRect();return (r.left+r.width/2)+','+(r.bottom+30);}}"
              + "return '';})();",
                new ValueCallback<String>() {
                    @Override public void onReceiveValue(String value) {
                        if (value != null) {
                            String v = value.replace("\"", "");
                            String[] parts = v.split(",");
                            if (parts.length == 2) {
                                try {
                                    float density = getResources().getDisplayMetrics().density;
                                    curX = Float.parseFloat(parts[0]) * density;
                                    curY = Float.parseFloat(parts[1]) * density;
                                } catch (NumberFormatException ignored) { }
                            }
                        }
                        ensureCursor();
                    }
                });
    }

    // ---- remote handling ----
    // dispatchKeyEvent runs BEFORE the WebView sees keys, so the D-pad,
    // media keys and Menu are ours even when the page has focus.

    private boolean isHandledKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD: case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_ZOOM_IN: case KeyEvent.KEYCODE_ZOOM_OUT:
            case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (!isHandledKey(keyCode)) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (dragging) {
                        dragging = false;
                        dispatchTouch(MotionEvent.ACTION_UP);
                    }
                    return true;
                default:
                    return true;
            }
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        float step = 16f * (1f + Math.min(event.getRepeatCount(), 12) * 0.45f);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    moveCursor(0, -step); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  moveCursor(0, step);  return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  moveCursor(-step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveCursor(step, 0);  return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0 && !dragging) {
                    ensureCursor();
                    dragging = true;
                    dispatchTouch(MotionEvent.ACTION_DOWN);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_ZOOM_IN:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (event.getRepeatCount() == 0) zoom(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_ZOOM_OUT:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (event.getRepeatCount() == 0) zoom(false);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (event.getRepeatCount() == 0) toggleLayers();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (event.getRepeatCount() == 0 && web != null) web.reload();
                return true;
            case KeyEvent.KEYCODE_BACK: {
                long now = SystemClock.uptimeMillis();
                if (now - lastBackMs < 3000) { finish(); return true; }
                lastBackMs = now;
                js("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));");
                return true;
            }
            default:
                return true;
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
