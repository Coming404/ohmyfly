import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / 'check_project.py'
spec = importlib.util.spec_from_file_location('check_project', MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MarkdownCheckTests(unittest.TestCase):
    def test_local_and_external_links(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'target.md').write_text('# Target')
            doc = root / 'README.md'
            doc.write_text('[local](target.md#section) [remote](https://example.com/)')
            self.assertEqual(module.markdown_errors(doc, root), [])
            doc.write_text('[missing](missing.md)')
            self.assertTrue(module.markdown_errors(doc, root))

    def test_path_cannot_escape_project(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'project'
            root.mkdir()
            (root.parent / 'outside.md').write_text('# Outside')
            doc = root / 'README.md'
            doc.write_text('[outside](../outside.md)')
            self.assertTrue(module.markdown_errors(doc, root))

    def test_note_status_must_match_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            note = root / '.agents/notes/proposed/architecture/2026-10-18-test.md'
            note.parent.mkdir(parents=True)
            note.write_text('# Agent Note: Test\n\nStatus: proposed\n\n## Alternatives considered\n')
            self.assertEqual(module.note_errors(note, root), [])
            note.write_text('# Agent Note: Test\n\nStatus: implemented\n\n## Alternatives considered\n')
            self.assertTrue(module.note_errors(note, root))

    def test_missing_structure_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertTrue(module.check(Path(directory)))


if __name__ == '__main__':
    unittest.main()
