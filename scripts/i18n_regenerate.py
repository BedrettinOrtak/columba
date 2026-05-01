#!/usr/bin/env python3
"""Regenerate values-{tr,ku,fa,ar}/strings.xml from English + translation table."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
EN_FILE = RES / "values/strings.xml"

sys.path.insert(0, str(ROOT / "scripts"))
from translations.data import T  # noqa: E402

LANGS = ("tr", "ku", "fa", "ar")
LANG_INDEX = {"tr": 0, "ku": 1, "fa": 2, "ar": 3}


def xml_escape(s: str) -> str:
    s = s.replace("&", "&amp;")
    s = s.replace("<", "&lt;")
    s = s.replace(">", "&gt;")
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    return s


def parse_en() -> list[tuple[str, str]]:
    text = EN_FILE.read_text(encoding="utf-8")
    return [(m.group(1), m.group(2))
            for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.DOTALL)]


def write_locale(lang: str, en_pairs: list[tuple[str, str]]) -> None:
    out_dir = RES / f"values-{lang}"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "strings.xml"
    idx = LANG_INDEX[lang]
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    translated = 0
    for key, en_val in en_pairs:
        translation = T.get(key, (None, None, None, None))[idx]
        if translation:
            translated += 1
            val = xml_escape(translation)
        else:
            val = en_val
        lines.append(f'    <string name="{key}">{val}</string>')
    lines.append("</resources>")
    lines.append("")
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print(f"  {lang}: {translated}/{len(en_pairs)} translated")


def main() -> None:
    en_pairs = parse_en()
    print(f"Source: {len(en_pairs)} keys, {len(T)} translation entries")
    for lang in LANGS:
        write_locale(lang, en_pairs)


if __name__ == "__main__":
    main()
