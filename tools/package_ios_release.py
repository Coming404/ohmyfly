#!/usr/bin/env python3
"""Validate and package an unsigned arm64 iPhoneOS Release archive on macOS."""
import argparse
import json
from pathlib import Path
import plistlib
import shutil
import struct
import subprocess
import tempfile


def macho_platforms(binary: bytes) -> set[int]:
    if len(binary) < 32:
        raise ValueError('Truncated Mach-O header')
    magic, cpu, _, filetype, count, command_bytes, _, _ = struct.unpack_from('<8I', binary)
    if magic != 0xFEEDFACF or cpu != 0x0100000C or filetype != 2:
        raise ValueError('Expected a thin arm64 Mach-O executable')
    end = 32 + command_bytes
    if end > len(binary):
        raise ValueError('Truncated Mach-O load commands')
    offset, platforms = 32, set()
    for _ in range(count):
        if offset + 8 > end:
            raise ValueError('Missing load-command header')
        command, size = struct.unpack_from('<2I', binary, offset)
        if size < 8 or offset + size > end:
            raise ValueError('Invalid load-command size')
        if command == 0x32:  # LC_BUILD_VERSION; platform 2 = iOS, 7 = iOS Simulator.
            if size < 24:
                raise ValueError('Truncated LC_BUILD_VERSION')
            platforms.add(struct.unpack_from('<I', binary, offset + 8)[0])
        offset += size
    if offset != end:
        raise ValueError('Load-command count/size mismatch')
    return platforms


def validate_app(app: Path) -> dict:
    info = plistlib.loads((app / 'Info.plist').read_bytes())
    if info.get('CFBundleSupportedPlatforms') != ['iPhoneOS']:
        raise ValueError('IPA must contain an iPhoneOS app, not a simulator app')
    if info.get('DTPlatformName') != 'iphoneos':
        raise ValueError('Unexpected SDK platform')
    if info.get('MinimumOSVersion') not in {'16.0', '16.0.0'}:
        raise ValueError('Expected the declared iOS 16.0 minimum')
    executable = (app / info['CFBundleExecutable']).resolve()
    if not executable.is_relative_to(app.resolve()):
        raise ValueError('Executable path escapes app bundle')
    if macho_platforms(executable.read_bytes()) != {2}:
        raise ValueError('Mach-O platform must be iOS device, never iOS Simulator')
    if (app / '_CodeSignature').exists():
        raise ValueError('Expected an unsigned app intended for user re-signing')
    return {'bundle_id': info['CFBundleIdentifier'], 'minimum_ios': info['MinimumOSVersion'],
            'architecture': 'arm64', 'platform': 'iphoneos', 'configuration': 'Release',
            'signed': False, 'installation': 'Re-sign with your own Apple credentials first.'}


def package(archive: Path, output: Path) -> None:
    archive = archive.resolve()
    archive_info = plistlib.loads((archive / 'Info.plist').read_bytes())
    app = (archive / 'Products' / archive_info['ApplicationProperties']['ApplicationPath']).resolve()
    if not app.is_relative_to(archive / 'Products'):
        raise ValueError('Application path escapes archive Products')
    metadata = validate_app(app)
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ios-package-', dir=output.parent) as directory:
        payload = Path(directory) / 'Payload'
        payload.mkdir()
        shutil.copytree(app, payload / app.name, symlinks=True)
        subprocess.run(['ditto', '-c', '-k', '--keepParent', str(payload),
                        str(output / 'ohmydrift-release-unsigned.ipa')], check=True)
    subprocess.run(['ditto', '-c', '-k', '--keepParent', str(archive),
                    str(output / 'ohmydrift-release-unsigned.xcarchive.zip')], check=True)
    symbols = archive / 'dSYMs'
    if symbols.is_dir():
        subprocess.run(['ditto', '-c', '-k', '--keepParent', str(symbols),
                        str(output / 'ohmydrift-release-dSYMs.zip')], check=True)
    (output / 'build-info.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print('Validated arm64 iPhoneOS Release archive; IPA requires user re-signing.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('output', type=Path)
    arguments = parser.parse_args()
    package(arguments.archive, arguments.output)
