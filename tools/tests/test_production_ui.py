"""Guard the production UI boundary; not a Kotlin/Swift compiler."""
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class ProductionUiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        roots = [ROOT / path for path in (
            'shared/src/commonMain', 'shared/src/androidMain', 'shared/src/iosMain',
            'androidApp/src', 'iosApp',
        )]
        output = subprocess.check_output(
            ['rg', '--files', '-g', '*.kt', '-g', '*.swift',
             *[str(path) for path in roots if path.is_dir()]], text=True,
        )
        cls.sources = [Path(path) for path in output.splitlines()]

    def test_runtime_file_names_have_no_showcase_or_test_placeholders(self):
        def tokens(name):
            return {token.casefold() for token in re.findall(r'[A-Z]+(?=[A-Z][a-z]|$)|[A-Z]?[a-z]+|[0-9]+', name)}

        self.assertIn('test', tokens('SpeedTestScreen'))
        self.assertIn('demo', tokens('DemoNode'))
        self.assertFalse({'demo', 'test'} & tokens('LatestResults'))
        self.assertTrue(self.sources)
        for path in self.sources:
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertFalse({'demo', 'test'} & tokens(path.stem))

    def test_runtime_sources_do_not_restore_fake_results_or_showcase_copy(self):
        markers = re.compile(
            r'\bDemo\w*|\bdemoNodes\b|\bsampleLatencyMs\b|'
            r'界面预览|非实测|演示节点|展开动效'
        )
        for path in self.sources:
            with self.subTest(path=path.relative_to(ROOT)):
                self.assertNotRegex(path.read_text(), markers)


if __name__ == '__main__':
    unittest.main()
