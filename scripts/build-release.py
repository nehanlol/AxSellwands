#!/usr/bin/env python3
"""Build the fork with the official 1.15.0 compiled optional hooks and libraries.

Requires Python 3 and JDK 21. All downloads are pinned and SHA-256 verified.
No private hook API JARs, Maven cache, source stubs, or generated fake APIs are used.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
VERSION = ET.parse(ROOT / 'pom.xml').getroot().find('{http://maven.apache.org/POM/4.0.0}version').text
BUILD = ROOT / 'target' / 'release'
DEPS = BUILD / 'dependencies'
DEPS.mkdir(parents=True, exist_ok=True)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def run(args):
    subprocess.run(args, check=True)


def main():
    # Only reuse optional hooks whose sources still match the official release.
    expected = json.loads((ROOT / 'scripts/upstream-hook-sources.json').read_text())
    actual = {str(f.relative_to(ROOT)).replace('\\', '/'):
              sha(f.read_bytes().replace(b'\r\n', b'\n'))
              for f in (ROOT / 'src/main/java/com/artillexstudios/axsellwands/hooks').rglob('*.java')}
    if actual != expected:
        raise RuntimeError('Hook sources changed; build them with their real API dependencies instead.')
    dependencies = json.loads((ROOT / 'scripts/release-dependencies.json').read_text())
    for name, url, digest in dependencies:
        dest = DEPS / name
        if not dest.exists():
            print('Downloading', name, flush=True)
            urllib.request.urlretrieve(url, dest)
        if sha(dest.read_bytes()) != digest:
            raise RuntimeError('SHA-256 mismatch: ' + name)

    # Match the package relocation of the official shaded JAR at compile time.
    sources = []
    for source in sorted((ROOT / 'src/main/java').rglob('*.java')):
        relative = source.relative_to(ROOT / 'src/main/java')
        if 'hooks' in relative.parts:
            continue
        text = source.read_text(encoding='utf-8')
        for original, relocated in (
            ('com.artillexstudios.axapi', 'com.artillexstudios.axsellwands.libs.axapi'),
            ('org.bstats', 'com.artillexstudios.axsellwands.libs.bstats'),
            ('revxrsal.commands', 'com.artillexstudios.axsellwands.libs.lamp'),
        ):
            text = text.replace(original, relocated)
        destination = BUILD / 'sources' / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text, encoding='utf-8')
        sources.append(destination)
    classes = BUILD / 'classes'
    classes.mkdir(exist_ok=True)
    classpath = os.pathsep.join(str(DEPS / name) for name, _, _ in dependencies)
    args = ['-proc:none', '--release', '21', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes)]
    args += [str(p) for p in sources]
    argfile = BUILD / 'javac.args'
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in args), encoding='utf-8')
    run(['javac', '@' + str(argfile)])

    # Run regression tests against the actual compiled duration class.
    tests = BUILD / 'tests'
    tests.mkdir(exist_ok=True)
    run(['javac', '--release', '21', '-cp', str(classes), '-d', str(tests),
         'src/test/java/com/artillexstudios/axsellwands/timing/WandDurationTest.java'])
    run(['java', '-cp', os.pathsep.join([str(classes), str(tests)]),
         'com.artillexstudios.axsellwands.timing.WandDurationTest'])

    baseline = DEPS / dependencies[0][0]
    with zipfile.ZipFile(baseline) as jar:
        entries = {entry.filename: jar.read(entry) for entry in jar.infolist() if not entry.is_dir()}
    core = 'com/artillexstudios/axsellwands/'
    # Recompile all core classes, retaining only unchanged hooks and shaded libraries.
    entries = {name: data for name, data in entries.items()
               if not (name.startswith(core) and name.endswith('.class')
                       and not name.startswith(core + 'hooks/') and not name.startswith(core + 'libs/'))}
    # Only include files generated for current source files, never stale classes.
    for source in sources:
        relative = source.relative_to(BUILD / 'sources').with_suffix('.class')
        compiled = classes / relative
        entries[relative.as_posix()] = compiled.read_bytes()
        for nested in compiled.parent.glob(compiled.stem + '$*.class'):
            entries[nested.relative_to(classes).as_posix()] = nested.read_bytes()
    for resource in (ROOT / 'src/main/resources').rglob('*'):
        if resource.is_file():
            entries[resource.relative_to(ROOT / 'src/main/resources').as_posix()] = resource.read_bytes().replace(
                b'${project.version}', VERSION.encode())
    for name in list(entries):
        if name.startswith('META-INF/maven/com.artillexstudios/AxSellwands/'):
            del entries[name]
    entries['META-INF/timed-wands-build.json'] = json.dumps({
        'version': VERSION, 'baseline': dependencies[0],
        'recompiled': 'All plugin core classes; unchanged hooks and bundled libraries retained',
    }, indent=2).encode()
    artifact = ROOT / 'target' / ('AxSellwands-' + VERSION + '.jar')
    with zipfile.ZipFile(artifact, 'w', compression=zipfile.ZIP_DEFLATED) as jar:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(info, data)
    with zipfile.ZipFile(artifact) as jar:
        assert jar.testzip() is None
        assert b'${project.version}' not in jar.read('plugin.yml')
        assert core + 'timing/WandTimer.class' in jar.namelist()
        assert core + 'commands/subcommands/Give.class' in jar.namelist()
        # Every reused hook/library entry must be identical to the verified baseline.
        with zipfile.ZipFile(baseline) as original:
            for name in jar.namelist():
                if name.startswith((core + 'hooks/', core + 'libs/')):
                    assert jar.read(name) == original.read(name), name
    digest = sha(artifact.read_bytes())
    artifact.with_suffix('.jar.sha256').write_text(digest + '  ' + artifact.name + '\n')
    print('Built:', artifact)
    print('SHA-256:', digest)


if __name__ == '__main__':
    main()
