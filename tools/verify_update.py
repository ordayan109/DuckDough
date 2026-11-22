"""Prove an in-place Android update retains a user-entered quantity on an emulator."""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.norman.duckdough"


def adb(*args: str, check: bool = True) -> str:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)
    return result.stdout.decode("utf-8", errors="replace").strip()


def count_field() -> ET.Element:
    adb("shell", "uiautomator", "dump", "/sdcard/duckdough-update.xml")
    xml = adb("exec-out", "cat", "/sdcard/duckdough-update.xml")
    root = ET.fromstring(xml)
    for node in root.iter("node"):
        if node.get("content-desc") == "מספר פיצות" and "EditText" in node.get("class", ""):
            return node
    diagnostics = ROOT / "outputs"
    diagnostics.mkdir(exist_ok=True)
    (diagnostics / "update-check-ui.xml").write_text(xml, encoding="utf-8")
    image = subprocess.check_output(["adb", "exec-out", "screencap", "-p"])
    (diagnostics / "update-check-ui.png").write_bytes(image)
    raise RuntimeError("Pizza-count field was not found in the native UI; a diagnostic snapshot was saved")


def verify() -> None:
    serial = adb("get-serialno")
    if not serial.startswith("emulator-") or adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise SystemExit("This disposable-data test runs only on an Android emulator, never a physical phone.")
    output = ROOT / "outputs/release"
    release_info = json.loads((output / "RELEASE.json").read_text())
    # The previous debug-test app is disposable; this is BEFORE the signed-release baseline.
    adb("uninstall", PACKAGE, check=False)
    adb("install", str(output / release_info["apk"]))
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    node = count_field()
    coords = [int(value) for value in re.findall(r"\d+", node.attrib["bounds"])]
    adb("shell", "input", "tap", str((coords[0] + coords[2]) // 2), str((coords[1] + coords[3]) // 2))
    adb("shell", "input", "keyevent", "123")
    for _ in range(8):
        adb("shell", "input", "keyevent", "67")
    adb("shell", "input", "text", "7")
    # Dismiss an IME if present, then relaunch to verify persisted rather than
    # transient editor state. Relaunch also handles a device with a hardware keyboard.
    adb("shell", "input", "keyevent", "4")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    if count_field().get("text") != "7":
        raise RuntimeError("The test quantity was not entered successfully")
    # No uninstall or data clear is permitted between these two signed APKs.
    installed = adb("install", "-r", str(ROOT / "app/build/update-probe.apk"))
    if "Success" not in installed:
        raise RuntimeError("Android rejected the in-place signed update")
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
    if count_field().get("text") != "7":
        raise RuntimeError("The saved pizza count was not retained across the update")
    package_info = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(r"versionCode=" + str(release_info["updateProbeVersionCode"]) + r"\b", package_info):
        raise RuntimeError("The installed app is not the expected newer version")
    report = {"result": "passed", "applicationId": PACKAGE,
              "oldVersionCode": release_info["versionCode"], "newVersionCode": release_info["updateProbeVersionCode"],
              "certificateSha256": release_info["certificateSha256"], "savedPizzaCountAfterUpdate": 7,
              "uninstallBetweenSignedVersions": False}
    (output / "UPDATE-VERIFICATION.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    verify()
