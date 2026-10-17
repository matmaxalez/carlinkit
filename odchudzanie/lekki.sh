#!/system/bin/sh
# Odchudzanie Carlinkit Tbox S (Android 13) bez roota.
# Uruchamiane NA urządzeniu jako użytkownik shell (adb shell), np.:
#   adb push lekki.sh pakiety.txt /data/local/tmp/
#   adb shell sh /data/local/tmp/lekki.sh raport
#
# Polecenia:
#   raport      – zapisuje stan systemu do /sdcard/lekki/ (nic nie zmienia)
#   zastosuj    – wyłącza pakiety z pakiety.txt + ustawienia wydajności
#   kompiluj    – kompiluje AOT (dex2oat speed) aplikacje z sekcji [kompiluj]
#   przywroc    – włącza z powrotem wszystko, co wyłączył ten skrypt
#   turbo-on / turbo-off – tryb stałej wydajności (jeśli Power HAL obsługuje)
#   home <pakiet/aktywnosc> – ustawia ekran główny (np. be.woogitsu.lekki/.HomeActivity)
#
# Wszystko jest odwracalne: pakiety są tylko wyłączane (pm disable-user),
# nie odinstalowywane, a lista wyłączonych trafia do /sdcard/lekki/wylaczone.txt.

DIR=$(cd "$(dirname "$0")" && pwd)
LIST="$DIR/pakiety.txt"
OUT=/sdcard/lekki
DISABLED="$OUT/wylaczone.txt"
SETTINGS_BACKUP="$OUT/ustawienia-przed.txt"

# Nigdy nie wyłączamy: rdzeń Androida, Google Play Services (Mapy, YouTube,
# logowanie), moduły APEX (ich wyłączenie dało bootloop na Tbox Ultra 2)
# i usługi Carlinkita, które obsługują CarPlay/Android Auto, Bluetooth i ekran główny.
PROTECTED_RE='^(android|com\.android\.(systemui|settings|phone|shell|providers\..*|inputmethod\..*|bluetooth|networkstack.*|permissioncontroller|packageinstaller|webview|externalstorage|server\..*|location\..*|nfc|se|wifi\..*|connectivity\..*|media\..*|launcher3|adservices.*|sdksandbox|federatedcompute.*|ondevicepersonalization.*)|com\.google\.android\.(gms|gsf|webview|packageinstaller|permissioncontroller|ext\..*|networkstack.*|modulemetadata|trichromelibrary.*|tts)|com\.android\.vending|com\.carsyso\..*|com\.suding\.(main|settings|speedplay|led|bluetooth)|carnetos\..*|com\.example\.car_launcher|vendor\..*)$'
CARPLAY_RE='(carplay|carlink|autokit|zlink|zjinnova|androidauto|gearhead|projection|cplay|airplay|mfi|ecar|link)'

say() { echo "[lekki] $*"; }

pkg_installed() { pm path "$1" >/dev/null 2>&1; }

# Wypisuje pakiety z danej sekcji pliku pakiety.txt (bez komentarzy).
section() {
    awk -v s="[$1]" '
        /^\[/ { on = ($0 == s); next }
        on { sub(/#.*/, ""); gsub(/[ \t\r]/, ""); if (length) print }
    ' "$LIST"
}

raport() {
    mkdir -p "$OUT"
    say "Zbieram raport do $OUT ..."
    getprop > "$OUT/getprop.txt"
    pm list packages -f -u > "$OUT/pakiety-wszystkie.txt"
    pm list packages -s > "$OUT/pakiety-systemowe.txt"
    pm list packages -3 > "$OUT/pakiety-uzytkownika.txt"
    pm list packages -d > "$OUT/pakiety-wylaczone.txt"
    cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME > "$OUT/launchery.txt" 2>&1
    dumpsys meminfo > "$OUT/meminfo.txt" 2>&1
    dumpsys activity processes > "$OUT/procesy.txt" 2>&1
    dumpsys cpuinfo > "$OUT/cpuinfo.txt" 2>&1
    dumpsys display > "$OUT/display.txt" 2>&1
    dumpsys SurfaceFlinger --display-id > "$OUT/surfaceflinger.txt" 2>&1
    dumpsys gfxinfo > "$OUT/gfxinfo.txt" 2>&1
    dumpsys usb > "$OUT/usb.txt" 2>&1
    dumpsys audio > "$OUT/audio.txt" 2>&1
    dumpsys bluetooth_manager > "$OUT/bluetooth.txt" 2>&1
    dumpsys thermalservice > "$OUT/thermal.txt" 2>&1
    dumpsys deviceidle > "$OUT/deviceidle.txt" 2>&1
    cat /proc/cpuinfo > "$OUT/proc-cpuinfo.txt" 2>&1
    cat /proc/meminfo > "$OUT/proc-meminfo.txt" 2>&1
    ls -l /dev/block/by-name/ > "$OUT/partycje.txt" 2>&1
    wm size > "$OUT/wm.txt" 2>&1; wm density >> "$OUT/wm.txt" 2>&1
    settings list global > "$OUT/settings-global.txt"
    settings list secure > "$OUT/settings-secure.txt"
    settings list system > "$OUT/settings-system.txt"
    device_config list > "$OUT/device_config.txt" 2>&1
    top -b -n 1 -m 40 > "$OUT/top.txt" 2>&1
    # Kandydaci na mostek CarPlay – tego NIE ruszamy, ale chcemy wiedzieć, co to jest.
    pm list packages | sed 's/^package://' | grep -iE "$CARPLAY_RE" > "$OUT/carplay-kandydaci.txt"
    say "Gotowe. Ściągnij: adb pull $OUT"
}

disable_pkg() {
    p=$1
    if echo "$p" | grep -qE "$PROTECTED_RE"; then say "POMIJAM (chroniony): $p"; return; fi
    if echo "$p" | grep -qiE "$CARPLAY_RE"; then say "POMIJAM (możliwy mostek CarPlay): $p"; return; fi
    pkg_installed "$p" || return
    if pm path "$p" | grep -q '/apex/'; then say "POMIJAM (moduł APEX): $p"; return; fi
    if pm disable-user --user 0 "$p" >/dev/null 2>&1; then
        grep -qx "$p" "$DISABLED" 2>/dev/null || echo "$p" >> "$DISABLED"
        say "wyłączony: $p"
    else
        say "nie udało się wyłączyć: $p"
    fi
}

backup_settings() {
    [ -f "$SETTINGS_BACKUP" ] && return
    for k in window_animation_scale transition_animation_scale animator_duration_scale \
             wifi_scan_always_enabled ble_scan_always_enabled; do
        echo "$k=$(settings get global $k)" >> "$SETTINGS_BACKUP"
    done
}

zastosuj() {
    [ -f "$LIST" ] || { say "Brak $LIST"; exit 1; }
    mkdir -p "$OUT"
    backup_settings

    say "1/4 Wyłączam zbędne pakiety (sekcja [wylacz])"
    for p in $(section wylacz); do disable_pkg "$p"; done

    say "2/4 Animacje wyłączone (system reaguje natychmiast)"
    settings put global window_animation_scale 0
    settings put global transition_animation_scale 0
    settings put global animator_duration_scale 0

    say "3/4 Mniej skanowania w tle"
    settings put global wifi_scan_always_enabled 0
    settings put global ble_scan_always_enabled 0

    say "4/4 Aplikacje z sekcji [tlo-ogranicz] nie działają w tle"
    for p in $(section tlo-ogranicz); do
        pkg_installed "$p" || continue
        cmd appops set "$p" RUN_ANY_IN_BACKGROUND ignore
        am set-standby-bucket "$p" restricted 2>/dev/null
        echo "tlo:$p" >> "$DISABLED"
        say "ograniczone tło: $p"
    done

    sync
    say "Gotowe. Odczekaj 2 minuty, potem zrestartuj Tboxa: adb reboot"
}

kompiluj() {
    say "Kompilacja AOT (może potrwać kilka minut na aplikację, nie wyłączaj zasilania)"
    for p in $(section kompiluj); do
        pkg_installed "$p" || continue
        say "kompiluję: $p"
        cmd package compile -m speed -f "$p" >/dev/null 2>&1 && say "  ok" || say "  błąd"
    done
    say "Reszta aplikacji: tryb speed-profile w tle"
    cmd package bg-dexopt-job >/dev/null 2>&1
    say "Gotowe."
}

przywroc() {
    if [ -f "$DISABLED" ]; then
        while read -r line; do
            case "$line" in
                tlo:*) p=${line#tlo:}
                       cmd appops set "$p" RUN_ANY_IN_BACKGROUND allow
                       am set-standby-bucket "$p" active 2>/dev/null
                       say "tło przywrócone: $p" ;;
                ?*)    pm enable "$line" >/dev/null 2>&1 && say "włączony: $line" ;;
            esac
        done < "$DISABLED"
        mv "$DISABLED" "$DISABLED.przywrocone-$(date +%Y%m%d%H%M%S)"
    else
        say "Brak listy wyłączonych pakietów – nic do włączenia."
    fi
    if [ -f "$SETTINGS_BACKUP" ]; then
        while IFS='=' read -r k v; do
            if [ "$v" = "null" ] || [ -z "$v" ]; then settings delete global "$k" >/dev/null
            else settings put global "$k" "$v"; fi
        done < "$SETTINGS_BACKUP"
        rm "$SETTINGS_BACKUP"
        say "Ustawienia przywrócone."
    fi
    sync
    say "Odczekaj 2 minuty, potem zrestartuj Tboxa: adb reboot"
}

case "$1" in
    raport)    raport ;;
    zastosuj)  zastosuj ;;
    kompiluj)  kompiluj ;;
    przywroc)  przywroc ;;
    turbo-on)  cmd power set-fixed-performance-mode-enabled true && say "Turbo włączone (obserwuj temperaturę!)" ;;
    home)      [ -n "$2" ] || { say "Podaj komponent, listę znajdziesz w raport: launchery.txt"; exit 1; }
               cmd package set-home-activity "$2" && say "Ekran główny: $2" ;;
    turbo-off) cmd power set-fixed-performance-mode-enabled false && say "Turbo wyłączone" ;;
    *) echo "Użycie: sh $0 raport|zastosuj|kompiluj|przywroc|turbo-on|turbo-off|home <komponent>"; exit 1 ;;
esac
