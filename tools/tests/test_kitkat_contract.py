"""Offline build/resource contracts for the Android 4.4 distribution.

Run from the repository root:
    python -m unittest discover -s tools/tests -v
Runtime behavior is covered separately by the Android JVM tests and device checklist.
"""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "launcher" / "app"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class KitKatBuildContract(unittest.TestCase):
    def setUp(self):
        self.gradle = (APP / "build.gradle").read_text(encoding="utf-8")

    def test_minimum_sdk_is_kitkat(self):
        minimum = re.search(r"\bminSdk\s+(\d+)", self.gradle)
        self.assertIsNotNone(minimum)
        self.assertEqual(19, int(minimum.group(1)), "KitKat cannot install an API 21 APK")

    def test_v1_signing_is_explicitly_retained(self):
        self.assertRegex(self.gradle, r"enableV1Signing\s*=\s*true")

    def test_signing_does_not_require_another_developers_private_key(self):
        self.assertNotIn("/Users/tom/", self.gradle)

    def test_framework_imageviews_get_raster_fallbacks(self):
        self.assertRegex(self.gradle, r"useSupportLibrary\s*=\s*false")
        self.assertRegex(self.gradle, r"generatedDensities\s*=.*mdpi.*hdpi.*xhdpi")

    def test_kitkat_theme_does_not_reference_material(self):
        styles = ET.parse(APP / "src/main/res/values/styles.xml").getroot()
        theme = styles.find("style[@name='Theme.TvHome']")
        self.assertIsNotNone(theme)
        self.assertEqual("android:Theme.Holo.NoActionBar.Fullscreen", theme.get("parent"))

    def test_modern_devices_keep_material_theme(self):
        path = APP / "src/main/res/values-v21/styles.xml"
        self.assertTrue(path.exists(), "Material must be in API-qualified resources")
        theme = ET.parse(path).getroot().find("style[@name='Theme.TvHome']")
        self.assertIsNotNone(theme)
        self.assertEqual("android:Theme.Material.NoActionBar.Fullscreen", theme.get("parent"))

    def test_ordinary_vendor_launchers_can_open_the_app(self):
        manifest = ET.parse(APP / "src/main/AndroidManifest.xml").getroot()
        activity = manifest.find("application/activity[@" + ANDROID + "name='.MainActivity']")
        self.assertIsNotNone(activity)
        filters = activity.findall("intent-filter")
        self.assertTrue(any(
            any(a.get(ANDROID + "name") == "android.intent.action.MAIN" for a in f.findall("action"))
            and any(c.get(ANDROID + "name") == "android.intent.category.LAUNCHER" for c in f.findall("category"))
            for f in filters
        ), "KitKat launchers do not necessarily query LEANBACK_LAUNCHER")


if __name__ == "__main__":
    unittest.main()
