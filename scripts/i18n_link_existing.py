#!/usr/bin/env python3
"""
i18n_link_existing.py — Replace literal occurrences of strings that are
already defined in values/strings.xml with stringResource(R.string.<key>).

Useful for shared strings like Cancel, Back, Skip etc. that the
auto-extractor skipped because they already exist as resources.
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENS = ROOT / "app/src/main/java/network/columba/app/ui/screens"
RES_EN = ROOT / "app/src/main/res/values/strings.xml"

STRING_RES_IMPORT = "import androidx.compose.ui.res.stringResource"
R_IMPORT = "import network.columba.app.R"

# UI context line marker
UI_CTX_RE = re.compile(
    r'(?:\bText\(|\btext\s*=|\bcontentDescription\s*=|\blabel\s*=|\bplaceholder\s*=|'
    r'\btitle\s*=|\bsubtitle\s*=|\bsupportingText\s*=|\bsearchPlaceholder\s*=)'
)


def load_resources() -> dict[str, str]:
    """value -> key (latest wins)."""
    text = RES_EN.read_text(encoding="utf-8")
    out = {}
    for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.DOTALL):
        key = m.group(1)
        # Only consider simple values (no XML entities, no placeholders)
        val = m.group(2)
        if "%" in val or "<" in val or "&lt;" in val or "&amp;" in val:
            continue
        # Reverse Android escapes: \' -> ', \" -> "
        val_clean = val.replace("\\'", "'").replace('\\"', '"')
        # Handle \u2026 in resource files
        if "\\u" in val_clean:
            try:
                val_clean = val_clean.encode().decode("unicode_escape")
            except Exception:
                pass
        # Skip very short values to avoid false positives
        if len(val_clean.strip()) < 3:
            continue
        out[val_clean] = key
    return out


def ensure_imports(content: str) -> str:
    lines = content.splitlines(keepends=True)
    has_string_res = any(STRING_RES_IMPORT in ln for ln in lines)
    has_r = any(ln.strip() == R_IMPORT for ln in lines)
    if has_string_res and has_r:
        return content
    import_indices = [i for i, ln in enumerate(lines) if ln.startswith("import ")]
    if not import_indices:
        return content
    new_lines = list(lines)
    additions = []
    if not has_string_res:
        additions.append(STRING_RES_IMPORT + "\n")
    if not has_r:
        additions.append(R_IMPORT + "\n")
    for line_to_add in additions:
        target = line_to_add.strip()
        inserted = False
        for i, ln in enumerate(new_lines):
            if ln.startswith("import ") and ln.strip() > target:
                new_lines.insert(i, line_to_add)
                inserted = True
                break
        if not inserted:
            new_lines.append(line_to_add)
    return "".join(new_lines)


def process_file(path: Path, value_to_key: dict[str, str]) -> int:
    content = path.read_text(encoding="utf-8")
    lines = content.split("\n")
    replaced = 0
    for i, line in enumerate(lines):
        if not UI_CTX_RE.search(line):
            continue
        if "stringResource(" in line and not re.search(r'"[^"]+"', line):
            continue
        # find any literal whose content matches a known resource value
        new_line = line
        for m in re.finditer(r'"([^"\\\n]+)"', line):
            val = m.group(1)
            if val in value_to_key:
                key = value_to_key[val]
                literal = '"' + val + '"'
                replacement = f"stringResource(R.string.{key})"
                if literal in new_line:
                    new_line = new_line.replace(literal, replacement, 1)
                    replaced += 1
        if new_line != line:
            lines[i] = new_line
    if replaced > 0:
        new_content = "\n".join(lines)
        new_content = ensure_imports(new_content)
        path.write_text(new_content, encoding="utf-8")
    return replaced


def main():
    value_to_key = load_resources()
    print(f"Resource values loaded: {len(value_to_key)}")
    total = 0
    for path in sorted(SCREENS.rglob("*.kt")):
        n = process_file(path, value_to_key)
        if n:
            print(f"  {n:3d}  {path.relative_to(SCREENS)}")
            total += n
    print(f"\nTotal extra replacements: {total}")


if __name__ == "__main__":
    main()
