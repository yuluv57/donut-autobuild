' Donut Relaunch - chay an script tu mo lai Minecraft (Legacy Launcher) khi loi Invalid session / crash
Set sh = CreateObject("WScript.Shell")
ps1 = sh.ExpandEnvironmentStrings("%APPDATA%") & "\.tlauncher\legacy\Minecraft\game\donut-relaunch\donut-relaunch.ps1"
sh.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File """ & ps1 & """", 0, False
