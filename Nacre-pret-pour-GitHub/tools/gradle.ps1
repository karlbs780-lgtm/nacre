$ErrorActionPreference = 'Stop'
$Version = '8.9'
$Sha = 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'
$Root = Split-Path -Parent $PSScriptRoot
$Cache = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $HOME '.gradle' }
$Base = Join-Path $Cache "nacre-bootstrap\$Version"
$Bin = Join-Path $Base "gradle-$Version\bin\gradle.bat"
if (-not (Test-Path $Bin)) {
    New-Item -ItemType Directory -Force -Path $Base | Out-Null
    $Zip = Join-Path $Base "gradle-$Version-bin.zip"
    Invoke-WebRequest "https://services.gradle.org/distributions/gradle-$Version-bin.zip" -OutFile $Zip
    if ((Get-FileHash -Algorithm SHA256 $Zip).Hash.ToLowerInvariant() -ne $Sha) { throw 'Gradle SHA-256 mismatch' }
    Expand-Archive -Path $Zip -DestinationPath $Base -Force
}
& $Bin '-p' $Root @args
exit $LASTEXITCODE
