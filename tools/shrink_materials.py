"""Halves the blurred glass materials (web/assets/materials/*-field-blur, -field-frost, -study-soft) to 960x540.

They are blurred by 14-42 px, so half the pixels look the same once the CSS stretches them back to 1920x1080, and each
one takes a quarter of the GPU memory (2 MB instead of 8 MB decoded). -field-clear (2 px blur) keeps full size.
Needs Pillow:  python3 tools/shrink_materials.py
"""
import glob
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for path in sorted(glob.glob(os.path.join(ROOT, 'web', 'assets', 'materials', '*.jpg'))):
    if not path.endswith(('-field-blur.jpg', '-field-frost.jpg', '-study-soft.jpg')):
        continue
    with Image.open(path) as im:
        if im.size == (960, 540):
            continue
        small = im.convert('RGB').resize((960, 540), Image.LANCZOS)
    small.save(path, 'JPEG', quality=90, subsampling=0, optimize=True)
    print('960x540', os.path.relpath(path, ROOT))
