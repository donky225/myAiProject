# stop-all.ps1
# start-all.ps1로 띄운 Docker 기반 서비스들을 한 번에 종료합니다.
# (음성 서버 / 리랭크 서버 / Stable Diffusion 콘솔 창은 별도 PowerShell 창에서
#  떠 있으므로, Docker와 무관하게 각 창을 직접 닫거나 Ctrl+C로 종료해주세요.)

Write-Host "메인 인프라(OpenSearch/Postgres/Ollama/Kafka/Redis) 종료 중..." -ForegroundColor Yellow
Set-Location "D:\MyAiProject"
docker compose stop opensearch postgres ollama kafka redis

Write-Host "Stable Diffusion WebUI(Docker 컨테이너) 종료 중..." -ForegroundColor Yellow
Set-Location "D:\stable-diffusion-webui-docker"
docker compose --profile auto stop

Write-Host ""
Write-Host "Docker 기반 서비스 종료 완료." -ForegroundColor Green
Write-Host "다음은 Docker가 아니라 별도 PowerShell 창에서 실행 중이므로 직접 닫아주세요:" -ForegroundColor Yellow
Write-Host "  - 음성 서버 (uvicorn, voice-pipeline 창)"
Write-Host "  - 리랭크 서버 (python rerank_service.py 창)"
Write-Host "  - Stable Diffusion WebUI 콘솔 창 (컨테이너는 위에서 이미 stop됨, 창만 닫으면 됨)"

Set-Location "D:\MyAiProject"
