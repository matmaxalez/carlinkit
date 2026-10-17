#!/usr/bin/env python3
"""Sprawdza, czy Tbox ufa publicznym kluczom testowym AOSP.

Użycie: python3 sprawdz_klucze.py odchudzanie/raport-tbox/klucze
Wymaga openssl w PATH (do odczytu podpisu framework-res.apk).

Jeśli otacerts.zip zawiera klucz testowy AOSP, to własną paczkę update.zip
można podpisać tym (publicznym) kluczem i Tbox ją przyjmie z karty microSD.
"""
import hashlib
import re
import subprocess
import sys
import zipfile
from pathlib import Path

# SHA-256 odcisków certyfikatów z AOSP build/make/target/product/security.
AOSP_KEYS = {
    "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc": "AOSP testkey (publiczny!)",
    "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8": "AOSP platform (publiczny!)",
}


def pem_to_der(pem: bytes) -> list[bytes]:
    import base64
    blocks = re.findall(rb"-----BEGIN CERTIFICATE-----(.+?)-----END CERTIFICATE-----", pem, re.S)
    return [base64.b64decode(b"".join(b.split())) for b in blocks]


def describe(der: bytes) -> str:
    fp = hashlib.sha256(der).hexdigest()
    return f"{fp}  ->  {AOSP_KEYS.get(fp, 'klucz prywatny producenta')}"


def check_otacerts(path: Path) -> None:
    print(f"\n== {path.name} (klucze akceptowane przy aktualizacji)")
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            data = z.read(name)
            ders = pem_to_der(data) if b"BEGIN CERTIFICATE" in data else [data]
            for der in ders:
                print(f"  {name}: {describe(der)}")


def check_apk(path: Path) -> None:
    print(f"\n== {path.name} (klucz, którym podpisano system)")
    with zipfile.ZipFile(path) as z:
        sigs = [n for n in z.namelist() if re.match(r"META-INF/.+\.(RSA|DSA|EC)$", n)]
        if not sigs:
            print("  Brak podpisu v1 – sprawdź: apksigner verify --print-certs " + str(path))
            return
        for name in sigs:
            out = subprocess.run(
                ["openssl", "pkcs7", "-inform", "DER", "-print_certs"],
                input=z.read(name), capture_output=True, check=False,
            ).stdout
            for der in pem_to_der(out):
                print(f"  {name}: {describe(der)}")


def main() -> None:
    folder = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    found = False
    for f in sorted(folder.glob("*otacerts.zip")):
        check_otacerts(f); found = True
    for f in sorted(folder.glob("*framework-res.apk")):
        check_apk(f); found = True
    props = folder / "bezpieczenstwo.txt"
    if props.exists():
        print("\n== Właściwości bootloadera / AVB")
        print(props.read_text(errors="replace"))
    if not found:
        sys.exit(f"Nie znaleziono plików kluczy w {folder}")


if __name__ == "__main__":
    main()
