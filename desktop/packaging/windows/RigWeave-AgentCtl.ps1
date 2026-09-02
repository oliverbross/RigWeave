param([ValidateSet('start','stop','restart','status','open','diagnostics','backup','restore','update-check','uninstall-preview')][string]$Command='status',[string]$InputPath,[switch]$Apply)
$ErrorActionPreference='Stop'
$state=Join-Path $env:LOCALAPPDATA 'RigWeave\State'; $data=Join-Path $env:LOCALAPPDATA 'RigWeave\Data'
New-Item -ItemType Directory -Force $state,$data | Out-Null
function PidPath($name){ Join-Path $state "$name.pid" }
function Live($name){ $p=PidPath $name; if(!(Test-Path $p)){return $false}; $id=[int](Get-Content $p -First 1); return $null -ne (Get-Process -Id $id -ErrorAction SilentlyContinue) }
function Start-One($name,$file){ if(Live $name){return}; Remove-Item (PidPath $name) -Force -ErrorAction SilentlyContinue; $p=Start-Process $file -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $state "$name.log") -RedirectStandardError (Join-Path $state "$name.error.log"); $p.Id | Set-Content (PidPath $name); Start-Sleep -Seconds 1; if(!(Live $name)){throw "$name failed to start"} }
function Stop-One($name){ if(!(Live $name)){Remove-Item (PidPath $name) -Force -ErrorAction SilentlyContinue; return}; $id=[int](Get-Content (PidPath $name) -First 1); Stop-Process -Id $id; Wait-Process -Id $id -Timeout 5 -ErrorAction SilentlyContinue; if(Get-Process -Id $id -ErrorAction SilentlyContinue){throw "$name did not stop safely"}; Remove-Item (PidPath $name) -Force }
switch($Command){
 'start' { Start-One agent (Join-Path $PSScriptRoot 'rigweave-stationd.exe'); Start-One hub (Join-Path $PSScriptRoot 'rigweave-application-service.exe') }
 'stop' { Stop-One hub; Stop-One agent }
 'restart' { & $PSCommandPath stop; & $PSCommandPath start }
 'status' { 'agent '+$(if(Live agent){'RUNNING'}else{'STOPPED'}); 'hub '+$(if(Live hub){'RUNNING'}else{'STOPPED'}) }
 'open' { Start-Process ($env:RIGWEAVE_WEB_URL ?? 'https://127.0.0.1:8443/home') }
 'diagnostics' { $out=Join-Path $state ('rigweave-support-'+(Get-Date -Format yyyyMMddTHHmmssZ)+'.zip'); $tmp=Join-Path $env:TEMP ('rigweave-support-'+[guid]::NewGuid()); New-Item -ItemType Directory $tmp|Out-Null; & $PSCommandPath status|Set-Content (Join-Path $tmp status.txt); Get-ComputerInfo|Out-File (Join-Path $tmp platform.txt); Compress-Archive "$tmp\*" $out; Remove-Item $tmp -Recurse; $out }
 'backup' { $out=Join-Path $state ('rigweave-backup-'+(Get-Date -Format yyyyMMddTHHmmssZ)+'.zip'); Get-ChildItem $data -Recurse -File|Where-Object Name -NotMatch '(?i)(credential|\.pem$|\.key$)'|Compress-Archive -DestinationPath $out; $out }
 'restore' { if(!(Test-Path $InputPath)){throw 'restore archive required'}; if(!$Apply){'preview only; repeat with -Apply'; return}; if((Live agent) -or (Live hub)){throw 'stop RigWeave before restore'}; Expand-Archive $InputPath $data -Force }
 'update-check' { if(!(Test-Path $InputPath)){throw 'local update manifest required'}; if((Get-Content $InputPath -Raw) -notmatch 'sha256|signature'){throw 'unverifiable update metadata'}; 'metadata accepted for preview; install requires quiesce, backup, health and rollback' }
 'uninstall-preview' { "Removes installed files and opt-in startup only; preserves $data and Credential Manager entries." }
}
