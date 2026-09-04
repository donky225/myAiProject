# 전체 환경 설치 가이드 (Windows)

이 문서는 순서대로 명령어를 따라 입력하면 이 프로젝트의 전체 기능(RAG, 이미지 생성, 음성 인식/합성, 실시간 음성 대화, Kafka, Redis)을 동일하게 재현할 수 있도록 작성되었습니다.

기준 환경: Windows 11, NVIDIA GPU(VRAM 6GB 기준)

---

## 목차

1. [사전 준비물 설치](#1-사전-준비물-설치)
2. [프로젝트 소스 받기](#2-프로젝트-소스-받기)
3. [메인 인프라 기동](#3-메인-인프라-기동)
4. [Spring Boot 애플리케이션 실행](#4-spring-boot-애플리케이션-실행)
5. [개발 편의 설정 (라이브 리로드)](#5-개발-편의-설정-라이브-리로드)
6. [이미지 생성 환경 (Stable Diffusion)](#6-이미지-생성-환경-stable-diffusion)
7. [음성 인식/합성 환경 (STT/TTS)](#7-음성-인식합성-환경-stttts)
8. [실시간 음성 대화 (WebSocket)](#8-실시간-음성-대화-websocket)
9. [비동기 문서 인제스트 (Kafka)](#9-비동기-문서-인제스트-kafka)
10. [응답 캐싱 (Redis)](#10-응답-캐싱-redis)
11. [Python 평가 환경 (RAGAS)](#11-python-평가-환경-ragas)
12. [전체 한 번에 실행하기](#12-전체-한-번에-실행하기)
13. [클라우드 배포 (GCP)](#13-클라우드-배포-gcp)
14. [자주 겪는 오류와 해결법 총정리](#14-자주-겪는-오류와-해결법-총정리)

---

## 1. 사전 준비물 설치

- **Git**: https://git-scm.com/download/win
- **IntelliJ IDEA**: https://www.jetbrains.com/idea/download
- **Docker Desktop**: https://www.docker.com/products/docker-desktop (WSL2 백엔드 선택, 설치 후 재부팅)
- **Python 3.11**: https://www.python.org/downloads/windows/ ("Add python.exe to PATH" 체크 필수)
- **FFmpeg**: `winget install Gyan.FFmpeg` (설치 후 모든 터미널 새로 열기)
- **Visual C++ Build Tools**: `winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"`
- **PowerShell 실행 정책**: `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

확인:
```powershell
docker --version
docker compose version
nvidia-smi
python --version
ffmpeg -version
```

---

## 2. 프로젝트 소스 받기

```powershell
cd D:\
git clone <저장소 URL> MyAiProject
cd MyAiProject
```

> **주의**: 프로젝트 폴더 이름을 나중에 바꾸면 Docker 볼륨(Ollama 모델 포함)이 새로 생성되어 기존 데이터가 사라집니다.

---

## 3. 메인 인프라 기동

```powershell
cd D:\MyAiProject
docker compose up -d opensearch postgres ollama ollama-init
```

확인:
```powershell
docker ps
docker exec -it local-ollama ollama list
```

---

## 4. Spring Boot 애플리케이션 실행

### 4.1 "Maven에 IDE 빌드/실행 작업 위임" 체크 (중요)
```
설정(Ctrl+Alt+S) → 빌드, 실행, 배포 → 빌드 도구 → Maven → 러너
→ "Maven에 IDE 빌드/실행 작업 위임" 체크 → 적용
```
안 하면 `release version 21 not supported` 에러가 날 수 있습니다.

### 4.2 콘솔 한글 깨짐 방지
`Run` → `Edit Configurations` → VM 옵션:
```
-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

### 4.3 실행
`AiApplication` 우클릭 → Run. `http://localhost:8080` 접속 확인.

---

## 5. 개발 편의 설정 (라이브 리로드)

### pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### application.yml
```yaml
spring:
  web:
    resources:
      cache:
        period: 0
      chain:
        cache: false
```

### IntelliJ 설정
```
설정 → 빌드, 실행, 배포 → 컴파일러 → "Build project automatically" 체크
Ctrl+Shift+A → Registry... → compiler.automake.allow.when.app.running 체크
```

---

## 6. 이미지 생성 환경 (Stable Diffusion)

별도 폴더에 설치:
```powershell
cd D:\
git clone https://github.com/AbdBarho/stable-diffusion-webui-docker.git
cd stable-diffusion-webui-docker
```

`docker-compose.override.yml` 생성:
```yaml
services:
  auto:
    environment:
      - CLI_ARGS=--api --cors-allow-origins=http://localhost:8080
```

```powershell
docker compose --profile download up --build
docker compose --profile auto up --build
```
`http://localhost:7860` 접속 확인.

---

## 7. 음성 인식/합성 환경 (STT/TTS)

```powershell
mkdir D:\MyAiProject\voice-pipeline
cd D:\MyAiProject\voice-pipeline
python -m venv voice-pipeline-env
.\voice-pipeline-env\Scripts\Activate.ps1

pip install "setuptools<81"
pip install faster-whisper
pip install fastapi uvicorn python-multipart
pip install git+https://github.com/myshell-ai/MeloTTS.git

pip install unidic-lite
New-Item -ItemType Directory -Force -Path ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir"
Copy-Item -Path ".\voice-pipeline-env\Lib\site-packages\unidic_lite\dicdir\*" -Destination ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir\" -Recurse -Force

pip install eunjeon
```

서버 실행 (`voice_service.py`를 이 폴더에 저장 후):
```powershell
uvicorn voice_service:app --host 0.0.0.0 --port 8001
```
확인: `curl.exe http://localhost:8001/health`

---

## 8. 실시간 음성 대화 (WebSocket)

### 8.1 pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 8.2 Tavily API 키 발급 (실시간 웹 검색용)
1. https://tavily.com 가입 (무료: 월 1,000회)
2. API 키 발급
3. `application.yml`에 **최상위 레벨로** 추가:
```yaml
tavily:
  api-key: tvly-여기에-발급받은-키
```

### 8.3 코드 배치
`WebSocketConfig`, `VoiceWebSocketHandler`(`com.ai.llm.voice`), `WebSearchService`(`com.ai.llm.websearch`), `OllamaService.generateStream()` 추가.

### 8.4 VAD 임계값 튜닝
```javascript
const SILENCE_THRESHOLD = 13;  // 조용할 때/말할 때 화면의 실시간 음량 표시를 보고 중간값으로 설정
```

### 8.5 동작 확인
1. 앱 재시작
2. 웹 UI "🎤 마이크 시작" → 말하기 → 잠깐 멈추기
3. 자동으로 캡처 중단 → STT → (필요시 웹검색) → 문장 단위 답변 텍스트+음성이 순서대로 나오는지 확인
4. 답변 끝나면 버튼이 "🎤 마이크 시작"으로 복귀, 다음 질문은 재클릭

---

## 9. 비동기 문서 인제스트 (Kafka)

### 9.1 docker-compose.yml에 Kafka 추가 (공식 이미지, KRaft 모드)
```yaml
  kafka:
    image: apache/kafka:latest
    container_name: local-kafka
    ports:
      - "9092:9092"
    environment:
      - KAFKA_NODE_ID=1
      - KAFKA_PROCESS_ROLES=broker,controller
      - KAFKA_LISTENERS=PLAINTEXT://:19092,CONTROLLER://:9093,PLAINTEXT_HOST://0.0.0.0:9092
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://local-kafka:19092,PLAINTEXT_HOST://localhost:9092
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      - KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT
      - KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_CONTROLLER_QUORUM_VOTERS=1@local-kafka:9093
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
      - KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0
      - KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1
      - KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1
      - CLUSTER_ID=4L6g3nShT-eMCtK--X86sw
    volumes:
      - kafka-data:/tmp/kraft-combined-logs
```

> **주의**: `bitnami/kafka` 계열 태그는 자주 삭제되니 반드시 공식 `apache/kafka` 이미지를 사용하세요.

### 9.2 pom.xml
```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### 9.3 application.yml
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: document-ingestion-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.ai.llm.kafka"
```

### 9.4 코드 배치 (`com.ai.llm.kafka` 패키지)
`DocumentIngestionEvent`, `ByteArrayMultipartFile`, `IngestionStatusService`, `DocumentIngestionProducer`, `DocumentIngestionConsumer`, `AsyncIngestionController`, **`KafkaProducerConfig`(`@EnableKafka` 필수! 없으면 Consumer가 아예 안 뜹니다)**.

### 9.5 동작 확인
```powershell
docker compose up -d kafka
```
```powershell
curl.exe -X POST http://localhost:8080/api/documents/async/upload -F "file=@문서.pdf" -F "store=pgvector"
curl.exe http://localhost:8080/api/documents/async/status/{jobId}
```
`QUEUED` → `PROCESSING` → `DONE`(청크 수 포함)까지 확인.

Consumer 그룹 상태 직접 확인:
```powershell
docker exec -it local-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group document-ingestion-group
```

---

## 10. 응답 캐싱 (Redis)

### 10.1 docker-compose.yml
```yaml
  redis:
    image: redis:7-alpine
    container_name: local-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
```

### 10.2 pom.xml
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 10.3 application.yml
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 10.4 코드 배치
`CacheService`(`com.ai.llm.cache`) 추가, `WebSearchService`와 `RagController`에 캐시 적용.

### 10.5 동작 확인
```powershell
docker compose up -d redis
docker exec -it local-redis redis-cli ping   # PONG 확인
```
같은 질문을 두 번 호출해서 두 번째가 즉시 응답하고 로그에 `[Cache] HIT`가 뜨는지 확인.

---

## 11. Python 평가 환경 (RAGAS)

OpenSearch vs pgvector RAG 응답 품질을 `answer_relevancy` 지표로 정량 비교합니다. OpenAI API 대신 로컬 Ollama(`qwen3:4b` 채점 / `qwen3-embedding:0.6b` 임베딩)로 완전 오프라인 평가하며, `faithfulness`는 로컬 소형 모델로는 안정적인 JSON 체인 출력이 어려워 제외했습니다.

```powershell
cd D:\MyAiProject
python -m venv rag-eval-env
.\rag-eval-env\Scripts\Activate.ps1
pip install "ragas<0.4" langchain-community langchain-ollama pandas requests psycopg2-binary
python collect_results.py
python evaluate_ragas.py
```

> **주의**: 반드시 `(rag-eval-env)`가 프롬프트 앞에 붙은 걸 확인하고 실행하세요. venv 활성화 없이 시스템 Python으로 실행하면 `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'`처럼 설치된 버전 조합이 안 맞아 엉뚱한 에러가 납니다.

`evaluate_ragas.py` 실행 시 Windows에서 전 항목이 정확히 `RunConfig`의 timeout(600초)에 걸려 전부 실패하는 증상이 있다면 14.16번 항목을 확인하세요.

결과는 `ragas_scores.csv`로 저장되고, store별 평균은 아래처럼 바로 확인할 수 있습니다:
```powershell
python -c "
import pandas as pd
df = pd.read_csv('ragas_scores.csv')
print(df.groupby('store')[['answer_relevancy']].mean().round(3))
"
```

---

## 12. 전체 한 번에 실행하기

`start-all.ps1`, `stop-all.ps1`을 `D:\MyAiProject`에 저장:
```powershell
cd D:\MyAiProject
.\start-all.ps1
```
Docker 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis), Stable Diffusion, 음성 서버를 새 창에서 기동. 이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**.

종료: `.\stop-all.ps1`

---

## 13. 클라우드 배포 (GCP)

경량화된 버전(OpenSearch/Stable Diffusion/음성 파이프라인 제외, pgvector+Ollama(CPU)+Spring Boot만)을 GCP 무료 체험으로 배포하는 절차입니다.

### 13.1 GCP 가입
https://cloud.google.com/free 에서 가입 ($300 크레딧, 90일). Oracle Cloud Always Free도 대안이지만, 가입 심사가 매우 까다로워(VPN/카드/전화번호 등) 실패하는 경우가 흔합니다.

### 13.2 VM 인스턴스 생성
- 리전: `asia-northeast3` (서울)
- 머신: E2 시리즈, `e2-standard-4` (vCPU 4, RAM 16GB)
- OS: Ubuntu 22.04 LTS, 디스크 50GB
- 방화벽: HTTP/HTTPS 트래픽 허용 체크

### 13.3 방화벽 규칙 추가 (앱 포트)
`VPC 네트워크 → 방화벽 → 방화벽 규칙 만들기` (Compute Engine 메뉴가 아닌 별도 메뉴입니다):
- 이름: `allow-8080`
- 대상: 네트워크의 모든 인스턴스
- 소스 IPv4 범위: `0.0.0.0/0`
- 프로토콜/포트: TCP, `8080`

### 13.4 서버에 Docker 설치
SSH 접속(콘솔의 "SSH" 버튼으로 브라우저에서 바로 접속 가능) 후:
```bash
sudo apt update
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
# 이후 SSH 재접속 필요
```

### 13.5 프로젝트 배포
```bash
git clone <저장소 URL> myaiproject
cd myaiproject
```

경량 `docker-compose.server.yml` 작성 (OpenSearch 제외, Ollama GPU 예약 블록 제거):
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      - POSTGRES_USER=rag_user
      - POSTGRES_PASSWORD=rag_password
      - POSTGRES_DB=rag_db
    ports: ["5432:5432"]
    volumes: ["pgvector-data:/var/lib/postgresql/data"]

  ollama:
    image: ollama/ollama:latest
    ports: ["11434:11434"]
    volumes: ["ollama-data:/root/.ollama"]
    # GPU 없는 서버이므로 GPU 예약 설정 제거, CPU로 구동

  ollama-init:
    image: ollama/ollama:latest
    depends_on: [ollama]
    entrypoint: >
      sh -c "until curl -s http://ollama:11434 >/dev/null; do sleep 2; done;
      OLLAMA_HOST=http://ollama:11434 ollama pull qwen3:4b;
      OLLAMA_HOST=http://ollama:11434 ollama pull qwen3-embedding:0.6b;"

  app:
    build: .
    depends_on: [ollama-init, postgres]
    ports: ["8080:8080"]
    environment:
      - SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/rag_db
      - SPRING_DATASOURCE_USERNAME=rag_user
      - SPRING_DATASOURCE_PASSWORD=rag_password

volumes:
  ollama-data:
  pgvector-data:
```

```bash
docker compose -f docker-compose.server.yml up -d --build
docker logs rag-app --tail 50
```

> **중요**: `OpenSearchIndexInitializer`가 OpenSearch 연결 실패 시 예외를 던져 앱 전체가 기동 실패하는 버그가 있습니다. 반드시 14.7번 항목의 수정을 먼저 적용하세요.

접속 확인: `http://<외부IP>:8080`

---

## 14. 자주 겪는 오류와 해결법 총정리

### 14.1 `Connection refused` (인프라 포트)
컨테이너 미기동 또는 볼륨 충돌. `docker ps` 확인 후 개별 기동.

### 14.2 PowerShell `curl` 이상 동작
`curl.exe`로 명시 호출, 한글/공백은 `-G --data-urlencode` 사용.

### 14.3 OpenSearch `Field 'embedding' is not knn_vector type`
인덱스 삭제 후 매핑 없이 재생성됨. 매핑을 먼저 지정해 재생성 (README 참고).

### 14.4 pgvector 검색 결과가 항상 비어있음
OpenSearch 임계값(0.55)을 그대로 적용하면 안 됨. pgvector 전용 낮은 임계값(0.25) 적용.

### 14.5 `pkg_resources` / `unidic download` / `eunjeon` 빌드 오류
각각 `setuptools<81` 고정, `unidic-lite`로 우회, Visual C++ Build Tools 설치로 해결 (7장 참고).

### 14.6 `faster-whisper`의 `cublas64_12.dll` 오류
CUDA 툴킷 미설치. `device="cpu"`로 설정 (small 모델은 CPU도 충분).

### 14.7 GCP 서버에서 앱이 기동조차 안 됨 (`OpenSearchIndexInitializer`)
**원인**: `@PostConstruct`가 OpenSearch 연결 실패 시 예외를 던져 Spring 컨텍스트 전체가 기동 실패.
**해결**: 아래처럼 바깥쪽 try-catch로 감싸 연결 실패를 경고 로그로만 남기고 넘어가도록 수정:
```java
@PostConstruct
public void createIndexIfNotExists() {
    try {
        try {
            // 기존 로직 (HEAD 요청 → 없으면 PUT으로 생성)
        } catch (HttpClientErrorException.NotFound e) {
            // 인덱스 생성
        }
    } catch (Exception e) {
        log.warn("OpenSearch에 연결할 수 없어 인덱스 초기화를 건너뜁니다: {}", e.getMessage());
    }
}
```

### 14.8 `bitnami/kafka:3.7` 이미지를 찾을 수 없음
Bitnami 무료 태그 정책 변경으로 구버전 삭제. **공식 `apache/kafka:latest`로 전환**.

### 14.9 Kafka `KafkaTemplate` 빈을 찾을 수 없음
Boot 자동 생성 템플릿이 와일드카드(`<?,?>`) 타입이라 구체 제네릭 타입(`KafkaTemplate<String, DocumentIngestionEvent>`)과 불일치. `ProducerFactory`/`KafkaTemplate`을 정확한 타입으로 직접 빈 등록.

### 14.10 Kafka Consumer Group이 생성되지 않음 (`GroupIdNotFoundException`)
**원인**: `@KafkaListener`는 인식되지만 리스너 컨테이너 자체가 기동 안 됨 (콘솔에 Producer 로그만 있고 Consumer 로그가 전혀 없는 게 증거).
**해결**: `@Configuration` 클래스에 **`@EnableKafka`** 추가. 이게 없으면 어노테이션만 있고 실제로는 아무 것도 동작 안 함.

### 14.11 Oracle Cloud 가입 반복 실패
VPN 끄기, 시크릿 모드, 전화번호/카드 정보 재확인. 반복 실패 시 GCP 무료 체험으로 전환 추천.

### 14.12 GCP 방화벽 메뉴를 못 찾음
Compute Engine 메뉴 안에 없고 **VPC 네트워크 → 방화벽**(별도 최상위 메뉴)에 있습니다. 검색이 안 되면 URL로 직접 이동: `console.cloud.google.com/networking/firewalls/list`

### 14.13 실시간 대화가 계속 영어로 응답
프롬프트에 "한국어로만 답하라"는 지시문 명시적으로 포함.

### 14.14 마이크 발화 중 요청이 여러 번 겹침
발화 종료 감지 즉시 오디오 캡처만 중단(소켓 유지) → 답변 완료 후 완전 종료하는 "한 번에 한 질문" 흐름으로 변경.

### 14.15 `index.html` 수정이 재시작 없이는 반영 안 됨
`spring-boot-devtools` 추가로 라이브 리로드 활성화 (5장 참고).

### 14.16 RAGAS 평가가 항목마다 정확히 timeout(600초)에 걸려 전부 실패
**증상**: 진행률 바에서 매 항목이 정확히 600.01초에 `TimeoutError()`를 던짐. 소요 시간에 편차가 없다는 것이 단순히 "느린" 게 아니라 요청이 아예 응답을 못 받고 멈춘(hang) 상태라는 신호입니다.
**진단 순서**: PowerShell에서 `Invoke-RestMethod`로 Ollama의 `/api/chat`, `/api/embed`를 직접 호출해 개별 호출 자체는 정상(수십 초 이내)인지 먼저 확인. 개별 호출은 정상인데 RAGAS를 통하면 멈춘다면 RAGAS/LangChain의 비동기 실행 레이어 문제로 좁혀집니다.
**원인**: Windows 기본 `ProactorEventLoop`가 `langchain_ollama`의 비동기 HTTP 클라이언트와 충돌해, 요청이 실제로는 응답을 받고도 콜백이 걸리지 않고 무한 대기.
**해결**: `evaluate_ragas.py` 최상단(다른 import보다 먼저)에 추가:
```python
import asyncio
import sys

if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
```

### 14.17 `evaluate_ragas.py` 실행 시 `KeyError: 'faithfulness'`
**원인**: `evaluate()` 호출의 `metrics=[]`에는 `AnswerRelevancy()`만 넣어놓고, 마지막 summary 집계 코드에는 `["faithfulness", "answer_relevancy"]` 두 컬럼을 그대로 참조해 컬럼 불일치 발생.
**해결**: 둘 중 하나로 통일. `faithfulness`를 실제로 채점하지 않는다면 summary 쪽에서도 제거:
```python
summary = scored_df.groupby("store")[["answer_relevancy"]].mean()
```

### 14.18 venv 미활성화 상태로 실행 시 엉뚱한 `ModuleNotFoundError`
**증상**: `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'` 등, 분명 설치했는데 없다는 에러.
**원인**: 프롬프트에 `(rag-eval-env)`가 안 붙어 있는 상태 — 즉 venv가 활성화되지 않아 시스템 전역 Python의 site-packages(버전 조합이 안 맞는)를 참조.
**해결**: `.\rag-eval-env\Scripts\Activate.ps1`로 venv부터 활성화 후 재실행. 에러 스택의 파일 경로가 `...\rag-eval-env\...`가 아니라 `...\AppData\Local\Programs\Python\...`이면 venv 미활성화가 확실합니다.

---

## 참고: 프로젝트 구조 요약

```
D:\MyAiProject\
├── src\main\java\com\ai\llm\
│   ├── rag\             # RagService, PgVectorRagService, RagController(+캐시)
│   ├── pgvector\        # PgVectorService, PgVectorIngestService, OCR 서비스
│   ├── opensearch\      # OpenSearchService, OpenSearchIndexInitializer
│   ├── ollama\          # OllamaService(생성/스트리밍), 프롬프트 번역
│   ├── voice\           # VoiceService, VoiceController, WebSocketConfig, VoiceWebSocketHandler
│   ├── websearch\       # WebSearchService (Tavily, 캐시 적용)
│   ├── kafka\           # DocumentIngestionEvent/Producer/Consumer, KafkaProducerConfig, IngestionStatusService
│   └── cache\           # CacheService (Redis)
├── src\main\resources\
│   ├── application.yml
│   └── static\index.html
├── docker-compose.yml           # 로컬용 (OpenSearch+pgvector+Ollama+Kafka+Redis, GPU)
├── docker-compose.server.yml    # GCP 배포용 (경량, CPU)
├── rag-eval-env\        # RAGAS 평가용 venv (answer_relevancy 평가 완료)
├── voice-pipeline\       # STT/TTS Python 서버
├── start-all.ps1 / stop-all.ps1
└── start.sh / stop.sh    # Git Bash용

D:\stable-diffusion-webui-docker\   # 별도 저장소
```
