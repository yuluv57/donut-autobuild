# Donut Relaunch — script chạy ẩn: game Minecraft (Legacy Launcher) tắt vì lỗi đăng nhập hoặc crash
# → tự bấm "Enter the game" trên đúng cửa sổ Legacy Launcher của tài khoản đó.
#
# Phối hợp với mod donut-relaunch (trong thư mục mods):
#   alive-<pid>.txt   mod ghi khi game khởi động  → tiến trình có mod, được phép tự mở lại
#   exit-<pid>.txt    mod ghi khi game thoát       → "restart" (lỗi đăng nhập) | "user" (bạn tự tắt, KHÔNG mở lại)
#                     không có file mà game tắt    → crash → mở lại
#   relaunch-<tên>.txt script ghi trước khi bấm   → mod tự vào lại server cuối của tài khoản đó
# Muốn dừng hẳn 1 tài khoản: tắt game rồi đóng luôn cửa sổ Legacy Launcher của nó.

param([string]$GameDir = (Join-Path $env:APPDATA '.tlauncher\legacy\Minecraft\game'))

$ErrorActionPreference = 'Continue'
$DataDir = Join-Path $GameDir 'donut-relaunch'
New-Item -ItemType Directory -Force -Path $DataDir | Out-Null
$LogFile = Join-Path $DataDir 'watchdog.log'
# pause.txt chứa 1 → tạm không bấm mở lại (vẫn theo dõi, ghi nhận); đổi thành 0 hoặc xoá file → làm tiếp
$PauseFile = Join-Path $DataDir 'pause.txt'
$script:pauseSaid = $false
function Is-Paused {
    try { return (Test-Path $PauseFile) -and ((Get-Content $PauseFile -Raw -ErrorAction Stop).Trim() -eq '1') } catch { return $false }
}

# chỉ chạy 1 bản
$created = $false
$mutex = [System.Threading.Mutex]::new($true, 'Local\DonutRelaunchWatchdog', [ref]$created)
if (-not $created) { exit }

function Log([string]$msg) {
    try {
        if ((Test-Path $LogFile) -and ((Get-Item $LogFile).Length -gt 1MB)) {
            Move-Item $LogFile ($LogFile + '.1') -Force
        }
        Add-Content -Path $LogFile -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $msg) -Encoding UTF8
    } catch { }
}

Add-Type -TypeDefinition @"
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class DRWin {
    public delegate bool EnumProc(IntPtr h, IntPtr p);
    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint x, uint y, uint d, UIntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, UIntPtr e);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT p);
    [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint flags);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool ClipCursor(IntPtr r);
    [DllImport("user32.dll")] public static extern IntPtr ChildWindowFromPointEx(IntPtr h, POINT p, uint flags);
    [DllImport("user32.dll")] public static extern int MapWindowPoints(IntPtr from, IntPtr to, ref POINT p, uint n);
    [DllImport("psapi.dll")] public static extern bool EmptyWorkingSet(IntPtr h);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }

    public static IntPtr[] WindowsOf(uint pid) {
        List<IntPtr> l = new List<IntPtr>();
        EnumWindows(delegate (IntPtr h, IntPtr p) {
            uint q; GetWindowThreadProcessId(h, out q);
            if (q == pid && IsWindowVisible(h)) l.Add(h);
            return true;
        }, IntPtr.Zero);
        return l.ToArray();
    }
    public static IntPtr[] AllVisible() {
        List<IntPtr> l = new List<IntPtr>();
        EnumWindows(delegate (IntPtr h, IntPtr p) { if (IsWindowVisible(h)) l.Add(h); return true; }, IntPtr.Zero);
        return l.ToArray();
    }
    public static string Title(IntPtr h) { StringBuilder sb = new StringBuilder(512); GetWindowText(h, sb, 512); return sb.ToString(); }
    public static uint Dpi(IntPtr h) {
        try { return GetDpiForWindow(h); } catch { return 96; }
    }
    [DllImport("user32.dll")] private static extern uint GetDpiForWindow(IntPtr h);
}
"@
[DRWin]::SetProcessDPIAware() | Out-Null

# Nút "Enter the game" nằm giữa cửa sổ, thấp hơn tâm vùng trong khoảng 47 px (ở 100% DPI). Thử lệch dần nếu bấm hụt.
$ButtonOffsets = @(47, 54, 40, 61, 33)
# Mở lại liên tục mà vẫn lỗi → chờ giãn dần trước lần bấm sau (giây): 0, 1, 2, 5, 10, 15 phút…
$Backoff = @(0, 60, 120, 300, 600, 900)

function Get-Games {
    $r = @{}
    try {
        $procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction Stop
    } catch { return $r }
    foreach ($p in $procs) {
        $cl = $p.CommandLine          # có chứa mã đăng nhập → KHÔNG bao giờ ghi ra log
        if (-not $cl) { continue }
        if ($cl -notmatch '--username') { continue }
        if ($cl.IndexOf($GameDir, [StringComparison]::OrdinalIgnoreCase) -lt 0) { continue }
        $m = [regex]::Match($cl, '--username\s+"?([^"\s]+)')
        $u = '?'
        if ($m.Success) { $u = $m.Groups[1].Value }
        $r[[int]$p.ProcessId] = [pscustomobject]@{ Pid = [int]$p.ProcessId; Parent = [int]$p.ParentProcessId; User = $u }
    }
    $cl = $null
    return $r
}

# ưu tiên CPU của game + launcher: mặc định BelowNormal để máy ưu tiên việc khác của bạn (game chỉ dùng CPU còn thừa).
# Đổi trong priority.txt: Idle / BelowNormal / Normal. Áp dụng cho game mới mở; đổi file thì áp lại cho game đang chạy.
$PriorityFile = Join-Path $DataDir 'priority.txt'
$script:prioStamp = $null
$script:prioWant = $null
$script:prioDone = @{}
function Get-WantedPriority {
    $want = 'BelowNormal'
    try {
        if (Test-Path $PriorityFile) {
            $v = (Get-Content $PriorityFile -Raw -ErrorAction Stop).Trim()
            if ($v -in @('Idle', 'BelowNormal', 'Normal', 'AboveNormal')) { $want = $v }
        } else {
            Set-Content -Path $PriorityFile -Value 'BelowNormal' -Encoding ASCII
        }
    } catch { }
    return $want
}
function Set-GamePriority($g) {
    try {
        $stamp = if (Test-Path $PriorityFile) { (Get-Item $PriorityFile).LastWriteTimeUtc } else { $null }
        if ($null -eq $script:prioWant -or $stamp -ne $script:prioStamp) {
            $script:prioWant = Get-WantedPriority
            $script:prioStamp = if (Test-Path $PriorityFile) { (Get-Item $PriorityFile).LastWriteTimeUtc } else { $null }
            $script:prioDone = @{}
        }
        # chỉ hạ ưu tiên của game. Launcher giữ Normal: lúc ẩn nó gần như không dùng CPU, còn lúc mở game mà bị hạ ưu tiên
        # trong khi máy đang bận (Valorant…) thì nó chuẩn bị game rất chậm (hơn 1 phút)
        foreach ($pair in @(@($g.Pid, $script:prioWant), @($g.Parent, 'Normal'))) {
            $id = $pair[0]; $want = $pair[1]
            if ($script:prioDone[$id] -eq $want) { continue }
            $p = Get-Process -Id $id -ErrorAction SilentlyContinue
            if (-not $p) { continue }
            if ([string]$p.PriorityClass -ne $want) { $p.PriorityClass = $want }
            $script:prioDone[$id] = $want
            if ($id -eq $g.Pid) { Log ('  ' + $g.User + ': ưu tiên CPU = ' + $want) }
        }
    } catch { }
}

# Legacy Launcher nằm ẩn suốt lúc game chạy nhưng vẫn giữ ~250 MB RAM mỗi cửa sổ → 90 giây sau khi game mở, rồi mỗi 30 phút,
# trả phần RAM nó không dùng lại cho Windows (EmptyWorkingSet). Launcher chỉ chạy lại khi cần mở game nên không ảnh hưởng gì.
$script:trimAt = @{}
function Trim-Launcher($g) {
    try {
        $id = $g.Parent
        $now = Get-Date
        if (-not $script:trimAt.ContainsKey($id)) { $script:trimAt[$id] = $now.AddSeconds(90); return }
        if ($now -lt $script:trimAt[$id]) { return }
        $script:trimAt[$id] = $now.AddMinutes(30)
        if ((Find-LauncherWindow $id) -ne [IntPtr]::Zero) { return }   # launcher đang hiện (đang mở game) → để yên
        $p = Get-Process -Id $id -ErrorAction SilentlyContinue
        if (-not $p) { return }
        $before = [int]($p.WorkingSet64 / 1MB)
        [DRWin]::EmptyWorkingSet($p.Handle) | Out-Null
        $p.Refresh()
        $after = [int]($p.WorkingSet64 / 1MB)
        if (-not $script:trimSaid) { $script:trimSaid = @{} }
        if (-not $script:trimSaid.ContainsKey($id)) {
            $script:trimSaid[$id] = $true
            Log ('  ' + $g.User + ': trả RAM thừa của Legacy Launcher (pid ' + $id + ') ' + $before + ' → ' + $after + ' MB')
        }
    } catch { }
}

# mỗi 10 phút ghi CPU/RAM của từng nick (như cột Task Manager) vào usage.log để so trước/sau khi tối ưu
$UsageFile = Join-Path $DataDir 'usage.log'
$script:usageAt = (Get-Date).AddSeconds(30)
$script:cpuPrev = @{}
function Log-Usage($games) {
    $now = Get-Date
    if ($now -lt $script:usageAt) { return }
    try {
        $cores = [Environment]::ProcessorCount
        $parts = New-Object System.Collections.ArrayList
        $sumCpu = 0.0; $sumWs = 0.0; $sumLl = 0.0; $haveCpu = $false
        $seen = @{}
        foreach ($g in @($games.Values | Sort-Object User)) {
            $p = Get-Process -Id $g.Pid -ErrorAction SilentlyContinue
            if (-not $p) { continue }
            $q = Get-Process -Id $g.Parent -ErrorAction SilentlyContinue
            $cpuMs = $p.TotalProcessorTime.TotalMilliseconds
            if ($q) { $cpuMs += $q.TotalProcessorTime.TotalMilliseconds }
            $seen[$g.Pid] = @($cpuMs, $now)
            $cpuTxt = '?'
            if ($script:cpuPrev.ContainsKey($g.Pid)) {
                $prev = $script:cpuPrev[$g.Pid]
                $wall = ($now - $prev[1]).TotalMilliseconds
                if ($wall -gt 0) {
                    $c = ($cpuMs - $prev[0]) * 100.0 / $wall / $cores
                    $sumCpu += $c; $haveCpu = $true
                    $cpuTxt = ('{0:0.0}%' -f $c)
                }
            }
            $ws = $p.WorkingSet64 / 1MB
            $ll = if ($q) { $q.WorkingSet64 / 1MB } else { 0 }
            $sumWs += $ws; $sumLl += $ll
            [void]$parts.Add(('{0}: CPU {1}, RAM {2:0} MB (cam kết {3:0} MB), launcher {4:0} MB' -f $g.User, $cpuTxt, $ws, ($p.PrivateMemorySize64 / 1MB), $ll))
        }
        $script:cpuPrev = $seen
        if ($parts.Count -eq 0) { $script:usageAt = $now.AddMinutes(10); return }
        $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
        $tot = $os.TotalVisibleMemorySize / 1MB; $free = $os.FreePhysicalMemory / 1MB
        $line = ($parts -join ' | ') + (' || tổng ' + $parts.Count + ' nick: ')
        if ($haveCpu) { $line += ('CPU {0:0.0}%, ' -f $sumCpu) }
        $line += ('RAM game {0:0} MB + launcher {1:0} MB; RAM cả máy đang dùng {2:0}% ({3:0.0}/{4:0.0} GB)' -f $sumWs, $sumLl, (($tot - $free) * 100 / $tot), ($tot - $free), $tot)
        if ($haveCpu) {
            if ((Test-Path $UsageFile) -and ((Get-Item $UsageFile).Length -gt 1MB)) { Move-Item $UsageFile ($UsageFile + '.1') -Force }
            Add-Content -Path $UsageFile -Value ((Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $line) -Encoding UTF8
            $script:usageAt = $now.AddMinutes(10)
        } else {
            $script:usageAt = $now.AddMinutes(1)   # lần đầu chỉ lấy mốc CPU → 1 phút sau ghi
        }
    } catch { $script:usageAt = $now.AddMinutes(10) }
}

# CPU của launcher: biết nó đang bận (đăng nhập / chuẩn bị game) hay đang rảnh chờ bấm.
# BusyAt = lần cuối thấy nó dùng ≥ 15% của 1 nhân CPU.
$script:llAct = @{}
function Update-LauncherActivity([int]$llPid) {
    $p = Get-Process -Id $llPid -ErrorAction SilentlyContinue
    if (-not $p) { return $null }
    $t = Get-Date
    $cpu = $p.TotalProcessorTime.TotalMilliseconds
    $a = $script:llAct[$llPid]
    if (-not $a) {
        $a = [pscustomobject]@{ Cpu = $cpu; At = $t; BusyAt = $t.AddSeconds(-9); Rate = 0.0 }   # đo thêm ≥ 3 giây rồi mới được bấm
        $script:llAct[$llPid] = $a
        return $a
    }
    $dt = ($t - $a.At).TotalMilliseconds
    if ($dt -ge 1000) {
        $a.Rate = ($cpu - $a.Cpu) * 100.0 / $dt
        if ($a.Rate -ge 15) { $a.BusyAt = $t }
        $a.Cpu = $cpu; $a.At = $t
    }
    return $a
}

# ===== Mở lại game bằng cách khởi động lại launcher (không cần bấm chuột) =====
# Bấm chuột giả lập bị Windows / anti-cheat (Valorant…) chặn khi bạn đang chơi game khác, lại còn giật chuột của bạn.
# Cách mới: tắt launcher cũ của nick đó rồi mở launcher mới với tham số --username <nick> --launch → launcher tự chọn đúng nick,
# tự đăng nhập và tự vào game sau 3 giây. Tham số dòng lệnh này launcher KHÔNG lưu lại vào tl.properties.
# Đồng thời tl.properties được chỉnh để game dùng tham số Java gọn (bỏ -Xms 1–2 GB + AlwaysPreTouch giữ chặt RAM, bỏ DisableExplicitGC).
$McDir = Split-Path $GameDir -Parent
$TlProps = Join-Path $McDir 'tl.properties'
$LauncherCfg = Join-Path $DataDir 'launcher.txt'
$DefaultJavaArgs = '-XX:+UseG1GC -XX:MaxGCPauseMillis=50 -XX:+ParallelRefProcEnabled -XX:G1ReservePercent=20 -XX:G1HeapRegionSize=8M -XX:InitiatingHeapOccupancyPercent=15 -XX:+UseStringDeduplication -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:ParallelGCThreads=2 -XX:ConcGCThreads=1 -Xms256M'
function Get-LauncherConfig {
    $c = @{ mode = 'restart'; javaargs = $DefaultJavaArgs }
    try {
        if (-not (Test-Path $LauncherCfg)) {
            $txt = @(
                '# Cách script mở lại game khi bị văng / Invalid session (sửa xong có hiệu lực ở lần mở lại sau)',
                '#   restart = tắt launcher cũ, mở launcher mới tự chọn đúng nick và tự vào game (không bấm chuột, chạy được khi bạn đang chơi game khác)',
                '#   click   = bấm nút Enter the game như bản cũ',
                'mode=restart',
                '# Tham số Java cho game, thay bộ tham số mặc định của Legacy Launcher (-Xms 1-2 GB + AlwaysPreTouch làm mỗi nick giữ chặt 1-2 GB RAM).',
                '# Để trống (javaargs=) = giữ cấu hình launcher như cũ.',
                ('javaargs=' + $DefaultJavaArgs)
            )
            Set-Content -Path $LauncherCfg -Value $txt -Encoding UTF8
        }
        foreach ($line in @(Get-Content $LauncherCfg -Encoding UTF8)) {
            $t = $line.Trim()
            if ($t.StartsWith('#')) { continue }
            $i = $t.IndexOf('=')
            if ($i -le 0) { continue }
            $k = $t.Substring(0, $i).Trim().ToLower(); $v = $t.Substring($i + 1).Trim()
            if ($k -eq 'mode' -and $v -in @('restart', 'click')) { $c.mode = $v }
            if ($k -eq 'javaargs') { $c.javaargs = $v }
        }
    } catch { }
    return $c
}

# chỉnh tl.properties (file cấu hình chung của Legacy Launcher): chọn sẵn đúng nick + tham số Java gọn. Giữ nguyên các dòng khác.
function Set-TlProperties([string]$user, [string]$javaArgs) {
    $enc = [System.Text.Encoding]::GetEncoding(28591)
    $bak = Join-Path $DataDir 'backup\tl.properties.bak'
    if ((Test-Path $TlProps) -and -not (Test-Path $bak)) {
        New-Item -ItemType Directory -Force -Path (Split-Path $bak -Parent) | Out-Null
        Copy-Item $TlProps $bak -Force
    }
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $TlProps) { $lines.AddRange([string[]][System.IO.File]::ReadAllLines($TlProps, $enc)) }
    $want = [ordered]@{ 'login.account' = $user }
    if ($javaArgs) {
        $want['minecraft.improvedargs.type'] = 'NONE'
        $want['minecraft.improvedargs'] = 'false'
        $want['minecraft.javaargs'] = $javaArgs
    }
    foreach ($k in $want.Keys) {
        $line = $k + '=' + ([string]$want[$k]).Replace('\', '\\')
        $idx = -1
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match ('^\s*' + [regex]::Escape($k) + '\s*[=:]')) { $idx = $i; break }
        }
        if ($idx -ge 0) { $lines[$idx] = $line } else { $lines.Add($line) }
    }
    [System.IO.File]::WriteAllLines($TlProps, $lines.ToArray(), $enc)
}

# tắt launcher cũ của nick rồi mở launcher mới tự vào game. Trả về pid launcher mới, 0 = không làm được.
function Restart-Launcher($j) {
    try {
        $cfg = Get-LauncherConfig
        if (-not $j.LlExe) {
            $p = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $j.LL) -ErrorAction Stop
            $exe = [string]$p.ExecutablePath; $cl = [string]$p.CommandLine   # dòng lệnh của LAUNCHER (không phải game), không ghi ra log
            if (-not $exe -or -not $cl) { Log ('  ' + $j.User + ': không đọc được lệnh chạy launcher'); return 0 }
            if ($cl.StartsWith('"' + $exe + '"')) { $a = $cl.Substring($exe.Length + 2) }
            elseif ($cl.StartsWith($exe)) { $a = $cl.Substring($exe.Length) }
            else { Log ('  ' + $j.User + ': lệnh chạy launcher lạ, không tự khởi động lại'); return 0 }
            if ($a -notmatch 'bootstrap') { Log ('  ' + $j.User + ': tiến trình cha không phải Legacy Launcher'); return 0 }
            # bỏ phần script đã thêm ở lần trước
            $a = [regex]::Replace($a.Trim(), '(\s+--username\s+\S+)?(\s+--usertype\s+\S+)?(\s+--launch)?\s*$', '')
            $j.LlExe = $exe; $j.LlArgs = $a
        }
        if (-not (Test-Path (Join-Path $McDir 'launcher'))) { Log ('  ' + $j.User + ': không thấy thư mục launcher ở ' + $McDir); return 0 }
        Set-TlProperties $j.User $cfg.javaargs
        # Launcher cũ GIỮ NGUYÊN (đang chọn đúng nick) cho tới khi chắc launcher mới vào đúng nick; sai nick → tắt cái mới, quay về cái cũ.
        # --usertype minecraft: nếu không tìm thấy nick thì launcher KHÔNG tự tạo nick offline trùng tên
        $np = Start-Process -FilePath $j.LlExe -ArgumentList ($j.LlArgs + ' --username ' + $j.User + ' --usertype minecraft --launch') -WorkingDirectory $McDir -PassThru
        Log ('  ' + $j.User + ': mở launcher mới (pid ' + $np.Id + ') tự đăng nhập + tự vào game' + $(if ($cfg.javaargs) { ', tham số Java gọn' } else { '' }) + '; launcher cũ (pid ' + $j.LL + ') để yên tới khi vào đúng nick')
        return $np.Id
    } catch {
        Log ('  ' + $j.User + ': không khởi động lại được launcher: ' + $_.Exception.Message)
        return 0
    }
}

# nick mà launcher mới không tự chọn được (vd nick không có trong danh sách tài khoản chung của launcher) → chỉ dùng cách bấm nút
$NoRestartFile = Join-Path $DataDir 'launcher-block.txt'
function Get-NoRestart {
    try { if (Test-Path $NoRestartFile) { return @(Get-Content $NoRestartFile -Encoding UTF8 | ForEach-Object { $_.Trim() } | Where-Object { $_ -and -not $_.StartsWith('#') }) } } catch { }
    return @()
}
function Add-NoRestart([string]$user) {
    try {
        if ((Get-NoRestart) -contains $user) { return }
        if (-not (Test-Path $NoRestartFile)) { Set-Content -Path $NoRestartFile -Value '# nick ở đây: script KHÔNG tự khởi động lại launcher (launcher mới chọn nhầm nick), chỉ bấm nút Enter the game. Xoá dòng = thử lại.' -Encoding UTF8 }
        Add-Content -Path $NoRestartFile -Value $user -Encoding UTF8
    } catch { }
}

# game của nick A mở ra từ launcher đang dành cho nick B (launcher chọn nhầm nick) → tắt ngay game đó (nó chưa vào server)
$script:killedPids = @{}
function Stop-WrongGame($g, $j) {
    try {
        Stop-Process -Id $g.Pid -Force -ErrorAction Stop
        $script:killedPids[$g.Pid] = $true
        Remove-Item (Join-Path $DataDir ('alive-' + $g.Pid + '.txt')) -Force -ErrorAction SilentlyContinue
        Log ('  ' + $j.User + ': launcher (pid ' + $g.Parent + ') mở nhầm nick ' + $g.User + ' (pid ' + $g.Pid + ') → đã tắt game nhầm đó')
    } catch { }
}

# ghi 1 lần cách Legacy Launcher được chạy (đường dẫn + tham số của tiến trình launcher, KHÔNG phải của game)
# để sau này script tự khởi động lại launcher đúng tài khoản mà không cần bấm chuột
$script:llInfoSaid = @{}
function Log-LauncherInfo([int]$llPid) {
    if ($script:llInfoSaid.ContainsKey($llPid)) { return }
    $script:llInfoSaid[$llPid] = $true
    try {
        $p = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $llPid) -ErrorAction Stop
        $cl = [string]$p.CommandLine
        if ($cl -match '(?i)accessToken|--uuid|session|token') { $cl = '[ẩn vì có thông tin đăng nhập]' }
        if ($cl.Length -gt 700) { $cl = $cl.Substring(0, 700) + '…' }
        $pp = Get-CimInstance Win32_Process -Filter ("ProcessId=" + $p.ParentProcessId) -ErrorAction SilentlyContinue
        $ppTxt = if ($pp) { $pp.Name + ' pid ' + $pp.ProcessId } else { 'không còn (pid ' + $p.ParentProcessId + ')' }
        Log ('  launcher pid ' + $llPid + ': ' + $p.ExecutablePath + ' | cha: ' + $ppTxt + ' | lệnh: ' + $cl)
    } catch { }
}

function Find-LauncherWindow([int]$llPid) {
    foreach ($h in [DRWin]::WindowsOf([uint32]$llPid)) {
        if ([DRWin]::Title($h) -like 'Legacy Launcher*') { return $h }
    }
    return [IntPtr]::Zero
}

function Close-Dialogs([int]$llPid, [IntPtr]$main) {
    # hộp thoại khác của launcher (VD báo crash) che cửa sổ chính → đóng
    foreach ($h in [DRWin]::WindowsOf([uint32]$llPid)) {
        if ($h -ne $main) {
            Log ("  đóng hộp thoại launcher: '" + [DRWin]::Title($h) + "'")
            [DRWin]::PostMessage($h, 0x0010, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null
        }
    }
}

function Click-Enter([IntPtr]$h, [int]$offset) {
    if ([DRWin]::IsIconic($h)) { [DRWin]::ShowWindow($h, 9) | Out-Null; Start-Sleep -Milliseconds 300 }
    # nhấn-thả Alt để Windows cho phép đưa cửa sổ lên trước
    [DRWin]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [DRWin]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
    [DRWin]::ShowWindow($h, 5) | Out-Null
    [DRWin]::BringWindowToTop($h) | Out-Null
    [DRWin]::SetForegroundWindow($h) | Out-Null
    Start-Sleep -Milliseconds 500
    if ([DRWin]::GetForegroundWindow() -ne $h) {
        Log '  chưa đưa được cửa sổ launcher lên trước → thử lại sau'
        return $false
    }
    $rc = New-Object DRWin+RECT
    [DRWin]::GetClientRect($h, [ref]$rc) | Out-Null
    $scale = [DRWin]::Dpi($h) / 96.0
    $pt = New-Object DRWin+POINT
    $pt.X = [int]($rc.Right / 2)
    $pt.Y = [int]($rc.Bottom / 2 + $offset * $scale)
    [DRWin]::ClientToScreen($h, [ref]$pt) | Out-Null
    $hit = [DRWin]::GetAncestor([DRWin]::WindowFromPoint($pt), 2)
    if ($hit -ne $h) {
        Log ("  điểm bấm (" + $pt.X + "," + $pt.Y + ") bị cửa sổ khác che → thử lại sau")
        return $false
    }
    # game Minecraft đang giữ chuột (khoá con trỏ trong cửa sổ game) → nhả ra trước khi di chuột
    [DRWin]::ClipCursor([IntPtr]::Zero) | Out-Null
    [DRWin]::SetCursorPos($pt.X, $pt.Y) | Out-Null
    Start-Sleep -Milliseconds 150
    [DRWin]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 80
    [DRWin]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Log ("  đã bấm 'Enter the game' tại (" + $pt.X + "," + $pt.Y + "), lệch " + $offset + " px")
    return $true
}

# Bấm bằng thông điệp cửa sổ (PostMessage) thay vì chuột thật: không cần đưa cửa sổ lên trước, không di chuột của bạn,
# và không bị Windows chặn khi đang mở app chạy quyền admin / game có anti-cheat (Valorant…) — lúc đó chuột giả lập bị chặn hết.
function Post-Click([IntPtr]$h, [int]$offset) {
    if ([DRWin]::IsIconic($h)) { [DRWin]::ShowWindow($h, 9) | Out-Null; Start-Sleep -Milliseconds 300 }
    $rc = New-Object DRWin+RECT
    [DRWin]::GetClientRect($h, [ref]$rc) | Out-Null
    $scale = [DRWin]::Dpi($h) / 96.0
    $pt = New-Object DRWin+POINT
    $pt.X = [int]($rc.Right / 2)
    $pt.Y = [int]($rc.Bottom / 2 + $offset * $scale)
    # cửa sổ con sâu nhất của launcher tại điểm đó (bỏ qua cửa sổ khác đang che bên trên)
    $target = $h
    $cp = $pt
    for ($i = 0; $i -lt 8; $i++) {
        $c = [DRWin]::ChildWindowFromPointEx($target, $cp, 1)
        if ($c -eq [IntPtr]::Zero -or $c -eq $target) { break }
        [DRWin]::MapWindowPoints($target, $c, [ref]$cp, 1) | Out-Null
        $target = $c
    }
    $lp = [IntPtr](($cp.Y -shl 16) -bor ($cp.X -band 0xFFFF))
    [DRWin]::PostMessage($target, 0x0200, [IntPtr]::Zero, $lp) | Out-Null      # WM_MOUSEMOVE
    Start-Sleep -Milliseconds 60
    [DRWin]::PostMessage($target, 0x0201, [IntPtr]1, $lp) | Out-Null           # WM_LBUTTONDOWN
    Start-Sleep -Milliseconds 90
    [DRWin]::PostMessage($target, 0x0202, [IntPtr]::Zero, $lp) | Out-Null      # WM_LBUTTONUP
    Log ("  đã gửi lệnh bấm 'Enter the game' (không dùng chuột) tại (" + $pt.X + "," + $pt.Y + ") trong cửa sổ, lệch " + $offset + " px")
    return $true
}

function Recent-Count([string]$user) {
    if (-not $script:history.ContainsKey($user)) { return 0 }
    $cut = (Get-Date).AddMinutes(-60)
    $script:history[$user] = @($script:history[$user] | Where-Object { $_ -gt $cut })
    return $script:history[$user].Count
}

$known = @{}
$jobs = New-Object System.Collections.ArrayList
$selfPath = $PSCommandPath
$selfStamp = (Get-Item $selfPath).LastWriteTimeUtc
$script:history = @{}
$first = $true
Log ('=== bắt đầu theo dõi, thư mục game: ' + $GameDir)

# việc mở lại còn dở từ bản script trước (script tự cập nhật giữa chừng) → làm tiếp
$PendingFile = Join-Path $DataDir 'pending-jobs.txt'
try {
    if (Test-Path $PendingFile) {
        if (((Get-Date) - (Get-Item $PendingFile).LastWriteTime).TotalMinutes -lt 15) {
            foreach ($line in @(Get-Content $PendingFile -Encoding UTF8)) {
                $f = $line -split "`t"
                if ($f.Count -lt 4) { continue }
                $llId = 0
                if (-not [int]::TryParse($f[1], [ref]$llId) -or -not (Get-Process -Id $llId -ErrorAction SilentlyContinue)) { continue }
                [void]$jobs.Add([pscustomobject]@{ User = $f[0]; LL = $llId; Since = (Get-Date); NextAt = (Get-Date); State = 'wait'; Clicks = [int]$f[2]; Fails = [int]$f[3]; ClickedAt = $null; Registered = $false; ClickCpu = 0; BusySaid = $false; LongWait = $false; LlRestarts = 0; LlExe = $null; LlArgs = $null; OldLL = 0; Manual = ($f.Count -gt 4 -and $f[4] -eq 'manual'); ManualSaid = $false })
                Log ($f[0] + ': làm tiếp việc mở lại dở dang từ bản script trước')
            }
        }
        Remove-Item $PendingFile -Force -ErrorAction SilentlyContinue
    }
} catch { }

while ($true) {
    $now = Get-Date
    $games = Get-Games

    # game mở nhầm nick từ launcher của việc mở lại khác → tắt ngay, không tính là game đang chạy
    foreach ($k in @($games.Keys)) {
        $g = $games[$k]
        foreach ($j in @($jobs)) {
            if ($j.User -eq $g.User -or $g.Parent -ne $j.LL) { continue }
            Stop-WrongGame $g $j
            $games.Remove($k)
            if ($j.State -eq 'llstart') {
                # launcher mới chọn nhầm → tắt nó, quay về launcher cũ (vẫn đang chọn đúng nick) và từ nay nick này chỉ dùng cách bấm nút
                Stop-Process -Id $j.LL -Force -ErrorAction SilentlyContinue
                Add-NoRestart $j.User
                Log ('  ' + $j.User + ': launcher mới không chọn được đúng nick → tắt nó, từ nay nick này mở lại bằng cách bấm nút (launcher-block.txt)')
                if ($j.OldLL -and (Get-Process -Id $j.OldLL -ErrorAction SilentlyContinue)) {
                    $j.LL = $j.OldLL; $j.OldLL = 0; $j.State = 'wait'; $j.NextAt = $now
                } else {
                    $j.Manual = $true; $j.State = 'wait'
                }
            } else {
                # launcher của nick này đang chọn nhầm nick khác → bấm nữa cũng vào nhầm → cần bạn chọn lại nick trong launcher
                $j.Manual = $true; $j.State = 'wait'
            }
            break
        }
    }

    # game mới
    foreach ($k in $games.Keys) {
        if (-not $known.ContainsKey($k)) {
            $known[$k] = $games[$k]
            Log ('theo dõi ' + $games[$k].User + ' (pid ' + $k + ', launcher pid ' + $games[$k].Parent + ')')
        }
        Set-GamePriority $games[$k]
        Trim-Launcher $games[$k]
        Log-LauncherInfo $games[$k].Parent
    }
    Log-Usage $games

    # game vừa tắt
    foreach ($k in @($known.Keys)) {
        if ($games.ContainsKey($k)) { continue }
        # đôi khi Windows đọc hụt dòng lệnh của tiến trình 1 lượt → chỉ coi là tắt khi tiến trình thật sự không còn
        if (Get-Process -Id $k -ErrorAction SilentlyContinue) { continue }
        $g = $known[$k]
        $known.Remove($k)
        if ($script:killedPids.ContainsKey($k)) { continue }
        Start-Sleep -Milliseconds 700
        $alive = Join-Path $DataDir ("alive-" + $k + ".txt")
        $exitF = Join-Path $DataDir ("exit-" + $k + ".txt")
        $hasMod = Test-Path $alive
        $kind = 'crash'; $why = 'game tắt mà không ghi lý do (crash / bị tắt ngang)'
        if (Test-Path $exitF) {
            $lines = @(Get-Content $exitF -Encoding UTF8)
            if ($lines.Count -gt 0) { $kind = $lines[0].Trim() }
            if ($lines.Count -gt 2) { $why = $lines[2] }
        }
        Remove-Item $alive, $exitF -Force -ErrorAction SilentlyContinue
        if (-not $hasMod) { Log ($g.User + ' (pid ' + $k + ') đã tắt; client này chưa có mod → không tự mở lại'); continue }
        if ($kind -eq 'user') { Log ($g.User + ' (pid ' + $k + '): bạn tự tắt game → không mở lại'); continue }
        $n = Recent-Count $g.User
        $wait = $Backoff[[Math]::Min($n, $Backoff.Count - 1)]
        Log ($g.User + ' (pid ' + $k + ') tắt: ' + $kind + ' — ' + $why + ' → mở lại' + $(if ($wait -gt 0) { ' sau ' + $wait + ' giây (đã mở lại ' + $n + ' lần trong 1 giờ)' } else { '' }))
        [void]$jobs.Add([pscustomobject]@{ User = $g.User; LL = $g.Parent; Since = $now; NextAt = $now.AddSeconds($wait); State = 'wait'; Clicks = 0; Fails = 0; ClickedAt = $null; Registered = $false; ClickCpu = 0; BusySaid = $false; LongWait = $false; LlRestarts = 0; LlExe = $null; LlArgs = $null; OldLL = 0; Manual = $false; ManualSaid = $false })
    }

    # xử lý từng việc mở lại (mỗi lúc 1 việc bấm để không lẫn chuột)
    $busy = @($jobs | Where-Object { $_.State -in @('clicked', 'llstart') }).Count -gt 0
    foreach ($j in @($jobs)) {
        $running = @($games.Values | Where-Object { $_.User -eq $j.User })
        if ($running.Count -gt 0) {
            Log ($j.User + ': game đã chạy lại (pid ' + $running[0].Pid + ')')
            if ($j.OldLL -and $j.OldLL -ne $running[0].Parent -and -not @($games.Values | Where-Object { $_.Parent -eq $j.OldLL }).Count) {
                Stop-Process -Id $j.OldLL -Force -ErrorAction SilentlyContinue
                Log ('  ' + $j.User + ': đã tắt launcher cũ (pid ' + $j.OldLL + ')')
            }
            if (-not $script:history.ContainsKey($j.User)) { $script:history[$j.User] = @() }
            $script:history[$j.User] += $now
            $jobs.Remove($j); $busy = $false; continue
        }
        if (-not (Get-Process -Id $j.LL -ErrorAction SilentlyContinue)) {
            if ($j.State -eq 'llstart' -and $j.OldLL -and (Get-Process -Id $j.OldLL -ErrorAction SilentlyContinue)) {
                Log ($j.User + ': launcher mới (pid ' + $j.LL + ') đã tắt trước khi vào game → quay về launcher cũ')
                $j.LL = $j.OldLL; $j.OldLL = 0; $j.State = 'wait'; $j.NextAt = $now.AddSeconds(30); $busy = $false; continue
            }
            if ($j.State -eq 'llstart' -and $j.LlExe) {
                # launcher mới do script mở bị tắt / crash khi đang khởi động → thử mở lại sau
                $j.Fails++
                $wait = $Backoff[[Math]::Min($j.Fails, $Backoff.Count - 1)]
                Log ($j.User + ': launcher mới (pid ' + $j.LL + ') đã tắt trước khi vào game → mở lại sau ' + $wait + ' giây')
                $j.State = 'wait'; $j.NextAt = $now.AddSeconds($wait); $busy = $false; continue
            }
            if ($j.State -eq 'wait' -and $j.LlExe -and $j.LlRestarts -lt 3) { $h = [IntPtr]::Zero; $act = $null }
            else {
                Log ($j.User + ': cửa sổ Legacy Launcher (pid ' + $j.LL + ') đã bị đóng → không mở lại')
                $jobs.Remove($j); continue
            }
        }
        $h = Find-LauncherWindow $j.LL

        $act = Update-LauncherActivity $j.LL

        if ($j.State -eq 'llstart') {
            # launcher mới: khởi động + tự đăng nhập + chuẩn bị game mất khoảng 30–90 giây
            if (($now - $j.ClickedAt).TotalSeconds -lt 240) { continue }
            Log ($j.User + ': launcher mới mở 4 phút mà chưa vào được game → tắt nó, thử lại')
            if ($j.OldLL -and (Get-Process -Id $j.OldLL -ErrorAction SilentlyContinue)) {
                Stop-Process -Id $j.LL -Force -ErrorAction SilentlyContinue
                $j.LL = $j.OldLL; $j.OldLL = 0
            }
            $j.State = 'wait'; $j.NextAt = $now; $busy = $false; continue
        }

        if ($j.State -eq 'clicked') {
            $elapsed = ($now - $j.ClickedAt).TotalSeconds
            if ($h -eq [IntPtr]::Zero -and $elapsed -lt 180) { continue }   # launcher đã ẩn = đang mở game, chờ
            if ($h -ne [IntPtr]::Zero -and $act) {
                # Trong lúc launcher đăng nhập / chuẩn bị game, nút "Enter the game" biến thành nút HUỶ → bấm thêm là huỷ luôn việc mở game.
                # Vì vậy chỉ bấm lại khi chắc chắn launcher không nhận lệnh (CPU không nhúc nhích) hoặc đã xong việc mà vẫn không mở được game.
                $used = $act.Cpu - $j.ClickCpu
                if (-not $j.Registered -and ($used -ge 800 -or $act.BusyAt -gt $j.ClickedAt)) {
                    $j.Registered = $true
                    Log ('  ' + $j.User + ': launcher đã nhận lệnh, đang chuẩn bị game (CPU +' + [int]$used + ' ms) → chờ, không bấm thêm')
                }
                if (-not $j.Registered) {
                    if ($elapsed -lt 15) { continue }
                    if ($j.Clicks -lt $ButtonOffsets.Count * 2) {
                        Log ($j.User + ': 15 giây launcher không phản ứng (CPU +' + [int]$used + ' ms) → bấm lại (vị trí khác)')
                        $j.State = 'wait'; $j.NextAt = $now; $busy = $false; continue
                    }
                } else {
                    $idle = ($now - $act.BusyAt).TotalSeconds
                    if ($idle -lt 40 -or $elapsed -lt 60) {
                        if ($elapsed -gt 900 -and -not $j.LongWait) { $j.LongWait = $true; Log ($j.User + ': launcher bận đã 15 phút, vẫn chờ (bấm lúc này là huỷ)') }
                        continue
                    }
                    if ($j.Clicks -lt $ButtonOffsets.Count * 2) {
                        Log ($j.User + ': launcher đã rảnh 40 giây mà game chưa mở (lần mở trước dừng giữa chừng) → bấm lại')
                        $j.State = 'wait'; $j.NextAt = $now; $busy = $false; continue
                    }
                }
            }
            $j.Fails++
            $wait = $Backoff[[Math]::Min($j.Fails, $Backoff.Count - 1)]
            Log ($j.User + ': chưa mở lại được game (lần hỏng ' + $j.Fails + ') → thử lại sau ' + $wait + ' giây')
            $j.State = 'wait'; $j.NextAt = $now.AddSeconds($wait); $busy = $false
            continue
        }

        if ($j.Manual) {
            if (-not $j.ManualSaid) { $j.ManualSaid = $true; Log ($j.User + ': CẦN BẠN — cửa sổ Legacy Launcher (pid ' + $j.LL + ') đang chọn nhầm nick khác. Chọn lại nick ' + $j.User + ' rồi bấm Enter the game; script không tự bấm để khỏi vào nhầm nick.') }
            continue
        }
        if ($busy -or $now -lt $j.NextAt) { continue }
        if (Is-Paused) {
            if (-not $script:pauseSaid) { Log 'đang tạm dừng tự mở lại (pause.txt = 1) → chờ'; $script:pauseSaid = $true }
            continue
        }
        if ($script:pauseSaid) { Log 'hết tạm dừng → mở lại tiếp'; $script:pauseSaid = $false }
        $cfgL = Get-LauncherConfig
        $useRestart = $cfgL.mode -eq 'restart' -and $j.LlRestarts -lt 3 -and -not ((Get-NoRestart) -contains $j.User)
        if ($h -eq [IntPtr]::Zero -and -not ($useRestart -and ($now - $j.Since).TotalSeconds -gt 20)) {
            if (($now - $j.Since).TotalSeconds -gt 90 -and -not $j.PSObject.Properties['Warned']) {
                Log ($j.User + ': 90 giây chưa thấy cửa sổ Legacy Launcher hiện lại, vẫn chờ')
                $j | Add-Member -NotePropertyName Warned -NotePropertyValue $true
            }
            continue
        }
        # launcher đang bận (đang tự mở game / đăng nhập — có thể do bạn vừa bấm tay) → không bấm, chờ nó rảnh 12 giây
        if ($act -and ($now - $act.BusyAt).TotalSeconds -lt 12) {
            if (-not $j.BusySaid -and $act.Rate -ge 15) { $j.BusySaid = $true; Log ('  ' + $j.User + ': launcher đang bận (CPU ' + [int]$act.Rate + '% 1 nhân) → chờ nó rảnh mới bấm') }
            continue
        }
        try { Set-Content -Path (Join-Path $DataDir ('relaunch-' + $j.User + '.txt')) -Value (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') -Encoding UTF8 } catch { }
        if ($useRestart) {
            $j.LlRestarts++
            Log ($j.User + ': mở lại game bằng cách khởi động lại launcher (lần ' + $j.LlRestarts + ')')
            $newLl = Restart-Launcher $j
            if ($newLl -gt 0) {
                if (-not $j.OldLL -and (Get-Process -Id $j.LL -ErrorAction SilentlyContinue)) { $j.OldLL = $j.LL }
                $j.LL = $newLl; $j.State = 'llstart'; $j.ClickedAt = Get-Date; $busy = $true
                continue
            }
            $j.LlRestarts = 99   # không làm được → dùng cách bấm nút cho việc này
            if (-not (Get-Process -Id $j.LL -ErrorAction SilentlyContinue)) { $j.NextAt = $now.AddSeconds(30); continue }
            $h = Find-LauncherWindow $j.LL
            if ($h -eq [IntPtr]::Zero) { continue }
        }
        Close-Dialogs $j.LL $h
        # bấm chuột thật, mỗi lần thử 1 độ lệch. (Gửi thông điệp cửa sổ — PostMessage — đã thử 11/10: launcher Java bỏ qua,
        # vì Java xác định nút bị bấm theo vị trí con trỏ thật.)
        $off = $ButtonOffsets[$j.Clicks % $ButtonOffsets.Count]
        Log ($j.User + ': bấm Enter the game (lần ' + ($j.Clicks + 1) + ')')
        $ok = Click-Enter $h $off
        if ($ok) {
            $j.Clicks++; $j.State = 'clicked'; $j.ClickedAt = Get-Date; $busy = $true
            $j.Registered = $false; $j.BusySaid = $false
            $j.ClickCpu = if ($act) { $act.Cpu } else { 0 }
        } else {
            # chưa bấm được (không đưa được launcher lên trước / bị cửa sổ khác che) → 10 giây sau thử lại vị trí kế
            $j.Clicks++
            $j.NextAt = $now.AddSeconds($(if ($j.Clicks -gt 6) { 30 } else { 10 }))
        }
    }

    # dọn file của tiến trình không còn
    if ($first -or ($now.Second -lt 3 -and $now.Minute % 10 -eq 0)) {
        foreach ($f in Get-ChildItem $DataDir -Filter 'alive-*.txt' -ErrorAction SilentlyContinue) {
            $p = 0
            if ([int]::TryParse(($f.BaseName -replace '^alive-', ''), [ref]$p) -and -not $games.ContainsKey($p) -and -not (Get-Process -Id $p -ErrorAction SilentlyContinue)) {
                Remove-Item $f.FullName -Force -ErrorAction SilentlyContinue
            }
        }
    }
    $first = $false

    # file script được cập nhật → tự chạy lại bản mới
    try {
        # chờ xong việc vừa bấm (đang mở game); việc đang chờ bấm thì ghi lại để bản mới làm tiếp — không kẹt ở bản cũ
        if ((Get-Item $selfPath).LastWriteTimeUtc -ne $selfStamp -and @($jobs | Where-Object { $_.State -in @('clicked', 'llstart') }).Count -eq 0) {
            if ($jobs.Count -gt 0) {
                $lines = @($jobs | ForEach-Object { $_.User + "`t" + $_.LL + "`t" + $_.Clicks + "`t" + $_.Fails + "`t" + $(if ($_.Manual) { 'manual' } else { '' }) })
                Set-Content -Path $PendingFile -Value $lines -Encoding UTF8
            }
            Log '=== script vừa được cập nhật → chạy lại bản mới'
            $mutex.ReleaseMutex(); $mutex.Dispose()
            Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden', '-File', ('"' + $selfPath + '"'))
            exit
        }
    } catch { }
    Start-Sleep -Seconds 3
}
