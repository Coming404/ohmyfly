#!/usr/bin/env python3
"""Canonicalize a signed AAB container without changing signed entry contents.

Never use this on APKs: APK v2/v3 signatures also protect ZIP structure.
The caller must subsequently run jarsigner -verify -strict.
"""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import stat
import zipfile

MANIFEST = 'META-INF/MANIFEST.MF'


def priority(name: str) -> int:
    if name == MANIFEST:
        return 0
    if name.startswith('META-INF/') and name.endswith('.SF'):
        return 1
    if name.startswith('META-INF/') and name.endswith(('.RSA', '.DSA', '.EC')):
        return 2
    return 3


def normalize(source: Path, output: Path) -> None:
    if source.suffix != '.aab' or output.suffix != '.aab' or source.resolve() == output.resolve():
        raise ValueError('Use distinct .aab input/output paths, never an APK')
    fingerprints = {}
    with zipfile.ZipFile(source) as original:
        entries = original.infolist()
        names = [entry.filename for entry in entries]
        if len(set(names)) != len(names):
            raise ValueError('Duplicate entries are ambiguous and cannot be normalized')
        if MANIFEST not in names or 'BundleConfig.pb' not in names:
            raise ValueError('Expected a signed Android App Bundle')
        if not any(priority(name) == 1 for name in names) or not any(priority(name) == 2 for name in names):
            raise ValueError('Missing JAR signature metadata')
        for entry in entries:
            path = PurePosixPath(entry.filename)
            if path.is_absolute() or '..' in path.parts:
                raise ValueError('Unsafe entry path')
            if stat.S_ISLNK(entry.external_attr >> 16):
                raise ValueError('Refusing to change symlink semantics')
            if entry.flag_bits & 1:
                raise ValueError('Encrypted bundle entries are unsupported')
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'w') as normalized:
            for entry in sorted(entries, key=lambda item: priority(item.filename)):
                data = original.read(entry)
                fingerprints[entry.filename] = hashlib.sha256(data).digest()
                clean = zipfile.ZipInfo(entry.filename, entry.date_time)
                clean.compress_type = entry.compress_type
                clean.comment = entry.comment
                clean.create_system = 0  # AAB has no install-time POSIX permission semantics.
                clean.external_attr = 0x10 if entry.is_dir() else 0x20
                normalized.writestr(clean, data)
    with zipfile.ZipFile(output) as normalized:
        actual = {entry.filename: hashlib.sha256(normalized.read(entry)).digest()
                  for entry in normalized.infolist()}
        if actual != fingerprints:
            raise ValueError('Entry contents changed; refusing to distribute this bundle')
    print('AAB container normalized; all entry bytes preserved. Strict signature verification is required.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('output', type=Path)
    arguments = parser.parse_args()
    normalize(arguments.source, arguments.output)
