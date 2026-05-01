#!/usr/bin/env python3
"""
i18n_apply.py — Apply auto-extracted i18n migration to Android Compose screens.

Reads build/i18n/extract.json (produced by i18n_extract.py) and:
  1. For non-template findings: replace the literal "text" with
     `stringResource(R.string.<key>)` on the exact line.
  2. Ensure each modified file has the imports for stringResource + R.
  3. Append <string name="key">text</string> entries to values/strings.xml.
  4. Templates are skipped (left for manual review) and reported.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENS = ROOT / "app/src/main/java/network/columba/app/ui/screens"
RES_EN = ROOT / "app/src/main/res/values/strings.xml"
EXTRACT_JSON = ROOT / "build/i18n/extract.json"

STRING_RES_IMPORT = "import androidx.compose.ui.res.stringResource"
R_IMPORT = "import network.columba.app.R"


def xml_escape(s: str) -> str:
    """Escape a string for XML resources."""
    s = s.replace("&", "&amp;")
    s = s.replace("<", "&lt;")
    s = s.replace(">", "&gt;")
    # Android string resource: ' must be \' and " must be \"
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    return s


def kotlin_unescape(s: str) -> str:
    """Reverse Kotlin string escapes to get the runtime value (best-effort)."""
    # The literal contained things like \" and \\; we want to keep them
    # for the XML as-is generally. But \" should become " in the runtime
    # text and Android XML can use straight " inside <string>. Let's
    # simply turn \" -> ", \\ -> \, \n -> newline, \t -> tab.
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            if n == '"':
                out.append('"')
            elif n == "\\":
                out.append("\\")
            elif n == "n":
                out.append("\n")
            elif n == "t":
                out.append("\t")
            elif n == "r":
                out.append("\r")
            else:
                # Keep escape sequence verbatim (e.g. \uXXXX)
                out.append(c)
                out.append(n)
            i += 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def ensure_imports(content: str) -> str:
    """Insert R + stringResource imports if missing, in alphabetical position."""
    lines = content.splitlines(keepends=True)
    has_string_res = any(STRING_RES_IMPORT in ln for ln in lines)
    has_r = any(ln.strip() == R_IMPORT for ln in lines)
    if has_string_res and has_r:
        return content
    # Find import block
    import_indices = [i for i, ln in enumerate(lines) if ln.startswith("import ")]
    if not import_indices:
        return content
    new_lines = list(lines)
    # Insert into sorted position. Simple strategy: append at end of import block.
    last = import_indices[-1]
    insert_at = last + 1
    additions = []
    if not has_string_res:
        additions.append(STRING_RES_IMPORT + "\n")
    if not has_r:
        additions.append(R_IMPORT + "\n")
    # Try to keep imports vaguely sorted: insert each at correct place.
    for line_to_add in additions:
        target = line_to_add.strip()
        inserted = False
        # Find first import that is alphabetically greater
        for i, ln in enumerate(new_lines):
            if ln.startswith("import "):
                if ln.strip() > target:
                    new_lines.insert(i, line_to_add)
                    inserted = True
                    break
        if not inserted:
            new_lines.insert(insert_at, line_to_add)
            insert_at += 1
    return "".join(new_lines)


def apply_file(path: Path, findings: list[dict]) -> tuple[int, list[dict]]:
    """Replace literals in one file. Returns (replacement_count, skipped_templates)."""
    content = path.read_text(encoding="utf-8")
    lines = content.split("\n")
    replaced = 0
    skipped = []
    # Sort by line number
    by_line: dict[int, list[dict]] = {}
    for f in findings:
        if f["template"]:
            skipped.append(f)
            continue
        by_line.setdefault(f["line"], []).append(f)
    for ln_num, items in by_line.items():
        idx = ln_num - 1
        if idx >= len(lines):
            continue
        line = lines[idx]
        for item in items:
            literal = '"' + item["text"] + '"'
            replacement = f"stringResource(R.string.{item['key']})"
            if literal in line:
                # Replace only first occurrence on this line
                line = line.replace(literal, replacement, 1)
                replaced += 1
            else:
                skipped.append({**item, "reason": "literal not found on line"})
        lines[idx] = line
    new_content = "\n".join(lines)
    if replaced > 0:
        new_content = ensure_imports(new_content)
        path.write_text(new_content, encoding="utf-8")
    return replaced, skipped


def append_resources(findings: list[dict]) -> int:
    """Append unique <string> entries to values/strings.xml. Returns count added."""
    text = RES_EN.read_text(encoding="utf-8")
    existing_keys = set(re.findall(r'<string name="([^"]+)">', text))
    additions: list[tuple[str, str]] = []
    seen_in_run: set[str] = set()
    for f in findings:
        if f["template"]:
            continue
        if f["key"] in existing_keys or f["key"] in seen_in_run:
            continue
        seen_in_run.add(f["key"])
        additions.append((f["key"], kotlin_unescape(f["text"])))
    if not additions:
        return 0
    block_lines = ["", "    <!-- Auto-extracted screen strings -->"]
    for key, val in additions:
        block_lines.append(f'    <string name="{key}">{xml_escape(val)}</string>')
    block = "\n".join(block_lines) + "\n"
    new_text = text.replace("</resources>", block + "</resources>", 1)
    RES_EN.write_text(new_text, encoding="utf-8")
    return len(additions)


def main():
    data = json.loads(EXTRACT_JSON.read_text(encoding="utf-8"))
    total_repl = 0
    all_skipped = []
    flat = []
    for filename, findings in data["files"].items():
        # filename may be subpath like "flasher/Foo.kt"
        path = SCREENS / filename
        if not path.exists():
            # Try treating filename as relative to SCREENS
            candidates = list(SCREENS.rglob(filename))
            if candidates:
                path = candidates[0]
            else:
                continue
        n, skipped = apply_file(path, findings)
        total_repl += n
        all_skipped.extend(skipped)
        flat.extend(findings)
        print(f"  {n:3d} replaced  {filename}")
    added = append_resources(flat)
    print()
    print(f"Total replacements: {total_repl}")
    print(f"Resource entries added to values/strings.xml: {added}")
    print(f"Skipped (templates / not-found): {len(all_skipped)}")
    skip_log = ROOT / "build/i18n/skipped.json"
    skip_log.write_text(json.dumps(all_skipped, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"Skipped log: {skip_log.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
