#!/usr/bin/env bash

# 색상 정의
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${CYAN}==================================================${NC}"
echo -e "${CYAN} 1/3. 메인 인프라 기동 (OpenSearch / PostgreSQL+pgvector / Ollama)${NC}"
echo -e "${CYAN}==================================================${NC}"

cd /d/MyAiProject || exit
docker compose up -d opensearch postgres ollama ollama-init

echo ""
echo -e "${CYAN}==================================================${NC}"
echo -e "${CYAN} 2/3. Stable Diffusion WebUI 기동 (새 창에서 실행됩니다)${NC}"
echo -e "${CYAN}==================================================${NC}"

start mintty bash -c "cd /d/stable-diffusion-webui-docker; echo -e '${YELLOW}Stable Diffusion WebUI 기동 중... (http://localhost:7860)${NC}'; docker compose --profile auto up; exec bash"

echo ""
echo -e "${CYAN}==================================================${NC}"
echo -e "${CYAN} 3/3. 음성 파이프라인 서버(STT/TTS) 기동 (새 창에서 실행됩니다)${NC}"
echo -e "${CYAN}==================================================${NC}"

start mintty bash -c "cd /d/MyAiProject/voice-pipeline; source ./voice-pipeline-env/Scripts/activate; echo -e '${YELLOW}음성 서버 기동 중... (http://localhost:8001)${NC}'; uvicorn voice_service:app --host 0.0.0.0 --port 8001; exec bash"

echo ""
echo -e "${GREEN}==================================================${NC}"
echo -e "${GREEN} 모든 백그라운드 서비스 기동 명령을 실행했습니다.${NC}"
echo -e "${GREEN}==================================================${NC}"
echo ""
echo "남은 작업:"
echo "  - IntelliJ에서 Spring Boot 앱(AiApplication)을 직접 Run 해주세요."
echo ""
echo "잠시(1~2분) 기다린 뒤 아래 명령으로 전부 정상 기동됐는지 확인하세요:"
echo "  docker ps"
echo "  curl http://localhost:8001/health        (음성 서버)"
echo "  curl http://localhost:7860                (Stable Diffusion WebUI, 브라우저로 접속 권장)"
echo "  curl http://localhost:9200/_cluster/health (OpenSearch)"
echo ""
echo "Spring Boot 앱까지 뜨면 http://localhost:8080 에서 전체 기능을 사용할 수 있습니다."