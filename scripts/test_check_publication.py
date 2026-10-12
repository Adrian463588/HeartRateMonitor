import unittest
from check_publication import forbidden_path


class PublicationPolicyTest(unittest.TestCase):
    def test_sdk_and_apk_bundles_are_rejected_at_any_location(self):
        for path in ("wear/libs/Samsung.AAR", "vendor/polar.aar", "app.aab", "mobile-debug.apk"):
            with self.subTest(path=path):
                self.assertTrue(forbidden_path(path))

    def test_signing_and_configuration_secrets_are_rejected(self):
        for path in (".env", ".env.production", "mobile/local.properties", "release.keystore",
                     "key.properties", "vendor/credentials.json", "private.pem"):
            with self.subTest(path=path):
                self.assertTrue(forbidden_path(path))

    def test_raw_recordings_and_arrays_are_rejected(self):
        for path in ("participant/ecg.csv", "ppg.npz", "acc.parquet", "sensor_data/trace.json",
                     "recordings/session.txt", "export.jsonl"):
            with self.subTest(path=path):
                self.assertTrue(forbidden_path(path))

    def test_generated_machine_files_are_rejected(self):
        for path in (".idea/gradle.xml", "shared/bin/Message.kt", "mobile/build/report.xml", "build_output.txt"):
            with self.subTest(path=path):
                self.assertTrue(forbidden_path(path))

    def test_source_specs_tests_and_gradle_wrapper_remain_publishable(self):
        for path in ("wear/src/main/java/Listener.kt", "shared/src/test/java/WatchControlTest.kt",
                     "openspec/specs/02-session-control-and-polar-recovery.md",
                     "gradle/wrapper/gradle-wrapper.jar", ".github/workflows/android-checks.yml"):
            with self.subTest(path=path):
                self.assertFalse(forbidden_path(path))


if __name__ == "__main__":
    unittest.main()
