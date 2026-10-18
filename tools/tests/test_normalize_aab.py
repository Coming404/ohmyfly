import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest
import warnings
import zipfile

MODULE_PATH = Path(__file__).resolve().parents[1] / 'normalize_aab.py'
spec = importlib.util.spec_from_file_location('normalize_aab', MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def write_bundle(path, extra=(), signatures=True):
    entries = [('base/manifest/AndroidManifest.xml', b'payload'), ('BundleConfig.pb', b'config'),
               (module.MANIFEST, b'Manifest-Version: 1.0\r\n\r\n')]
    if signatures:
        entries += [('META-INF/TEST.SF', b'signed manifest'), ('META-INF/TEST.RSA', b'signature block')]
    with zipfile.ZipFile(path, 'w') as archive:
        for name, data in entries + list(extra):
            archive.writestr(name, data)


class NormalizeAABTests(unittest.TestCase):
    def test_preserves_all_bytes_and_places_signature_metadata_first(self):
        with tempfile.TemporaryDirectory() as directory:
            source, target = Path(directory) / 'source.aab', Path(directory) / 'clean.aab'
            write_bundle(source, extra=[('base/assets/extra', b'\0\xffbinary')])
            module.normalize(source, target)
            with zipfile.ZipFile(source) as before, zipfile.ZipFile(target) as after:
                self.assertEqual(after.namelist()[:3], [module.MANIFEST, 'META-INF/TEST.SF', 'META-INF/TEST.RSA'])
                self.assertEqual({name: before.read(name) for name in before.namelist()},
                                 {name: after.read(name) for name in after.namelist()})
                self.assertTrue(all(entry.create_system == 0 and entry.external_attr >> 16 == 0
                                    for entry in after.infolist()))

    def test_never_normalizes_apks(self):
        with self.assertRaises(ValueError):
            module.normalize(Path('input.apk'), Path('output.aab'))

    def test_rejects_in_place_rewrite(self):
        with self.assertRaises(ValueError):
            module.normalize(Path('same.aab'), Path('same.aab'))

    def test_rejects_duplicate_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.aab'
            with warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                write_bundle(source, extra=[('BundleConfig.pb', b'ambiguous')])
            with self.assertRaises(ValueError):
                module.normalize(source, Path(directory) / 'clean.aab')

    def test_rejects_symlinks_instead_of_changing_their_meaning(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.aab'
            write_bundle(source)
            link = zipfile.ZipInfo('base/assets/link')
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            with zipfile.ZipFile(source, 'a') as archive:
                archive.writestr(link, b'target')
            with self.assertRaises(ValueError):
                module.normalize(source, Path(directory) / 'clean.aab')

    def test_rejects_missing_signature_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.aab'
            write_bundle(source, signatures=False)
            with self.assertRaises(ValueError):
                module.normalize(source, Path(directory) / 'clean.aab')

    def test_rejects_unsafe_entry_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'source.aab'
            write_bundle(source, extra=[('../outside', b'unsafe')])
            with self.assertRaises(ValueError):
                module.normalize(source, Path(directory) / 'clean.aab')


if __name__ == '__main__':
    unittest.main()
