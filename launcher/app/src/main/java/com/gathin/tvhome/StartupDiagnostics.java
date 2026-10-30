package com.gathin.tvhome;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

/** Activated ONLY by the diagnostic manifest; ordinary builds leave checkpoints inert. */
public final class StartupDiagnostics extends Application {
    private static volatile Report active;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Report report = new Report(getFilesDir());
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(report, previous));
            active = report;
            // Do not reset evidence here: the reporter and desktop start in different processes,
            // and reopening either process must preserve the last failure until an explicit test.
        } catch (Throwable ignored) {
            // Diagnostic setup itself must not become a new reason for the launcher to crash.
        }
    }

    static void mark(String phase) {
        Report report = active;
        if (report != null) report.mark(phase);
    }

    static String deviceInfo(Context context) {
        String version = "unknown";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return "APK: " + version + "\n"
                + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n"
                + "TV: " + Build.MANUFACTURER + " / " + Build.MODEL + " / " + Build.DEVICE + "\n"
                + "ABI: " + Build.CPU_ABI + " / VM: " + System.getProperty("java.vm.version") + "\n"
                + "Screen: " + dm.widthPixels + "x" + dm.heightPixels + " / " + dm.densityDpi + "dpi"
                + " / Heap: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB";
    }

    static final class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Report report;
        private final Thread.UncaughtExceptionHandler previous;

        CrashHandler(Report report, Thread.UncaughtExceptionHandler previous) {
            this.report = report;
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable failure) {
            try {
                report.capture(thread, failure);
            } catch (Throwable ignored) {
                // Includes OOM and failures while formatting a vendor's exception.
            } finally {
                // Never resume a broken main loop or launch an Activity from a dying process.
                if (previous != null) previous.uncaughtException(thread, failure);
                else {
                    Process.killProcess(Process.myPid());
                    System.exit(10);
                }
            }
        }
    }

    /** No cached preferences: the reporter reads the desktop process's last completed file writes. */
    static final class Report {
        private static final int LIMIT = 16 * 1024;
        private final File directory;

        Report(File directory) {
            if (directory == null) throw new IllegalArgumentException("No private files directory");
            this.directory = directory;
        }

        boolean begin(String device) {
            // Explicit Start is the ONLY operation that discards previous evidence.
            return write("device", device, null)
                    & write("error", "", null)
                    & write("phase", "S00 已请求启动桌面；尚未进入 MainActivity", null);
        }

        void mark(String phase) {
            write("phase", phase, null);
        }

        void capture(Thread thread, Throwable failure) {
            write("error", "Thread: " + thread.getName() + " / PID: " + Process.myPid() + "\n", failure);
        }

        String text() {
            String phase = read("phase");
            String error = read("error");
            String device = read("device");
            return "最后到达的步骤（不是已确认的故障点）：\n"
                    + (phase.isEmpty() ? "尚未开始，或记录文件不可读。" : phase)
                    + "\n\n捕获的异常：\n"
                    + (error.isEmpty() ? "暂无。系统杀进程或底层崩溃可能没有 Java 异常。" : error)
                    + (device.isEmpty() ? "" : "\n\n本次测试设备：\n" + device);
        }

        private File file(String part) {
            return new File(directory, "startup-" + part + ".txt");
        }

        private synchronized boolean write(String part, String text, Throwable failure) {
            File target = file(part);
            File pending = new File(directory, target.getName() + ".tmp");
            try {
                // Publish by rename after sync: another process sees an old or a complete report,
                // not an empty/half-written phase. No shared-preference multi-process caching.
                try (FileOutputStream output = new FileOutputStream(pending);
                     Writer writer = new LimitedWriter(new OutputStreamWriter(output, "UTF-8"), LIMIT)) {
                    PrintWriter print = new PrintWriter(writer);
                    print.print(text);
                    if (failure != null) failure.printStackTrace(print);
                    print.flush();
                    if (print.checkError()) throw new IOException("Report write failed");
                    output.getFD().sync();
                }
                return pending.renameTo(target);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private String read(String part) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file(part)), "UTF-8")) {
                StringBuilder out = new StringBuilder();
                char[] buffer = new char[1024];
                int count;
                while (out.length() < LIMIT && (count = reader.read(buffer, 0,
                        Math.min(buffer.length, LIMIT - out.length()))) > 0) out.append(buffer, 0, count);
                if (out.length() == LIMIT) out.append("\n[报告已截断]");
                return out.toString();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    /** Stream the stack directly to a capped writer rather than allocate a complete stack string. */
    private static final class LimitedWriter extends FilterWriter {
        private int remaining;

        LimitedWriter(Writer out, int limit) {
            super(out);
            remaining = limit;
        }

        @Override
        public void write(int c) throws IOException {
            if (remaining > 0) {
                out.write(c);
                remaining--;
            }
        }

        @Override
        public void write(char[] chars, int offset, int count) throws IOException {
            int written = Math.min(count, remaining);
            out.write(chars, offset, written);
            remaining -= written;
        }

        @Override
        public void write(String text, int offset, int count) throws IOException {
            int written = Math.min(count, remaining);
            out.write(text, offset, written);
            remaining -= written;
        }
    }
}
