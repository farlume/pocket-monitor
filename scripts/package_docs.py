#!/usr/bin/env python3
"""Export portable documentation from the exact committed source archive."""
import argparse
import os
from pathlib import Path
import posixpath
import re
import subprocess
import tempfile
from urllib.parse import quote, unquote, urlsplit, urlunsplit
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
MARKDOWN_TARGET = re.compile(r"(\]\()([^\s)]+)")
HTML_TARGET = re.compile(r"((?:src|href)=[\"'])([^\"']+)([\"'])")
IMAGES = {".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp"}


def prose_parts(text):
    """Preserve fenced code examples verbatim when checking or rewriting links."""
    return re.split(r"(```.*?```|~~~.*?~~~)", text, flags=re.S)


def local_targets(text):
    for part in prose_parts(text):
        if part.startswith(("```", "~~~")):
            continue
        for pattern in (MARKDOWN_TARGET, HTML_TARGET):
            for match in pattern.finditer(part):
                url = urlsplit(match[2])
                if not url.scheme and not url.netloc and url.path:
                    yield unquote(url.path)


def online_readme(text, repository, revision):
    """Standalone Release attachments need commit-pinned links, including images."""
    def absolute(target):
        url = urlsplit(target)
        if url.scheme or url.netloc or not url.path:
            return target
        path = quote(unquote(url.path), safe="/")
        if Path(url.path).suffix.lower() in IMAGES:
            base = f"https://raw.githubusercontent.com/{repository}/{revision}/{path}"
        else:
            base = f"https://github.com/{repository}/blob/{revision}/{path}"
        parsed = urlsplit(base)
        return urlunsplit((parsed.scheme, parsed.netloc, parsed.path, url.query, url.fragment))

    parts = prose_parts(text)
    for i, part in enumerate(parts):
        if part.startswith(("```", "~~~")):
            continue
        part = MARKDOWN_TARGET.sub(lambda m: m[1] + absolute(m[2]), part)
        parts[i] = HTML_TARGET.sub(lambda m: m[1] + absolute(m[2]) + m[3], part)
    return "".join(parts)


def package_documentation(source_zip, output, prefix, repository, revision):
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository):
        raise ValueError("Expected repository in owner/name format")
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Expected a full source commit hash")
    root = prefix + "/"
    with ZipFile(source_zip) as source:
        members = {info.filename[len(root):]: info for info in source.infolist()
                   if info.filename.startswith(root) and not info.is_dir()}
        selected = {name: info for name, info in members.items()
                    if name.startswith("docs/") or name.startswith("androidApp/src/main/assets/licenses/")
                    or ("/" not in name and (name.endswith(".md") or name in {"LICENSE", "NOTICE"}))}
        for required in ("README.md", "README.zh-CN.md"):
            if required not in selected:
                raise ValueError(f"Source archive is missing {required}")
        for name, info in selected.items():
            if not name.endswith(".md") or name.startswith("androidApp/src/main/assets/licenses/"):
                continue
            for target in local_targets(source.read(info).decode("utf-8")):
                resolved = posixpath.normpath(posixpath.join(posixpath.dirname(name), target))
                if resolved not in selected and not any(p.startswith(resolved.rstrip("/") + "/") for p in selected):
                    raise ValueError(f"Documentation archive: {name} links to missing {target}")
        output = Path(output)
        output.mkdir(parents=True, exist_ok=True)
        with ZipFile(output / f"{prefix}-docs.zip", "w", ZIP_DEFLATED) as archive:
            for name, info in sorted(selected.items()):
                archive.writestr(root + name, source.read(info))
        for name in ("README.md", "README.zh-CN.md", "THIRD_PARTY_NOTICES.md", "LICENSE", "NOTICE"):
            if name in selected:
                data = source.read(selected[name])
                if name.endswith(".md"):
                    data = online_readme(data.decode("utf-8"), repository, revision).encode("utf-8")
                (output / name).write_bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/documentation")
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", "farlume/pocket-monitor"))
    args = parser.parse_args()
    if args.output.exists() and any(args.output.iterdir()):
        raise SystemExit("Output directory must be empty")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    prefix = f"pocket-monitor-{revision[:7]}"
    with tempfile.TemporaryDirectory() as temp:
        source = Path(temp) / "source.zip"
        subprocess.run(["git", "archive", "--format=zip", f"--prefix={prefix}/", f"--output={source}", "HEAD"],
                       cwd=ROOT, check=True)
        package_documentation(source, args.output, prefix, args.repository, revision)
    print(f"Documentation exported from {revision} to {args.output}")


if __name__ == "__main__":
    main()
