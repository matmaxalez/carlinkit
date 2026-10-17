# Własny / lżejszy firmware dla Carlinkit Tbox S: analiza

Stan wiedzy: 17.10.2026, po trzech rundach wyszukiwania (Sonnet + 2× Opus) i własnej weryfikacji.
**Potwierdzone** = źródło otwarte i sprawdzone, **przypuszczenie** = wniosek bez twardego dowodu.

## 0. Najważniejsze ustalenia (runda 2)

1. **Firmware Carlinkita robi chiński producent Suding** (Shenzhen Suding Technology,
   marka SDK: Carsyso). Ten sam firmware trafia też do Atoto i innych marek.
   „chelianyi” (车连易) to w firmware kod klienta Carlinkit.
   **Potwierdzone** w zrzucie firmware i w publikacji z punktu 3.
2. **Istnieją publiczne zrzuty firmware bliźniaczych Tboxów**
   na [dumps.tadiphone.dev](https://dumps.tadiphone.dev).
   - Każdy zrzut ma rozpakowane partycje system/vendor/product, `boot.img`
     i `init_boot.img`.
   - Lista zrzutów:
     - [qti/bengal_515](https://dumps.tadiphone.dev/dumps/qti/bengal_515): AutoKit Ambient, Android 13. Najbliższy krewny.
     - [qti/bengal](https://dumps.tadiphone.dev/dumps/qti/bengal): Tbox Mini.
     - [qti/sdm660_64](https://dumps.tadiphone.dev/dumps/qti/sdm660_64): Ambient 660.
     - [doro/qcm6125](https://dumps.tadiphone.dev/dumps/doro/qcm6125) i [doro/dsb0230](https://dumps.tadiphone.dev/dumps/doro/dsb0230): Tbox Plus.
   - **Zrzutu samego Tbox S (X8) nie ma nigdzie.**
3. **Sam sprawdziłem zrzut Ambient (Android 13):**
   - `otacerts.zip` zawiera **publiczny klucz testowy AOSP** (SHA-256 `a40da80a…f5dc`).
     Tbox Ambient przyjmie więc `update.zip` podpisany publicznym kluczem, czyli
     paczkę zrobioną przez nas. Build jest oznaczony `test-keys`.
   - W firmware jest setuidowy plik `/system/xbin/sdsu` (oraz `sdsu_no`).
   - Usługi startowane przez producenta (`/system/etc/main/load_services.json`):
     - `com.suding.speedplay`: **mostek CarPlay/Android Auto**;
     - `com.carsyso.mainui`;
     - `carnetos.usbmedia`;
     - `com.sd.ball`: pływająca kulka;
     - `com.suding.screendot`;
     - `com.suding.datamanager`: „ReportDataService”, czyli wysyłanie danych.
   - Dokładne nazwy pakietów wszystkich dodatków producenta są w `odchudzanie/pakiety.txt`.
4. **Publikacja o urządzeniu z identycznym buildem jak Twój.**
   [The Machine in the Middle](https://github.com/jpuckett11/The-Machine-in-the-Middle)
   (J. Puckett, 1.09.2026) opisuje box Atoto CB6 z buildem `TKQ1.230627.001`,
   firmware Suding i procesorem SM6115. Autor podaje:
   - `ro.adb.secure=0` oraz root przez `sdsu` („adb shell, su, pełny root”);
   - brak zabezpieczeń OEM w procesorze (OEM_ID=0);
   - podpis kluczami testowymi AOSP;
   - działający tryb EDL.

   Procedur i zrzutów nie opublikował. To jedno, niezrecenzowane źródło.
5. **Loader EDL pasujący do tego buildu jest publiczny.** W [bkerler/Loaders](https://github.com/bkerler/Loaders) leży
   `qualcomm/factory/sdm662/0014d0e100000000_d40eee56f3194665_FHPRG.bin`.
   Jego HWID i PK_HASH zgadzają się z tym, co publikacja zalogowała na tym buildzie.
   Przypuszczenie: zadziała też na Tbox S, co pozwoliłoby zrobić pełną kopię zapasową.
6. **FCC:** wniosek Carlinkita [2BKBF-AMBIENT](https://fccid.io/2BKBF-AMBIENT/Letter/FCC-Declaration-on-Model-Differences-7697462)
   wymienia Tbox S1 i S2 jako ten sam projekt płytki co Ambient.
7. **„Wolny kod”, który mógł znaleźć GPT.** Najpewniej jedno z trzech:
   - Ogólnodostępny kod Qualcomma dla tego procesora: kernel 4.19 „bengal”
     na [CodeLinaro](https://git.codelinaro.org/clo/la/kernel/msm-4.19),
     tag `LA.UM.9.15.2.*-KAMORTA.QSSI13*`.
   - Kernele LineageOS dla innych urządzeń z SM6115, np.
     [android_kernel_fxtec_sm6115](https://github.com/LineageOS/android_kernel_fxtec_sm6115).
   - Repozytoria donglów Carlinkit (inny sprzęt).

   To jest kod **procesora**, nie Tboxa. Nie ma w nim konfiguracji płytki Carlinkita
   ani mostka CarPlay, więc nie da się z niego zbudować działającego systemu dla Tboxa.

### Co to zmienia

Nadal **nie ma kodu źródłowego Tboxa**. Ale realna jest droga, którą proponował GPT:
**odchudzony firmware na bazie oryginału**. Prawdopodobnie da się to zrobić nawet
bez flashowania przez EDL:

1. `lekki.cmd raport` sprawdzi trzy rzeczy:
   - czy Twój `otacerts.zip` to klucz AOSP;
   - czy masz `/system/xbin/sdsu`;
   - czy `ro.adb.secure=0`.
2. Jeśli jest `sdsu` (root): kopia zapasowa **wszystkich partycji** przez `dd` na kartę lub komputer.
   To będzie Twoja prywatna kopia zapasowa Tbox S. Bez niej nie ruszamy dalej.
3. Z tej kopii budujemy lżejszy system: usunięcie aplikacji z partycji `system/product/vendor`
   (albo bezpieczniej: moduł Magiska, który je „ukrywa”), zostawiając SpeedPlay i usługi Suding.
4. Wgranie: przez `update.zip` podpisany kluczem testowym (jeśli `otacerts` = AOSP) albo przez
   root. Zablokowany bootloader i AVB sprawdzimy w raporcie (`verifiedbootstate`).

---

## 1. Otwarty kod źródłowy: brak

- **Potwierdzone:** Carlinkit nie udostępnił źródeł kernela (GPL), drzewa urządzenia
  ani vendor blobów dla żadnego Tboxa. Na GitHubie (wyszukiwania „carlinkit”, „cpc200”)
  nie ma firmware Tboxa.
  - `github.com/wojownik208/device_carlinkit_cpc200tbox` jest puste (lipiec 2025).
  - Brak portów LineageOS/AOSP/GSI dla Carlinkit, Ottocast, MMB i Binize.
- Repozytoria, które istnieją, dotyczą czegoś innego:
  - [teab0www/carlinkit-tbox-ultra2-recovery](https://github.com/teab0www/carlinkit-tbox-ultra2-recovery):
    odchudzanie i odblokowanie przez EDL dla **Tbox Ultra 2** (SM6350, Android 15).
    To najlepszy wzór dla nas: lista pakietów i ostrzeżenie o APEX.
  - [riveerxd/AmbientDock](https://github.com/riveerxd/AmbientDock): launcher
    dla zrootowanego **Tbox Ambient** (SM6225, Android 13).
    - Fabryczny launcher to tam `com.example.car_launcher`.
    - Firmware jest podpisany publicznym testowym kluczem AOSP.
  - [SadykovIV/Carlife_on_box](https://github.com/SadykovIV/Carlife_on_box): opis,
    jak na Tbox Ultra działa projekcja (patrz punkt 3).
  - node-CarPlay, FastCarPlay, carlink, catplay to sterowniki **w odwrotną
    stronę**: obsługują dongle Carlinkita z PC/Raspberry. To nie jest firmware Tboxa.

## 2. Firmware i wgrywanie

- **Potwierdzone:** na [carlinkit.com/download.html](https://www.carlinkit.com/download.html)
  są obrazy dla Tbox Ultra, Ambient, Plus, Mini i Basic, ale **nie dla Tbox S/S2**.
  Carlinkit wysyła firmware na prośbę: podajesz model, wersję, auto i telefon.
- Format publicznych paczek: jeden ZIP około 2 GB, który wgrywa się jako `update.zip`
  z karty microSD (FAT32).
  - Według użytkowników XDA w środku są `payload.bin`, `boot.img` i `init_boot.img`,
    czyli typowy pakiet A/B.
  - Paczki są podpisane. Zmodyfikowaną przyjmie Tbox tylko wtedy, gdy podpiszemy
    ją kluczem, któremu ufa (patrz punkt 2a).
- **Root, opisany dla innych modeli** (fragmenty z XDA):
  1. Wyciągasz `boot`/`init_boot` z firmware.
  2. Łatasz je Magiskiem.
  3. Wgrywasz przez **QFIL w trybie EDL (9008)**. To kasuje wszystkie dane, a zły
     obraz (np. z innego wariantu EAU/EM) daje „cegłę” do odratowania tylko przez QFIL.
- **Dla SM6115 (Tbox S):** nikt nie potwierdził odblokowania bootloadera, dostępności
  fastboot, loadera firehose do EDL ani roota.
  - Przypuszczenie: na Ultra 2 zadziałał ogólny loader SM6350 od innego producenta,
    więc podobnie może być z SM6115. Nikt tego nie sprawdził.

## 2a. Kluczowy test: czy Carlinkit użył publicznych kluczy testowych AOSP

Plan „rozpakuj oryginalny firmware → usuń zbędne → złóż → wgraj z microSD”
(proponowany też przez ChatGPT) ma dwie bramki. GPT o nich nie wspomniał:

1. **Podpis `update.zip`.** Recovery sprawdza paczkę kluczami z
   `/system/etc/security/otacerts.zip`. Jeśli to prywatny klucz Carlinkita,
   własnej paczki nie podpiszemy.
2. **AVB / dm-verity.** Zmieniony `system`/`product` musi mieć nowy hash
   w `vbmeta`, podpisany kluczem, który zna bootloader. Przy zablokowanym
   bootloaderze i prywatnym kluczu Tbox się nie uruchomi.

**Dlaczego jest nadzieja:** firmware Tbox Ambient (Android 13) był podpisany
**publicznym kluczem testowym AOSP**
([AmbientDock](https://github.com/riveerxd/AmbientDock)). Tanie chińskie
firmware często używa też testowych kluczy AVB z `external/avb/test/data`.
Jeśli tak jest w Tbox S, obie bramki da się przejść naszym podpisem
i nie trzeba trybu EDL ani rozkręcania.

**Jak sprawdzamy (zero ryzyka, sam odczyt):**
1. `lekki.cmd raport` kopiuje `otacerts.zip`, `framework-res.apk` i właściwości
   bootloadera do `raport-tbox/klucze/`.
2. `python3 narzedzia/sprawdz_klucze.py odchudzanie/raport-tbox/klucze` porównuje
   je z kluczami AOSP:
   - `testkey`: SHA-256 `a40da80a…f5dc`
   - `platform`: SHA-256 `c8a2e9bc…2ab8`
3. Wynik:
   - **klucz AOSP w otacerts** → własny `update.zip` jest wykonalny.
     Potrzebny jeszcze oryginalny firmware Tbox S (od Carlinkita) jako baza.
   - **klucz prywatny** → zostaje tylko odchudzanie przez ADB albo root przez EDL.
   - `ro.boot.verifiedbootstate=orange` → bootloader odblokowany, AVB nie
     blokuje zmienionych obrazów. `green` → zablokowany. Wtedy decyduje,
     czy `vbmeta` w firmware jest podpisany kluczem testowym AVB, co sprawdzimy
     `avbtool info_image` na pliku z paczki.

## 3. Dlaczego czysty Android wyłączyłby obraz w aucie

- Na Tbox Ultra opisano, że projekcja CarPlay/Android Auto to **natywny proces producenta**:
  - startuje wcześnie i przejmuje gadżet USB;
  - koduje ekran (EGL + MediaCodec → H.264);
  - odbiera dotyk z auta.
- Przypuszczenie (bardzo prawdopodobne): jest też chip autoryzacji Apple MFi
  i licencjonowany kod Apple.
- GSI albo LineageOS nie zawiera ani tego procesu, ani poprawek kernela do gadżetu USB.
  Skutek: **Tbox nie pojawi się na ekranie Volvo.**
- Nie znaleziono żadnej relacji z wgrania GSI na taki box.

## 4. Co z tego wynika

| Droga | Ryzyko | Efekt | Rekomendacja |
|---|---|---|---|
| Odchudzenie przez ADB (`odchudzanie/`) | niskie, odwracalne | mniej procesów i RAM, brak animacji, AOT | **zrobić teraz** |
| Własny `update.zip` / root `sdsu` na bazie oryginału | średnie (po pełnej kopii partycji) | prawdziwie lżejszy system, bez zbędnych aplikacji na partycjach | **sprawdzić raportem teraz**; bliźniaczy firmware ma klucz AOSP i `sdsu` |
| Root Magisk przez EDL/QFIL + moduł usuwający aplikacje systemowe | wysokie (kasuje dane, możliwa cegła) | system „lżejszy” na poziomie partycji, pełna kontrola | ewentualnie później, gdy będą firmware S i działający loader EDL |
| Własny ROM / GSI | ogromne | najpewniej koniec CarPlay w aucie | **nie robić** |

## 5. Gdyby kiedyś etap 2 (root)

Kolejność, bez pomijania kroków:

1. Zdobyć od Carlinkita **pełny firmware Tbox S EAU w wersji dla QFIL**
   (e-mail w `email-carlinkit.md`).
2. Sprawdzić, czy da się wejść w EDL (przytrzymany przycisk przy podłączaniu USB-C,
   jak w Ultra 2) i czy jakikolwiek loader firehose SM6115 odpowiada (`bkerler/edl`).
3. **Najpierw zrobić pełny zrzut wszystkich partycji przez EDL** i przechować go
   w dwóch miejscach. Bez tego nie ma powrotu.
4. Dopiero wtedy:
   1. Załatać Magiskiem `init_boot`.
   2. Wgrać tylko `init_boot_a`/`init_boot_b` przez menedżer partycji QFIL.
   3. Zrobić „odchudzenie systemowe” modułem Magiska. Nie modyfikować bezpośrednio `system`/`vendor`.
5. Nigdy nie mieszać wariantów (EAU ≠ EM).

## Źródła

- https://dumps.tadiphone.dev/dumps/qti/bengal_515 (zrzut Ambient A13, sprawdzony)
- https://github.com/jpuckett11/The-Machine-in-the-Middle
- https://github.com/bkerler/Loaders, https://github.com/bkerler/edl
- https://fccid.io/2BKBF-AMBIENT
- https://git.codelinaro.org/clo/la/kernel/msm-4.19
- https://github.com/LineageOS/android_kernel_fxtec_sm6115

- https://www.carlinkit.com/s2
- https://www.carlinkit.com/download.html
- https://www.carlinkit.com/download/a13.html
- https://www.carlinkit.com/download_xx/14.html
- https://www.carlinkit.com/faq_detail/11.html
- https://github.com/teab0www/carlinkit-tbox-ultra2-recovery
- https://github.com/riveerxd/AmbientDock
- https://github.com/SadykovIV/Carlife_on_box
- https://github.com/jamal2362/Mini-Car-Launcher
- https://github.com/lvalen91/CPC200-CCPA_resources
- https://xdaforums.com/t/successfully-rooted-carlinkit-tbox-ambient-led-cpc200-ai-box.4718050/
- https://xdaforums.com/t/question-carlinkit-ai-box-rooted-rom.4490597/
- https://xdaforums.com/t/support-with-carlinkit-x21-android-15.4765838/
- https://technicallyjeff.com/automotive/new-carlinkit-tbox-plus-firmware-split-screen-presets-improved-wireless-and-launcher-support
