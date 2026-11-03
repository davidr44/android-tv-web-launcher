#!/usr/bin/env python3
"""Runs the plain-Java tests in app/test (no Android needed): OnScreen, the watch-time span logic.

    python3 tools/test.py
"""
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'out', 'test')

# The classes under test must not use Android APIs, so they compile on a plain JDK.
SOURCES = ['app/src/pt/inicio/launcher/OnScreen.java', 'app/test/pt/inicio/launcher/OnScreenTest.java']


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    os.makedirs(OUT)
    subprocess.run(['javac', '-d', OUT] + [os.path.join(ROOT, s) for s in SOURCES], check=True)
    sys.exit(subprocess.run(['java', '-cp', OUT, 'pt.inicio.launcher.OnScreenTest']).returncode)


if __name__ == '__main__':
    main()
