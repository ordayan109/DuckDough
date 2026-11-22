"""Prove an in-place Android update retains a user-entered quantity on an emulator."""
from __future__ import annotations

import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "com.norman.duckdough"
LAUNCHER_DIALOG_RECOVERIES = 0


def adb(*args: str, check: bool = True) -> str:
    result = subprocess.run(["adb", *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=check)
    return result.stdout.decode("utf-8", errors="replace").strip()


def count_field() -> ET.Element:
    global LAUNCHER_DIALOG_RECOVERIES
    for _ in range(4):
        adb("shell", "uiautomator", "dump", "/sdcard/duckdough-update.xml")
        xml = adb("exec-out", "cat", "/sdcard/duckdough-update.xml")
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            # Compose may expose the label on the EditText itself or on its
            # child semantics node after the editor gains focus.
            if "EditText" in node.get("class", "") and any(
                child.get("content-desc") == "מספר פיצות" for child in node.iter("node")
            ):
                return node
        # Recover ONLY the unrelated emulator launcher ANR. An app crash or an
        # unexpected system dialog is still a test failure and is not dismissed.
        launcher_dialog = any("Pixel Launcher" in node.get("text", "") and "responding" in node.get("text", "") for node in root.iter("node"))
        close = next((node for node in root.iter("node") if node.get("text") == "Close app" and node.get("package") == "android"), None)
        if not launcher_dialog or close is None:
            break
        bounds = [int(value) for value in re.findall(r"\d+", close.attrib["bounds"])]
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2))
        LAUNCHER_DIALOG_RECOVERIES += 1
        adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
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
              "uninstallBetweenSignedVersions": False, "emulatorLauncherDialogRecoveries": LAUNCHER_DIALOG_RECOVERIES}
    (output / "UPDATE-VERIFICATION.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report))


if __name__ == "__main__":
    verify()
