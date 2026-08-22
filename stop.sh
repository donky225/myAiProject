#!/usr/bin/env bash

# 색상 정의
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${YELLOW}메인 인프라(OpenSearch/Postgres/Ollama) 종료 중...${NC}"
cd /d/MyAiProject || exit
docker compose stop opensearch postgres ollama

echo -e "${YELLOW}Stable Diffusion WebUI 종료 중...${NC}"
cd /d/stable-diffusion-webui-docker || exit
docker compose --profile auto stop

echo -e "${GREEN}완료. 음성 서버(uvicorn) 창은 직접 닫아주세요.${NC}"