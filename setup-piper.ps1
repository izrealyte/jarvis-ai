$ErrorActionPreference = 'Stop'

Write-Host 'JARVIS Piper voice setup' -ForegroundColor Cyan

if (-not (Get-Command py -ErrorAction SilentlyContinue) -and -not (Get-Command python -ErrorAction SilentlyContinue)) {
  throw 'Python was not found. Install Python 3.10 or newer from https://www.python.org/downloads/windows/ and run this script again.'
}

$pythonLauncher = if (Get-Command py -ErrorAction SilentlyContinue) { 'py' } else { 'python' }
$venv = Join-Path $PSScriptRoot '.piper-env'
$python = Join-Path $venv 'Scripts\python.exe'

if (-not (Test-Path $python)) {
  Write-Host 'Creating a local Piper Python environment...'
  if ($pythonLauncher -eq 'py') { & py -3 -m venv $venv } else { & python -m venv $venv }
}

Write-Host 'Installing Piper...' -ForegroundColor Yellow
& $python -m pip install --upgrade pip
& $python -m pip install piper-tts

$voices = Join-Path $PSScriptRoot 'voices'
New-Item -ItemType Directory -Force -Path $voices | Out-Null

Write-Host 'Downloading the English voice model...' -ForegroundColor Yellow
& $python -m piper.download_voices en_US-lessac-medium --data-dir $voices

Write-Host ''
Write-Host 'Piper is ready.' -ForegroundColor Green
Write-Host 'The .env.example file already points JARVIS at this local voice.'
Write-Host 'Copy .env.example to .env, add your Groq key, then run npm start.'
