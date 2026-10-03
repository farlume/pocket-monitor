#!/usr/bin/env python3
"""Package a built release with matching source, notices and pinned UVC material."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.request
from package_docs import package_documentation

ROOT = Path(__file__).resolve().parents[1]
DEPENDENCIES = {
    "UVCAndroid-1.0.13.aar": (
        "https://repo.maven.apache.org/maven2/com/herohan/UVCAndroid/1.0.13/UVCAndroid-1.0.13.aar",
        "4f1295a6c6b5e8590fb7dde3c60f30a94166db45c2d07efa63c85d5da8bfff17",
    ),
    "UVCAndroid-1.0.13-source.zip": (
        "https://codeload.github.com/shiyinghan/UVCAndroid/zip/refs/tags/1.0.13",
        "84ea3fed61ca23a5b54bd25de13430daf677634b1818055d24e375941c55fb4c",
    ),
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "artifacts/release")
    parser.add_argument("--dependency-dir", type=Path, help="Use existing, hash-verified UVC files")
    args = parser.parse_args()
    apk_dir = ROOT / "androidApp/build/outputs/apk/release"
    metadata = json.loads((apk_dir / "output-metadata.json").read_text())
    elements = metadata["elements"]
    if len(elements) != 1:
        raise SystemExit("Expected one universal release APK")
    element = elements[0]
    version = element["versionName"]
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", version):
        raise SystemExit("Unsupported version name")
    tag = os.environ.get("EXPECTED_TAG", "")
    if tag and tag != f"v{version}":
        raise SystemExit("Tag does not match the built APK version")
    apk_name = element["outputFile"]
    if Path(apk_name).name != apk_name:
        raise SystemExit("APK metadata must name a file in the release output directory")
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    if any(output.iterdir()):
        raise SystemExit("Output directory must be empty to avoid mixing releases")
    prefix = f"pocket-monitor-{version}"
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    shutil.copyfile(apk_dir / apk_name, output / f"{prefix}-release.apk")
    shutil.copyfile(ROOT / "androidApp/build/outputs/bundle/release/androidApp-release.aab",
                    output / f"{prefix}-release.aab")
    subprocess.run(["git", "archive", "--format=zip", f"--prefix={prefix}/",
                    f"--output={output / (prefix + '-source.zip')}", "HEAD"], cwd=ROOT, check=True)
    package_documentation(output / (prefix + "-source.zip"), output, prefix,
                          os.environ.get("GITHUB_REPOSITORY", "farlume/pocket-monitor"), revision)
    shutil.make_archive(str(output / "third-party-licenses"), "zip",
                        ROOT / "androidApp/src/main/assets", "licenses")
    for name, (url, expected_hash) in DEPENDENCIES.items():
        target = output / name
        if args.dependency_dir:
            shutil.copyfile(args.dependency_dir / name, target)
        else:
            with urllib.request.urlopen(url, timeout=60) as response, target.open("wb") as stream:
                shutil.copyfileobj(response, stream)
        if digest(target) != expected_hash:
            raise SystemExit(f"Dependency checksum mismatch: {name}")
    (output / "BUILD_INFO.json").write_text(json.dumps({
        "versionName": version, "versionCode": element["versionCode"], "commit": revision,
        "signingCertificateSha256": os.environ.get("ANDROID_SIGNING_CERT_SHA256", ""),
        "workflowRun": os.environ.get("GITHUB_RUN_ID", "local"),
    }, indent=2) + "\n")
    files = sorted(path for path in output.iterdir() if path.is_file())
    (output / "SHA256SUMS").write_text("".join(f"{digest(path)}  {path.name}\n" for path in files))
    print(f"Packaged {prefix} from {revision}; {len(files)} files checksummed")


if __name__ == "__main__":
    main()
