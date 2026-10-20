package app.glasshome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Glass Home: a tvOS-style home screen in plain Android views (no WebView, no libraries) so it stays
 * smaller and faster than Fire OS's own launcher. Layout is designed at 1920x1080 and scaled by px().
 */
public class Main extends Activity {
    // Dock order as asked. "a|b" = first installed of these packages (YouTube's Fire TV app, or the Android TV one).
    static final String[] DOCK = {"com.stremio.one", "com.amazon.firetv.youtube|com.google.android.youtube.tv", "com.plexapp.android",
        "com.amazon.firebat", "com.spotify.tv.android"};
    static final String[] MORE = {"com.netflix.ninja", "com.amazon.venezia", "com.amazon.cloud9", "com.esaba.downloader", "com.apple.airplay.amazon.airplay"};
    static final String[] ICON_ONLY = {"com.amazon.venezia", "com.amazon.cloud9", "com.esaba.downloader", "com.apple.airplay.amazon.airplay"};
    static final String[][] TABS = {{"explore", "Explore"}, {"movie", "Movies"}, {"series", "Shows"}};
    static final String STREMIO = "com.stremio.one", YOUTUBE = "com.amazon.firetv.youtube";
    static final String CINEMETA = "https://v3-cinemeta.strem.io/";   // what Stremio's Discover tab shows
    static final String METAHUB = "https://images.metahub.space/", TMDB = "https://api.themoviedb.org/3/";
    static final String A = "com.amazon.device.settings.action.";
    // Only these Fire OS settings screens accept launches from a third-party app.
    static final String[][] SETTINGS = {{"Network", "android.settings.WIFI_SETTINGS"}, {"Applications", A + "APPLICATIONS"},
        {"Remotes & Bluetooth", A + "CONTROLLERS"}, {"Device & Software", A + "DEVICE"}};
    static final long IDLE_MS = 180_000, DRIFT_MS = 240_000, GALLERY_MS = 4_000, CACHE_MS = 6 * 3_600_000L;
    static final int MAIN = 0, SHEET = 1, BRIGHT = 2, MOVE = 3, WHITE = Color.WHITE, INK = 0xFF111114, IMDB_YELLOW = 0xFFF5C518;
    static final int BACKDROP = 0, LOGO = 1, THUMB = 2, CACHE_VERSION = 6;   // bump when the shelf JSON shape changes
    // Tiles are 16:9 like TV app banners. The dock sits at the bottom; a low-key carousel above it; the grid peeks below.
    // DPAD = the dock's inset on all sides, so tile corners (DOCK_R - DPAD) run parallel to the dock's own corners.
    static final float TW = 220, TH = 124, GAP = 22, DPAD = 20, DOCK_R = 48, DOCK_H = TH + 2 * DPAD, DOCK_TOP = 1080 - 44 - DOCK_H, GRID_TOP = 1080 - 18, PITCH = 160, DOCK_AT_TOP = 64;
    // Carousel: exactly 7 thumbs span the 1728px between the side margins (7 x 228 + 6 x 22), so both edges line up.
    static final float CW = 228, CH = 128, CGAP = 22, CAR_TOP = DOCK_TOP - 34 - CH;
    static final int CAR_VISIBLE = 7;
    static final OvershootInterpolator OVERSHOOT = new OvershootInterpolator(1.1f);   // shared: no allocation per key press
    static final DecelerateInterpolator DECEL = new DecelerateInterpolator(2f);

    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService io = Executors.newFixedThreadPool(2);
    final ExecutorService big = Executors.newSingleThreadExecutor();   // HD backdrops decode one at a time (memory peak)
    final ExecutorService pre = Executors.newSingleThreadExecutor();   // neighbours prefetched to disk; never in front of what you are looking at
    final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(14 << 20) {   // current backdrop + logos + thumbnails
        @Override protected int sizeOf(String k, Bitmap b) { return b.getAllocationByteCount(); }
    };
    float s;
    String key, tmdbKey;   // optional: Stremio auth key (library + Continue), TMDB key (more backdrops on the Open button)
    FrameLayout root, content;
    ImageView bgA, bgB, logo, contImg;
    boolean frontA = true, idle, stopped, drawnReported;
    View shade, idleView, contFill;
    LinearLayout info, infoText, items, car;
    int carFirst;
    TextView title, meta, desc, clock, toastText, pct, contTitle, contSub, imdbScore;
    Glass dock, openBtn, trailerBtn, libBtn, imdb, bSearch, bBt, bBright, bSettings, sheet, bright, toastBox;
    Tile cont;
    Bar bar;
    final List<TextView> tabViews = new ArrayList<>();
    final List<View> dockTiles = new ArrayList<>(), thumbs = new ArrayList<>();
    final List<View[]> gridRows = new ArrayList<>(), rows = new ArrayList<>();
    final List<JSONObject> shelf = new ArrayList<>();
    final List<String> gallery = new ArrayList<>();
    JSONObject resume;
    final java.util.Map<String, JSONObject> lib = new java.util.HashMap<>();   // your Stremio library by id (including removed/temp entries)
    Icon libIcon;
    View focused, sheetReturn;
    String tab = "explore", shown = "";
    int mode = MAIN, slide, token, pctNow, liftRow = -1, galleryAt;
    long lastRefresh;

    int px(float v) { return Math.round(v * s); }

    // ---- build ----------------------------------------------------------------------------------
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Glass.all.clear();
        s = getResources().getDisplayMetrics().widthPixels / 1920f;
        key = readAsset("stremio.key");
        tmdbKey = readAsset("tmdb.key");
        // Cached shelves from an older build lack fields this one needs (thumb, imdb): drop them and refetch.
        if (prefs().getInt("v", 0) != CACHE_VERSION) prefs().edit().clear().putInt("v", CACHE_VERSION).apply();
        root = new FrameLayout(this);
        noClip(root);
        root.setBackgroundColor(Color.BLACK);
        bgA = backdrop();
        bgB = backdrop();
        root.addView(bgA);
        root.addView(bgB);   // the shading is baked into the backdrop bitmap: one full-screen layer, not four
        shade = new View(this);
        shade.setBackgroundColor(Color.BLACK);
        shade.setAlpha(0);
        root.addView(shade, match());

        content = new FrameLayout(this);   // everything that lifts when you go down into the grid
        noClip(content);
        root.addView(content, match());
        buildTop();
        buildShelf();
        FrameLayout carBox = new FrameLayout(this);   // clips at both margins; 12px of headroom for the focus lift
        carBox.setOutlineProvider(ViewOutlineProvider.BOUNDS);   // clipChildren only clips a child to its own edges; the outline clips to the box
        carBox.setClipToOutline(true);
        car = hbox();
        carBox.addView(car, at(-2, px(CH), Gravity.TOP | Gravity.START, 0, 12, 0, 0));
        content.addView(carBox, at(px(1728), px(CH + 24), Gravity.TOP | Gravity.START, 96, CAR_TOP - 12, 0, 0));
        buildDockAndGrid();
        buildSheet();
        buildBrightness();

        toastBox = new Glass(this, px(36), 0x99202024);
        toastText = text(28, 600, WHITE);
        toastText.setPadding(px(36), 0, px(36), 0);
        toastText.setGravity(Gravity.CENTER);
        toastBox.addView(toastText, new FrameLayout.LayoutParams(-2, -1));
        toastBox.setAlpha(0);
        root.addView(toastBox, at(-2, px(72), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 600, 0, 0));

        idleView = new View(this);
        idleView.setBackgroundColor(Color.BLACK);
        idleView.setAlpha(0);
        root.addView(idleView, match());

        setContentView(root);
        getWindow().setBackgroundDrawable(null);   // the root paints black; skip a full-screen overdraw

        String c = prefs().getString("continue", null);
        if (c != null) try { setContinue(new JSONObject(c)); } catch (Exception ignored) { }
        showTab(tab);
        indexRows();
        setFocus(dockTiles.isEmpty() ? bSearch : dockTiles.get(0));
        io.execute(this::trimDiskCache);
        // Fire OS can wipe accessibility settings; put the Home service back the moment that happens.
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false,
            new android.database.ContentObserver(ui) { @Override public void onChange(boolean self) { Keeper.ensure(Main.this); } });
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false,
            new android.database.ContentObserver(ui) { @Override public void onChange(boolean self) { Keeper.ensure(Main.this); } });
        Keeper.ensure(this);
        Keeper.schedule(this);
    }

    /** Top row: Explore / Movies / Shows on the left, time and quick buttons on the right. */
    void buildTop() {
        Glass tabsPill = new Glass(this, px(32), 0x26FFFFFF);
        LinearLayout tl = hbox();
        tl.setPadding(px(6), 0, px(6), 0);
        for (String[] t : TABS) {
            TextView v = text(27, 600, WHITE);
            v.setText(t[1]);
            v.setTag(t[0]);
            v.setGravity(Gravity.CENTER);
            v.setPadding(px(26), 0, px(26), 0);
            tl.addView(v, lp(-2, px(52)));
            tabViews.add(v);
        }
        tabsPill.addView(tl, new FrameLayout.LayoutParams(-2, -1));
        content.addView(tabsPill, at(-2, px(64), Gravity.TOP | Gravity.START, 96, 64, 0, 0));

        LinearLayout cluster = hbox();
        Glass timePill = new Glass(this, px(32), 0x26FFFFFF);
        clock = text(27, 600, WHITE);
        clock.setPadding(px(26), 0, px(26), 0);
        clock.setGravity(Gravity.CENTER);
        timePill.addView(clock, new FrameLayout.LayoutParams(-2, -1));
        cluster.addView(timePill, lp(-2, px(64)));   // same 64px height and top as the tab pill
        bSearch = circle(Icon.SEARCH, () -> open("stremio:///search", STREMIO));
        bBt = circle(Icon.BLUETOOTH, () -> start(new Intent(A + "CONTROLLERS")));
        bBright = circle(Icon.SUN, this::openBright);
        bSettings = circle(Icon.GEAR, this::openSheet);
        for (Glass g : new Glass[] {bSearch, bBt, bBright, bSettings}) cluster.addView(g, margins(lp(px(64), px(64)), 14, 0, 0, 0));
        content.addView(cluster, at(-2, px(64), Gravity.TOP | Gravity.END, 0, 64, 96, 0));
    }

    /** Top Shelf: logo, [IMDb 7.8] 2h 6m · 2026 · Genre, a blurb, and Open. */
    void buildShelf() {
        info = vbox();
        infoText = vbox();
        FrameLayout titleBox = new FrameLayout(this);
        logo = new ImageView(this);
        logo.setAdjustViewBounds(true);
        logo.setMaxHeight(px(120));
        logo.setMaxWidth(px(600));
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        titleBox.addView(logo, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        title = text(62, 700, WHITE);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setMaxWidth(px(1000));
        title.setLetterSpacing(-.02f);
        titleBox.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        infoText.addView(titleBox, lp(-2, px(124)));

        LinearLayout metaRow = hbox();
        imdb = new Glass(this, px(20), 0x33FFFFFF);   // IMDb as an inline liquid-glass badge
        LinearLayout ib = hbox();
        ib.setPadding(px(14), 0, px(16), 0);
        TextView mark = text(23, 900, IMDB_YELLOW);
        mark.setText("IMDb");
        mark.setLetterSpacing(-.03f);
        ib.addView(mark);
        imdbScore = text(24, 650, WHITE);
        ib.addView(imdbScore, margins(lp(-2, -2), 10, 0, 0, 0));
        imdb.addView(ib, new FrameLayout.LayoutParams(-2, -1));
        metaRow.addView(imdb, margins(lp(-2, px(40)), 0, 0, 16, 0));
        meta = text(27, 600, 0xF2FFFFFF);
        metaRow.addView(meta);
        infoText.addView(metaRow, margins(lp(-2, px(40)), 0, 18, 0, 0));
        desc = text(26, 400, 0xCCFFFFFF);
        desc.setMaxLines(3);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        desc.setMaxWidth(px(780));   // keeps clear of faces in wide backdrops
        desc.setLineSpacing(0, 1.2f);
        infoText.addView(desc, margins(lp(-2, -2), 0, 14, 0, 0));
        info.addView(infoText, lp(-2, -2));   // default params would be MATCH_PARENT and squeeze the text to the button's width
        LinearLayout actions = hbox();
        openBtn = pill(Icon.PLAY, "Open in Stremio", 60);
        openBtn.action = () -> openItem(slide);
        actions.addView(openBtn, lp(-2, px(60)));
        trailerBtn = pill(Icon.TRAILER, "Watch Trailer", 60);   // the title's trailer, played in the YouTube app
        trailerBtn.action = () -> {
            String yt = shelf.isEmpty() ? "" : shelf.get(slide).optString("trailer");
            if (!yt.isEmpty()) open("https://www.youtube.com/watch?v=" + yt, YOUTUBE);
        };
        actions.addView(trailerBtn, margins(lp(-2, px(60)), 14, 0, 0, 0));
        libBtn = circle(Icon.PLUS, this::toggleLibrary);   // + to add, check when it is in your library
        libIcon = (Icon) libBtn.getChildAt(0);
        actions.addView(libBtn, margins(lp(px(60), px(60)), 14, 0, 0, 0));
        info.addView(actions, margins(lp(-2, px(60)), 0, 26, 0, 0));
        info.setAlpha(0);
        content.addView(info, at(-2, -2, Gravity.TOP | Gravity.START, 96, 160, 0, 0));
    }

    /** Dock at the bottom (Continue + your apps), then a grid of the rest and the HDMI inputs, stacked downwards in the same columns. */
    void buildDockAndGrid() {
        PackageManager pm = getPackageManager();
        if (key != null) dockTiles.add(cont = continueTile());
        List<Tile> apps = new ArrayList<>();
        for (String p : DOCK) { Tile t = appTile(pm, p); if (t != null) apps.add(t); }
        // Your saved order (long-press OK to rearrange); apps not in it keep their DOCK position at the end.
        List<String> ord = java.util.Arrays.asList(getSharedPreferences("dock", MODE_PRIVATE).getString("order", "").split(","));
        apps.sort(java.util.Comparator.comparingInt(a -> { int i = ord.indexOf((String) a.getTag()); return i < 0 ? 1000 : i; }));
        dockTiles.addAll(apps);
        List<View> gridTiles = new ArrayList<>();
        for (String p : MORE) { Tile t = appTile(pm, p); if (t != null) gridTiles.add(t); }
        for (String[] in : inputs()) gridTiles.add(hdmiTile(in));

        int n = Math.max(1, dockTiles.size());
        float dockW = n * TW + (n - 1) * GAP + 2 * DPAD;
        dock = new Glass(this, px(DOCK_R), 0x26FFFFFF);
        dock.setClipToOutline(false);   // let the focused tile's springy scale breathe past the edge
        LinearLayout row = hbox();
        row.setPadding(px(DPAD - GAP / 2), 0, px(DPAD - GAP / 2), 0);   // every tile has the same margins, so tiles can swap places
        for (View tile : dockTiles) {
            row.addView(tile, margins(lp(px(TW), px(TH)), GAP / 2, 0, GAP / 2, 0));
            ((Glass) tile).r = px(DOCK_R - DPAD);   // concentric with the dock
            tile.invalidateOutline();
        }
        dock.addOnLayoutChangeListener((v, l, tp, r, b, ol, ot, or, ob) -> { v.setPivotX(v.getWidth() / 2f); v.setPivotY(v.getHeight()); });
        dock.addView(row, new FrameLayout.LayoutParams(-2, -1));
        content.addView(dock, at(px(dockW), px(DOCK_H), Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, DOCK_TOP, 0, 0));

        LinearLayout grid = vbox();
        for (int i = 0; i < gridTiles.size(); i += n) {
            LinearLayout r = hbox();
            List<View> cells = gridTiles.subList(i, Math.min(gridTiles.size(), i + n));
            for (View cell : cells) r.addView(cell, margins(lp(px(TW), px(TH)), 0, 0, GAP, 0));
            grid.addView(r, margins(lp(-2, px(TH)), 0, 0, 0, PITCH - TH));
            gridRows.add(cells.toArray(new View[0]));
        }
        content.addView(grid, at(-2, -2, Gravity.TOP | Gravity.START, (1920 - dockW) / 2 + DPAD, GRID_TOP, 0, 0));
    }

    /** The tile appears at once; its banner is drawn and centred off the main thread. */
    Tile appTile(PackageManager pm, String pkgs) {
        String p = null;
        Intent li = null;
        for (String c : pkgs.split("\\|")) { li = launchIntent(c); if (li != null) { p = c; break; } }
        if (li == null) return null;
        final String pkg = p;
        final Intent launch = li;
        boolean iconOnly = false;
        for (String x : ICON_ONLY) iconOnly |= x.equals(pkg);
        final boolean icon = iconOnly;
        Tile t = new Tile(px(22));
        t.setTag(pkg);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        t.addView(iv, 0, icon ? new FrameLayout.LayoutParams(px(80), px(80), Gravity.CENTER) : match());
        t.action = pkg.startsWith("com.apple.airplay") ? () -> toast("AirPlay is always on: cast from your iPhone or Mac")   // Fire OS lets only the system open its screen
            : () -> start(launch);
        io.execute(() -> {
            try {
                Drawable d0 = null;
                if (!icon) {
                    try { d0 = pm.getActivityBanner(launch); } catch (Exception ignored) { }
                    if (d0 == null) d0 = pm.getApplicationBanner(pkg);
                }
                final Drawable d = d0;
                // Rasterise once at the tile's focused size: the GPU holds a small texture instead of the app's full-size banner.
                Bitmap b = d != null ? centered(d, Math.round(px(TW) * 1.1f), Math.round(px(TH) * 1.1f))
                    : centered(pm.getApplicationIcon(pkg), Math.round(px(80) * 1.1f), Math.round(px(80) * 1.1f));
                if (d == null && !icon) ui.post(() -> iv.setLayoutParams(new FrameLayout.LayoutParams(px(80), px(80), Gravity.CENTER)));
                ui.post(() -> { iv.setImageBitmap(b); t.opaque = d != null; t.invalidate(); });
            } catch (Exception ignored) { }
        });
        return t;
    }

    Tile hdmiTile(String[] in) {
        Tile t = new Tile(px(22));
        LinearLayout col = vbox();
        col.setPadding(px(18), px(14), px(18), px(12));
        col.addView(new Icon(this, Icon.HDMI), lp(px(32), px(32)));
        TextView name = text(24, 650, WHITE);
        name.setText(in[1]);
        name.setSingleLine();
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name, margins(lp(-2, -2), 0, 10, 0, 0));
        TextView sub = text(18, 500, 0xB3FFFFFF);
        sub.setText(in[2]);
        col.addView(sub, margins(lp(-2, -2), 0, 3, 0, 0));
        t.addView(col, 0, match());
        t.action = () -> start(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in[0])));
        return t;
    }

    /** First dock tile: resume the most recent thing you were watching in Stremio. */
    Tile continueTile() {
        Tile t = new Tile(px(22));
        t.opaque = true;
        contImg = new ImageView(this);
        contImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(contImg, 0, match());
        View g = new View(this);
        g.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xE6000000, 0x33000000}));
        t.addView(g, 1, match());
        LinearLayout col = vbox();
        col.setGravity(Gravity.BOTTOM);
        col.setPadding(px(14), 0, px(14), px(18));
        contSub = text(17, 650, 0xE6FFFFFF);
        contSub.setText("Continue");
        contTitle = text(21, 700, WHITE);
        contTitle.setSingleLine();
        contTitle.setEllipsize(TextUtils.TruncateAt.END);
        contTitle.setText("Stremio");
        col.addView(contTitle);
        col.addView(contSub, margins(lp(-2, -2), 0, 4, 0, 0));
        t.addView(col, 2, match());
        FrameLayout track = new FrameLayout(this);   // time watched, as a bar along the bottom
        track.setBackground(round(0x4DFFFFFF, px(3)));
        contFill = new View(this);
        contFill.setBackground(round(WHITE, px(3)));
        track.addView(contFill, new FrameLayout.LayoutParams(0, -1));
        t.addView(track, 3, at(-1, px(6), Gravity.BOTTOM, 14, 0, 14, 8));
        t.action = () -> {
            if (resume == null) { open("stremio:///", STREMIO); return; }
            String id = resume.optString("id");
            open("stremio:///detail/" + resume.optString("type") + "/" + id + "/" + resume.optString("video", id), STREMIO);
        };
        return t;
    }

    void setContinue(JSONObject c) {
        resume = c;
        if (cont == null || c == null) return;
        contTitle.setText(c.optString("name"));
        contSub.setText(c.optString("sub"));
        contFill.getLayoutParams().width = Math.round(px(TW - 28) * (float) c.optDouble("progress", 0));
        contFill.requestLayout();
        image(c.optString("thumb"), THUMB, b -> { if (b != null && resume == c) contImg.setImageBitmap(b); });
    }

    void buildSheet() {
        sheet = new Glass(this, px(44), 0x8C1C1C20);
        LinearLayout col = vbox();
        col.setPadding(px(40), px(66), px(40), px(40));
        TextView h = text(46, 700, WHITE);
        h.setText("Settings");
        col.addView(h, margins(lp(-2, -2), 26, 0, 0, 28));
        items = vbox();
        for (String[] it : SETTINGS) {
            TextView t = text(31, 600, WHITE);
            t.setText(it[0]);
            t.setTag(it[1]);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(26), 0, px(26), 0);
            items.addView(t, lp(-1, px(84)));
        }
        col.addView(items);
        TextView note = text(24, 400, 0xB3FFFFFF);
        note.setText("Picture and sound: press ⚙ on your remote, or hold Home.");
        note.setLineSpacing(0, 1.2f);
        col.addView(note, margins(lp(-1, -2), 26, 30, 26, 0));
        sheet.addView(col, match());
        sheet.setTranslationX(px(720));
        root.addView(sheet, at(px(620), -1, Gravity.TOP | Gravity.END, 0, 24, 24, 24));
    }

    void buildBrightness() {
        bright = new Glass(this, px(48), 0x8C1C1C20);
        LinearLayout row = hbox();
        row.setPadding(px(34), 0, px(34), 0);
        row.addView(new Icon(this, Icon.SUN), lp(px(34), px(34)));
        bar = new Bar(this);
        LinearLayout.LayoutParams bl = lp(0, px(20));
        bl.weight = 1;
        row.addView(bar, margins(bl, 24, 0, 24, 0));
        pct = text(28, 650, WHITE);
        pct.setGravity(Gravity.END);
        row.addView(pct, lp(px(86), -2));
        bright.addView(row, match());
        bright.setAlpha(0);
        bright.setScaleX(.9f);
        bright.setScaleY(.9f);
        root.addView(bright, at(px(600), px(96), Gravity.TOP | Gravity.END, 0, 150, 96, 0));
    }

    // ---- data: Stremio Discover (Cinemeta) + your Stremio library -----------------------------------
    SharedPreferences prefs() { return getSharedPreferences("home", MODE_PRIVATE); }

    /** Explore = your unwatched library titles mixed with Discover; Movies / Shows = Discover. Continue = your latest in-progress title. */
    void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < 60_000) return;   // coming back to Home often shouldn't refetch every time
        lastRefresh = now;
        io.execute(() -> {
            try {
                SharedPreferences p = prefs();
                SharedPreferences.Editor e = p.edit();
                if (now - p.getLong("discoverAt", 0) > CACHE_MS || p.getString("movie", null) == null) {
                    e.putString("movie", items(new JSONObject(get(CINEMETA + "catalog/movie/top.json")).getJSONArray("metas"), 16).toString());
                    e.putString("series", items(new JSONObject(get(CINEMETA + "catalog/series/top.json")).getJSONArray("metas"), 16).toString());
                    e.putLong("discoverAt", now).apply();
                }
                JSONArray mv = new JSONArray(p.getString("movie", "[]")), sr = new JSONArray(p.getString("series", "[]"));
                JSONArray unwatched = new JSONArray();
                if (key != null) {
                    JSONArray lib = new JSONObject(post("https://api.strem.io/api/datastoreGet", new JSONObject().put("authKey", key)
                        .put("collection", "libraryItem").put("ids", new JSONArray()).put("all", true).toString())).getJSONArray("result");
                    java.util.Map<String, JSONObject> byId = new java.util.HashMap<>();
                    for (int i = 0; i < lib.length(); i++) byId.put(lib.getJSONObject(i).optString("_id"), lib.getJSONObject(i));
                    ui.post(() -> { this.lib.clear(); this.lib.putAll(byId); updateLibBtn(); });
                    JSONObject c = continueItem(lib);
                    e.putString("continue", c == null ? null : c.toString());
                    unwatched = unwatched(lib, p, e);
                    ui.post(() -> setContinue(c));
                }
                JSONArray explore = new JSONArray();
                Set<String> seen = new HashSet<>();
                for (int i = 0; explore.length() < 16 && i < 16; i++)
                    for (JSONArray src : new JSONArray[] {unwatched, mv, sr})
                        if (i < src.length() && seen.add(src.getJSONObject(i).optString("id"))) explore.put(src.getJSONObject(i));
                e.putString("explore", explore.toString()).apply();
                ui.post(() -> { if (focused == null || !thumbs.contains(focused) && !isAction(focused)) showTab(tab); });
            } catch (Exception ignored) { }   // offline or bad key: keep what's cached
        });
    }

    static JSONArray items(JSONArray metas, int max) throws Exception {
        JSONArray out = new JSONArray();
        for (int i = 0; i < metas.length() && out.length() < max; i++) { JSONObject it = item(metas.getJSONObject(i)); if (it != null) out.put(it); }
        return out;
    }

    /** Library titles never started, newest first, with details from Cinemeta (cached per title). */
    JSONArray unwatched(JSONArray lib, SharedPreferences p, SharedPreferences.Editor e) throws Exception {
        List<JSONObject> cand = new ArrayList<>();
        for (int i = 0; i < lib.length(); i++) {
            JSONObject it = lib.getJSONObject(i), st = it.optJSONObject("state");
            String type = it.optString("type");
            if (it.optBoolean("removed") || it.optBoolean("temp") || !("movie".equals(type) || "series".equals(type))) continue;
            if (st != null && (st.optInt("timesWatched") > 0 || st.optLong("timeOffset") > 0 || st.optInt("flaggedWatched") > 0
                || !st.optString("video_id").isEmpty())) continue;
            cand.add(it);
        }
        cand.sort((a, b) -> b.optString("_mtime").compareTo(a.optString("_mtime")));
        JSONArray out = new JSONArray();
        for (JSONObject it : cand) {
            if (out.length() >= 8) break;
            String id = it.optString("_id"), k = "meta:" + id, cached = p.getString(k, null);
            JSONObject m;
            if (cached != null) m = new JSONObject(cached);
            else {
                try { m = item(new JSONObject(get(CINEMETA + "meta/" + it.optString("type") + "/" + id + ".json")).getJSONObject("meta")); }
                catch (Exception ex) { continue; }
                if (m == null) continue;
                e.putString(k, m.toString());
            }
            out.put(m.put("meta", "In your library  ·  " + m.optString("meta")));
        }
        return out;
    }

    /**
     * The title you played most recently. Stremio keeps things you watched without adding them to your library as
     * temp + removed; its own Continue Watching counts those, so we do too. Only titles you removed yourself are skipped.
     */
    static JSONObject continueItem(JSONArray lib) throws Exception {
        JSONObject best = null;
        for (int i = 0; i < lib.length(); i++) {
            JSONObject it = lib.getJSONObject(i), st = it.optJSONObject("state");
            String type = it.optString("type");
            if (st == null || st.optString("lastWatched").isEmpty() || (it.optBoolean("removed") && !it.optBoolean("temp"))
                || !("movie".equals(type) || "series".equals(type))) continue;
            if (best == null || st.optString("lastWatched").compareTo(best.getJSONObject("state").optString("lastWatched")) > 0) best = it;
        }
        if (best == null) return null;
        JSONObject st = best.getJSONObject("state");
        String id = best.optString("_id"), video = st.optString("video_id", id);
        double dur = st.optDouble("duration", 0), off = st.optDouble("timeOffset", 0);
        long leftMin = dur > 0 ? Math.round((dur - off) / 60000) : 0;
        String[] ep = video.split(":");
        String sub = "series".equals(best.optString("type")) && ep.length >= 3 ? "S" + ep[1] + " · E" + ep[2] + "  ·  " : "";
        sub += off < 60_000 ? "Up next" : leftMin + "m left";   // a few seconds in means Stremio queued the next episode
        return new JSONObject().put("id", id).put("type", best.optString("type")).put("name", best.optString("name")).put("video", video)
            .put("sub", sub).put("progress", dur > 0 ? Math.min(1, off / dur) : 0)
            .put("thumb", METAHUB + "background/medium/" + id + "/img");
    }

    /** One shelf entry: id, type, name, backdrop, thumbnail, logo, IMDb rating, "2h 6m · 2026 · Drama", blurb. */
    static JSONObject item(JSONObject m) throws Exception {
        String bg = m.optString("background"), id = m.optString("id");
        if (bg.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        Matcher rt = Pattern.compile("(\\d+)\\s*min").matcher(m.optString("runtime"));
        if (rt.find()) { int min = Integer.parseInt(rt.group(1)); parts.add(min >= 60 ? (min / 60) + "h " + (min % 60) + "m" : min + "m"); }
        if (!m.optString("releaseInfo").isEmpty()) parts.add(m.optString("releaseInfo"));
        JSONArray g = m.optJSONArray("genres");
        if (g != null && g.length() > 0) parts.add(g.length() > 1 ? g.getString(0) + " & " + g.getString(1) : g.getString(0));
        parts.add("series".equals(m.optString("type")) ? "Show" : "Movie");
        return new JSONObject().put("id", id).put("type", m.optString("type")).put("name", m.optString("name"))
            .put("bg", bg).put("thumb", METAHUB + "background/small/" + id + "/img").put("logo", m.optString("logo")).put("poster", m.optString("poster")).put("trailer", trailerId(m))
            .put("imdb", m.optString("imdbRating")).put("meta", TextUtils.join("  ·  ", parts)).put("desc", m.optString("description"));
    }

    static String trailerId(JSONObject m) {
        JSONArray s = m.optJSONArray("trailerStreams");
        if (s != null && s.length() > 0) return s.optJSONObject(0).optString("ytId");
        JSONArray t = m.optJSONArray("trailers");
        return t != null && t.length() > 0 ? t.optJSONObject(0).optString("source") : "";
    }

    void openItem(int i) {
        if (i < 0 || i >= shelf.size()) return;
        JSONObject it = shelf.get(i);
        open("stremio:///detail/" + it.optString("type") + "/" + it.optString("id"), STREMIO);
    }

    void showTab(String t) {
        String json = prefs().getString(t, "[]");
        if (t.equals(tab) && json.equals(shown)) return;   // a refresh with nothing new: don't restart the shelf
        tab = t;
        shown = json;
        for (TextView v : tabViews) if (v != focused) styleTab(v, false);
        shelf.clear();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) shelf.add(a.getJSONObject(i));
        } catch (Exception ignored) { }
        fillCarousel();
        indexRows();
        if (shelf.isEmpty()) return;
        info.animate().alpha(liftRow >= 0 ? 0 : 1).setDuration(250);
        showSlide(0, false);
    }

    /** The low-key carousel: one small backdrop per title in this tab. Moving along it changes the shelf. */
    void fillCarousel() {
        car.removeAllViews();
        thumbs.clear();
        car.setTranslationX(0);
        carFirst = 0;
        for (int i = 0; i < shelf.size(); i++) {
            Tile t = new Tile(px(18));
            t.grow = 1.06f;
            t.opaque = true;
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            t.addView(iv, 0, match());
            View g = new View(this);   // the title sits on the image over a soft gradient
            g.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] {0xD9000000, 0x00000000}));
            t.addView(g, 1, at(-1, px(CH * .6f), Gravity.BOTTOM, 0, 0, 0, 0));
            TextView name = text(21, 650, WHITE);
            name.setText(shelf.get(i).optString("name"));
            name.setSingleLine();
            name.setEllipsize(TextUtils.TruncateAt.END);
            t.addView(name, 2, at(-1, -2, Gravity.BOTTOM, 14, 0, 14, 12));
            final int idx = i;
            t.action = () -> openItem(idx);
            car.addView(t, margins(lp(px(CW), px(CH)), 0, 0, i < shelf.size() - 1 ? CGAP : 0, 0));
            thumbs.add(t);
        }
        // Exact full width: measured inside the 1728px clip box, a wrap-width row would squash thumbs 8+ to nothing.
        car.getLayoutParams().width = Math.max(1, px(shelf.size() * (CW + CGAP) - CGAP));
        car.requestLayout();
        loadThumbs();
    }

    void showSlide(int n, boolean instant) {
        if (shelf.isEmpty()) return;
        stopGallery();
        slide = (n + shelf.size()) % shelf.size();
        final JSONObject it = shelf.get(slide);
        final int tok = ++token;
        for (int i = 0; i < thumbs.size(); i++) thumbs.get(i).setAlpha(i == slide ? 1f : .55f);
        scrollCarousel(slide);
        // Wait a beat before decoding the HD backdrop, so scrolling quickly through the carousel stays light.
        ui.postDelayed(() -> {
            if (tok != token) return;
            backdropFor(hd(it.optString("bg")), tok);
            for (int d : new int[] {1, -1}) prefetch(hd(shelf.get((slide + d + shelf.size()) % shelf.size()).optString("bg")));
        }, instant ? 0 : 200);   // scrolling quickly through the carousel skips the in-between backdrops
        Runnable fill = () -> {
            title.setText(it.optString("name"));
            title.setVisibility(View.VISIBLE);
            logo.setVisibility(View.GONE);
            image(it.optString("logo"), LOGO, b -> {
                // tall stacked logos would render tiny in the 600x120 slot; the title text reads better
                if (tok != token || b == null || b.getWidth() < b.getHeight() * 1.3f) return;
                logo.setImageBitmap(b);
                logo.setVisibility(View.VISIBLE);
                title.setVisibility(View.INVISIBLE);
            });
            String r = it.optString("imdb");
            imdb.setVisibility(r.isEmpty() ? View.GONE : View.VISIBLE);
            imdbScore.setText(r);
            meta.setText(it.optString("meta"));
            desc.setText(it.optString("desc"));
            updateLibBtn();
            infoText.animate().alpha(1).translationY(0).setDuration(200).setInterpolator(DECEL);
        };
        if (instant) fill.run();
        else infoText.animate().alpha(0).translationY(-px(8)).setDuration(90).withEndAction(fill);
    }

    void backdropFor(String url, int tok) {
        image(url, BACKDROP, b -> {
            if (tok != token || b == null) return;
            showBackdrop(b);
            io.execute(() -> { Bitmap bl = blur(b); ui.post(() -> { if (tok == token) Glass.setBlur(bl); }); });
        });
    }

    /** Scroll by whole thumbs so the row always starts and ends exactly on the margins. */
    void scrollCarousel(int i) {
        if (i >= thumbs.size()) return;
        if (i < carFirst) carFirst = i;
        else if (i > carFirst + CAR_VISIBLE - 1) carFirst = i - CAR_VISIBLE + 1;
        car.animate().translationX(-carFirst * px(CW + CGAP)).setDuration(200).setInterpolator(DECEL);
        for (int k = 0; k < thumbs.size(); k++) {   // edge thumbs grow inward so the lift never crosses a margin
            View v = thumbs.get(k);
            int slot = k - carFirst;
            v.setPivotX(slot == 0 ? 0 : slot == CAR_VISIBLE - 1 ? px(CW) : px(CW) / 2f);
            v.setPivotY(px(CH) / 2f);
        }
    }

    // ---- Add to / remove from your Stremio library ---------------------------------------------------
    boolean inLibrary(String id) { JSONObject x = lib.get(id); return x != null && !x.optBoolean("removed") && !x.optBoolean("temp"); }

    void updateLibBtn() {
        if (libBtn == null) return;
        trailerBtn.setVisibility(shelf.isEmpty() || shelf.get(slide).optString("trailer").isEmpty() ? View.GONE : View.VISIBLE);
        libBtn.setVisibility(key == null || shelf.isEmpty() ? View.GONE : View.VISIBLE);
        if (shelf.isEmpty()) return;
        boolean in = inLibrary(shelf.get(slide).optString("id"));
        libIcon.kind = in ? Icon.CHECK : Icon.PLUS;
        libIcon.invalidate();
    }

    /** Removing only flags the item removed, so your watch history for it stays in your account. */
    void toggleLibrary() {
        if (key == null || shelf.isEmpty()) return;
        final JSONObject it = shelf.get(slide);
        final String id = it.optString("id");
        final boolean adding = !inLibrary(id);
        final JSONObject existing = lib.get(id);
        io.execute(() -> {
            try {
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                String now = iso.format(new java.util.Date());
                JSONObject li = existing != null ? new JSONObject(existing.toString()) : new JSONObject()
                    .put("_id", id).put("name", it.optString("name")).put("type", it.optString("type")).put("poster", it.optString("poster"))
                    .put("posterShape", "poster").put("_ctime", now)
                    .put("state", new JSONObject().put("lastWatched", JSONObject.NULL).put("timeWatched", 0).put("timeOffset", 0)
                        .put("overallTimeWatched", 0).put("timesWatched", 0).put("flaggedWatched", 0).put("duration", 0)
                        .put("video_id", JSONObject.NULL).put("watched", JSONObject.NULL).put("noNotif", false))
                    .put("behaviorHints", new JSONObject().put("defaultVideoId", JSONObject.NULL).put("featuredVideoId", JSONObject.NULL).put("hasScheduledVideos", false));
                li.put("removed", !adding).put("temp", false).put("_mtime", now);
                JSONObject r = new JSONObject(post("https://api.strem.io/api/datastorePut", new JSONObject().put("authKey", key)
                    .put("collection", "libraryItem").put("changes", new JSONArray().put(li)).toString()));
                if (r.has("error")) throw new IOException(r.optJSONObject("error") + "");
                ui.post(() -> { lib.put(id, li); updateLibBtn(); toast(adding ? "Added to your library" : "Removed from your library"); lastRefresh = 0; });
            } catch (Exception e) { ui.post(() -> toast("Couldn't reach Stremio")); }
        });
    }

    // ---- Open button: cycle through more HD backdrops of this title (TMDB) ------------------------
    final Runnable galleryStep = new Runnable() {
        @Override public void run() {
            if (!isAction(focused) || gallery.isEmpty() || stopped) return;
            galleryAt = (galleryAt + 1) % gallery.size();
            backdropFor(gallery.get(galleryAt), token);
            prefetch(gallery.get((galleryAt + 1) % gallery.size()));
            ui.postDelayed(this, GALLERY_MS);
        }
    };

    void startGallery() {
        if (tmdbKey == null || shelf.isEmpty()) return;
        final JSONObject it = shelf.get(slide);
        final int tok = token;
        io.execute(() -> {
            List<String> urls = backdrops(it);
            ui.post(() -> {
                if (tok != token || !isAction(focused) || urls.isEmpty()) return;
                gallery.clear();
                gallery.add(hd(it.optString("bg")));
                gallery.addAll(urls);
                galleryAt = 0;
                prefetch(gallery.get(1));
                ui.postDelayed(galleryStep, 1500);
            });
        });
    }

    void stopGallery() {
        ui.removeCallbacks(galleryStep);
        if (galleryAt != 0 && !shelf.isEmpty()) backdropFor(hd(shelf.get(slide).optString("bg")), token);   // back to the title's own backdrop
        galleryAt = 0;
        gallery.clear();
    }

    /** Up to 5 textless backdrops for a title from TMDB, cached per title. */
    List<String> backdrops(JSONObject it) {
        List<String> out = new ArrayList<>();
        try {
            String id = it.optString("id"), k = "bd:" + id, cached = prefs().getString(k, null);
            JSONArray paths;
            if (cached != null) paths = new JSONArray(cached);
            else {
                JSONObject f = new JSONObject(get(TMDB + "find/" + id + "?external_source=imdb_id&api_key=" + tmdbKey));
                boolean tv = "series".equals(it.optString("type"));
                JSONArray r = f.optJSONArray(tv ? "tv_results" : "movie_results");
                paths = new JSONArray();
                if (r != null && r.length() > 0) {
                    JSONArray bd = new JSONObject(get(TMDB + (tv ? "tv/" : "movie/") + r.getJSONObject(0).getInt("id")
                        + "/images?include_image_language=null&api_key=" + tmdbKey)).optJSONArray("backdrops");
                    for (int i = 0; bd != null && i < bd.length() && paths.length() < 5; i++) paths.put(bd.getJSONObject(i).getString("file_path"));
                }
                prefs().edit().putString(k, paths.toString()).apply();
            }
            for (int i = 0; i < paths.length(); i++) out.add("https://image.tmdb.org/t/p/original" + paths.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    void showBackdrop(Bitmap b) {
        ImageView back = frontA ? bgB : bgA, front = frontA ? bgA : bgB;
        frontA = !frontA;
        back.setImageBitmap(b);
        back.bringToFront();
        shade.bringToFront();
        content.bringToFront();
        for (View v : new View[] {sheet, bright, toastBox, idleView}) v.bringToFront();
        back.animate().cancel();
        back.setAlpha(0f);
        // ponytail: plain crossfade, no Ken Burns zoom; a moving full-screen bitmap redraws every frame on this GPU.
        // Free the old image only if a newer crossfade hasn't already put a backdrop into that view.
        back.animate().alpha(1).setDuration(300).withEndAction(() -> { if ((frontA ? bgA : bgB) != front) front.setImageDrawable(null); });
        if (!drawnReported) { drawnReported = true; reportFullyDrawn(); }
    }

    // ---- focus ------------------------------------------------------------------------------------
    void indexRows() {
        rows.clear();
        List<View> top = new ArrayList<>(tabViews);
        top.add(bSearch); top.add(bBt); top.add(bBright); top.add(bSettings);
        rows.add(top.toArray(new View[0]));
        if (!shelf.isEmpty()) { rows.add(new View[] {openBtn, trailerBtn, libBtn}); rows.add(thumbs.toArray(new View[0])); }
        if (!dockTiles.isEmpty()) rows.add(dockTiles.toArray(new View[0]));
        rows.addAll(gridRows);
    }

    int rowOf(View v) {
        for (int r = 0; r < rows.size(); r++) for (View x : rows.get(r)) if (x == v) return r;
        return -1;
    }

    View nearest(View[] row, View from) {
        if (row.length > 0 && thumbs.contains(row[0]) && slide < row.length) return row[slide];   // land on the title on screen
        if (row[0] == openBtn) return openBtn;   // the main action, whichever thumb you came from
        int[] a = new int[2], b = new int[2];
        from.getLocationOnScreen(a);
        float cx = a[0] + from.getWidth() / 2f, best = Float.MAX_VALUE;
        View pick = row[0];
        for (View v : row) {
            if (v.getVisibility() != View.VISIBLE) continue;
            v.getLocationOnScreen(b);
            float d = Math.abs(b[0] + v.getWidth() / 2f - cx);
            if (d < best) { best = d; pick = v; }
        }
        return pick;
    }

    void move(int dx, int dy) {
        int ri = rowOf(focused);
        if (ri < 0) return;
        if (dy != 0) {
            int t = ri + dy;
            if (t >= 0 && t < rows.size() && rows.get(t).length > 0) setFocus(nearest(rows.get(t), focused));
            return;
        }
        View[] row = rows.get(ri);
        int ci = thumbs.indexOf(focused);
        if (ci >= 0 && thumbs.size() > 1) { setFocus(thumbs.get((ci + dx + thumbs.size()) % thumbs.size())); return; }   // the carousel wraps around
        for (int i = 0; i < row.length; i++)
            if (row[i] == focused)   // step over hidden buttons (no trailer, no Stremio key)
                for (int j = i + dx; j >= 0 && j < row.length; j += dx)
                    if (row[j].getVisibility() == View.VISIBLE) { setFocus(row[j]); return; }
    }

    void setFocus(View v) {
        if (v == null) return;
        View was = focused;
        if (was != null) lit(was, false);
        focused = v;
        lit(v, true);
        if (tabViews.contains(v) && !tab.equals(v.getTag())) showTab((String) v.getTag());   // tvOS: focusing a tab switches it
        int ti = thumbs.indexOf(v);
        if (ti >= 0 && ti != slide) showSlide(ti, false);
        boolean onActions = isAction(v), wasActions = isAction(was);
        if (onActions && !wasActions) startGallery();
        else if (!onActions && wasActions) stopGallery();
        int gr = -1;
        for (int i = 0; i < gridRows.size(); i++) for (View x : gridRows.get(i)) if (x == v) gr = i;
        car.animate().alpha(onActions || gr >= 0 ? 0f : ti >= 0 ? 1f : .8f).setDuration(220);   // on the buttons or in the grid, the carousel steps aside
        if (gr != liftRow) {   // going down into the grid lifts everything so that row sits mid-screen, and dims the shelf
            liftRow = gr;
            content.animate().translationY(gr < 0 ? 0 : -px(gridLift(gr))).setDuration(320).setInterpolator(DECEL);
            info.animate().alpha(gr >= 0 || shelf.isEmpty() ? 0 : 1).setDuration(200);
        }
        boolean onDock = dockTiles.contains(v);
        if (onDock != dockFocus) {   // tvOS: focusing the dock lifts it a touch and dims what's behind
            dockFocus = onDock;
            dock.animate().scaleX(onDock ? 1.04f : 1f).scaleY(onDock ? 1.04f : 1f).setDuration(240).setInterpolator(DECEL);
        }
        shade.animate().alpha(shadeLevel()).setDuration(260);
    }

    /** Going into the grid moves the dock up to the top of the screen; lower rows scroll further only if they would fall off the bottom. */
    static float gridLift(int row) {
        float lift = DOCK_TOP - DOCK_AT_TOP, rowBottom = GRID_TOP + row * PITCH + TH - lift;
        return lift + Math.max(0, rowBottom - (1080 - 64));
    }

    boolean dockFocus;
    float shadeLevel() { return liftRow >= 0 ? .55f : dockFocus ? .41f : 0f; }

    // ---- rearranging the dock: hold OK on an app, move it with left/right, press OK (or Back) to drop it ----
    Tile moving;

    boolean isDockApp(View v) { return v instanceof Tile && dockTiles.contains(v) && v != cont; }

    void startMove() {
        mode = MOVE;
        moving = (Tile) focused;
        moving.animate().scaleX(1.16f).scaleY(1.16f).translationY(-px(12)).setInterpolator(OVERSHOOT).setDuration(200);
        for (View t : dockTiles) if (t != moving) t.animate().alpha(.55f).setDuration(200);
        toast("Move it with \u25C0 \u25B6, then press OK");
    }

    void swap(int dir) {
        int i = dockTiles.indexOf(moving), j = i + dir;
        if (j < 0 || j >= dockTiles.size() || dockTiles.get(j) == cont) return;
        View other = dockTiles.get(j);
        ViewGroup row = (ViewGroup) moving.getParent();
        row.removeView(moving);
        row.addView(moving, j);   // keeps its layout params
        java.util.Collections.swap(dockTiles, i, j);
        float step = px(TW + GAP);   // slide both from where they were into their new slots
        moving.setTranslationX(-dir * step);
        other.setTranslationX(dir * step);
        moving.animate().translationX(0).setDuration(180).setInterpolator(DECEL);
        other.animate().translationX(0).setDuration(180).setInterpolator(DECEL);
        indexRows();
    }

    void endMove() {
        mode = MAIN;
        for (View t : dockTiles) t.animate().alpha(1).setDuration(160);
        moving.animate().translationY(0).setDuration(160);
        lit(moving, true);
        List<String> order = new ArrayList<>();
        for (View t : dockTiles) if (t != cont) order.add((String) t.getTag());
        getSharedPreferences("dock", MODE_PRIVATE).edit().putString("order", TextUtils.join(",", order)).apply();
        moving = null;
        hideToast.run();
    }

    boolean isAction(View v) { return v != null && (v == openBtn || v == trailerBtn || v == libBtn); }

    void styleTab(TextView v, boolean focus) {
        boolean sel = tab.equals(v.getTag());
        v.setBackground(focus ? round(WHITE, px(26)) : sel ? round(0x40FFFFFF, px(26)) : null);
        v.setTextColor(focus ? INK : sel ? WHITE : 0xB3FFFFFF);
    }

    void lit(View v, boolean on) {
        if (v instanceof Tile) { ((Tile) v).focus(on); return; }
        if (v instanceof Glass) {
            Glass g = (Glass) v;
            g.lit = on;
            g.invalidate();
            tint(g, on ? INK : WHITE);
        } else if (tabViews.contains(v)) styleTab((TextView) v, on);
        else if (v instanceof TextView) {
            v.setBackground(on ? round(WHITE, px(22)) : null);
            ((TextView) v).setTextColor(on ? INK : WHITE);
        }
        float sc = on ? (v instanceof TextView ? 1.04f : 1.08f) : 1f;
        v.animate().scaleX(sc).scaleY(sc).setInterpolator(on ? OVERSHOOT : DECEL).setDuration(on ? 180 : 120);
    }

    void home() {
        if (mode == MOVE) endMove();
        closeBright();
        closeSheet();
        setFocus(dockTiles.isEmpty() ? bSearch : dockTiles.get(0));
    }

    // ---- sheet, brightness, toast -----------------------------------------------------------------
    void openSheet() {
        mode = SHEET;
        sheetReturn = focused;
        lit(focused, false);
        shade.animate().alpha(.6f).setDuration(250);
        sheet.animate().translationX(0).setInterpolator(new OvershootInterpolator(.7f)).setDuration(360);
        focused = items.getChildAt(0);
        lit(focused, true);
    }

    void closeSheet() {
        if (mode != SHEET) return;
        mode = MAIN;
        lit(focused, false);
        focused = null;
        sheet.animate().translationX(px(720)).setInterpolator(DECEL).setDuration(260);
        shade.animate().alpha(shadeLevel()).setDuration(260);
        setFocus(sheetReturn);
    }

    void openBright() {
        mode = BRIGHT;
        showBrightness(Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 102));
        bright.animate().alpha(1).scaleX(1).scaleY(1).setInterpolator(OVERSHOOT).setDuration(260);
    }

    void closeBright() {
        if (mode != BRIGHT) return;
        mode = MAIN;
        bright.animate().alpha(0).scaleX(.9f).scaleY(.9f).setInterpolator(DECEL).setDuration(180);
    }

    /** System brightness 10-255 drives this TV's backlight. Writing needs WRITE_SETTINGS (granted by set-home.ps1). */
    void stepBrightness(int delta) {
        int v = Math.max(0, Math.min(100, pctNow + delta)), raw = Math.round(10 + v * 2.45f);
        if (Settings.System.canWrite(this) && Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, raw)) showBrightness(raw);
        else toast("Brightness needs a one-time permission over ADB");
    }

    void showBrightness(int raw) {
        pctNow = Math.round((Math.max(10, raw) - 10) / 2.45f);
        bar.set(pctNow / 100f);
        pct.setText(pctNow + "%");
    }

    final Runnable hideToast = () -> toastBox.animate().alpha(0).setDuration(300);

    void toast(String m) {
        toastText.setText(m);
        toastBox.animate().alpha(1).setDuration(300);
        ui.removeCallbacks(hideToast);
        ui.postDelayed(hideToast, 2600);
    }

    // ---- keys, idle, drift, lifecycle -------------------------------------------------------------
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean nav = ok || k == KeyEvent.KEYCODE_BACK || (k >= KeyEvent.KEYCODE_DPAD_UP && k <= KeyEvent.KEYCODE_DPAD_RIGHT);
        if (!nav) return super.dispatchKeyEvent(e);   // volume, media, voice: untouched
        if (ok && mode == MAIN) {   // OK acts on release, so holding it can mean "rearrange" instead
            if (e.getAction() == KeyEvent.ACTION_UP) {
                boolean fire = okDown && !longFired;
                okDown = false;
                if (fire && focused instanceof Glass && ((Glass) focused).action != null) ((Glass) focused).action.run();
                return true;
            }
            if (e.getRepeatCount() == 0) { okDown = !wake(); longFired = false; return true; }
            if (okDown && !longFired && isDockApp(focused)) { longFired = true; startMove(); }
            return true;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (wake()) return true;   // first press only wakes the dimmed screen
        if (mode == MOVE) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) swap(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
            else if (ok || k == KeyEvent.KEYCODE_BACK) endMove();
            return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {   // a home screen never exits
            if (mode == BRIGHT) closeBright(); else if (mode == SHEET) closeSheet(); else home();
            return true;
        }
        if (mode == BRIGHT) {
            if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) stepBrightness(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 10 : -10);
            else closeBright();
            return true;
        }
        if (mode == SHEET) {
            int i = items.indexOfChild(focused);
            if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                View n = items.getChildAt(i + (k == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1));
                if (n != null) { lit(focused, false); focused = n; lit(n, true); }
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT) closeSheet();
            else if (ok) { String a = (String) focused.getTag(); closeSheet(); start(new Intent(a)); }
            return true;
        }
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) move(-1, 0);
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) move(1, 0);
        else if (k == KeyEvent.KEYCODE_DPAD_UP) move(0, -1);
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) move(0, 1);
        return true;
    }

    boolean okDown, longFired;

    final Runnable goIdle = () -> { idle = true; idleView.animate().alpha(.85f).setDuration(3000); };   // burn-in care

    boolean wake() {
        boolean was = idle;
        idle = false;
        if (idleView.getAlpha() > 0) idleView.animate().alpha(0).setDuration(300);
        ui.removeCallbacks(goIdle);
        ui.postDelayed(goIdle, IDLE_MS);
        return was;
    }

    final Random rnd = new Random();
    final Runnable drift = new Runnable() {   // burn-in care: shift everything a few px every few minutes
        @Override public void run() {
            root.animate().translationX(px(rnd.nextInt(13) - 6)).translationY(px(rnd.nextInt(13) - 6)).setDuration(8000);
            ui.postDelayed(this, DRIFT_MS);
        }
    };

    final Runnable tick = new Runnable() {
        @Override public void run() {
            Calendar c = Calendar.getInstance();
            int h = c.get(Calendar.HOUR_OF_DAY);
            String greet = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
            clock.setText(greet + "  ·  " + new SimpleDateFormat("HH:mm", Locale.US).format(c.getTime()));   // 24-hour
            ui.postDelayed(this, 10_000);
        }
    };

    boolean released;

    @Override protected void onStart() {
        super.onStart();
        stopped = false;
        tick.run();
        wake();
        ui.postDelayed(drift, DRIFT_MS);
        if (released) {   // coming back: the blurred thumbnail shows at once, the HD backdrop (on disk) fades in over it
            released = false;
            if (Glass.blur != null) { (frontA ? bgA : bgB).setImageBitmap(Glass.blur); (frontA ? bgA : bgB).setAlpha(1f); }
            loadThumbs();
            if (resume != null) setContinue(resume);
            showSlide(slide, true);
        }
        refresh();
    }

    @Override protected void onStop() {   // nothing runs while another app is in front
        super.onStop();
        stopped = true;
        for (Runnable r : new Runnable[] {tick, drift, goIdle, galleryStep}) ui.removeCallbacks(r);
    }

    /** Hidden: drop every big image so the app in front gets the memory (and the GPU its textures). Banners stay; they're tiny. */
    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || released) return;
        released = true;
        ui.removeCallbacks(galleryStep);   // not stopGallery(): that would reload a backdrop while hidden
        gallery.clear();
        galleryAt = 0;
        for (ImageView v : new ImageView[] {bgA, bgB, logo, contImg}) if (v != null) v.setImageDrawable(null);
        for (View t : thumbs) ((ImageView) ((ViewGroup) t).getChildAt(0)).setImageDrawable(null);
        mem.evictAll();
        Runtime.getRuntime().gc();   // bitmap memory is freed when the Java objects are collected; the heap is tiny, so this is quick
    }

    void loadThumbs() {
        for (int i = 0; i < thumbs.size() && i < shelf.size(); i++) {
            ImageView iv = (ImageView) ((ViewGroup) thumbs.get(i)).getChildAt(0);
            image(shelf.get(i).optString("thumb"), THUMB, b -> { if (b != null) iv.setImageBitmap(b); });
        }
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); wake(); home(); }   // Home pressed

    // ---- TV bridges -------------------------------------------------------------------------------
    Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    void start(Intent i) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception e) { toast("Couldn't open that"); }
    }

    void open(String uri, String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg);
        if (i.resolveActivity(getPackageManager()) != null) start(i);
        else { Intent l = launchIntent(pkg); if (l != null) start(l); else toast("That app isn't installed"); }
    }

    /** HDMI ports as {id, name, subtitle}. A CEC device on a port replaces its id and names it. */
    List<String[]> inputs() {
        List<String[]> out = new ArrayList<>();
        try {
            TvInputManager tm = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
            List<TvInputInfo> all = tm.getTvInputList();
            for (TvInputInfo i : all) {
                if (i.getType() != TvInputInfo.TYPE_HDMI || i.getParentId() != null || i.isHidden(this)) continue;
                String id = i.getId(), name = label(i), sub = null;
                int st = tm.getInputState(id);
                for (TvInputInfo c : all)
                    if (i.getId().equals(c.getParentId())) { sub = name; id = c.getId(); name = label(c); st = tm.getInputState(id); }
                out.add(new String[] {id, name, sub != null ? sub : st == TvInputManager.INPUT_STATE_CONNECTED ? "Connected" : "No device"});
            }
            out.sort((a, b) -> a[1].compareTo(b[1]));
        } catch (Exception ignored) { }
        return out;
    }

    String label(TvInputInfo i) {
        CharSequence c = i.loadCustomLabel(this);
        return String.valueOf(c != null && c.length() > 0 ? c : i.loadLabel(this));
    }

    String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            String v = read(in).trim();
            return v.isEmpty() ? null : v;
        } catch (IOException e) { return null; }
    }

    // ---- images: memory + disk cache -----------------------------------------------------------------
    /** Cinemeta hands out 1280px backdrops; metahub's "large" is 3000px, so ask for that and scale to the screen. */
    static String hd(String bg) { return bg.replace("/background/medium/", "/background/large/").replace("/background/small/", "/background/large/"); }

    /**
     * BACKDROP: the original (3000px metahub / 4K TMDB) is scaled once to exactly the screen width and re-saved at that size
     * (so later decodes are small and fast), held in full 8-bit colour with the shading painted in. LOGO: PNG trimmed to
     * its visible pixels. THUMB: downsampled on decode for small tiles. A null callback only prefetches to disk.
     */
    void image(String url, int kind, Consumer<Bitmap> cb) {
        if (url == null || url.isEmpty()) { if (cb != null) cb.accept(null); return; }
        Bitmap hit = mem.get(url);
        if (hit != null) { if (cb != null) cb.accept(hit); return; }
        (kind == BACKDROP ? big : io).execute(() -> load(url, kind, cb));
    }

    /** The loader itself, run on whichever background thread called it. One URL is processed by one thread at a time. */
    void load(String url, int kind, Consumer<Bitmap> cb) {
        Bitmap b = null;
        synchronized (url.intern()) {
            try {
                File f = new File(getCacheDir(), "img" + Integer.toHexString(url.hashCode()));
                if (!f.exists()) {
                    File tmp = new File(f.getPath() + ".part");
                    download(url, tmp);
                    if (!tmp.renameTo(f)) throw new IOException("rename");
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                o.inJustDecodeBounds = false;
                if (kind == BACKDROP) {
                    int sw = getResources().getDisplayMetrics().widthPixels;
                    if (o.outWidth > sw) {   // first time only: sample down by whole powers of two, scale the rest, re-save
                        BitmapFactory.Options so = new BitmapFactory.Options();
                        so.inSampleSize = 1;
                        while (o.outWidth / (so.inSampleSize * 2) >= sw) so.inSampleSize *= 2;
                        Bitmap full = BitmapFactory.decodeFile(f.getPath(), so);
                        Bitmap sc = Bitmap.createBitmap(sw, Math.round(full.getHeight() * sw / (float) full.getWidth()), Bitmap.Config.ARGB_8888);
                        new Canvas(sc).drawBitmap(full, null, new Rect(0, 0, sc.getWidth(), sc.getHeight()), new Paint(Paint.FILTER_BITMAP_FLAG));
                        full.recycle();
                        File tmp = new File(f.getPath() + ".part");
                        try (OutputStream out = new FileOutputStream(tmp)) { sc.compress(Bitmap.CompressFormat.JPEG, 93, out); }
                        tmp.renameTo(f);
                        sc.recycle();
                    }
                    if (cb == null) return;   // prefetch: it's on disk at screen size now
                    o.inMutable = true;
                    b = BitmapFactory.decodeFile(f.getPath(), o);
                    if (b != null) { shadeBackdrop(b); b.prepareToDraw(); }   // start the GPU upload now, not on the frame that shows it
                } else if (kind == THUMB) {
                    o.inSampleSize = 1;
                    while (o.outWidth / (o.inSampleSize * 2) >= px(TW) * 1.1f) o.inSampleSize *= 2;
                    b = BitmapFactory.decodeFile(f.getPath(), o);
                } else {
                    b = BitmapFactory.decodeFile(f.getPath());
                    if (b != null) b = trim(b);
                }
                if (b != null) mem.put(url, b);
            } catch (Exception ignored) { }
        }
        if (cb == null) return;
        final Bitmap r = b;
        ui.post(() -> cb.accept(r));
    }

    /**
     * Download an HD backdrop to disk on the low-priority thread so showing it later skips the network. Download only:
     * decoding a 3000-4000px original here as well as on the backdrop thread would double the memory peak.
     */
    void prefetch(String url) {
        if (url == null || url.isEmpty()) return;
        File f = new File(getCacheDir(), "img" + Integer.toHexString(url.hashCode()));
        if (f.exists()) return;
        pre.execute(() -> {
            synchronized (url.intern()) {
                if (f.exists()) return;
                try { File tmp = new File(f.getPath() + ".part"); download(url, tmp); tmp.renameTo(f); } catch (IOException ignored) { }
            }
        });
    }

    /** Paint the legibility gradients into the backdrop itself (top-left for the text, bottom for carousel and dock). */
    static void shadeBackdrop(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setDither(true);
        // drawn over the whole frame so the gradients fade out with no edge
        p.setShader(new RadialGradient(0, h * .25f, w * .65f, 0xA6000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * .5f, 0, h, 0x00000000, 0xC0000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * .28f, 0x80000000, 0x00000000, Shader.TileMode.CLAMP));   // tabs and clock on bright art
        c.drawRect(0, 0, w, h, p);
    }

    /** Crop away fully transparent margins (most logo PNGs ship with padding). */
    static Bitmap trim(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), l = w, t = h, r = -1, btm = -1;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((px[y * w + x] >>> 24) > 12) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y; }
        if (r < 0 || (l == 0 && t == 0 && r == w - 1 && btm == h - 1)) return b;
        return Bitmap.createBitmap(b, l, t, r - l + 1, btm - t + 1);
    }

    /**
     * Draw an app banner or icon into exactly w x h with its logo centred. On a flat background (Spotify, Netflix, ...)
     * the logo is found by colour, cropped and re-centred with even margins; anything else is scaled to fit, uncropped.
     */
    static Bitmap centered(Drawable d, int w, int h) {
        int iw = d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : w, ih = d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : h;
        int sw = Math.max(w, Math.min(iw, 2 * w)), sh = Math.max(1, Math.round(sw * ih / (float) iw));
        Bitmap src = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, sw, sh);
        d.draw(new Canvas(src));
        int[] px = new int[sw * sh];
        src.getPixels(px, 0, sw, 0, 0, sw, sh);
        int bg = px[sw + 1];
        boolean flat = near(px[2 * sw - 2], bg) && near(px[(sh - 2) * sw + 1], bg) && near(px[(sh - 1) * sw - 2], bg);
        int l = sw, t = sh, r = -1, btm = -1;
        if (flat) for (int y = 0; y < sh; y++) for (int x = 0; x < sw; x++)
            if (!near(px[y * sw + x], bg)) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > btm) btm = y; }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        Rect from;
        float boxW, boxH;
        if (flat && r > l && btm > t && (r - l) * (btm - t) < .85f * sw * sh) {
            c.drawColor(bg);
            from = new Rect(l, t, r + 1, btm + 1);
            boxW = w * .74f; boxH = h * .56f;   // even margins around the logo
        } else {
            from = new Rect(0, 0, sw, sh);
            boxW = w; boxH = h;
        }
        float k = Math.min(boxW / from.width(), boxH / from.height());
        float dw = from.width() * k, dh = from.height() * k;
        c.drawBitmap(src, from, new RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), p);
        src.recycle();
        return out;
    }

    static boolean near(int a, int b) {
        return Math.abs((a >>> 24) - (b >>> 24)) + Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
            + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)) + Math.abs((a & 255) - (b & 255)) < 60;
    }

    void trimDiskCache() {   // ponytail: age-based only; Android also clears cacheDir under storage pressure
        File[] fs = getCacheDir().listFiles();
        if (fs == null) return;
        long old = System.currentTimeMillis() - 7 * 86_400_000L;
        for (File f : fs) if (f.getName().startsWith("img") && f.lastModified() < old) f.delete();
    }

    static HttpURLConnection connect(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        return c;
    }

    static String get(String u) throws IOException {
        HttpURLConnection c = connect(u);
        try (InputStream in = c.getInputStream()) { return read(in); } finally { c.disconnect(); }
    }

    static String post(String u, String json) throws IOException {
        HttpURLConnection c = connect(u);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = c.getOutputStream()) { o.write(json.getBytes("UTF-8")); }
        try (InputStream in = c.getInputStream()) { return read(in); } finally { c.disconnect(); }
    }

    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        copy(in, o);
        return o.toString("UTF-8");
    }

    static void download(String u, File to) throws IOException {
        HttpURLConnection c = connect(u);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) { copy(in, out); }
        finally { c.disconnect(); }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
    }

    /** 160x90 box-blurred copy of the backdrop; every glass surface samples it. Cheap enough to redo per backdrop. */
    static Bitmap blur(Bitmap src) {
        int w = 160, h = 90;
        int[] a = new int[w * h], t = new int[w * h];
        Bitmap.createScaledBitmap(src, w, h, true).getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) { box(a, t, w, h, 4, true); box(t, a, w, h, 4, false); }
        return Bitmap.createBitmap(a, w, h, Bitmap.Config.ARGB_8888);
    }

    static void box(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rs = 0, gs = 0, bs = 0;
                for (int k = -r; k <= r; k++) {
                    int c = horiz ? in[y * w + Math.min(w - 1, Math.max(0, x + k))] : in[Math.min(h - 1, Math.max(0, y + k)) * w + x];
                    rs += (c >> 16) & 255; gs += (c >> 8) & 255; bs += c & 255;
                }
                int n = 2 * r + 1;
                out[y * w + x] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
            }
    }

    // ---- view helpers -----------------------------------------------------------------------------
    ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(match());
        return v;
    }

    static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    TextView text(float size, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    Glass circle(int icon, Runnable action) {
        Glass g = new Glass(this, px(32), 0x26FFFFFF);
        g.addView(new Icon(this, icon), new FrameLayout.LayoutParams(px(28), px(28), Gravity.CENTER));
        g.action = action;
        return g;
    }

    Glass pill(int icon, String label, float h) {
        Glass g = new Glass(this, px(h / 2), 0x2EFFFFFF);
        LinearLayout row = hbox();
        row.setPadding(px(28), 0, px(32), 0);
        row.addView(new Icon(this, icon), lp(px(24), px(24)));
        TextView t = text(27, 600, WHITE);
        t.setText(label);
        row.addView(t, margins(lp(-2, -2), 14, 0, 0, 0));
        g.addView(row, new FrameLayout.LayoutParams(-2, -1));
        return g;
    }

    static void tint(View v, int color) {
        if (v instanceof Icon) ((Icon) v).setColor(color);
        else if (v instanceof TextView) ((TextView) v).setTextColor(color);
        else if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) tint(((ViewGroup) v).getChildAt(i), color);
    }

    LinearLayout vbox() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); noClip(l); return l; }

    LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        noClip(l);
        return l;
    }

    static void noClip(ViewGroup g) { g.setClipChildren(false); g.setClipToPadding(false); }
    static FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, float l, float t, float r, float b) {
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    FrameLayout.LayoutParams at(int w, int h, int gravity, float l, float t, float r, float b) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, gravity);
        p.setMargins(px(l), px(t), px(r), px(b));
        return p;
    }

    // ---- views ------------------------------------------------------------------------------------
    /** Liquid Glass: the blurred backdrop behind it plus a tint. No edge line, no shadow. White when lit (focused). */
    static class Glass extends FrameLayout {
        static final List<Glass> all = new ArrayList<>();
        static Bitmap blur;
        float r;
        final int tint;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Matrix m = new Matrix();
        final int[] loc = new int[2];
        final RectF box = new RectF();
        boolean lit, opaque;   // opaque: artwork covers it, so skip drawing glass nobody sees
        Runnable action;

        Glass(Context c, float radius, int tint) {
            super(c);
            r = radius;
            this.tint = tint;
            setWillNotDraw(false);
            noClip(this);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(r, v.getHeight() / 2f)); }
            });
            setClipToOutline(true);   // no elevation: no shadows to render
            if (blur != null) fill.setShader(new BitmapShader(blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            all.add(this);
        }

        static void setBlur(Bitmap b) {
            blur = b;
            for (Glass g : all) { g.fill.setShader(new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)); if (!g.opaque) g.invalidate(); }
        }

        @Override protected void onDraw(Canvas c) {
            box.set(0, 0, getWidth(), getHeight());
            float rr = Math.min(r, getHeight() / 2f);
            if (lit) { solid.setColor(0xF2FFFFFF); c.drawRoundRect(box, rr, rr, solid); return; }
            if (opaque) return;
            if (blur != null && fill.getShader() != null) {
                getLocationOnScreen(loc);
                float k = (float) getRootView().getWidth() / blur.getWidth();
                m.setScale(k, k);
                m.postTranslate(-loc[0], -loc[1]);
                fill.getShader().setLocalMatrix(m);
                c.drawRoundRect(box, rr, rr, fill);
            }
            solid.setColor(tint);
            c.drawRoundRect(box, rr, rr, solid);
        }
    }

    /** App / title tile: focus lifts it with a quick springy scale and one sheen sweep. No edge, no shadow, no looping motion. */
    class Tile extends Glass {
        final View sheen = new View(Main.this);
        float grow = 1.1f;

        Tile(float radius) {
            super(Main.this, radius, 0x2EFFFFFF);
            sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {0x00FFFFFF, 0x40FFFFFF, 0x00FFFFFF}));
            sheen.setTranslationX(-10_000);
            addView(sheen, match());
        }

        void focus(boolean on) {
            animate().scaleX(on ? grow : 1f).scaleY(on ? grow : 1f).setInterpolator(on ? OVERSHOOT : DECEL).setDuration(on ? 160 : 110);
            if (!on || stopped) return;
            sheen.setTranslationX(-getWidth());
            sheen.animate().translationX(getWidth()).setStartDelay(40).setDuration(550);
        }
    }

    /** Line icons drawn on a 24-unit grid, so no image assets ship with the app. */
    static class Icon extends View {
        static final int SEARCH = 0, SUN = 1, GEAR = 2, HDMI = 3, PLAY = 4, BLUETOOTH = 5, PLUS = 6, CHECK = 7, TRAILER = 8;
        int kind;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();

        Icon(Context c, int kind) {
            super(c);
            this.kind = kind;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(WHITE);
        }

        void setColor(int c) { p.setColor(c); invalidate(); }

        void rays(Canvas c, float from, float to) {
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                c.drawLine((float) (12 + from * Math.cos(a)), (float) (12 + from * Math.sin(a)), (float) (12 + to * Math.cos(a)), (float) (12 + to * Math.sin(a)), p);
            }
        }

        @Override protected void onDraw(Canvas c) {
            float u = getWidth() / 24f;
            c.scale(u, u);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            path.reset();
            switch (kind) {
                case SEARCH: c.drawCircle(10.5f, 10.5f, 6.5f, p); c.drawLine(15.5f, 15.5f, 21, 21, p); break;
                case SUN:
                    c.drawCircle(12, 12, 4.5f, p);
                    rays(c, 7.5f, 10f);
                    p.setStyle(Paint.Style.FILL);
                    c.drawArc(7.5f, 7.5f, 16.5f, 16.5f, -90, 180, true, p);
                    break;
                case GEAR:
                    c.drawCircle(12, 12, 6.5f, p);
                    c.drawCircle(12, 12, 2.6f, p);
                    p.setStrokeWidth(3.2f);
                    rays(c, 7.6f, 9.6f);
                    break;
                case HDMI:
                    path.moveTo(3, 8.5f); path.lineTo(21, 8.5f); path.lineTo(21, 13); path.lineTo(18, 16); path.lineTo(6, 16); path.lineTo(3, 13); path.close();
                    c.drawPath(path, p);
                    c.drawLine(7, 11.5f, 17, 11.5f, p);
                    break;
                case PLAY:
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(7, 4.5f); path.lineTo(7, 19.5f); path.lineTo(20, 12); path.close();
                    c.drawPath(path, p);
                    break;
                case TRAILER:   // a frame with a play symbol
                    c.drawRoundRect(3, 5.5f, 21, 18.5f, 3, 3, p);
                    p.setStyle(Paint.Style.FILL);
                    path.moveTo(10, 9); path.lineTo(10, 15); path.lineTo(15.5f, 12); path.close();
                    c.drawPath(path, p);
                    break;
                case PLUS: p.setStrokeWidth(2.4f); c.drawLine(12, 5, 12, 19, p); c.drawLine(5, 12, 19, 12, p); break;
                case CHECK: p.setStrokeWidth(2.4f); path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19, 7); c.drawPath(path, p); break;
                case BLUETOOTH:   // the rune: a vertical stroke with two arrowheads
                    path.moveTo(6.5f, 7.5f); path.lineTo(17, 16.5f); path.lineTo(12, 21); path.lineTo(12, 3); path.lineTo(17, 7.5f); path.lineTo(6.5f, 16.5f);
                    c.drawPath(path, p);
                    break;
            }
        }
    }

    /** Brightness bar with an animated fill. */
    static class Bar extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF box = new RectF();
        float f;

        Bar(Context c) { super(c); }

        void set(float to) {
            ValueAnimator a = ValueAnimator.ofFloat(f, to);
            a.setDuration(240);
            a.addUpdateListener(v -> { f = (float) v.getAnimatedValue(); invalidate(); });
            a.start();
        }

        @Override protected void onDraw(Canvas c) {
            float h = getHeight();
            box.set(0, 0, getWidth(), h);
            p.setColor(0x40FFFFFF);
            c.drawRoundRect(box, h / 2, h / 2, p);
            box.right = Math.max(h, getWidth() * f);
            p.setColor(WHITE);
            c.drawRoundRect(box, h / 2, h / 2, p);
        }
    }
}
