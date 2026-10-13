#!/usr/bin/env python3
"""Cross-check Android resource references against the resource table.

Contributors to this project often have no Android SDK available, which means no
compiler to catch a typo'd `R.string.foo` or a deleted drawable that is still
referenced from a layout. Both are first-frame crashes on a launcher.

This script collects every resource defined under `app/src/main/res` and then
checks:

  * `@type/name` references inside every res/**/*.xml file
  * `R.type.name` references inside every app/src/**/*.kt file

Framework references (`@android:...`, `android.R.*`) and well-known library style
families are skipped. Exits non-zero when anything is unresolved, so it can run
in CI.

Usage:
    python3 tools/check_resources.py            # from the repository root
    python3 tools/check_resources.py app/src/main
"""
import collections
import glob
import os
import re
import sys

REF_TYPES = {
    "string", "color", "dimen", "drawable", "layout", "anim", "animator", "array",
    "integer-array", "string-array", "bool", "integer", "plurals", "mipmap", "raw",
    "font", "menu", "navigation", "xml", "interpolator", "transition",
}
VALUE_KINDS = (
    "string|color|dimen|bool|integer|string-array|integer-array|array|style|attr|"
    "plurals|item|declare-styleable"
)
# Style names that belong to a library or the framework and are therefore not
# expected to be defined locally.
STYLE_PREFIXES = ("android:", "Widget.", "Theme.", "TextAppearance.", "Base.",
                  "ShapeAppearance.", "MaterialAlertDialog.", "Animation.")


def collect(root):
    """Return {type: {name, ...}} for everything defined under res/."""
    defined = collections.defaultdict(set)
    res = os.path.join(root, "res")
    for folder in sorted(os.listdir(res)):
        path = os.path.join(res, folder)
        if not os.path.isdir(path):
            continue
        kind = folder.split("-")[0]

        if kind == "values":
            for f in glob.glob(os.path.join(path, "*.xml")):
                txt = open(f, encoding="utf-8").read()
                for m in re.finditer(r"<(%s)\b([^>]*)>" % VALUE_KINDS, txt):
                    tag, attrs = m.group(1), m.group(2)
                    nm = re.search(r'name="([^"]+)"', attrs)
                    if not nm:
                        continue
                    name = nm.group(1)
                    if tag == "item":
                        t = re.search(r'type="([^"]+)"', attrs)
                        tag = t.group(1) if t else "id"
                    if tag == "style":
                        # R.style.Theme_LiteTV_Dialog refers to <style name="Theme.LiteTV.Dialog">
                        defined["style"].add(name.replace(".", "_"))
                        defined["style"].add(name)
                    else:
                        defined[tag].add(name)
            continue

        for f in glob.glob(os.path.join(path, "*")):
            name = os.path.splitext(os.path.basename(f))[0]
            if name.endswith(".9"):
                name = name[:-2]
            defined[kind].add(name)
            if kind in ("layout", "menu", "navigation", "xml"):
                txt = open(f, encoding="utf-8").read()
                for m in re.finditer(r'@\+?id/([A-Za-z0-9_]+)', txt):
                    defined["id"].add(m.group(1))
                for m in re.finditer(r'android:id="@\+?id/([A-Za-z0-9_]+)"', txt):
                    defined["id"].add(m.group(1))
    return defined


def check_xml(path, defined):
    problems = []
    txt = open(path, encoding="utf-8").read()
    for m in re.finditer(r"@(\+?)(\w+)/([A-Za-z0-9_.]+)", txt):
        created, kind, name = m.group(1), m.group(2), m.group(3)
        ref = m.group(0)
        if kind == "android" or ref.startswith("@android:"):
            continue
        if kind == "id":
            # @+id/ declares; @id/ must exist
            if created or name in defined["id"]:
                continue
            problems.append((ref, "id"))
            continue
        if kind == "style":
            if name not in defined["style"] and not name.startswith(STYLE_PREFIXES):
                problems.append((ref, "style"))
            continue
        if kind in REF_TYPES and (kind not in defined or name not in defined[kind]):
            problems.append((ref, kind))
    return problems


def check_kt(path, defined):
    problems = []
    txt = open(path, encoding="utf-8").read()
    for m in re.finditer(r"\bR\.(\w+)\.(\w+)", txt):
        if txt[max(0, m.start() - 9):m.start()].endswith("android."):
            continue  # android.R.attr.state_focused and friends
        kind, name = m.group(1), m.group(2)
        if kind not in defined:
            problems.append((f"R.{kind}.{name}", f"unknown type {kind}"))
        elif name not in defined[kind]:
            problems.append((f"R.{kind}.{name}", kind))
    return problems


def main(root):
    defined = collect(root)
    print("Defined resources:")
    for kind in sorted(defined):
        print(f"  {kind}: {len(defined[kind])}")

    problems = []
    for dirpath, _dirs, names in os.walk(os.path.join(root, "res")):
        for name in sorted(names):
            if name.endswith(".xml"):
                p = os.path.join(dirpath, name)
                problems += [(p, r, t) for r, t in check_xml(p, defined)]
    java = os.path.join(root, "java")
    if os.path.isdir(java):
        for dirpath, _dirs, names in os.walk(java):
            for name in sorted(names):
                if name.endswith(".kt"):
                    p = os.path.join(dirpath, name)
                    problems += [(p, r, t) for r, t in check_kt(p, defined)]

    print(f"\nBroken references: {len(problems)}")
    for p, ref, kind in problems:
        print(f"  [{kind}] {ref}  <- {os.path.relpath(p, root)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main"))
