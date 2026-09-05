# start-all.ps1
# PC를 켠 뒤 이 스크립트 하나로 (Spring Boot 앱 제외) 필요한 모든 백그라운드 서비스를 기동합니다.
#
# 사용법 (PowerShell에서):
#   cd D:\MyAiProject
#   .\start-all.ps1
#
# 종료할 때는 각각 새로 열린 PowerShell 창을 닫거나 Ctrl+C로 중지하면 됩니다.
# Docker 컨테이너는 창을 닫아도 백그라운드에서 계속 돌아가므로, 완전히 끄려면
# stop-all.ps1(또는 이 문서 하단의 종료 명령)을 사용하세요.

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 1/4. 메인 인프라 기동 (OpenSearch / PostgreSQL+pgvector / Ollama / Kafka / Redis)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

Set-Location "D:\MyAiProject"
docker compose up -d opensearch postgres ollama ollama-init kafka redis

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 2/4. Stable Diffusion WebUI 기동 (새 창에서 실행됩니다)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd D:\stable-diffusion-webui-docker; Write-Host 'Stable Diffusion WebUI 기동 중... (http://localhost:7860)' -ForegroundColor Yellow; docker compose --profile auto up"
)

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 3/4. 음성 파이프라인 서버(STT/TTS) 기동 (새 창에서 실행됩니다)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd D:\MyAiProject\voice-pipeline; .\voice-pipeline-env\Scripts\Activate.ps1; Write-Host '음성 서버 기동 중... (http://localhost:8001)' -ForegroundColor Yellow; uvicorn voice_service:app --host 0.0.0.0 --port 8001"
)

Write-Host ""
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host " 4/4. 리랭크 서버(RAG 재정렬, BAAI/bge-reranker-v2-m3) 기동 (새 창에서 실행됩니다)" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

Start-Process powershell -ArgumentList @(
    "-NoExit",
    "-Command",
    "cd D:\MyAiProject; .\rerank-env\Scripts\Activate.ps1; Write-Host '리랭크 서버 기동 중... (http://localhost:8002)' -ForegroundColor Yellow; python rerank_service.py"
)

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host " 모든 백그라운드 서비스 기동 명령을 실행했습니다." -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
Write-Host ""
Write-Host "남은 작업:"
Write-Host "  - IntelliJ에서 Spring Boot 앱(AiApplication)을 직접 Run 해주세요."
Write-Host ""
Write-Host "잠시(1~2분) 기다린 뒤 아래 명령으로 전부 정상 기동됐는지 확인하세요:"
Write-Host "  docker ps"
Write-Host "  curl.exe http://localhost:8001/health        (음성 서버)"
Write-Host "  curl.exe http://localhost:8002/health        (리랭크 서버)"
Write-Host "  curl.exe http://localhost:7860                (Stable Diffusion WebUI, 브라우저로 접속 권장)"
Write-Host "  curl.exe http://localhost:9200/_cluster/health (OpenSearch)"
Write-Host "  docker logs local-kafka --tail 20          (Kafka - 에러 없이 기동됐는지)"
Write-Host "  docker exec -it local-redis redis-cli ping (Redis - PONG이 나오면 정상)"
Write-Host ""
Write-Host "Spring Boot 앱까지 뜨면 http://localhost:8080 에서 전체 기능을 사용할 수 있습니다."
Write-Host "비동기 문서 업로드(Kafka)는 POST /api/documents/async/upload 로 테스트하세요."
Write-Host "동일 질문 반복 시 Redis 캐시가 적용되어 두 번째 호출부터는 즉시 응답합니다."

Set-Location "D:\MyAiProject"