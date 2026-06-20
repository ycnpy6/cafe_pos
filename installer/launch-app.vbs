Set shell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

appDir = fso.GetParentFolderName(WScript.ScriptFullName)
javaExe = appDir & "\\runtime\\bin\\java.exe"
jfxDir = appDir & "\\app\\jfx"
cp = appDir & "\\app\\cafe-pos-0.1.0.jar;" & appDir & "\\app\\lib\\*"

logRoot = shell.ExpandEnvironmentStrings("%APPDATA%") & "\\CafePOS\\logs"
If Not fso.FolderExists(logRoot) Then
  fso.CreateFolder(logRoot)
End If
logFile = logRoot & "\\launcher.log"

cmd = "cmd /c \"\"\"" & javaExe & "\" --module-path \"\"" & jfxDir & "\"\" --add-modules javafx.controls,javafx.fxml -cp \"\"" & cp & "\"\" com.cafepos.MainApp >> \"\"" & logFile & "\"\" 2>&1\""

shell.Run cmd, 0, False
