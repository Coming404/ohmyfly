import importlib.util
from pathlib import Path
import plistlib
import struct
import tempfile
import unittest
from unittest.mock import patch

MODULE_PATH = Path(__file__).resolve().parents[1] / 'package_ios_release.py'
spec = importlib.util.spec_from_file_location('package_ios_release', MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def executable(platform=2, cpu=0x0100000C):
    header = struct.pack('<8I', 0xFEEDFACF, cpu, 0, 2, 1, 24, 0, 0)
    return header + struct.pack('<6I', 0x32, 24, platform, 0x100000, 0x1A0000, 0)


class IOSReleasePackageTests(unittest.TestCase):
    def test_accepts_device_platform(self):
        self.assertEqual(module.macho_platforms(executable()), {2})

    def test_identifies_simulator_even_when_cpu_is_arm64(self):
        self.assertEqual(module.macho_platforms(executable(platform=7)), {7})

    def test_rejects_wrong_architecture_and_truncated_commands(self):
        for data in (b'', executable(cpu=0x01000007), executable()[:-1]):
            with self.subTest(data_length=len(data)):
                with self.assertRaises(ValueError):
                    module.macho_platforms(data)

    def test_rejects_malformed_load_command(self):
        data = bytearray(executable())
        struct.pack_into('<I', data, 36, 0)
        with self.assertRaises(ValueError):
            module.macho_platforms(data)

    def test_validates_device_bundle_and_rejects_simulator_binary(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            info = {'CFBundleSupportedPlatforms': ['iPhoneOS'], 'DTPlatformName': 'iphoneos',
                    'MinimumOSVersion': '16.0', 'CFBundleExecutable': 'OhMyFly',
                    'CFBundleIdentifier': 'com.ohmyfly.app'}
            (app / 'Info.plist').write_bytes(plistlib.dumps(info))
            (app / 'OhMyFly').write_bytes(executable())
            self.assertFalse(module.validate_app(app)['signed'])
            (app / 'OhMyFly').write_bytes(executable(platform=7))
            with self.assertRaises(ValueError):
                module.validate_app(app)

    def test_rejects_simulator_bundle_and_signed_bundle(self):
        with tempfile.TemporaryDirectory() as directory:
            app = Path(directory)
            info = {'CFBundleSupportedPlatforms': ['iPhoneSimulator'],
                    'DTPlatformName': 'iphonesimulator', 'MinimumOSVersion': '16.0',
                    'CFBundleExecutable': 'OhMyFly', 'CFBundleIdentifier': 'com.ohmyfly.app'}
            (app / 'Info.plist').write_bytes(plistlib.dumps(info))
            (app / 'OhMyFly').write_bytes(executable())
            with self.assertRaises(ValueError):
                module.validate_app(app)
            info.update(CFBundleSupportedPlatforms=['iPhoneOS'], DTPlatformName='iphoneos')
            (app / 'Info.plist').write_bytes(plistlib.dumps(info))
            (app / '_CodeSignature').mkdir()
            with self.assertRaises(ValueError):
                module.validate_app(app)

    def test_packages_only_ipa_not_generic_zip_archives(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'app.xcarchive'
            app = archive / 'Products/Applications/OhMyFly.app'
            app.mkdir(parents=True)
            (archive / 'Info.plist').write_bytes(plistlib.dumps({
                'ApplicationProperties': {'ApplicationPath': 'Applications/OhMyFly.app'}}))
            (app / 'Info.plist').write_bytes(plistlib.dumps({
                'CFBundleSupportedPlatforms': ['iPhoneOS'], 'DTPlatformName': 'iphoneos',
                'MinimumOSVersion': '16.0', 'CFBundleExecutable': 'OhMyFly',
                'CFBundleIdentifier': 'com.ohmyfly.app'}))
            (app / 'OhMyFly').write_bytes(executable())
            (archive / 'dSYMs').mkdir()
            output = Path(directory) / 'output'
            with patch.object(module.subprocess, 'run') as run:
                module.package(archive, output)
            self.assertEqual(run.call_count, 1)
            self.assertEqual(run.call_args.args[0][-1], str(output / 'ohmyfly-release-unsigned.ipa'))
            self.assertTrue((output / 'build-info.json').is_file())


if __name__ == '__main__':
    unittest.main()
