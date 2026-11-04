#!/bin/sh
# Copyright (C) 2026 Evolution X
# SPDX-License-Identifier: Apache-2.0
#
# End-to-end checks on a flashed userdebug device with "adb root", using the ClipReader test
# app (m ClipReader). It is never part of the ROM: the test installs it over adb and removes
# it at the end, which also drops its app op mode.
#
#   sh tests/device_test.sh          # all sections
#   sh tests/device_test.sh op       # only some: op ui
#
# The op section sets READ_CLIPBOARD the way Settings does; the ui section taps the app's row
# in Settings. Both check what ClipReader can then read.

ADB=${ADB:-adb}
OUT=${OUT:-$(cd "$(dirname "$0")/../../../.." && pwd)/out/target/product/fairlady}
APP=org.evolution.clipreader
SECTIONS=${*:-op ui}
passed=0
failed=0

sh_dev() { $ADB shell "$@" | tr -d '\r'; }

check() {
    if [ "$2" = 0 ]; then
        echo "ok   $1"
        passed=$((passed + 1))
    else
        echo "FAIL $1"
        failed=$((failed + 1))
    fi
}

# Retries a condition for up to 10 seconds; Settings saves off the main thread.
eventually() {
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
        eval "$1" && return 0
        sleep 1
    done
    return 1
}

want() { case " $SECTIONS " in *" $1 "*) return 0 ;; esac; return 1; }

host_path() {
    case "$ADB" in *.exe) wslpath -w "$1" ;; *) echo "$1" ;; esac
}

# Runs ClipReader in the foreground with the given extras and prints its log line.
run_app() {
    $ADB shell am force-stop $APP
    # Only the main buffer, so the crash check at the end still sees every run.
    $ADB logcat -c -b main
    $ADB shell am start -W -n $APP/.ClipActivity "$@" > /dev/null
    sleep 2
    sh_dev logcat -d -s ClipReader:I | grep -oE '(WROTE|READ) .*' | tail -n 1
}

write_clip() { run_app --es write "$1"; }
read_clip() { run_app; }
mode() { sh_dev cmd appops get $APP READ_CLIPBOARD | grep -oE 'allow|ignore|deny|default' | head -n 1; }

### Setup
$ADB logcat -c -b crash
apk=$OUT/testcases/ClipReader/arm64/ClipReader.apk
[ -f "$apk" ] || apk=$(find "$OUT" -name ClipReader.apk 2> /dev/null | head -n 1)
if [ ! -f "$apk" ]; then
    echo "ClipReader.apk not found; build it with: m ClipReader"
    exit 1
fi
$ADB root > /dev/null
$ADB wait-for-device
$ADB install -r -t "$(host_path "$apk")" > /dev/null
check "ClipReader installs" $?
$ADB shell input keyevent WAKEUP
$ADB shell wm dismiss-keyguard

### App op: what Settings writes and what ClipboardService does with it
if want op; then
    $ADB shell cmd appops set $APP READ_CLIPBOARD allow
    [ "$(write_clip alpha)" = "WROTE alpha" ]
    check "op: an allowed app writes" $?
    [ "$(read_clip)" = "READ text=alpha has=true desc=test" ]
    check "op: an allowed app reads the clip, its presence and description" $?

    $ADB shell cmd appops set $APP READ_CLIPBOARD ignore
    [ "$(read_clip)" = "READ text=null has=false desc=null" ]
    check "op: a blocked app reads nothing" $?
    [ "$(write_clip beta)" = "WROTE beta" ]
    check "op: a blocked app still writes" $?
    [ "$(read_clip)" = "READ text=null has=false desc=null" ]
    check "op: a blocked app can't read its own clip" $?
    [ "$(mode)" = ignore ]
    check "op: the block survives a force stop" $?

    $ADB shell cmd appops set $APP READ_CLIPBOARD allow
    [ "$(read_clip)" = "READ text=beta has=true desc=test" ]
    check "op: unblocking restores reading, and the blocked write took effect" $?

    # Settings counts "deny" as blocked too; AppOps throws instead of returning nothing.
    $ADB shell cmd appops set $APP READ_CLIPBOARD deny
    [ "$(read_clip)" = "READ denied" ]
    check "op: deny also blocks reading" $?
    $ADB shell cmd appops set $APP READ_CLIPBOARD allow
fi

### Settings UI: the app's row writes the op above
# Prints "x y" of the center of the first node whose text or content-desc starts with one
# of the "|"-separated labels.
find_node() {
    $ADB shell uiautomator dump /sdcard/ca_ui.xml > /dev/null
    sh_dev cat /sdcard/ca_ui.xml | python3 -c '
import re, sys, xml.etree.ElementTree as ET
labels = sys.argv[1].split("|")
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    names = (n.get("text") or "", n.get("content-desc") or "")
    if any(name.startswith(l) for name in names for l in labels):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        sys.exit(0)
sys.exit(1)
' "$1"
}

# Taps the node, scrolling the list down until it shows.
tap() {
    for attempt in 1 2 3 4 5 6; do
        if xy=$(find_node "$1"); then
            $ADB shell input tap $xy
            sleep 2
            return 0
        fi
        $ADB shell input swipe 600 2000 600 1000 300
        sleep 1
    done
    return 1
}

# Restarts Settings on the screen, so the list starts at the top instead of where it was left.
# The activity isn't exported; adb root may start it anyway.
open_settings() {
    $ADB shell am start -S -W -n \
        com.android.settings/org.evolution.settings.clipboardaccess.ClipboardAccessActivity \
        > /dev/null
    sleep 3
    # Only third-party apps, so Clip Reader shows without scrolling past system apps.
    tap "Downloaded|Сторонние"
}

ROW="Block clipboard reading for Clip Reader|Запретить чтение буфера обмена для Clip Reader"

if want ui; then
    $ADB shell cmd appops set $APP READ_CLIPBOARD allow
    write_clip gamma > /dev/null
    open_settings

    tap "$ROW"
    check "ui: the row is listed" $?
    eventually '[ "$(mode)" = ignore ]'
    check "ui: checking the row blocks the app" $?
    [ "$(read_clip)" = "READ text=null has=false desc=null" ]
    check "ui: the blocked app reads nothing" $?

    open_settings
    tap "$ROW"
    eventually '[ "$(mode)" = allow ]'
    check "ui: unchecking the row allows it again" $?
    [ "$(read_clip)" = "READ text=gamma has=true desc=test" ]
    check "ui: the allowed app reads again" $?
    $ADB shell input keyevent BACK
fi

### Cleanup, SELinux and crashes
$ADB uninstall $APP > /dev/null
denials=$(sh_dev logcat -d -b all | grep -E 'avc: +denied .*(system_app|clipreader)' | grep -v permissive=1)
[ -z "$denials" ]
check "no SELinux denials for Settings or the test app" $?
[ -n "$denials" ] && echo "$denials" | sed -E 's/.*avc: +denied/  denied/' | sort -u
! sh_dev logcat -d -b crash | grep -qE "com.android.settings|$APP"
check "no crashes" $?

echo "$passed passed, $failed failed"
[ "$failed" -eq 0 ]
