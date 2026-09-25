$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$src  = Join-Path $PSScriptRoot 'MojibakeRepair.java'
$out  = Join-Path $env:TEMP 'mojibake-repair'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& javac -encoding UTF-8 -d $out $src
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$res = Join-Path $PSScriptRoot '..\app\src\main\res'
& java -cp $out MojibakeRepair $res
if ($LASTEXITCODE -ne 0) { throw 'repair failed' }

