"""Builds web/assets/verses.js from the Berean Standard Bible (CC0), a hand-picked list of short verses:
    python3 tools/verses.py BSB.json web/assets/verses.js
(BSB.json: github.com/scrollmapper/bible_databases, formats/json/BSB.json)
"""
import json, sys
src, out = sys.argv[1], sys.argv[2]
def display(name):
    """The dataset's book names, as usually cited: 'I John' -> '1 John', 'Psalms' -> 'Psalm'."""
    for roman, n in (('III ', '3 '), ('II ', '2 '), ('I ', '1 ')):
        if name.startswith(roman):
            return n + name[len(roman):]
    return {'Psalms': 'Psalm', 'Revelation of John': 'Revelation', 'Song of Solomon': 'Song of Songs'}.get(name, name)


REFS = '''Genesis 1:1; Genesis 28:15; Exodus 14:14; Exodus 15:2; Deuteronomy 31:6; Deuteronomy 31:8; Joshua 1:9;
Joshua 24:15; I Samuel 16:7; I Chronicles 16:34; Nehemiah 8:10; Psalms 4:8; Psalms 16:8; Psalms 18:2; Psalms 19:1;
Psalms 23:1; Psalms 23:4; Psalms 27:1; Psalms 28:7; Psalms 30:5; Psalms 31:24; Psalms 34:8; Psalms 34:18; Psalms 37:4;
Psalms 37:5; Psalms 46:1; Psalms 46:10; Psalms 51:10; Psalms 55:22; Psalms 56:3; Psalms 62:1; Psalms 73:26;
Psalms 86:5; Psalms 90:12; Psalms 91:1; Psalms 91:11; Psalms 100:5; Psalms 103:2; Psalms 107:1; Psalms 118:24;
Psalms 119:105; Psalms 121:1; Psalms 121:2; Psalms 133:1; Psalms 136:1; Psalms 138:8; Psalms 139:14; Psalms 145:18;
Psalms 147:3; Proverbs 3:5; Proverbs 3:6; Proverbs 4:23; Proverbs 15:1; Proverbs 16:3; Proverbs 16:9; Proverbs 17:17;
Proverbs 18:10; Proverbs 22:6; Ecclesiastes 3:1; Isaiah 26:3; Isaiah 40:8; Isaiah 40:29; Isaiah 40:31; Isaiah 41:10;
Isaiah 43:2; Isaiah 54:10; Isaiah 55:8; Jeremiah 17:7; Jeremiah 29:11; Jeremiah 33:3; Lamentations 3:22;
Lamentations 3:23; Micah 6:8; Nahum 1:7; Zephaniah 3:17; Habakkuk 3:19; Zechariah 4:6; Matthew 5:9; Matthew 5:14;
Matthew 6:33; Matthew 6:34; Matthew 7:7; Matthew 11:28; Matthew 19:26; Matthew 28:20; Mark 9:23; Mark 10:27;
Luke 1:37; Luke 6:31; John 1:5; John 3:16; John 8:12; John 8:32; John 10:10; John 11:25; John 13:34; John 14:1;
John 14:6; John 14:27; John 15:5; John 15:12; John 16:33; Acts 1:8; Romans 5:8; Romans 8:28; Romans 8:31;
Romans 12:12; Romans 12:21; Romans 15:13; I Corinthians 13:4; I Corinthians 13:13; I Corinthians 16:14;
II Corinthians 5:7; II Corinthians 5:17; II Corinthians 12:9; Galatians 5:22; Galatians 6:9; Ephesians 2:8;
Ephesians 4:32; Philippians 1:6; Philippians 4:4; Philippians 4:6; Philippians 4:7; Philippians 4:13;
Philippians 4:19; Colossians 3:23; I Thessalonians 5:16; I Thessalonians 5:17; I Thessalonians 5:18; II Timothy 1:7;
Hebrews 11:1; Hebrews 13:5; Hebrews 13:8; James 1:5; James 1:17; I Peter 5:7; I John 1:9; I John 4:8; I John 4:18;
I John 4:19; Revelation of John 21:4'''
data = json.load(open(src, encoding='utf-8'))
books = {b['name']: b for b in data['books']}
verses, skipped = [], []
for ref in [r.strip() for r in REFS.replace('\n', ' ').split(';') if r.strip()]:
    name, cv = ref.rsplit(' ', 1)
    c, v = map(int, cv.split(':'))
    ch = next(x for x in books[name]['chapters'] if x['chapter'] == c)
    text = next(x for x in ch['verses'] if x['verse'] == v)['text'].strip()
    # A verse cut out of a longer speech can carry only one of its quote marks: drop or close the stray one.
    for open_q, close_q in (('\u201c', '\u201d'), ('\u2018', '\u2019')):
        if text.count(open_q) < text.count(close_q) and text.endswith(close_q):
            text = text[:-1]
        if text.count(open_q) > text.count(close_q):
            text = text[1:] if text.startswith(open_q) else text + close_q
    # Verses that start mid-sentence ("being confident of this...") still start with a capital on screen.
    text = text[:1].upper() + text[1:]
    if len(text) > 170:
        skipped.append((ref, len(text))); continue
    verses.append({'ref': '%s %d:%d' % (display(name), c, v), 'text': text})
with open(out, 'w', encoding='utf-8') as f:
    f.write('/* One verse a day (main.js, verseHtml). Text: Berean Standard Bible, public domain (CC0),\n'
            '   via github.com/scrollmapper/bible_databases. Made by tools/verses.py: change the REFS list there,\n'
            '   or edit this file by hand. */\n')
    f.write('window.InicioVerses = ' + json.dumps(verses, ensure_ascii=False, indent=0) + ';\n')
print(len(verses), 'verses; skipped (too long):', skipped)
