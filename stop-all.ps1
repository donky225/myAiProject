# stop-all.ps1
# start-all.ps1로 띄운 Docker 기반 서비스들을 한 번에 종료합니다.
# (uvicorn 음성 서버는 별도 창에서 떠 있으므로 그 창을 직접 닫거나 Ctrl+C로 종료해주세요.)

Write-Host "메인 인프라(OpenSearch/Postgres/Ollama/Kafka/Redis) 종료 중..." -ForegroundColor Yellow
Set-Location "D:\MyAiProject"
docker compose stop opensearch postgres ollama kafka redis

Write-Host "Stable Diffusion WebUI 종료 중..." -ForegroundColor Yellow
Set-Location "D:\stable-diffusion-webui-docker"
docker compose --profile auto stop

Write-Host "완료. 음성 서버(uvicorn) 창은 직접 닫아주세요." -ForegroundColor Green
