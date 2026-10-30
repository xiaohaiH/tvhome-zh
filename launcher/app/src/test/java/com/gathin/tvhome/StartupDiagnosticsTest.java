package com.gathin.tvhome;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.robolectric.util.ReflectionHelpers.callInstanceMethod;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real file/UI tests. Reflection allows the first red run to fail assertions, not compilation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {19, 21, 28}, qualifiers = "land-xhdpi", application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class StartupDiagnosticsTest {
    private static Class<?> requiredType(String name) {
        try {
            return Class.forName("com.gathin.tvhome." + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Missing startup diagnostic implementation: " + name, e);
        }
    }

    private static Object report(File directory) throws Exception {
        Constructor<?> constructor = requiredType("StartupDiagnostics$Report").getDeclaredConstructor(File.class);
        constructor.setAccessible(true);
        return constructor.newInstance(directory);
    }

    private static File files() {
        return RuntimeEnvironment.getApplication().getFilesDir();
    }

    private static String text(Object report) {
        return callInstanceMethod(report, "text");
    }

    private static boolean begin(Object report) {
        return callInstanceMethod(report, "begin", from(String.class, "test device API 19"));
    }

    @Test
    public void checkpointsSurviveNewReaderWithoutCachedPreferences() throws Exception {
        Object report = report(files());
        assertTrue(begin(report));
        callInstanceMethod(report, "mark", from(String.class, "M02 layout inflation"));
        String saved = text(report(files()));
        assertTrue(saved, saved.contains("M02 layout inflation"));
        assertTrue(saved, saved.contains("test device API 19"));
        callInstanceMethod(report, "mark", from(String.class, "M03 font loading"));
        saved = text(report(files()));
        assertTrue(saved, saved.contains("M03 font loading"));
        assertFalse(saved, saved.contains("M02 layout inflation"));
    }

    @Test
    public void errorsIncludeCauseAndAreClearedOnlyForExplicitNewTest() throws Exception {
        Object report = report(files());
        begin(report);
        Throwable crash = new RuntimeException("inflate failed", new NoClassDefFoundError("vendor.Class"));
        callInstanceMethod(report, "capture", from(Thread.class, Thread.currentThread()), from(Throwable.class, crash));
        String saved = text(report(files()));
        assertTrue(saved, saved.contains("inflate failed"));
        assertTrue(saved, saved.contains("Caused by:"));
        assertTrue(saved, saved.contains("vendor.Class"));
        assertTrue(saved, saved.contains("StartupDiagnosticsTest"));
        // Constructing readers, refreshing or marking a phase must not erase the exception.
        callInstanceMethod(report(files()), "mark", from(String.class, "M30 after resume"));
        assertTrue(text(report(files())).contains("inflate failed"));
        begin(report);
        assertFalse(text(report(files())).contains("inflate failed"));
    }

    @Test
    public void errorCaptureIsBounded() throws Exception {
        Object report = report(files());
        begin(report);
        char[] large = new char[100_000];
        Arrays.fill(large, 'x');
        callInstanceMethod(report, "capture", from(Thread.class, Thread.currentThread()),
                from(Throwable.class, new RuntimeException(new String(large))));
        String saved = text(report(files()));
        assertTrue(saved.contains("RuntimeException"));
        assertTrue("Report must stay small on low-memory TVs", saved.length() < 60_000);
        for (File file : files().listFiles()) {
            if (file.getName().startsWith("startup-")) assertTrue(file.length() < 60_000);
        }
    }

    @Test
    public void unwritableStorageDoesNotThrowAndHandlerStillDelegates() throws Exception {
        File notDirectory = new File(files(), "blocked");
        assertTrue(notDirectory.createNewFile());
        Object report = report(notDirectory);
        assertFalse(begin(report));
        callInstanceMethod(report, "mark", from(String.class, "M01"));
        assertNotNull(text(report));
        Throwable crash = new VerifyError("test verifier failure");
        AtomicBoolean delegated = new AtomicBoolean();
        Thread.UncaughtExceptionHandler previous = (thread, failure) -> {
            assertSame(crash, failure);
            delegated.set(true);
        };
        handler(report, previous).uncaughtException(Thread.currentThread(), crash);
        assertTrue("Never swallow the original uncaught exception", delegated.get());
    }

    @Test
    public void handlerSavesBeforeCallingThePreviousHandler() throws Exception {
        Object report = report(files());
        begin(report);
        AtomicBoolean delegated = new AtomicBoolean();
        Thread.UncaughtExceptionHandler previous = (thread, failure) -> {
            assertTrue(text(report).contains("injected linkage failure"));
            delegated.set(true);
        };
        handler(report, previous).uncaughtException(Thread.currentThread(),
                new NoSuchMethodError("injected linkage failure"));
        assertTrue(delegated.get());
    }

    private static Thread.UncaughtExceptionHandler handler(Object report,
            Thread.UncaughtExceptionHandler previous) throws Exception {
        Constructor<?> constructor = requiredType("StartupDiagnostics$CrashHandler").getDeclaredConstructor(
                requiredType("StartupDiagnostics$Report"), Thread.UncaughtExceptionHandler.class);
        constructor.setAccessible(true);
        return (Thread.UncaughtExceptionHandler) constructor.newInstance(report, previous);
    }

    @Test
    public void diagnosticPageShowsEvidenceWithoutAutoLaunchingDesktop() throws Exception {
        Object report = report(files());
        begin(report);
        callInstanceMethod(report, "capture", from(Thread.class, Thread.currentThread()),
                from(Throwable.class, new IllegalStateException("saved TV crash")));
        Class<? extends Activity> type = requiredType("DiagnosticsActivity").asSubclass(Activity.class);
        try (ActivityController<? extends Activity> controller = Robolectric.buildActivity(type).setup()) {
            Activity activity = controller.get();
            TextView body = activity.getWindow().getDecorView().findViewWithTag("diagnostics-report");
            assertNotNull(body);
            assertTrue(body.getText().toString().contains("saved TV crash"));
            assertNull(shadowOf(activity).getNextStartedActivity());
            activity.getWindow().getDecorView().findViewWithTag("diagnostics-refresh").performClick();
            assertTrue(body.getText().toString().contains("saved TV crash"));
            activity.getWindow().getDecorView().findViewWithTag("diagnostics-start").performClick();
            Intent intent = shadowOf(activity).getNextStartedActivity();
            assertNotNull(intent);
            assertEquals(activity.getPackageName(), intent.getComponent().getPackageName());
            assertEquals("com.gathin.tvhome.MainActivity", intent.getComponent().getClassName());
            assertNotEquals(0, intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK);
            assertNotEquals(0, intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK);
            assertFalse(text(report).contains("saved TV crash"));
            assertTrue(text(report).contains("S00"));
        }
    }

    @Test
    public void diagnosticApplicationInstallsBeforeDesktopAndPreservesPreviousEvidence() throws Exception {
        Object saved = report(files());
        begin(saved);
        callInstanceMethod(saved, "capture", from(Thread.class, Thread.currentThread()),
                from(Throwable.class, new RuntimeException("previous attempt")));
        Class<?> type = requiredType("StartupDiagnostics");
        Application diagnostic = (Application) type.getDeclaredConstructor().newInstance();
        callInstanceMethod(diagnostic, "attachBaseContext", from(Context.class, RuntimeEnvironment.getApplication()));
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Object active = ReflectionHelpers.getStaticField(type, "active");
        try {
            diagnostic.onCreate();
            assertEquals(requiredType("StartupDiagnostics$CrashHandler"),
                    Thread.getDefaultUncaughtExceptionHandler().getClass());
            assertTrue(text(report(files())).contains("previous attempt"));
            try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
                assertNotNull(controller.get().findViewById(R.id.grid));
                assertTrue(text(report(files())).contains("M30"));
                assertTrue(text(report(files())).contains("previous attempt"));
            }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous);
            ReflectionHelpers.setStaticField(type, "active", active);
        }
    }

    @Test
    public void ordinaryBuildDoesNotInstallACrashHandlerOrWriteReports() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            assertSame(previous, Thread.getDefaultUncaughtExceptionHandler());
            assertNotNull(controller.get().findViewById(R.id.grid));
            for (File file : files().listFiles()) assertFalse(file.getName().startsWith("startup-"));
        }
    }
}
