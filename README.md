# Lekki Tbox — odchudzenie Carlinkit CPC200-Tbox S

Cel: żeby Carlinkit Tbox S (EAU, 4/64 GB, Android 13, Qualcomm SM6115, Adreno 610)
działał płynnie w Volvo XC60 2024 i XC40 2021 i bez zbędnych dodatków uruchamiał
Waze, Mapy Google, Spotify, YouTube, HBO Max itp.

## Najpierw ważne: czy da się wgrać „czysty Android”?

**Nie da się tego zrobić bezpiecznie i tak, żeby Tbox dalej działał w aucie.** Szczegóły i źródła są w [docs/ROM-analiza.md](docs/ROM-analiza.md). W skrócie:

1. **Nie ma otwartego kodu źródłowego tego firmware.** Carlinkit nie opublikował
   źródeł kernela, drzewa urządzenia ani niczego, z czego da się zbudować ROM.
   Nie istnieje też żaden LineageOS/GSI dla żadnego Tboxa. Jedyne repo
   „device_carlinkit_cpc200tbox” na GitHubie jest puste.
2. **Obraz w Volvo robi zamknięty stos Carlinkita.** Tbox udaje iPhone'a z CarPlay:
   natywny proces producenta przejmuje port USB już przy starcie, koduje ekran do
   H.264 i wysyła go do auta. Prawdopodobnie korzysta też z chipu
   autoryzacji Apple MFi. Czysty Android (GSI/LineageOS) tego nie ma, więc
   **po wgraniu Tbox przestałby się wyświetlać w Volvo**.
3. Firmware dla Tbox S nie jest publicznie dostępny (na carlinkit.com są inne modele).
   Nikt nie opisał też odblokowania bootloadera ani roota dla wersji SM6115.

Da się natomiast **odchudzić fabryczny system bez roota i w pełni odwracalnie**.
To daje praktycznie to samo, co „lekki Android”: mniej procesów w tle,
więcej wolnego RAM, brak animacji i szybszy start aplikacji. Tym zajmuje się
to repo.

## Co jest w repo

| Folder | Co to jest |
|---|---|
| `odchudzanie/` | Skrypt `lekki.sh` (działa na Tboxie przez ADB) + lista pakietów `pakiety.txt` + uruchamiacze dla Windows (`lekki.cmd`) i Mac/Linux (`lekki-pc.sh`). |
| `launcher/` | **Opcjonalny** ekran główny (54 KB, bez bibliotek). Przydaje się tylko wtedy, gdy fabryczny launcher Carlinkita jest ciężki. Nie musisz go używać. |
| `dist/` | Gotowe APK launchera. |
| `docs/` | Analiza ROM/root oraz gotowy e-mail do Carlinkita z prośbą o firmware i źródła. |

## Co robi skrypt `lekki.sh`

- **`raport`**: zbiera listę pakietów, zużycie RAM/CPU, procesy, ekran, temperatury itp.
  Nic nie zmienia.
- **`zastosuj`**:
  - wyłącza zbędne aplikacje z `pakiety.txt` (`pm disable-user`, czyli nie kasuje ich);
  - wyłącza animacje;
  - wyłącza skanowanie Wi-Fi i Bluetooth w tle;
  - blokuje działanie w tle aplikacjom wideo (HBO Max, Netflix itp.). Nawigacja i muzyka zostają nietknięte.
- **`kompiluj`**: kompiluje z wyprzedzeniem (AOT, tryb `speed`) Waze, Mapy,
  Spotify, YouTube i HBO Max. Na słabszym SoC to zauważalnie szybszy start i mniej
  szarpania.
- **`przywroc`**: włącza z powrotem wszystko, co skrypt wyłączył, i przywraca ustawienia.
- **`home <komponent>`**: ustawia wybrany ekran główny.
- **`turbo-on` / `turbo-off`**: tryb stałej wydajności CPU, jeśli firmware go
  obsługuje. Tbox jest mały i się grzeje, więc obserwuj, czy nie zwalnia po dłuższej jeździe.

Wbudowane zabezpieczenia: skrypt **nigdy** nie wyłącza
- rdzenia Androida ani Google Play Services (potrzebne Mapom i YouTube);
- modułów APEX (na Tbox Ultra 2 ich wyłączenie skończyło się bootloopem);
- niczego, co wygląda na mostek CarPlay/Android Auto (`com.carsyso.*`,
  `com.suding.settings`, `carnetos.*`, nazwy z `carplay`/`carlink`/`autokit`/`link` itd.);
- fabrycznego launchera.

## Krok po kroku

### 1. Włącz ADB po Wi-Fi

Port USB Tboxa udaje iPhone'a dla auta, więc **ADB działa tylko przez Wi-Fi**.

1. Połącz Tbox z tym samym Wi-Fi co komputer (np. hotspot z telefonu).
2. Ustawienia → Informacje o urządzeniu → 7× stuknij „Numer kompilacji”.
   - Jeśli to nie działa, spróbuj w ustawieniach Carlinkita „Factory settings”
     z kodem `adbdev` (programista) lub `adbwf` (debugowanie Wi-Fi).
   - Te kody znane są z innych modeli Tboxa i nie są potwierdzone na S.
3. Opcje programisty → **Debugowanie bezprzewodowe** → „Sparuj urządzenie kodem”.
4. Na komputerze (potrzebne [platform-tools](https://developer.android.com/tools/releases/platform-tools)):
   ```
   adb pair 192.168.x.x:PORT_PAROWANIA     (wpisz kod z ekranu)
   adb connect 192.168.x.x:PORT
   adb devices
   ```
   Debugowanie bezprzewodowe wyłącza się po restarcie Tboxa, więc po każdym
   restarcie trzeba zrobić `adb connect` ponownie.

### 2. Zrób raport (nic nie zmienia)

Wszystkie polecenia `lekki` uruchamiaj w folderze `odchudzanie/`.

```
lekki.cmd raport          (Windows)
./lekki-pc.sh raport      (Mac/Linux)
```
Raport ląduje w `odchudzanie/raport-tbox/`. **Prześlij mi ten folder.**
Najważniejsze są `pakiety-*.txt`, `top.txt`, `meminfo.txt` i `carplay-kandydaci.txt`.
Na tej podstawie dopiszę do `pakiety.txt` dokładnie te aplikacje Carlinkita,
które są zbędne na Twoim egzemplarzu. Obecna lista bazuje na bliźniaczych
modelach (Ultra 2, Ambient, Plus) oraz na typowych dodatkach Google.

### 3. Odchudź

```
lekki.cmd zastosuj
lekki.cmd kompiluj
```
Odczekaj 2 minuty, potem `adb reboot`. Sprawdź w aucie: obraz CarPlay,
dotyk, Bluetooth, dźwięk, Waze/Mapy, Spotify, YouTube, HBO Max.

### 4. Coś nie działa? Cofnij

```
lekki.cmd przywroc
```
Pojedynczy pakiet włączysz ręcznie: `adb shell pm enable NAZWA.PAKIETU`.

## Dwa auta: XC60 (pion) i XC40 (podzielony ekran)

- Rozdzielczość obrazu ustala Tbox razem z autem przy podłączeniu (Carlinkit
  podaje „Adaptive”), więc każde Volvo dostaje swój kształt okna. **Nie zmieniamy
  `wm size` ani `wm density`**. Te ustawienia są globalne i dobre dla jednego
  auta zepsułyby obraz w drugim.
- Jeśli używasz launchera z tego repo, sam liczy kolumny z bieżącej szerokości
  okna. W wąskim, pionowym oknie XC60 pokaże 2–3 duże kafle w rzędzie, a w szerszym
  oknie XC40 więcej kafli w poziomie. Wielkość kafli zmienisz w „Ustawienia → Rozmiar kafli”.

## Launcher (opcjonalnie)

`dist/LekkiLauncher-1.0.0.apk`:
- zero bibliotek (żadnego AndroidX/Compose), czyli minimalny RAM;
- bez animacji, czarne tło, brak tapety;
- duże kafle i szuflada wszystkich aplikacji;
- przytrzymanie kafla albo ikony: dodaj, usuń, przesuń, informacje, odinstaluj;
- opcja „Uruchom po starcie” automatycznie otwiera np. Waze po włączeniu auta;
- przy pierwszym starcie sam przypina zainstalowane: Waze, Mapy, Spotify,
  YouTube, YouTube Music, HBO Max, Netflix, Sklep Play.

Instalacja i ustawienie jako ekranu głównego:
```
adb install -r ../dist/LekkiLauncher-1.0.0.apk
lekki.cmd home be.woogitsu.lekki/.HomeActivity
```
Powrót do fabrycznego: Ustawienia → Aplikacje → Aplikacje domyślne → Ekran główny,
albo `adb uninstall be.woogitsu.lekki`.

APK jest podpisany kluczem debug. Jeśli kiedyś zbudujesz go na innym komputerze,
przed instalacją odinstaluj poprzednią wersję.

Budowanie: `cd launcher && ./gradlew assembleRelease` (JDK 17+, Android SDK 35).

## Co dalej (etap 2, tylko po namyśle)

Jedyna realna droga do modyfikacji samego firmware to: firmware Tbox S od Carlinkita,
następnie root Magiskiem przez tryb EDL/QFIL, a potem systemowe usunięcie aplikacji
modułem Magiska. To ryzykowne, kasuje dane i nikt jeszcze nie opisał tego dla
SM6115. Opis i ryzyka są w [docs/ROM-analiza.md](docs/ROM-analiza.md), a e-mail do
Carlinkita w [docs/email-carlinkit.md](docs/email-carlinkit.md).
