# Clipboard Access

Controls which apps can read the clipboard. Turning on an app's switch blocks
clipboard reading. Apps can still write to the clipboard.

Licensed under [Apache 2.0](LICENSE).

## Tests

`tests/device_test.sh` runs on a flashed userdebug device. It needs the ClipReader test app, built with `m ClipReader`. The script blocks and unblocks the test app through the app op and through Settings, and checks what the app can read.
