$ErrorActionPreference = 'Stop'
Write-Host 'JARVIS Kokoro voice setup' -ForegroundColor Cyan
Write-Host ''
Write-Host '1/2 Installing the Kokoro JavaScript runtime...' -ForegroundColor Yellow
npm install
if ($LASTEXITCODE -ne 0) { throw 'npm install failed. Check your internet connection and Node.js installation.' }
Write-Host ''
Write-Host '2/2 Kokoro model download' -ForegroundColor Yellow
Write-Host 'The model is downloaded automatically the first time JARVIS speaks.' -ForegroundColor Gray
Write-Host 'Default: Kokoro-82M ONNX / q8 / CPU / af_heart' -ForegroundColor Gray
Write-Host ''
Write-Host 'Kokoro setup is ready. Start JARVIS with:' -ForegroundColor Green
Write-Host 'npm start' -ForegroundColor White
