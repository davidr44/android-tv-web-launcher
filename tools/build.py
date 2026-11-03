#!/usr/bin/env python3
"""Builds Inicio.apk without the Android SDK: javac against the Android 10 framework jar, dx for the dex, our own
manifest compiler (axml.py) and apksig to sign. Needs a JDK 17+ and Python 3; the three jars come from Maven Central
into tools/cache the first time.

    INICIO_KEY_PASSWORD=... python3 tools/build.py
"""
import hashlib
import os
import shutil
import subprocess
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, 'tools', 'cache')
OUT = os.path.join(ROOT, 'out')
APK = os.path.join(ROOT, 'Inicio.apk')
KEYSTORE = os.path.join(ROOT, 'inicio.keystore')
# The signing key stays out of git. Keep inicio.keystore and its password safe: Android only installs an update signed
# with the same key (otherwise uninstall first). Without a keystore, a new one is made with this password.
KEY_PASSWORD = os.environ.get('INICIO_KEY_PASSWORD', '')

MAVEN = 'https://repo1.maven.org/maven2/'
JARS = {
    'android-all-29.jar': 'org/robolectric/android-all/10-robolectric-5803371/android-all-10-robolectric-5803371.jar',
    'dx.jar': 'com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar',
    'apksig.jar': 'com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar',
}
# SHA-256 of the jars the first build used: a different file (a bad download, a changed mirror) stops the build.
SHA256 = {
    'android-all-29.jar': 'a0ad701eedd47bdaefbac1c2cc80dd400c0adea3cccd8a3ad286f79da1ca294c',
    'dx.jar': '1e4b645628e3bdb097b5331d669e177ef235a551582a8c646dbe36865e541907',
    'apksig.jar': '9637078c0016244e4be0941836295365a7e2e5b164c59cb7885783c40460bfee',
}

sys.path.insert(0, os.path.join(ROOT, 'tools'))
import axml  # noqa: E402


def run(*cmd):
    subprocess.run(cmd, check=True)


def jars():
    os.makedirs(CACHE, exist_ok=True)
    for name, path in JARS.items():
        dest = os.path.join(CACHE, name)
        if not os.path.exists(dest):
            print('Downloading', name)
            urllib.request.urlretrieve(MAVEN + path, dest + '.part')
            os.replace(dest + '.part', dest)
        with open(dest, 'rb') as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        if digest != SHA256[name]:
            os.remove(dest)
            sys.exit('%s does not match its known SHA-256 (got %s); deleted, run again' % (name, digest))
    return {name: os.path.join(CACHE, name) for name in JARS}


def main():
    if not KEY_PASSWORD:
        sys.exit('Set INICIO_KEY_PASSWORD to the keystore password (a new inicio.keystore is made with it if missing).')
    j = jars()
    shutil.rmtree(OUT, ignore_errors=True)
    classes = os.path.join(OUT, 'classes')
    os.makedirs(classes)

    sources = [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(ROOT, 'app', 'src')) for f in fs
               if f.endswith('.java')]
    run('javac', '-nowarn', '--release', '8', '-encoding', 'UTF-8', '-cp', j['android-all-29.jar'], '-d', classes,
        *sources)
    dex = os.path.join(OUT, 'classes.dex')
    run('java', '-cp', j['dx.jar'], 'com.android.dx.command.Main', '--dex', '--min-sdk-version=28',
        '--output=' + dex, classes)

    with open(os.path.join(ROOT, 'app', 'AndroidManifest.xml'), encoding='utf-8') as f:
        manifest = axml.compile_manifest(f.read())

    unsigned = os.path.join(OUT, 'unsigned.apk')
    with zipfile.ZipFile(unsigned, 'w') as z:
        z.writestr(zipfile.ZipInfo('AndroidManifest.xml'), manifest, zipfile.ZIP_DEFLATED)
        z.write(dex, 'classes.dex', zipfile.ZIP_DEFLATED)
        web = os.path.join(ROOT, 'web')
        for d, _, fs in sorted(os.walk(web)):
            for name in sorted(fs):
                path = os.path.join(d, name)
                arc = 'assets/web/' + os.path.relpath(path, web).replace(os.sep, '/')
                packed = name.lower().endswith(('.jpg', '.png', '.woff2', '.webp'))
                stored(z, path, arc) if packed else z.write(path, arc, zipfile.ZIP_DEFLATED)

    if not os.path.exists(KEYSTORE):
        run('keytool', '-genkeypair', '-keystore', KEYSTORE, '-storetype', 'PKCS12', '-storepass', KEY_PASSWORD,
            '-alias', 'inicio', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
            '-dname', 'CN=Inicio launcher')
    # apksig 2.3.0 (the one on Maven Central) touches a JDK-internal class even when only signing v2.
    run('java', '--add-exports', 'java.base/sun.security.x509=ALL-UNNAMED', '-cp', j['apksig.jar'],
        os.path.join(ROOT, 'tools', 'Sign.java'), KEYSTORE, KEY_PASSWORD,
        unsigned, APK)
    print('Built', os.path.relpath(APK, ROOT), '(%d KB)' % (os.path.getsize(APK) // 1024))


def stored(z, path, arc):
    """Already-compressed files go in uncompressed and 4-byte aligned, as zipalign would leave them."""
    info = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    header_end = z.fp.tell() + 30 + len(arc.encode('utf-8'))
    info.extra = b'\0' * ((4 - header_end % 4) % 4)
    with open(path, 'rb') as f:
        z.writestr(info, f.read())


if __name__ == '__main__':
    main()
