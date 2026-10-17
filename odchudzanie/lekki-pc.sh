#!/bin/sh
# macOS/Linux: ./lekki-pc.sh raport|zastosuj|kompiluj|przywroc|turbo-on|turbo-off
set -e
cd "$(dirname "$0")"
[ -n "$1" ] || { echo "Użycie: $0 raport|zastosuj|kompiluj|przywroc|turbo-on|turbo-off"; exit 1; }
adb push lekki.sh pakiety.txt /data/local/tmp/
adb shell sh /data/local/tmp/lekki.sh "$1"
[ "$1" = raport ] && adb pull /sdcard/lekki ./raport-tbox
exit 0
