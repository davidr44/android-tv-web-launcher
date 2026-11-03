"""Compiles AndroidManifest.xml to Android's binary XML (AXML), the job aapt2 normally does.

Only what this launcher's manifest needs: no app resources (there is no resources.arsc), android: attributes from the
table below, literal strings, numbers, booleans, enums and @android: framework references.
"""
import struct
import xml.etree.ElementTree as ET

ANDROID_NS = 'http://schemas.android.com/apk/res/android'

# name -> (resource id, kind). Ids from android.R.attr (API 29).
ATTRS = {
    'theme': (0x01010000, 'ref'),
    'label': (0x01010001, 'str'),
    'icon': (0x01010002, 'ref'),
    'name': (0x01010003, 'str'),
    'permission': (0x01010006, 'str'),
    'exported': (0x01010010, 'bool'),
    'taskAffinity': (0x01010012, 'str'),
    'clearTaskOnLaunch': (0x01010015, 'bool'),
    'stateNotNeeded': (0x01010016, 'bool'),
    'excludeFromRecents': (0x01010017, 'bool'),
    'launchMode': (0x0101001d, 'enum'),
    'screenOrientation': (0x0101001e, 'enum'),
    'configChanges': (0x0101001f, 'flags'),
    'minSdkVersion': (0x0101020c, 'int'),
    'versionCode': (0x0101021b, 'int'),
    'versionName': (0x0101021c, 'str'),
    'targetSdkVersion': (0x01010270, 'int'),
    'allowBackup': (0x01010280, 'bool'),
    'required': (0x0101028e, 'bool'),
    'hardwareAccelerated': (0x010102d3, 'bool'),
    'banner': (0x010103f2, 'ref'),
    'resumeWhilePausing': (0x010104b2, 'bool'),
    'usesCleartextTraffic': (0x010104ec, 'bool'),
}

ENUMS = {
    'launchMode': {'standard': 0, 'singleTop': 1, 'singleTask': 2, 'singleInstance': 3},
    'screenOrientation': {'unspecified': -1, 'landscape': 0, 'portrait': 1, 'sensorLandscape': 6},
}
FLAGS = {
    'configChanges': {
        'mcc': 0x1, 'mnc': 0x2, 'locale': 0x4, 'touchscreen': 0x8, 'keyboard': 0x10, 'keyboardHidden': 0x20,
        'navigation': 0x40, 'orientation': 0x80, 'screenLayout': 0x100, 'uiMode': 0x200, 'screenSize': 0x400,
        'smallestScreenSize': 0x800, 'density': 0x1000, 'layoutDirection': 0x2000, 'colorMode': 0x4000,
        'fontScale': 0x40000000,
    },
}
# @android:style/... -> id, from android.R.style (API 29).
FRAMEWORK_REFS = {
    '@android:style/Theme.Material.NoActionBar.Fullscreen': 0x0103022f,
    '@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen': 0x0103012a,
}

TYPE_REFERENCE, TYPE_STRING, TYPE_INT_DEC, TYPE_INT_HEX, TYPE_INT_BOOLEAN = 0x01, 0x03, 0x10, 0x11, 0x12


def _chunk(kind, header_size, header_rest, body):
    size = 8 + len(header_rest) + len(body)
    assert header_size == 8 + len(header_rest)
    return struct.pack('<HHI', kind, header_size, size) + header_rest + body


def _string_pool(strings):
    offsets, data = [], b''
    for s in strings:
        offsets.append(len(data))
        units = s.encode('utf-16-le')
        n = len(units) // 2
        assert n < 0x8000, 'string too long'
        data += struct.pack('<H', n) + units + b'\0\0'
    while len(data) % 4:
        data += b'\0'
    header_rest = struct.pack('<IIIII', len(strings), 0, 0, 28 + 4 * len(strings), 0)
    return _chunk(0x0001, 28, header_rest, b''.join(struct.pack('<I', o) for o in offsets) + data)


def _value(attr, raw):
    """(rawValue string or None, dataType, data) for one android: attribute."""
    if attr not in ATTRS:
        raise ValueError('android:%s is not in the table in tools/axml.py' % attr)
    kind = ATTRS[attr][1]
    if kind == 'str':
        return raw, TYPE_STRING, None
    if kind == 'bool':
        if raw not in ('true', 'false'):
            raise ValueError('android:%s must be true or false' % attr)
        return None, TYPE_INT_BOOLEAN, 0xFFFFFFFF if raw == 'true' else 0
    if kind == 'int':
        return None, TYPE_INT_DEC, int(raw)
    if kind == 'enum':
        return None, TYPE_INT_DEC, ENUMS[attr][raw] & 0xFFFFFFFF
    if kind == 'flags':
        bits = 0
        for part in raw.split('|'):
            bits |= FLAGS[attr][part.strip()]
        return None, TYPE_INT_HEX, bits
    if kind == 'ref':
        if raw not in FRAMEWORK_REFS:
            raise ValueError('android:%s="%s": only the framework references in tools/axml.py are known' % (attr, raw))
        return None, TYPE_REFERENCE, FRAMEWORK_REFS[raw]
    raise AssertionError(kind)


def compile_manifest(text):
    root = ET.fromstring(text)

    # Attribute names with resource ids must come first in the pool, in the same order as the resource map.
    used = set()
    for el in root.iter():
        for key in el.attrib:
            if key.startswith('{%s}' % ANDROID_NS):
                used.add(key.split('}', 1)[1])
    res_names = sorted(used, key=lambda n: ATTRS[n][0] if n in ATTRS else 0)
    for n in res_names:
        if n not in ATTRS:
            raise ValueError('android:%s is not in the table in tools/axml.py' % n)
    strings = list(res_names)
    index = {n: i for i, n in enumerate(res_names)}

    def sidx(s):
        if s not in index:
            index[s] = len(strings)
            strings.append(s)
        return index[s]

    body = []
    ns_prefix, ns_uri = sidx('android'), sidx(ANDROID_NS)
    body.append(_chunk(0x0100, 16, struct.pack('<II', 1, 0xFFFFFFFF), struct.pack('<II', ns_prefix, ns_uri)))

    def element(el):
        attrs = []
        for key, raw in el.attrib.items():
            if key.startswith('{%s}' % ANDROID_NS):
                name = key.split('}', 1)[1]
                raw_str, dtype, data = _value(name, raw)
                ns, name_i, resid = ns_uri, index[name], ATTRS[name][0]
            else:
                name, raw_str, dtype, data = key, raw, TYPE_STRING, None
                ns, name_i, resid = 0xFFFFFFFF, sidx(key), 0
            raw_i = sidx(raw_str) if raw_str is not None else 0xFFFFFFFF
            if dtype == TYPE_STRING:
                data = raw_i
            attrs.append((resid, ns, name_i, raw_i, dtype, data))
        attrs.sort(key=lambda a: a[0])  # the framework reads attributes assuming resource-id order
        tag = sidx(el.tag)
        ext = struct.pack('<IIHHHHHH', 0xFFFFFFFF, tag, 20, 20, len(attrs), 0, 0, 0)
        attr_bytes = b''.join(struct.pack('<IIIHBBI', ns, name_i, raw_i, 8, 0, dtype, data)
                              for _, ns, name_i, raw_i, dtype, data in attrs)
        body.append(_chunk(0x0102, 16, struct.pack('<II', 1, 0xFFFFFFFF), ext + attr_bytes))
        for child in el:
            element(child)
        body.append(_chunk(0x0103, 16, struct.pack('<II', 1, 0xFFFFFFFF), struct.pack('<II', 0xFFFFFFFF, tag)))

    element(root)
    body.append(_chunk(0x0101, 16, struct.pack('<II', 1, 0xFFFFFFFF), struct.pack('<II', ns_prefix, ns_uri)))

    pool = _string_pool(strings)
    resmap = _chunk(0x0180, 8, b'', b''.join(struct.pack('<I', ATTRS[n][0]) for n in res_names))
    return _chunk(0x0003, 8, b'', pool + resmap + b''.join(body))
