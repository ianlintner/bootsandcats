#!/usr/bin/env python3

from __future__ import annotations

import re
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1] / "infrastructure" / "k8s"

    kustomizations = list(root.rglob("kustomization.yaml"))

    referenced: set[Path] = set()

    def add_ref(base: Path, rel: str) -> None:
        rel = rel.strip().strip('"').strip("'")
        if not rel:
            return
        referenced.add((base / rel).resolve())

    for kfile in kustomizations:
        text = kfile.read_text(encoding="utf-8")
        base = kfile.parent

        # patches: - path: some.yaml
        for m in re.finditer(r"^[ \t-]*path:\s*([^\n#]+)", text, re.M):
            add_ref(base, m.group(1))

        # resources/components list items containing *.yaml / *.yml
        for m in re.finditer(r"^[ \t-]+([^\n#]+\.ya?ml)\s*$", text, re.M):
            add_ref(base, m.group(1))

        # resources list items pointing to directories
        for m in re.finditer(r"^[ \t-]+([A-Za-z0-9_./-]+)\s*$", text, re.M):
            val = m.group(1).strip()
            if val.endswith((".yaml", ".yml")):
                continue
            if val.startswith((
                "apiVersion",
                "kind",
                "labels",
                "resources",
                "patches",
                "generatorOptions",
                "configMapGenerator",
                "secretGenerator",
                "patchesJson6902",
                "images",
                "replacements",
                "components",
                "namePrefix",
                "nameSuffix",
                "namespace",
                "commonLabels",
                "commonAnnotations",
            )):
                continue

            p = (base / val)
            if p.exists() and p.is_dir():
                referenced.add(p.resolve())

    yaml_files = sorted({*root.rglob("*.yaml"), *root.rglob("*.yml")})
    templates_dir = (root / "templates").resolve()

    unreferenced: list[Path] = []
    for f in yaml_files:
        fr = f.resolve()
        if str(fr).startswith(str(templates_dir) + str(Path("/"))):
            # templates intentionally not deployed
            continue

        # referenced directly
        if fr in referenced:
            continue

        # referenced via a directory resource
        is_under_referenced_dir = any(
            r.is_dir() and str(fr).startswith(str(r) + str(Path("/"))) for r in referenced
        )
        if is_under_referenced_dir:
            continue

        unreferenced.append(f)

    print("UNREFERENCED YAML (not under infrastructure/k8s/templates):")
    for f in unreferenced:
        print(f" - {f.relative_to(root.parent.parent)}")
    print(f"\nCount: {len(unreferenced)}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
