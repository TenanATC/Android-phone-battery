#!/usr/bin/env python3
"""Emulator check for Studio EQ.

Switches the equalizer on through its UI the way a user would, then fails
unless the engine service is running. Prints the engine's log and the audio
server's effect state so a failure (or a quiet pass) can be read from the job
log without downloading artifacts.
"""
import re
import subprocess
import sys
import time

PKG = "com.tenan.equalizer"


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def master_switch():
    """Centre and state of the first toggle in the app: the Equalizer switch."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    for node in re.findall(r"<node [^>]*>", xml):
        if 'checkable="true"' in node and f'package="{PKG}"' in node:
            x1, y1, x2, y2 = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
            return (x1 + x2) // 2, (y1 + y2) // 2, 'checked="true"' in node
    return None


def service_dump():
    return adb("shell", "dumpsys", "activity", "services", PKG)


adb("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
time.sleep(3)
switch = master_switch()
if switch is None:
    sys.exit("Could not find the Equalizer switch in the UI dump")
x, y, checked = switch
if not checked:
    adb("shell", "input", "tap", str(x), str(y))

running = False
for _ in range(10):
    time.sleep(1)
    dump = service_dump()
    running = "EqService" in dump
    if running:
        break

print("--- dumpsys activity services ---")
print(dump)
print("--- engine log ---")
print(adb("logcat", "-d", "-s", "EqService:V", "EffectChain:V", "AndroidRuntime:E"))
print("--- audio_flinger (session 4242 / DynamicsProcessing) ---")
flinger = adb("shell", "dumpsys", "media.audio_flinger")
print("\n".join(l for l in flinger.splitlines() if re.search(r"4242|dynamics", l, re.I)) or "(none)")

if not running:
    sys.exit("EqService is not running after switching the equalizer on")
print("EqService is running")
