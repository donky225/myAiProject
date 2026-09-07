#!/usr/bin/env bash

# 색상 정의
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${YELLOW}메인 인프라(OpenSearch/Postgres/Ollama/Kafka/Redis/Prometheus/Grafana) 종료 중...${NC}"
cd /d/MyAiProject || exit
docker compose stop opensearch postgres ollama kafka redis prometheus grafana

echo -e "${YELLOW}Stable Diffusion WebUI(Docker 컨테이너) 종료 중...${NC}"
cd /d/stable-diffusion-webui-docker || exit
docker compose --profile auto stop

echo ""
echo -e "${GREEN}Docker 기반 서비스 종료 완료.${NC}"
echo -e "${YELLOW}다음은 Docker가 아니라 별도 mintty 창에서 실행 중이므로 직접 닫아주세요:${NC}"
echo "  - 음성 서버 (uvicorn, voice-pipeline 창)"
echo "  - 리랭크 서버 (python rerank_service.py 창)"
echo "  - Stable Diffusion WebUI 콘솔 창 (컨테이너는 위에서 이미 stop됨, 창만 닫으면 됨)"
