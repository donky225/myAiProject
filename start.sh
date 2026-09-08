#!/usr/bin/env bash
#
# 사용법:
#   ./start.sh                # 기본: 메인 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis/Prometheus/Grafana) + 리랭크 서버만 기동
#   ./start.sh --sd           # 위에 + Stable Diffusion WebUI
#   ./start.sh --voice        # 위에 + 음성 파이프라인 서버
#   ./start.sh --sd --voice   # 위에 + 둘 다
#   ./start.sh --all          # 전부 기동 (--sd --voice와 동일)
#
# RAG 텍스트 질답 테스트만 할 때는 옵션 없이 실행하면 필요한 것만 가볍게 뜹니다.
# 이미지 생성이나 실시간 음성 대화를 테스트할 때만 해당 플래그를 붙이세요.

WITH_SD=false
WITH_VOICE=false

for arg in "$@"; do
    case "$arg" in
        --sd) WITH_SD=true ;;
        --voice) WITH_VOICE=true ;;
        --all) WITH_SD=true; WITH_VOICE=true ;;
        *) echo "알 수 없는 옵션: $arg (사용 가능: --sd, --voice, --all)"; exit 1 ;;
    esac
done

# 색상 정의
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${CYAN}==================================================${NC}"
echo -e "${CYAN} 1/3. 메인 인프라 기동 (OpenSearch / PostgreSQL+pgvector / Ollama / Kafka / Redis / Prometheus / Grafana)${NC}"
echo -e "${CYAN}==================================================${NC}"

cd /d/MyAiProject || exit
docker compose up -d opensearch postgres ollama ollama-init kafka redis prometheus grafana

if [ "$WITH_SD" = true ]; then
    echo ""
    echo -e "${CYAN}==================================================${NC}"
    echo -e "${CYAN} 2/3. Stable Diffusion WebUI 기동 (새 창에서 실행됩니다)${NC}"
    echo -e "${CYAN}==================================================${NC}"

    start mintty bash -c "cd /d/stable-diffusion-webui-docker; echo -e '${YELLOW}Stable Diffusion WebUI 기동 중... (http://localhost:7860)${NC}'; docker compose --profile auto up; exec bash"
else
    echo ""
    echo -e "${YELLOW}(건너뜀) Stable Diffusion WebUI — 필요하면 './start.sh --sd'로 켜세요${NC}"
fi

if [ "$WITH_VOICE" = true ]; then
    echo ""
    echo -e "${CYAN}==================================================${NC}"
    echo -e "${CYAN} 3/3. 음성 파이프라인 서버(STT/TTS) 기동 (새 창에서 실행됩니다)${NC}"
    echo -e "${CYAN}==================================================${NC}"

    start mintty bash -c "cd /d/MyAiProject/voice-pipeline; source ./voice-pipeline-env/Scripts/activate; echo -e '${YELLOW}음성 서버 기동 중... (http://localhost:8001)${NC}'; uvicorn voice_service:app --host 0.0.0.0 --port 8001; exec bash"
else
    echo -e "${YELLOW}(건너뜀) 음성 파이프라인 서버 — 필요하면 './start.sh --voice'로 켜세요${NC}"
fi

echo ""
echo -e "${CYAN}==================================================${NC}"
echo -e "${CYAN} 리랭크 서버(RAG 재정렬, BAAI/bge-reranker-v2-m3) 기동 (새 창에서 실행됩니다)${NC}"
echo -e "${CYAN}==================================================${NC}"

start mintty bash -c "cd /d/MyAiProject; source ./rerank-env/Scripts/activate; echo -e '${YELLOW}리랭크 서버 기동 중... (http://localhost:8002)${NC}'; python rerank_service.py; exec bash"

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
if [ "$WITH_VOICE" = true ]; then
    echo "  curl http://localhost:8001/health        (음성 서버)"
fi
echo "  curl http://localhost:8002/health        (리랭크 서버)"
if [ "$WITH_SD" = true ]; then
    echo "  curl http://localhost:7860                (Stable Diffusion WebUI, 브라우저로 접속 권장)"
fi
echo "  curl http://localhost:9200/_cluster/health (OpenSearch)"
echo "  docker logs local-kafka --tail 20          (Kafka - 에러 없이 기동됐는지)"
echo "  curl http://localhost:9090/-/healthy       (Prometheus)"
echo "  브라우저로 http://localhost:3000 접속 (Grafana, admin/admin)"
echo ""
echo "Spring Boot 앱까지 뜨면 http://localhost:8080 에서 전체 기능을 사용할 수 있습니다."
echo "비동기 문서 업로드(Kafka)는 POST /api/documents/async/upload 로 테스트하세요."