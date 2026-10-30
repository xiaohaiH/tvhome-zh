package com.gathin.tvhome;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.PopupWindow;

import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowNetworkInfo;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** JVM regression checks, not a substitute for testing a vendor's Dalvik/TV firmware. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {19, 21, 28}, qualifiers = "land-xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class KitKatCompatibilityTest {
    @Test
    public void homeCanStartStopRestartAndDestroy() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertNotNull(controller.get().findViewById(R.id.grid));
            controller.pause().stop().start().resume();
            assertNotNull(controller.get().findViewById(R.id.btnInputs));
        }
    }

    @Test
    public void connectedEthernetIsNotReportedOffline() {
        ConnectivityManager cm = (ConnectivityManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        shadowOf(cm).setActiveNetworkInfo(ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_ETHERNET,
                0, true, true));
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            TextView label = controller.get().findViewById(R.id.btnWifi);
            assertEquals(controller.get().getString(R.string.ethernet), label.getText().toString());
        }
    }

    @Test
    public void settingsPopupAndInputFallbackDoNotCallUnavailableApis() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity home = controller.get();
            View panel = home.getLayoutInflater().inflate(R.layout.popup_inputs, home.root, false);
            assertNotNull(home.glassPanel(panel, home.findViewById(R.id.btnInputs), 150));
            if (Build.VERSION.SDK_INT < 21) {
                home.findViewById(R.id.btnInputs).performClick();
                assertEquals(home.getString(R.string.no_inputs), ShadowToast.getTextOfLatestToast());
            }
        }
    }

    @Test
    public void quickSettingsAndScreenSaverResourcesInflate() {
        try (ActivityController<QuickSettingsActivity> controller =
                     Robolectric.buildActivity(QuickSettingsActivity.class).setup()) {
            assertNotNull(controller.get().findViewById(R.id.rows));
            assertNotNull(controller.get().getLayoutInflater().inflate(R.layout.dream_clock, null));
        }
    }

    @Test
    public void cardsAndGlassCanBeDrawnWithoutApi21Overloads() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            MainActivity home = controller.get();
            View item = home.getLayoutInflater().inflate(R.layout.item_app, new FrameLayout(home), false);
            AppAdapter.Holder holder = new AppAdapter.Holder(item, null, 4);
            assertTrue(holder.card instanceof RoundedCardLayout);
            holder.card.layout(0, 0, 360, 202);
            Bitmap cardBitmap = Bitmap.createBitmap(360, 202, Bitmap.Config.ARGB_8888);
            holder.card.draw(new Canvas(cardBitmap));
            cardBitmap.recycle();
            GlassView glass = new GlassView(home, null);
            glass.layout(0, 0, 400, 250);
            Bitmap bitmap = Bitmap.createBitmap(400, 250, Bitmap.Config.ARGB_8888);
            glass.draw(new Canvas(bitmap));
            bitmap.recycle();
        }
    }

    @Test
    public void everyIconHasAnInflatableFallback() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        for (java.lang.reflect.Field field : R.drawable.class.getFields()) {
            if (!field.getName().startsWith("ic_")) continue;
            android.graphics.drawable.Drawable icon = app.getResources().getDrawable(field.getInt(null));
            assertNotNull(field.getName(), icon);
            if (Build.VERSION.SDK_INT < 21) {
                assertTrue(field.getName(), icon instanceof android.graphics.drawable.BitmapDrawable);
            }
        }
    }

    @Test
    public void allPopupLayoutsInflate() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            int[] layouts = {R.layout.popup_wallpaper, R.layout.popup_dock, R.layout.popup_library,
                    R.layout.popup_city, R.layout.popup_weather, R.layout.item_day, R.layout.item_hour,
                    R.layout.item_dock, R.layout.item_city, R.layout.item_place};
            for (int layout : layouts) {
                assertNotNull(controller.get().getLayoutInflater().inflate(layout, controller.get().root, false));
            }
        }
    }

    // Intent category strings can be declared and queried even before Android exposes a constant.
    private static final String TV_CATEGORY = "android.intent.category.LEANBACK_LAUNCHER";

    @Test
    public void desktopDefaultsToTelevisionEntriesOnEveryApi() {
        installLauncherApp("example.ordinary", "Ordinary app", Intent.CATEGORY_LAUNCHER);
        ComponentName tv = installLauncherApp("example.television", "TV app", TV_CATEGORY);
        AppAdapter adapter = new AppAdapter(RuntimeEnvironment.getApplication(), null, 4, Collections.emptyList());
        adapter.reload();
        assertEquals(1, adapter.getItemCount());
        assertEquals(tv, adapter.items().get(0).component);
        assertTrue(adapter.items().get(0).tv);
        assertNull(adapter.items().get(0).banner); // A TV entry does not require a TV banner.
    }

    @Test
    public void noTelevisionEntriesDoesNotImportOrdinaryApps() {
        installLauncherApp("example.ordinary", "Ordinary app", Intent.CATEGORY_LAUNCHER);
        AppAdapter adapter = new AppAdapter(RuntimeEnvironment.getApplication(), null, 4, Collections.emptyList());
        adapter.reload();
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void ordinaryAppsRequireExplicitLibrarySelection() {
        Application app = RuntimeEnvironment.getApplication();
        ComponentName ordinary = installLauncherApp("example.ordinary", "Ordinary app", Intent.CATEGORY_LAUNCHER);
        LibraryApps library = new LibraryApps(app);
        assertTrue(library.add(ordinary.flattenToString()));
        AppAdapter adapter = new AppAdapter(app, null, 4, Collections.emptyList());
        adapter.reload();
        assertEquals(1, adapter.getItemCount());
        assertEquals(ordinary, adapter.items().get(0).component);
        assertFalse(adapter.items().get(0).tv);
        AppAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(app), 0);
        adapter.onBindViewHolder(holder, 0);
        holder.card.performClick();
        Intent launched = shadowOf(app).getNextStartedActivity();
        assertNotNull(launched);
        assertTrue(launched.hasCategory(Intent.CATEGORY_LAUNCHER));
        assertEquals(ordinary, launched.getComponent());

        library.remove(ordinary.flattenToString());
        adapter.reload();
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void televisionAppsKeepTheirTelevisionLaunchCategory() {
        Application app = RuntimeEnvironment.getApplication();
        ComponentName tv = installLauncherApp("example.television", "TV app", TV_CATEGORY);
        AppAdapter adapter = new AppAdapter(app, null, 4, Collections.emptyList());
        adapter.reload();
        assertEquals(1, adapter.getItemCount());
        AppAdapter.Holder holder = adapter.onCreateViewHolder(new FrameLayout(app), 0);
        adapter.onBindViewHolder(holder, 0);
        holder.card.performClick();
        Intent launched = shadowOf(app).getNextStartedActivity();
        assertNotNull(launched);
        assertTrue(launched.hasCategory(TV_CATEGORY));
        assertEquals(tv, launched.getComponent());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void favouritesOnlyListTvAppsAndExplicitLibrarySelections() {
        Application app = RuntimeEnvironment.getApplication();
        installLauncherApp("example.ordinary", "Ordinary app", Intent.CATEGORY_LAUNCHER);
        installLauncherApp("example.television", "TV app", TV_CATEGORY);
        ComponentName selected = installLauncherApp("example.selected", "Selected app", Intent.CATEGORY_LAUNCHER);
        new LibraryApps(app).add(selected.flattenToString());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            controller.get().findViewById(R.id.btnDock).performClick();
            PopupWindow popup = ReflectionHelpers.getField(controller.get(), "dockPicker");
            RecyclerView list = popup.getContentView().findViewById(R.id.rows);
            RecyclerView.Adapter<MainActivity.DockHolder> adapter =
                    (RecyclerView.Adapter<MainActivity.DockHolder>) list.getAdapter();
            Set<String> labels = new HashSet<>();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                MainActivity.DockHolder holder = adapter.onCreateViewHolder(list, 0);
                adapter.onBindViewHolder(holder, i);
                labels.add(holder.name.getText().toString());
            }
            assertEquals(new HashSet<>(Arrays.asList("TV app", "Selected app")), labels);
            popup.dismiss();
        }
    }

    private static ComponentName installLauncherApp(String pkg, String label, String category) {
        Application app = RuntimeEnvironment.getApplication();
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg;
        info.activityInfo.name = pkg + ".Main";
        info.activityInfo.nonLocalizedLabel = label;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = pkg;
        info.nonLocalizedLabel = label;
        PackageInfo installed = new PackageInfo();
        installed.packageName = pkg;
        installed.applicationInfo = info.activityInfo.applicationInfo;
        installed.activities = new ActivityInfo[]{info.activityInfo};
        shadowOf(app.getPackageManager()).installPackage(installed);
        shadowOf(app.getPackageManager()).addResolveInfoForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(category), info);
        return new ComponentName(pkg, info.activityInfo.name);
    }
}
