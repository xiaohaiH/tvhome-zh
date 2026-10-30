package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Deliberately uses only old framework widgets, system fonts and a system theme. */
public final class DiagnosticsActivity extends Activity {
    private StartupDiagnostics.Report report;
    private TextView details;
    private Button start;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_Holo_NoActionBar_Fullscreen);
        super.onCreate(savedInstanceState);
        report = new StartupDiagnostics.Report(getFilesDir());
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(Color.BLACK);

        TextView title = new TextView(this);
        title.setText("TVHome 启动诊断（不是修复版）");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        page.addView(title);
        TextView help = new TextView(this);
        help.setText("先拍下本页，再选「启动桌面测试」。闪退后重新打开本应用查看报告。\n"
                + "报告可用遥控器上下滚动；重新测试会清除上次报告。不要设为默认桌面。");
        help.setTextSize(16);
        help.setTextColor(Color.LTGRAY);
        page.addView(help);

        LinearLayout buttons = new LinearLayout(this);
        start = new Button(this);
        start.setTag("diagnostics-start");
        start.setText("启动桌面测试");
        start.setOnClickListener(v -> startDesktop());
        buttons.addView(start);
        Button refresh = new Button(this);
        refresh.setTag("diagnostics-refresh");
        refresh.setText("刷新报告");
        refresh.setOnClickListener(v -> refreshReport());
        buttons.addView(refresh);
        page.addView(buttons);

        scroll = new ScrollView(this);
        scroll.setFocusable(true);
        scroll.setFillViewport(true);
        details = new TextView(this);
        details.setTag("diagnostics-report");
        details.setTextColor(Color.WHITE);
        details.setTextSize(17);
        details.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        scroll.addView(details);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        start.requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        start.setEnabled(true);
        refreshReport();
    }

    private void refreshReport() {
        details.setText(report.text() + "\n\n当前设备：\n" + StartupDiagnostics.deviceInfo(this));
        scroll.scrollTo(0, 0);
    }

    private void startDesktop() {
        start.setEnabled(false); // Prevent two requests while the first desktop is starting.
        if (!report.begin(StartupDiagnostics.deviceInfo(this))) {
            details.setText("诊断记录无法写入，暂不启动桌面测试。请拍下本页。\n"
                    + StartupDiagnostics.deviceInfo(this));
            start.setEnabled(true);
            return;
        }
        try {
            // A string is intentional: loading the desktop class here would put a verifier failure
            // into the reporter process. The manifest gives the desktop a separate process/task.
            Intent intent = new Intent().setClassName(getPackageName(), "com.gathin.tvhome.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } catch (RuntimeException failure) {
            report.capture(Thread.currentThread(), failure);
            start.setEnabled(true);
        }
        refreshReport();
    }
}
