"""Safety/entry-point contracts for the separate no-ADB diagnostic build."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

APP = Path(__file__).resolve().parents[2] / "launcher" / "app"
JAVA = APP / "src/main/java/com/gathin/tvhome"
A = "{http://schemas.android.com/apk/res/android}"
T = "{http://schemas.android.com/tools}"


class StartupDiagnosticsContract(unittest.TestCase):
    def manifest(self):
        path = APP / "src/diagnostic/AndroidManifest.xml"
        self.assertTrue(path.exists(), "A separate diagnostic manifest is required")
        return ET.parse(path).getroot()

    def test_release_derived_variant_does_not_replace_existing_app(self):
        gradle = (APP / "build.gradle").read_text(encoding="utf-8")
        self.assertRegex(gradle, r"diagnostic\s*\{")
        self.assertIn("initWith release", gradle)
        self.assertIn("applicationIdSuffix '.diagnostic'", gradle)

    def test_reporter_is_entry_and_desktop_is_a_private_separate_process(self):
        app = self.manifest().find("application")
        self.assertEqual(".StartupDiagnostics", app.get(A + "name"))
        main = app.find("activity[@" + A + "name='.MainActivity']")
        self.assertIsNotNone(main)
        self.assertEqual(":desktop", main.get(A + "process"))
        self.assertEqual("false", main.get(A + "exported"))
        self.assertEqual("${applicationId}.desktop", main.get(A + "taskAffinity"))
        self.assertEqual("removeAll", main.find("intent-filter").get(T + "node"))
        reporter = app.find("activity[@" + A + "name='.DiagnosticsActivity']")
        self.assertIsNotNone(reporter)
        categories = {c.get(A + "name") for c in reporter.findall("intent-filter/category")}
        self.assertIn("android.intent.category.LAUNCHER", categories)
        self.assertIn("android.intent.category.LEANBACK_LAUNCHER", categories)
        self.assertNotIn("android.intent.category.HOME", categories)
        self.assertEqual("@android:style/Theme.Holo.NoActionBar.Fullscreen", reporter.get(A + "theme"))

    def test_no_extra_permissions_or_enabled_background_services(self):
        manifest = self.manifest()
        self.assertEqual([], manifest.findall("uses-permission"))
        for name in (".HomeKeyService", ".ClockDream"):
            service = manifest.find("application/service[@" + A + "name='" + name + "']")
            self.assertIsNotNone(service)
            self.assertEqual("false", service.get(A + "enabled"))

    def test_early_checkpoints_precede_risky_startup_calls(self):
        source = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        for stage, call in (("M01", "super.onCreate(savedInstanceState)"),
                            ("M02", "setContentView(R.layout.activity_main)"),
                            ("M03", "Typefaces.apply(findViewById(android.R.id.content))"),
                            ("M04", "cfg = Config.load(this)"),
                            ("M20", "adapter.reload()")):
            self.assertIn('StartupDiagnostics.mark("' + stage, source)
            self.assertLess(source.index('StartupDiagnostics.mark("' + stage), source.index(call))

    def test_reporter_does_not_load_the_desktop_class_or_custom_ui(self):
        path = JAVA / "DiagnosticsActivity.java"
        self.assertTrue(path.exists(), "A framework-only reporter is required")
        source = path.read_text(encoding="utf-8")
        for forbidden in ("MainActivity.class", "Typefaces.", "Glass", "R.layout.", "androidx."):
            self.assertNotIn(forbidden, source)
        self.assertIn('"com.gathin.tvhome.MainActivity"', source)


if __name__ == "__main__":
    unittest.main()
