; Common Grounds POS - Inno Setup
; Build prerequisites:
; 1) Build app image (build-installer.bat) -> dist\app-image\Common Grounds POS
; 2) Provide assets\wizard-side.bmp, assets\wizard-header.bmp, and icon.ico

#define MyAppName "Common Grounds POS"
#define MyAppVersion "0.1.0"
#define MyAppPublisher "Common Grounds"
#define MyAppExeName "CommonGroundsPOS.exe"

[Setup]
AppId={{A5F4C9E1-5E35-4BA7-9E71-9E3AE71286A6}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={pf}\{#MyAppName}
DefaultGroupName={#MyAppName}
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
SetupIconFile=..\src\main\resources\com\cafepos\images\icon.ico
WizardImageFile=assets\wizard-side.bmp
WizardSmallImageFile=assets\wizard-header.bmp
OutputDir=..\dist\installer
OutputBaseFilename=Common-Grounds-POS-Setup

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop icon"; GroupDescription: "Additional icons:"; Flags: unchecked

[Files]
Source: "..\dist\app-image\CommonGroundsPOS\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs
Source: "assets\README.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "launch-app.cmd"; DestDir: "{app}"; Flags: ignoreversion
Source: "assets\GUIDE_UTILISATEUR.txt"; DestDir: "{app}\Documentation"; Flags: ignoreversion
Source: "assets\clients_template.csv"; DestDir: "{app}\Documentation"; Flags: ignoreversion
Source: "assets\clients_template_LISEZMOI.txt"; DestDir: "{app}\Documentation"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{cmd}"; Parameters: "/c ""{app}\launch-app.cmd"""; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{commondesktop}\{#MyAppName}"; Filename: "{cmd}"; Parameters: "/c ""{app}\launch-app.cmd"""; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon
Name: "{group}\Guide utilisateur"; Filename: "{app}\Documentation\GUIDE_UTILISATEUR.txt"
Name: "{group}\Modele import clients"; Filename: "{app}\Documentation"

[Run]
Filename: "{cmd}"; Parameters: "/c ""{app}\launch-app.cmd"""; WorkingDir: "{app}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
