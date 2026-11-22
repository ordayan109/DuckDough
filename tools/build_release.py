"""Build and verify a permanent-key release without logging private signing material."""
from __future__ import annotations

import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]


def build() -> None:
    raw_bundle = os.environ.get("DUCKDOUGH_SIGNING_BUNDLE", "")
    if not raw_bundle:
        raise SystemExit("DUCKDOUGH_SIGNING_BUNDLE is not configured.")
    bundle = json.loads(raw_bundle)
    expected = (ROOT / "signing/release-certificate.sha256").read_text().strip().lower()
    if bundle.get("certificateSha256", "").lower() != expected:
        raise SystemExit("The configured key does not match the pinned release certificate.")
    for value in (bundle["password"], bundle["keystoreBase64"]):
        if os.environ.get("GITHUB_ACTIONS") == "true":
            print("::add-mask::" + value, flush=True)
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tool_directory = sdk / "build-tools/35.0.0"
    signer = tool_directory / ("apksigner.bat" if os.name == "nt" else "apksigner")
    gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
    output = ROOT / "outputs/release"
    output.mkdir(parents=True, exist_ok=True)
    probe = ROOT / "app/build/update-probe.apk"
    with tempfile.TemporaryDirectory(prefix="duckdough-signing-", dir=os.environ.get("RUNNER_TEMP")) as temporary:
        key_file = Path(temporary) / "release.p12"
        key_file.write_bytes(base64.b64decode(bundle["keystoreBase64"], validate=True))
        key_file.chmod(0o600)
        env = os.environ.copy()
        env.pop("DUCKDOUGH_SIGNING_BUNDLE", None)
        env.update({"DUCKDOUGH_KEYSTORE_PATH": str(key_file),
                    "DUCKDOUGH_STORE_PASSWORD": bundle["password"],
                    "DUCKDOUGH_KEY_PASSWORD": bundle["password"],
                    "DUCKDOUGH_KEY_ALIAS": bundle["alias"]})
        subprocess.run([gradle, "--no-daemon", "assembleRelease", "--stacktrace"], cwd=ROOT, env=env, check=True)
        metadata = json.loads((ROOT / "app/build/outputs/apk/release/output-metadata.json").read_text())
        element = metadata["elements"][0]
        code, version = element["versionCode"], element["versionName"]
        release = output / ("DuckDough-v" + version + ".apk")
        shutil.copy2(ROOT / "app/build/outputs/apk/release" / element["outputFile"], release)
        subprocess.run([gradle, "--no-daemon", "assembleRelease", "-PduckdoughVersionCode=" + str(code + 1),
                        "-PduckdoughVersionName=" + version + "-update-check", "--stacktrace"], cwd=ROOT, env=env, check=True)
        shutil.copy2(ROOT / "app/build/outputs/apk/release/app-release.apk", probe)
        fingerprints = []
        verification = []
        for apk in (release, probe):
            result = subprocess.check_output([str(signer), "verify", "--verbose", "--print-certs", str(apk)], text=True, env=env)
            match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)", result)
            if not match or match.group(1).lower() != expected:
                raise RuntimeError("APK signer does not match the permanent release certificate")
            fingerprints.append(match.group(1).lower())
            verification.append(apk.name + "\n" + result)
        if fingerprints[0] != fingerprints[1]:
            raise RuntimeError("Release and update probe use different signing keys")
    manifest = {"applicationId": "com.norman.duckdough", "versionCode": code, "versionName": version,
                "apk": release.name, "certificateSha256": expected,
                "apkSha256": hashlib.sha256(release.read_bytes()).hexdigest(), "updateProbeVersionCode": code + 1}
    (output / "RELEASE.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "SIGNATURE-VERIFICATION.txt").write_text("\n".join(verification), encoding="utf-8")
    (output / "SHA256SUMS.txt").write_text(manifest["apkSha256"] + "  " + release.name + "\n")
    print("Permanent-key release and update probe verified. Certificate SHA256: " + expected)


if __name__ == "__main__":
    build()
