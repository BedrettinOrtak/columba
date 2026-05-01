#!/usr/bin/env python3
"""
i18n_extract.py — Scan Compose screens for hardcoded English UI strings.

Strategy:
  - Walk app/src/main/java/network/columba/app/ui/screens/*.kt
  - Find double-quoted string literals that look like UI text
    (contain whitespace OR start with capital and >=4 chars), and
    that appear in known UI contexts (Text, contentDescription, label,
    placeholder, title, subtitle, text =).
  - Skip strings that are template literals ("$x ...") for now —
    flagged separately so we can convert to %1$s placeholders.
  - Skip strings already present in res/values/strings.xml.

Output:
  - A JSON report with per-file findings and a flat candidate list.
  - Prints a summary table.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENS = ROOT / "app/src/main/java/network/columba/app/ui/screens"
RES_EN = ROOT / "app/src/main/res/values/strings.xml"
OUT_DIR = ROOT / "build/i18n"
OUT_DIR.mkdir(parents=True, exist_ok=True)
OUT_JSON = OUT_DIR / "extract.json"

# Already migrated screens (skip)
DONE = {"MainScreen.kt", "SavedPeersScreen.kt", "BlockedUsersScreen.kt", "WelcomeScreen.kt"}

# Existing strings (value -> key)
existing = {}
if RES_EN.exists():
    text = RES_EN.read_text(encoding="utf-8")
    for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.DOTALL):
        existing[m.group(2).strip()] = m.group(1)

# Match Kotlin string literals (no triple-quoted, no escape handling perfection)
LIT = re.compile(r'"((?:[^"\\\n]|\\.)*)"')

# Lines we definitely don't want (imports, package, comments)
SKIP_LINE_RE = re.compile(r'^\s*(package |import |//|\* |/\*|\*/)')

# UI context markers — we'll grab strings on lines containing these
UI_CTX_RE = re.compile(
    r'(?:\bText\(|\btext\s*=|\bcontentDescription\s*=|\blabel\s*=|\bplaceholder\s*=|'
    r'\btitle\s*=|\bsubtitle\s*=|\bsupportingText\s*=|\bheadlineContent\s*=|'
    r'\bsupportingContent\s*=|\boverlineContent\s*=|\bsearchPlaceholder\s*=)'
)


def looks_like_ui(s: str) -> bool:
    """Heuristic: is this likely user-visible text?"""
    if not s:
        return False
    if len(s) < 2:
        return False
    if not any(c.isalpha() for c in s):
        return False
    # Tag/identifier-ish: snake_case, dotted, all-lowercase no spaces -> probably not UI
    if re.fullmatch(r'[a-z][a-z0-9_./-]*', s):
        return False
    # ALL_CAPS_CONST-ish: skip pure short uppercase identifiers (<= 3 chars)
    # but allow longer all-caps strings (CANCEL, CREATE, IMPORT) as they
    # are common Compose button labels.
    if re.fullmatch(r'[A-Z][A-Z0-9_]*', s) and len(s) <= 3:
        return False
    # Has space, OR starts with capital, OR ends with punct
    if " " in s:
        return True
    if s[0].isupper() and len(s) >= 4:
        return True
    if s.endswith(("?", "!", ".", ":", "…", "\\u2026")):
        return True
    return False


def has_template(s: str) -> bool:
    return bool(re.search(r'\$\w|\$\{', s))


def slugify(s: str, prefix: str) -> str:
    base = re.sub(r'\\u\w{4}', '', s)
    base = re.sub(r'[^A-Za-z0-9 ]+', ' ', base)
    words = [w.lower() for w in base.split() if w]
    if not words:
        return f"{prefix}_text"
    key = "_".join(words[:6])[:60]
    return f"{prefix}_{key}"


def screen_prefix(filename: str) -> str:
    name = filename.removesuffix("Screen.kt").removesuffix(".kt")
    # CamelCase -> snake
    s = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', name).lower()
    return s


def extract_file(path: Path) -> list[dict]:
    findings = []
    prefix = screen_prefix(path.name)
    seen_in_file: set[str] = set()
    lines = path.read_text(encoding="utf-8").splitlines()
    for ln, raw in enumerate(lines, 1):
        line = raw
        if SKIP_LINE_RE.match(line):
            continue
        if 'stringResource' in line:
            # already migrated on this line
            continue
        if not UI_CTX_RE.search(line):
            continue
        for m in LIT.finditer(line):
            s = m.group(1)
            if not looks_like_ui(s):
                continue
            if s in existing:
                continue  # already in resources; we just need to swap to stringResource
            templ = has_template(s)
            key = slugify(s, prefix)
            # Disambiguate within file
            if key in seen_in_file:
                # Append short hash suffix
                import hashlib
                suffix = hashlib.md5(s.encode()).hexdigest()[:4]
                key = f"{key}_{suffix}"
            seen_in_file.add(key)
            findings.append({
                "file": str(path.relative_to(ROOT)),
                "line": ln,
                "text": s,
                "key": key,
                "template": templ,
                "context": line.strip()[:120],
            })
    return findings


def main():
    files = sorted(p for p in SCREENS.rglob("*.kt") if p.name not in DONE)
    all_findings = []
    per_file = {}
    for p in files:
        f = extract_file(p)
        if f:
            rel = str(p.relative_to(SCREENS))
            per_file[rel] = f
            all_findings.extend(f)
    # Stats
    print(f"Scanned {len(files)} screens (excluding {len(DONE)} already-migrated).")
    print(f"Found {len(all_findings)} candidate strings across {len(per_file)} files.")
    print(f"Templates (need %s placeholders): {sum(1 for x in all_findings if x['template'])}")
    print()
    # Top files by count
    by_file = sorted(per_file.items(), key=lambda kv: -len(kv[1]))
    print("Top files:")
    for name, items in by_file[:15]:
        print(f"  {len(items):4d}  {name}")
    OUT_JSON.write_text(json.dumps({
        "summary": {
            "total": len(all_findings),
            "files": len(per_file),
            "templates": sum(1 for x in all_findings if x['template']),
        },
        "files": per_file,
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nWrote: {OUT_JSON.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
