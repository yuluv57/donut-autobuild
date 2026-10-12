' Cai dat Donut Relaunch: chep file tu chay vao Startup va bat script ngay (chay an)
Set sh = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")
here = fso.GetParentFolderName(WScript.ScriptFullName)
dataDir = sh.ExpandEnvironmentStrings("%APPDATA%") & "\.tlauncher\legacy\Minecraft\game\donut-relaunch"
If Not fso.FolderExists(dataDir) Then fso.CreateFolder dataDir
fso.CopyFile here & "\donut-relaunch.ps1", dataDir & "\donut-relaunch.ps1", True
fso.CopyFile here & "\Donut Relaunch.vbs", sh.SpecialFolders("Startup") & "\Donut Relaunch.vbs", True
sh.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & dataDir & "\donut-relaunch.ps1""", 0, False
MsgBox "Da cai Donut Relaunch: script dang chay an va se tu chay moi khi bat may." & vbCrLf & "Nhat ky: " & dataDir & "\watchdog.log", 64, "Donut Relaunch"
