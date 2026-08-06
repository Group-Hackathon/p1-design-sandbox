#!/bin/bash
# Builds the single-file offline bodymap.html (Three.js inlined) and
# distributes it to both mobile apps:
#   - Android: androidp1/app/src/main/assets/bodymap/bodymap.html
#   - iOS:     iosp1/P1/P1/Resources/bodymap.html  (Xcode synchronized folder)
set -euo pipefail
cd "$(dirname "$0")"

python3 - <<'PY'
from pathlib import Path

root = Path.cwd()
template = (root / "template.html").read_text(encoding="utf-8")
three = (root / "vendor" / "three.min.js").read_text(encoding="utf-8")
bodymap = (root / "src" / "bodymap.js").read_text(encoding="utf-8")

for name, payload in (("three.min.js", three), ("bodymap.js", bodymap)):
    if "</" + "script" in payload.lower():
        raise SystemExit(f"{name} contains a closing script tag; cannot inline safely")

html = template.replace("/*__THREE_JS__*/", three).replace("/*__BODYMAP_JS__*/", bodymap)

dist = root / "dist" / "bodymap.html"
dist.parent.mkdir(exist_ok=True)
dist.write_text(html, encoding="utf-8")

targets = [
    root.parent / "androidp1" / "app" / "src" / "main" / "assets" / "bodymap" / "bodymap.html",
    root.parent / "iosp1" / "P1" / "P1" / "Resources" / "bodymap.html",
]
for target in targets:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(html, encoding="utf-8")
    print(f"copied -> {target}")

print(f"built {dist} ({dist.stat().st_size // 1024} KB)")
PY
