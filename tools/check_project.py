#!/usr/bin/env python3
"""Lightweight structural checks, not a Kotlin/Xcode compiler."""
from pathlib import Path
import hashlib
import json
import os
import plistlib
import re
import subprocess
import sys
import tomllib
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
WRAPPER_SHA256 = '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7'


def markdown_errors(path: Path, root: Path) -> list[str]:
    text = path.read_text()
    errors = []
    for dest in re.findall(r'\]\(([^)]+)\)', text):
        dest = dest.strip().split(' "', 1)[0]
        if not dest or dest.startswith('#') or urllib.parse.urlsplit(dest).scheme:
            continue
        target = urllib.parse.unquote(dest.split('#', 1)[0])
        resolved = (path.parent / target).resolve()
        if not resolved.is_relative_to(root.resolve()) or not resolved.exists():
            errors.append(f'{path.relative_to(root)}: broken local link {dest}')
    if re.search(r'flutter|ohmytower|oh_my_tower', text, re.I):
        errors.append(f'{path.relative_to(root)}: unrelated historical/rule-package reference')
    return errors


def note_errors(path: Path, root: Path) -> list[str]:
    parts = path.relative_to(root / '.agents' / 'notes').parts
    text = path.read_text()
    errors = []
    if len(parts) != 3 or parts[0] not in {'proposed', 'implemented', 'rejected', 'archived'}:
        return [f'{path}: invalid note path']
    if parts[1] not in {'architecture', 'feature', 'bug-fix', 'simplification', 'process', 'testing'}:
        errors.append(f'{path}: invalid note category')
    if not text.startswith('# Agent Note: ') or f'\nStatus: {parts[0]}' not in text:
        errors.append(f'{path}: missing title or status/path mismatch')
    if '## Alternatives considered' not in text:
        errors.append(f'{path}: missing alternatives')
    if parts[0] == 'implemented' and ('## Decision' not in text or '## Proposal' in text):
        errors.append(f'{path}: implemented note must describe the actual decision')
    return errors


def check(root: Path = ROOT) -> list[str]:
    errors = []
    required = ['AGENTS.md', 'README.md', 'settings.gradle.kts', 'build.gradle.kts',
                'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.jar',
                'gradle/wrapper/gradle-wrapper.properties', 'gradlew', 'gradlew.bat',
                'shared/build.gradle.kts', 'androidApp/build.gradle.kts',
                'docs/references.md', 'docs/development.md',
                '.github/workflows/checks.yml', '.github/workflows/build.yml']
    for item in required:
        if not (root / item).is_file():
            errors.append(f'missing {item}')
    if errors:
        return errors
    files = [Path(p) for p in subprocess.check_output(
        ['rg', '--files', '--hidden', '-g', '!.git/**', '-g', '!**/build/**',
         '-g', '!**/__pycache__/**', str(root)], text=True).splitlines()]
    for path in files:
        if path.suffix == '.md':
            errors += markdown_errors(path, root)
            if '.agents/notes/' in path.as_posix():
                errors += note_errors(path, root)
        try:
            if path.suffix == '.json':
                json.loads(path.read_text())
            elif path.suffix == '.plist':
                plistlib.loads(path.read_bytes())
            elif path.suffix in {'.xml', '.xcscheme'}:
                ET.parse(path)
        except Exception as exc:
            errors.append(f'{path.relative_to(root)}: invalid structured file: {exc}')
    catalog = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())
    for key, value in catalog['versions'].items():
        if '+' in value or value.startswith('latest.'):
            errors.append(f'floating version {key}={value}')
    if catalog['versions']['android-minSdk'] != '33':
        errors.append('Android minimum must be API 33 / Android 13 for miuix-blur')
    settings = (root / 'settings.gradle.kts').read_text()
    if ':desktopApp' in settings or ':webApp' in settings:
        errors.append('unexpected extra platform')
    pbx = (root / 'iosApp/iosApp.xcodeproj/project.pbxproj').read_text()
    targets = re.findall(r'IPHONEOS_DEPLOYMENT_TARGET = ([^;]+);', pbx)
    if not targets or any(t != '16.0' for t in targets):
        errors.append('iOS deployment targets must all be 16.0')
    if not os.access(root / 'gradlew', os.X_OK):
        errors.append('gradlew must be executable')
    actual = hashlib.sha256((root / 'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest()
    if actual != WRAPPER_SHA256:
        errors.append('wrapper JAR differs from the inspected official template copy')
    app = (root / 'shared/src/commonMain/kotlin/dev/ohmydrift/App.kt').read_text()
    if 'import top.yukonga.miuix.kmp.basic.Scaffold' not in app:
        errors.append('Miuix Scaffold import missing')
    return errors


if __name__ == '__main__':
    problems = check()
    if problems:
        print('\n'.join(problems), file=sys.stderr)
        raise SystemExit(1)
    print('PASS: structure, declared versions, local links, note format, JSON/XML/plist and wrapper fingerprint.')
    print('NOT RUN: Gradle/Kotlin compilation, Xcode build, signing, native Go integration or device tests.')
