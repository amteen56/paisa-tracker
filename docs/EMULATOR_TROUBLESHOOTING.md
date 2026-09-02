# Emulator Troubleshooting — Pixel_9 (API 36)

Record of a real failure on this machine (2026-09-02) and the recovery that worked. Read this
before spending time re-diagnosing a stuck emulator.

---

## ⚠️ State at the time of the PC restart

A `-wipe-data` cold boot **was in progress and had not yet been confirmed** when the machine was
restarted. The corrupt userdata image had already been reset successfully
(`userdata-qemu.img.qcow2` went from 203.9 MB back to 0.2 MB), but boot completion was never
observed.

**After the restart, run the verification in [Recovery](#recovery-the-sequence-that-worked)
step 5 before trying to run the app.**

---

## Symptoms observed

- Android Studio: `Error running 'app'` →
  `Installation failed due to: 'Unknown failure: cmd: Can't find service: package'`
- Device Manager stuck on **"Starting up"** indefinitely
- Emulator window opens but shows a **permanently black screen**
- `adb devices` reports `emulator-5554  offline` — never `device`
- Studio's **Wipe Data** silently fails
- `adb` commands hang instead of returning

`Can't find service: package` simply means Studio tried to install into a device whose system
server never started. It is a symptom, not the cause.

---

## Root cause

The `/data` partition image was corrupt. From the emulator kernel log:

```
init: [libfs_mgr] Failed to mount an un-encryptable, interrupted, or wiped
      partition on /dev/block/vdc at /data options: nomblk_io_submit,errors=panic
init: Failure (reboot suppressed): init_user0_failed
```

`init_user0_failed` means Android could not create user 0's data directory, so boot aborts.
Because reboot is suppressed, the emulator does not crash or report an error — it just sits on a
black screen forever, which is why it looks "stuck" rather than "failed".

Two things made this worse and hid the cause:

1. **Stale lock files** — `hardware-qemu.ini.lock` (a directory) and `multiinstance.lock`, left
   behind when the emulator did not exit cleanly. Studio still believed the AVD was running, so it
   **refused to Wipe Data** — blocking the one action that would have fixed it.
2. **Quick Boot snapshot** — `fastboot.forceFastBoot=yes` with a `default_boot` snapshot captured
   *from the already-broken session*. Every relaunch restored the broken state, so the failure
   reproduced identically each time and looked unfixable.

### Contributing factor: RAM

The AVD was configured with `hw.ramSize=2048`. **2 GB is not enough for an API 36 Google Play
system image** and causes boot thrashing on its own. This was raised to 4096.

### Ruled out

- **Hardware acceleration** — `emulator -accel-check` reported
  `WHPX(10.0.26200) is installed and usable`. Acceleration was never the problem, despite WSL2 /
  Hyper-V being active.

---

## Recovery: the sequence that worked

Paths assume the SDK at `C:\Users\HP\AppData\Local\Android\Sdk`.

### 1. Kill the emulator by PID — not via adb

`adb emu kill` hangs when the guest is wedged, because adb itself is stuck. Kill the processes
directly:

```powershell
Get-Process -Name "qemu*","emulator*","crashpad_handler" -ErrorAction SilentlyContinue |
    Stop-Process -Force
```

### 2. Clear the stale locks

**This is what unblocks Wipe Data.**

```powershell
$avd = "$env:USERPROFILE\.android\avd\Pixel_9.avd"
Remove-Item "$avd\hardware-qemu.ini.lock" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$avd\multiinstance.lock"     -Force -ErrorAction SilentlyContinue
```

### 3. Delete the poisoned Quick Boot snapshot

```powershell
Remove-Item "$avd\snapshots\default_boot" -Recurse -Force -ErrorAction SilentlyContinue
```

### 4. Fix the AVD config

Edited in `%USERPROFILE%\.android\avd\Pixel_9.avd\config.ini`:

| Setting | Was | Now | Why |
|---|---|---|---|
| `hw.ramSize` | 2048 | **4096** | 2 GB cannot boot an API 36 Play image |
| `vm.heapSize` | 228 | **512** | Matches the larger RAM |
| `fastboot.forceFastBoot` | yes | **no** | Stop restoring a bad snapshot |
| `fastboot.forceColdBoot` | yes | **yes** | Force a clean boot every time |

Once the emulator is reliably stable, Quick Boot can be re-enabled for faster startups by
reversing the last two.

### 5. Cold boot with a wipe, from the command line

Launch from the CLI rather than Studio — Studio's embedded launcher hides the diagnostics that
identified this problem.

```powershell
$emu = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"
& $emu -avd Pixel_9 -wipe-data -no-snapshot -gpu swiftshader_indirect -no-boot-anim -verbose -show-kernel
```

`-wipe-data` recreates `/data` from the system image — this is the actual fix for
`init_user0_failed`. It erases everything inside the emulator (apps, settings, accounts); nothing
of value was in it.

Then wait for boot — **allow 3–5 minutes** for a wiped cold boot:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb wait-for-device
& $adb -s emulator-5554 shell getprop sys.boot_completed    # want: 1
```

Only install the app once that prints `1`. Installing earlier produces the misleading
`Can't find service: package` error.

---

## Diagnostic techniques worth reusing

**Tell a hang from a slow boot.** Sample the qemu process; if RAM is frozen at an exact value,
CPU is barely moving, and the userdata qcow2 is not growing, it is hung — not slow:

```powershell
$avd = "$env:USERPROFILE\.android\avd\Pixel_9.avd"
1..6 | ForEach-Object {
  $p = Get-Process -Name "qemu-system-x86_64" -ErrorAction SilentlyContinue
  $q = (Get-Item "$avd\userdata-qemu.img.qcow2").Length / 1MB
  "{0} CPU={1:N0}s RAM={2:N0}MB qcow2={3:N1}MB" -f (Get-Date -Format 'HH:mm:ss'), $p.CPU, ($p.WorkingSet64/1MB), $q
  Start-Sleep 12
}
```

A real cold boot pegs the CPU. During the failure this showed ~2% of one core, RAM pinned at
exactly 1011 MB, and the qcow2 frozen at 203.9 MB — conclusive.

**Never let adb hang the terminal.** Wrap it with a timeout:

```powershell
$job = Start-Job { & $using:adb -s emulator-5554 shell getprop sys.boot_completed }
if (Wait-Job $job -Timeout 30) { Receive-Job $job } else { "adb not responding"; Stop-Job $job }
Remove-Job $job -Force
```

**Read the guest kernel log.** `-verbose -show-kernel` with output redirected to a file is what
surfaced `init_user0_failed`. Studio's embedded emulator never showed it.

**Check acceleration before blaming it:**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -accel-check
```

---

## If it happens again

1. Kill by PID; do not rely on `adb emu kill`.
2. Clear the two lock files.
3. Boot with `-wipe-data` from the CLI with `-verbose -show-kernel`, redirecting to a log.
4. Grep the log for `init:`, `init_user0_failed`, `Failed to mount`, `panic`.
5. If `/data` corruption recurs, **recreate the AVD** rather than repairing it — and prefer a
   `google_apis` image over `google_apis_playstore`, which is lighter and boots faster. The Play
   Store image is only needed for testing Play Services, which this app never uses (it is fully
   offline with no Google dependencies).

### Free RAM matters

At the time of failure the host had only **6.6 GB free of 31.7 GB**, with Android Studio
(2.4 GB), WSL (`vmmemWSL`, ~0.8 GB), Visual Studio, SSMS, Node and browsers all resident. With the
emulator now claiming 4 GB, close what you can first. `wsl --shutdown` reclaims ~800 MB cheaply.
