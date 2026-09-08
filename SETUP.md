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
12. [QLoRA 로컬 파인튜닝 환경 (Unsloth)](#12-qlora-로컬-파인튜닝-환경-unsloth)
13. [리랭킹 환경 (Cross-Encoder Reranking)](#13-리랭킹-환경-cross-encoder-reranking)
14. [MCP 서버 환경 (Model Context Protocol)](#14-mcp-서버-환경-model-context-protocol)
15. [실시간 스트리밍 응답 (Flux + SSE)](#15-실시간-스트리밍-응답-flux--sse)
16. [관측성 환경 (Prometheus + Grafana)](#16-관측성-환경-prometheus--grafana)
17. [전체 한 번에 실행하기](#17-전체-한-번에-실행하기)
18. [클라우드 배포 (GCP)](#18-클라우드-배포-gcp)
19. [자주 겪는 오류와 해결법 총정리](#19-자주-겪는-오류와-해결법-총정리)

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

## 12. QLoRA 로컬 파인튜닝 환경 (Unsloth)

RTX 3060 6GB VRAM에서 Unsloth로 `Qwen3-4B`를 QLoRA(4bit) 파인튜닝하고 GGUF로 변환해 Ollama에 등록하는 환경입니다. 목적은 도메인 특화가 아닌 QLoRA 기법 자체의 시연이며, 공개 데이터셋(`yahma/alpaca-cleaned`)을 사용합니다.

> **환경을 WSL2 Ubuntu로 분리하는 이유**: Windows 네이티브에서도 Unsloth가 동작은 하지만, `bitnami/kafka` 이미지 문제처럼 `bitsandbytes`/`triton` 계열 패키지가 리눅스 기준으로 빌드·배포되는 경우가 많아 Windows에서 설치 실패가 잦습니다. WSL2 + conda 조합이 가장 안정적입니다.

### 12.1 WSL2 Ubuntu 설치 확인 및 준비

Docker Desktop이 쓰는 `docker-desktop` WSL 인스턴스와 일반 작업용 Ubuntu는 별개입니다. 확인:

```powershell
wsl -l -v
```

`Ubuntu`가 목록에 없다면 새로 설치:

```powershell
wsl --install -d Ubuntu
```

설치 중 유닉스 사용자명/비밀번호를 설정합니다. 완료 후:

```powershell
wsl -d Ubuntu
```

GPU 인식 확인 (Docker Desktop의 WSL2 GPU 연동이 이미 돼 있어 드라이버 재설치 없이 바로 되는 경우가 대부분):

```bash
nvidia-smi
```
RTX 3060, VRAM 6144MiB 정도가 출력되면 정상입니다.

### 12.2 Miniconda 설치 (Python 3.11 격리 환경)

WSL Ubuntu의 시스템 기본 Python이 3.14처럼 최신 버전이면 `bitsandbytes`/`unsloth` 생태계와 호환이 안 될 수 있습니다. `deadsnakes` PPA로 3.11을 받으려 해도 최신 Ubuntu 배포판엔 아직 패키지가 없는 경우가 있어(15.20 참고), Miniconda로 격리하는 게 가장 안정적입니다.

```bash
cd ~
wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh
bash Miniconda3-latest-Linux-x86_64.sh -b -p $HOME/miniconda3
```

`-b -p` 옵션으로 라이선스 동의 등 모든 대화형 프롬프트를 건너뛰고 자동 설치합니다(대화형으로 진행 시 중간에 다른 명령이 섞이면 설치가 조용히 실패하는 경우가 있었음 — 15.21 참고).

```bash
~/miniconda3/bin/conda init bash
source ~/.bashrc
conda --version
```

**주의**: `conda init` 직후에는 반드시 `source ~/.bashrc`로 재로드하거나 터미널을 완전히 새로 열어야 `conda` 명령이 인식됩니다.

Anaconda 채널 이용약관(ToS) 동의가 필요할 수 있습니다 (15.22 참고):
```bash
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
```

conda 환경 생성:
```bash
conda create -n qlora python=3.11 -y
conda activate qlora
python --version   # Python 3.11.x 확인
```

### 12.3 PyTorch(CUDA) 및 Unsloth 설치

```bash
pip install torch --index-url https://download.pytorch.org/whl/cu121
python -c "import torch; print(torch.__version__, torch.cuda.is_available(), torch.cuda.get_device_name(0))"
```
`True`와 GPU 이름이 나와야 정상입니다.

```bash
sudo apt update
sudo apt install build-essential -y   # gcc — Triton 커널 컴파일에 필수 (15.19 참고)
pip install unsloth
```

설치 검증 겸 실제 4bit 모델 로드까지 확인:
```bash
python -c "
from unsloth import FastLanguageModel
import torch
model, tokenizer = FastLanguageModel.from_pretrained(
    model_name='unsloth/Qwen3-4B-unsloth-bnb-4bit',
    max_seq_length=1024,
    load_in_4bit=True,
)
print('모델 로드 성공, VRAM(GB):', torch.cuda.memory_allocated()/1024**3)
"
```
6GB 중 3~4GB대로 로드되면 정상입니다.

### 12.4 학습 실행

`train_qlora.py`를 프로젝트 폴더(`~/qlora-project`)에 배치 (Windows에서 다운로드한 경우 `/mnt/c/Users/<사용자명>/Downloads/train_qlora.py`에서 `cp`로 복사).

```bash
cd ~/qlora-project
pip install trl datasets
python train_qlora.py            # 짧은 시험 학습 (기본, 10 step)
QUICK_TEST=0 python train_qlora.py  # 본 학습 (200 step, 15~20분 내외 — GGUF 변환의 최초 원본 모델 재다운로드 포함)
```

**핵심 설계 포인트 (15.24, 15.27 트러블슈팅과 직결)**: 학습 데이터 포맷은 반드시 `tokenizer.apply_chat_template()`로 Qwen3 고유 ChatML 포맷을 사용해야 합니다. Alpaca 원본의 `### 지시사항:` 포맷으로 학습하면 Ollama 서빙 시 사용되는 ChatML 템플릿과 불일치해 무한 반복 생성이 발생할 수 있습니다.

### 12.5 GGUF 변환 및 Ollama 등록

`train_qlora.py`의 본 학습(`QUICK_TEST=0`)이 완료되면 자동으로 GGUF 변환까지 진행됩니다 (`qwen3-4b-qlora-demo_gguf/` 폴더에 `.gguf`와 `Modelfile` 생성).

WSL → Windows로 결과물 복사 (Ollama가 Windows 네이티브에서 서비스 중이므로):
```bash
cp -r qwen3-4b-qlora-demo_gguf /mnt/c/Users/<사용자명>/qwen3-4b-qlora-demo_gguf
```

Modelfile의 `repeat_penalty`를 반드시 확인/수정 (기본값 `1`은 반복 억제가 꺼진 상태 — 15.24 참고):
```bash
sed -i 's/PARAMETER repeat_penalty 1/PARAMETER repeat_penalty 1.15/' /mnt/c/Users/<사용자명>/qwen3-4b-qlora-demo_gguf/Modelfile
```

PowerShell에서 등록:
```powershell
cd C:\Users\<사용자명>\qwen3-4b-qlora-demo_gguf
ollama create qwen3-4b-qlora-demo -f .\Modelfile
ollama run qwen3-4b-qlora-demo "테스트 질문"
```

### 12.6 Before/After 비교 (베이스 모델과 공정 비교)

Qwen3 계열은 SYSTEM 프롬프트로 한국어를 강제해도 내부 thinking 채널까지는 통제되지 않아 영어로 새어나올 수 있습니다 (15.27 참고). 비교 시 두 모델 모두 동일 조건으로 맞추는 걸 권장합니다.

베이스 모델용 한국어 강제 Modelfile 생성:
```powershell
@"
FROM qwen3:4b
SYSTEM 항상 한국어로만 답변하세요. 영어를 절대 사용하지 마세요.
"@ | Out-File -Encoding utf8 Modelfile-base-ko -NoNewline

ollama create qwen3-4b-ko -f .\Modelfile-base-ko
```

비교 실행 (thinking 노출 억제):
```powershell
ollama run qwen3-4b-ko --hidethinking "질문"
ollama run qwen3-4b-qlora-demo "질문"
```

---

## 13. 리랭킹 환경 (Cross-Encoder Reranking)

벡터 검색(bi-encoder)이 뽑은 top-10 후보를 cross-encoder 리랭커(`BAAI/bge-reranker-v2-m3`)로 재정렬해 관련성을 높입니다. 기존 `voice-pipeline`과 동일한 패턴(독립 FastAPI 서비스)으로 구현해, 메인 Spring Boot 앱과 분리했습니다.

### 13.1 리랭크 서비스 설치 및 실행

```powershell
cd D:\MyAiProject
python -m venv rerank-env
.\rerank-env\Scripts\Activate.ps1
pip install fastapi uvicorn sentence-transformers torch
```

> **주의**: `pip install torch`는 Windows에서 기본적으로 **CPU 전용 빌드**를 설치합니다. 리랭커(1.1GB급, cross-encoder)는 CPU로도 충분히 빠르므로 `rerank_service.py`의 `DEVICE = "cpu"` 그대로 두는 걸 권장합니다. GPU를 꼭 쓰고 싶다면 `pip install torch --index-url https://download.pytorch.org/whl/cu121`로 재설치 후 `DEVICE = "cuda"`로 변경하세요 (단, Ollama가 이미 VRAM 3~4GB를 쓰고 있어 6GB 환경에서는 동시 구동 시 빠듯할 수 있습니다).

```powershell
python rerank_service.py
```
`리랭커 모델 로드 완료`, `Uvicorn running on http://0.0.0.0:8002` 로그가 뜨면 정상입니다. 첫 실행 시 모델(약 2.5GB)을 자동 다운로드합니다.

### 13.2 동작 확인

새 PowerShell 창에서:
```powershell
Invoke-RestMethod -Uri http://localhost:8002/health
```

```powershell
$body = @{
    query = "블록체인의 보안 원리"
    documents = @(
    @{ id = "1"; text = "블록체인은 각 블록이 이전 블록의 해시를 포함해 체인처럼 연결되어 변조가 어렵습니다." }
    @{ id = "2"; text = "오늘 서울 날씨는 맑고 기온은 20도입니다." }
    )
    top_k = 1
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri http://localhost:8002/rerank -Method Post -Body $body -ContentType "application/json"
```
날씨 문서가 제외되고 블록체인 문서가 높은 점수로 반환되면 정상입니다.

### 13.3 Spring Boot 통합

`pom.xml`에 별도 의존성 추가는 필요 없습니다 (`RestClient`는 Spring Boot 기본 제공). `RerankService.java`(새 패키지 `com.ai.llm.rerank`)를 배치하고, 기존 `RagService.java`(OpenSearch)와 `PgVectorRagService.java`에 리랭킹을 통합합니다.

`application.yml`:
```yaml
rerank:
  enabled: true
  service-url: http://localhost:8002
  candidate-count: 10   # 벡터검색으로 넓게 가져올 후보 수
  top-k: 3               # 리랭크 후 최종 컨텍스트로 쓸 문서 수
```

**설계 핵심**:
- 벡터 검색을 기존 top-3(또는 5)에서 top-10으로 넓혀 리랭커에게 재정렬할 재료를 충분히 제공
- 리랭크 서비스 호출 실패 시 예외를 던지지 않고 빈 결과를 반환 → 호출부가 기존 코사인 유사도 방식으로 자동 폴백 (Redis 캐시 페일세이프와 동일한 철학)
- 리랭커에 넘기는 텍스트는 원본 문서와 최대한 동일한 형식(제목+본문 등)으로 맞춰야 스토어 간 공정 비교가 가능 (17.29 참고)

### 13.4 리랭킹 켬/끔 정량 비교 (RAGAS)

`collect_results.py`, `evaluate_ragas.py`에 `RERANK_LABEL` 환경변수를 추가해, `application.yml`의 `rerank.enabled`를 켬/끔으로 각각 재시작하며 결과를 따로 수집·평가할 수 있게 했습니다.

```powershell
# 1) rerank.enabled: true 로 Spring Boot 재시작 후
$env:RERANK_LABEL = "on"
python collect_results.py
python evaluate_ragas.py

# 2) rerank.enabled: false 로 Spring Boot 재시작 후
$env:RERANK_LABEL = "off"
python collect_results.py
python evaluate_ragas.py
```

결과 비교:
```powershell
python -c "
import pandas as pd
on_df = pd.read_csv('ragas_scores_on.csv')
off_df = pd.read_csv('ragas_scores_off.csv')
print('=== 리랭킹 ON ==='); print(on_df.groupby('store')[['answer_relevancy']].mean().round(3))
print('=== 리랭킹 OFF ==='); print(off_df.groupby('store')[['answer_relevancy']].mean().round(3))
"
```

---

## 14. MCP 서버 환경 (Model Context Protocol)

기존 RAG 파이프라인(벡터검색 → 리랭킹 → LLM 생성)을 MCP 표준 도구로 노출해, Claude Desktop 같은 MCP 클라이언트가 직접 호출할 수 있게 합니다. Spring AI 2.0의 네이티브 `@McpTool` 어노테이션을 사용해 별도 언어/프레임워크 없이 기존 스택 그대로 구현합니다.

### 14.1 의존성 추가

`pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

### 14.2 설정

`application.yml`:
```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: local-ai-platform-mcp
        version: 1.0.0
        type: SYNC
```

### 14.3 도구 구현

`RagMcpTools.java`(새 패키지 `com.ai.llm.mcp`)를 배치합니다. `@McpTool` 메서드 하나가 기존 `RagService`/`PgVectorRagService`의 `askWithContext()`를 그대로 호출하는 얇은 어댑터 역할만 합니다 — MCP 계층에 검색/생성 로직을 새로 만들지 않습니다.

Maven 재빌드 후 재시작:
```powershell
cd D:\MyAiProject
.\mvnw.cmd clean install -DskipTests
```
콘솔 로그에 `Registered tools: 1`이 뜨면 도구 등록 성공입니다.

### 14.4 엔드포인트 확인

Spring AI MCP webmvc의 기본 SSE 엔드포인트는 `/sse`입니다. 브라우저로 직접 열어 확인할 수 있습니다:
```
http://localhost:8080/sse
```
`event:endpoint` / `data:/mcp/message?sessionId=...` 형태의 SSE 스트림이 보이면 정상입니다 (탭은 확인 후 닫아서 세션을 낭비하지 않도록 합니다).

### 14.5 MCP Inspector로 검증

Claude Desktop 설정을 건드리기 전에, 공식 테스트 도구로 먼저 검증합니다. Node.js가 없다면 먼저 설치:
```powershell
winget install OpenJS.NodeJS.LTS
```
**설치 후 PowerShell 창을 완전히 새로 열어야** PATH가 반영됩니다 (같은 창에서 계속 시도하면 `npx: 용어가 인식되지 않습니다` 에러가 반복됨).

```powershell
node --version   # 새 창에서 확인
npx @modelcontextprotocol/inspector
```

브라우저에서 Inspector가 열리면:
1. **Add Servers** → Transport: **SSE** (또는 `Streamable HTTP`) → URL: `http://localhost:8080/sse`
2. 서버 카드의 토글 스위치를 켜서 **Connect**
3. `Connected` 상태 확인 후, 상단 **Tools** 탭 → 좌측 도구 목록에서 `search_company_documents` 선택
4. `question` 파라미터에 **실제 업로드된 문서 내용과 관련된 질문**을 입력 (프로젝트에 없는 내용을 물으면 "찾을 수 없습니다"로 정직하게 답하는 게 정상 동작입니다)
5. **Run Tool** → 결과에 `answer`, `sources`, `elapsedMillis`가 반환되면 성공. 동시에 리랭크 서비스 창에 `POST /rerank` 로그가 찍히는지 확인하면, MCP 호출이 기존 리랭킹 파이프라인까지 전부 태웠다는 것이 검증됩니다.

---

## 15. 실시간 스트리밍 응답 (Flux + SSE)

텍스트 RAG 질답도 답변을 한 번에 기다리지 않고, 토큰 단위로 실시간 스트리밍합니다. Java 쪽은 `Flux<String>`을 `text/event-stream`으로 반환하고, 프론트엔드는 브라우저 표준 `EventSource`로 수신합니다.

### 15.1 의존성

별도 의존성 추가가 필요 없습니다. `reactor-core`는 Spring AI가 이미 transitively 가져오므로, Spring MVC(서블릿 기반) 앱에서도 WebFlux 전체 스택 없이 `Flux` 반환 타입을 그대로 쓸 수 있습니다.

### 15.2 핵심 구현

- `OllamaService`: 기존 콜백 기반 `generateStream(prompt, Consumer<String>)`(음성 파이프라인용)은 그대로 두고, `Flux.create()`로 감싼 `generateStreamReactive()`를 추가. 블로킹 HTTP 스트리밍 호출은 `Schedulers.boundedElastic()`에서 실행해 서블릿 요청 스레드를 막지 않습니다.
- `RagService`/`PgVectorRagService`: 기존 `ask()`와 동일한 검색+리랭킹+프롬프트 로직을 재사용하는 `askStream()` 추가. 벡터검색은 스트림 구독 전에 동기로 먼저 끝냅니다.
- `RagController`: `GET /api/rag/ask/stream` (`produces = TEXT_EVENT_STREAM_VALUE`) 신설. **캐싱은 적용하지 않음** (부분 토큰 스트림을 캐시했다가 재생하는 것은 복잡도 대비 이득이 적어, 캐싱이 필요하면 기존 `/api/rag/ask`를 쓰도록 분리).
- `index.html`: jQuery CDN 추가, `ask()` 함수를 `EventSource` 기반으로 교체. 토큰이 도착할 때마다 `#answer`에 이어붙여 타이핑 효과를 냅니다.

### 15.3 정상 종료 처리 (중요)

`Flux`가 정상 완료되어 서버가 SSE 연결을 닫으면, 브라우저의 `EventSource`는 기본적으로 **자동 재연결**을 시도합니다. 이때 `readyState`가 `CLOSED`가 아니라 `CONNECTING`이 되어, 정상 종료와 실제 연결 장애를 구분하기 어렵습니다 (19.34 참고).

**해결**: 스트림 끝에 명시적 종료 마커를 하나 더 흘려보냅니다.
```java
public static final String STREAM_DONE_MARKER = "[[STREAM_DONE]]";

@GetMapping(value = "/api/rag/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> askStream(...) {
        Flux<String> tokens = ragService.askStream(question);
        return tokens.concatWith(Flux.just(STREAM_DONE_MARKER));
        }
```
클라이언트는 이 마커를 받으면 화면에 표시하지 않고 `eventSource.close()`를 먼저 호출해, 브라우저의 자동 재연결이 시작되기 전에 선제적으로 연결을 끊습니다.

### 15.4 테스트

```powershell
cd D:\MyAiProject
.\mvnw.cmd clean install -DskipTests
```
Spring Boot 재시작 후 `http://localhost:8080`에서 질문 입력 → 답변이 한 글자씩(또는 토큰 단위로) 실시간으로 채워지는지 확인. 브라우저 개발자도구(F12) → Network 탭 → 요청 클릭 → **EventStream** 탭에서 프레임이 여러 번에 나눠 도착하는지로도 검증 가능합니다 (답변이 짧으면 체감상 한 번에 온 것처럼 보일 수 있어, 이 방법이 가장 확실합니다).

---

## 16. 관측성 환경 (Prometheus + Grafana)

Micrometer(애플리케이션 내부 계측) → Prometheus(수집/저장) → Grafana(시각화) 스택으로, 요청 지연시간·캐시 히트율·Kafka consumer lag·리랭킹 성공/폴백 비율을 대시보드로 관찰합니다.

### 16.1 의존성

`pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
<groupId>io.micrometer</groupId>
<artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### 16.2 설정

`application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, prometheus, metrics
  endpoint:
    prometheus:
      enabled: true
  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true   # p50/p95/p99 계산용 히스토그램 버킷
    tags:
      application: rag-app

logging:
  level:
    com.ai.llm: DEBUG
    root: INFO
```

**Kafka consumer lag은 별도 코드가 필요 없습니다** — `micrometer-registry-prometheus`가 클래스패스에 있으면 Spring for Apache Kafka가 자동으로 `KafkaClientMetrics`를 등록해, `kafka_consumer_fetch_manager_records_lag_max` 지표가 자동으로 노출됩니다.

### 16.3 커스텀 메트릭 (캐시 히트율, 리랭킹 성공/폴백)

`CacheService`에 `MeterRegistry`를 주입받아 히트/미스/에러 `Counter`를 추가하고, `RerankService`에도 성공/폴백 `Counter`를 추가합니다 (`rag_cache_hits_total`, `rag_cache_misses_total`, `rag_rerank_success_total`, `rag_rerank_fallback_total`).

### 16.4 Docker Compose 통합

`docker-compose.yml`에 `prometheus`, `grafana` 서비스를 추가합니다. Spring Boot 앱과 리랭크 서비스는 Docker 밖(IntelliJ/venv)에서 직접 실행되므로, Prometheus는 `host.docker.internal`로 접근합니다.

**필요한 설정 파일들** (프로젝트 루트 기준):
```
prometheus.yml                              # 스크레이프 대상 정의
grafana-provisioning/datasources/datasource.yml  # Prometheus 데이터소스 자동 등록
grafana-provisioning/dashboards/dashboard.yml    # 대시보드 프로비저닝 설정
grafana-dashboards/rag-observability.json        # 대시보드 정의 (6개 패널)
```

> **주의**: `docker compose up` 실행 전에 이 파일들이 실제로 존재해야 합니다. 파일이 없는 상태에서 먼저 `docker compose up`을 실행하면, Docker가 볼륨 마운트 경로를 찾다가 호스트에 없으면 **자동으로 빈 디렉토리를 생성**해버립니다. 이후 진짜 파일을 같은 이름으로 복사해도 이미 생성된 디렉토리에 가려 인식되지 않는 문제가 있었습니다 (19.35 참고). 파일을 먼저 배치한 뒤 `docker compose up`을 실행하세요. 이미 잘못 생성된 경우:
> ```powershell
> Remove-Item D:\MyAiProject\prometheus.yml -Recurse -Force
> # 이후 실제 파일로 교체
> ```

### 16.5 기동 및 확인

```powershell
docker compose up -d prometheus grafana
```
(또는 `start.sh`/`start-all.ps1`에 이미 포함되어 있어 전체 기동 시 같이 뜹니다.)

1. `http://localhost:8080/actuator/prometheus` — Micrometer 메트릭 텍스트 출력 확인
2. `http://localhost:9090/targets` — `spring-boot-rag` 타겟이 `UP`인지 확인
3. `http://localhost:3000` (Grafana, `admin`/`admin`) → 왼쪽 사이드바 **Dashboards** 메뉴 → "Local AI Platform - RAG 관측성" 클릭

**Kafka Consumer Lag 패널 테스트**:
```powershell
1..5 | ForEach-Object {
    curl.exe -X POST "http://localhost:8080/api/documents/async/upload" -F "file=@test.pdf" -F "store=pgvector"
}
```
여러 건을 한꺼번에 큐에 넣어 Consumer가 처리하는 동안 lag이 순간적으로 올라갔다가 내려가는 그래프를 관찰할 수 있습니다.

**캐시 히트율 패널 테스트**:
```powershell
$q = [System.Uri]::EscapeDataString("테스트 질문")
Invoke-RestMethod -Uri "http://localhost:8080/api/rag/ask?question=$q&store=opensearch"
Measure-Command { Invoke-RestMethod -Uri "http://localhost:8080/api/rag/ask?question=$q&store=opensearch" }
```
두 번째 호출이 수십 ms 이내로 나오면 캐시 히트가 확인된 것입니다 (첫 호출은 벡터검색+리랭킹+LLM 생성까지 거쳐 수 초 소요).

---

## 17. 전체 한 번에 실행하기

`start-all.ps1`, `stop-all.ps1`을 `D:\MyAiProject`에 저장:
```powershell
cd D:\MyAiProject
.\start-all.ps1                # 기본: 메인 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis/Prometheus/Grafana) + 리랭크 서버만
.\start-all.ps1 -Sd            # 위에 + Stable Diffusion WebUI
.\start-all.ps1 -Voice         # 위에 + 음성 파이프라인 서버
.\start-all.ps1 -All           # 전부 기동
```
Git Bash에서는 `./start.sh`, `./start.sh --sd`, `./start.sh --voice`, `./start.sh --all`로 동일하게 사용합니다.

Stable Diffusion/음성 서버는 GPU·리소스를 적지 않게 쓰고 기동에 시간이 걸려서, 텍스트 RAG 질답만 테스트할 땐 옵션 없이 가볍게 띄우고 필요할 때만 `-Sd`/`-Voice`(또는 `--sd`/`--voice`)로 켜는 것을 권장합니다. 메인 인프라와 리랭크 서비스는 텍스트 RAG에 항상 필요해 옵션 없이 기동됩니다.

이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**.

종료: `.\stop-all.ps1` (또는 `./stop.sh`)

---

## 18. 클라우드 배포 (GCP)

경량화된 버전(OpenSearch/Stable Diffusion/음성 파이프라인 제외, pgvector+Ollama(CPU)+Spring Boot만)을 GCP 무료 체험으로 배포하는 절차입니다.

### 14.1 GCP 가입
https://cloud.google.com/free 에서 가입 ($300 크레딧, 90일). Oracle Cloud Always Free도 대안이지만, 가입 심사가 매우 까다로워(VPN/카드/전화번호 등) 실패하는 경우가 흔합니다.

### 14.2 VM 인스턴스 생성
- 리전: `asia-northeast3` (서울)
- 머신: E2 시리즈, `e2-standard-4` (vCPU 4, RAM 16GB)
- OS: Ubuntu 22.04 LTS, 디스크 50GB
- 방화벽: HTTP/HTTPS 트래픽 허용 체크

### 14.3 방화벽 규칙 추가 (앱 포트)
`VPC 네트워크 → 방화벽 → 방화벽 규칙 만들기` (Compute Engine 메뉴가 아닌 별도 메뉴입니다):
- 이름: `allow-8080`
- 대상: 네트워크의 모든 인스턴스
- 소스 IPv4 범위: `0.0.0.0/0`
- 프로토콜/포트: TCP, `8080`

### 14.4 서버에 Docker 설치
SSH 접속(콘솔의 "SSH" 버튼으로 브라우저에서 바로 접속 가능) 후:
```bash
sudo apt update
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
# 이후 SSH 재접속 필요
```

### 14.5 프로젝트 배포
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

## 19. 자주 겪는 오류와 해결법 총정리

### 19.1 `Connection refused` (인프라 포트)
컨테이너 미기동 또는 볼륨 충돌. `docker ps` 확인 후 개별 기동.

### 19.2 PowerShell `curl` 이상 동작
`curl.exe`로 명시 호출, 한글/공백은 `-G --data-urlencode` 사용.

### 19.3 OpenSearch `Field 'embedding' is not knn_vector type`
인덱스 삭제 후 매핑 없이 재생성됨. 매핑을 먼저 지정해 재생성 (README 참고).

### 19.4 pgvector 검색 결과가 항상 비어있음
OpenSearch 임계값(0.55)을 그대로 적용하면 안 됨. pgvector 전용 낮은 임계값(0.25) 적용.

### 19.5 `pkg_resources` / `unidic download` / `eunjeon` 빌드 오류
각각 `setuptools<81` 고정, `unidic-lite`로 우회, Visual C++ Build Tools 설치로 해결 (7장 참고).

### 19.6 `faster-whisper`의 `cublas64_12.dll` 오류
CUDA 툴킷 미설치. `device="cpu"`로 설정 (small 모델은 CPU도 충분).

### 19.7 GCP 서버에서 앱이 기동조차 안 됨 (`OpenSearchIndexInitializer`)
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

### 19.8 `bitnami/kafka:3.7` 이미지를 찾을 수 없음
Bitnami 무료 태그 정책 변경으로 구버전 삭제. **공식 `apache/kafka:latest`로 전환**.

### 19.9 Kafka `KafkaTemplate` 빈을 찾을 수 없음
Boot 자동 생성 템플릿이 와일드카드(`<?,?>`) 타입이라 구체 제네릭 타입(`KafkaTemplate<String, DocumentIngestionEvent>`)과 불일치. `ProducerFactory`/`KafkaTemplate`을 정확한 타입으로 직접 빈 등록.

### 19.10 Kafka Consumer Group이 생성되지 않음 (`GroupIdNotFoundException`)
**원인**: `@KafkaListener`는 인식되지만 리스너 컨테이너 자체가 기동 안 됨 (콘솔에 Producer 로그만 있고 Consumer 로그가 전혀 없는 게 증거).
**해결**: `@Configuration` 클래스에 **`@EnableKafka`** 추가. 이게 없으면 어노테이션만 있고 실제로는 아무 것도 동작 안 함.

### 19.11 Oracle Cloud 가입 반복 실패
VPN 끄기, 시크릿 모드, 전화번호/카드 정보 재확인. 반복 실패 시 GCP 무료 체험으로 전환 추천.

### 19.12 GCP 방화벽 메뉴를 못 찾음
Compute Engine 메뉴 안에 없고 **VPC 네트워크 → 방화벽**(별도 최상위 메뉴)에 있습니다. 검색이 안 되면 URL로 직접 이동: `console.cloud.google.com/networking/firewalls/list`

### 19.13 실시간 대화가 계속 영어로 응답
프롬프트에 "한국어로만 답하라"는 지시문 명시적으로 포함.

### 19.14 마이크 발화 중 요청이 여러 번 겹침
발화 종료 감지 즉시 오디오 캡처만 중단(소켓 유지) → 답변 완료 후 완전 종료하는 "한 번에 한 질문" 흐름으로 변경.

### 19.15 `index.html` 수정이 재시작 없이는 반영 안 됨
`spring-boot-devtools` 추가로 라이브 리로드 활성화 (5장 참고).

### 19.16 RAGAS 평가가 항목마다 정확히 timeout(600초)에 걸려 전부 실패
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

### 19.17 `evaluate_ragas.py` 실행 시 `KeyError: 'faithfulness'`
**원인**: `evaluate()` 호출의 `metrics=[]`에는 `AnswerRelevancy()`만 넣어놓고, 마지막 summary 집계 코드에는 `["faithfulness", "answer_relevancy"]` 두 컬럼을 그대로 참조해 컬럼 불일치 발생.
**해결**: 둘 중 하나로 통일. `faithfulness`를 실제로 채점하지 않는다면 summary 쪽에서도 제거:
```python
summary = scored_df.groupby("store")[["answer_relevancy"]].mean()
```

### 19.18 venv 미활성화 상태로 실행 시 엉뚱한 `ModuleNotFoundError`
**증상**: `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'` 등, 분명 설치했는데 없다는 에러.
**원인**: 프롬프트에 `(rag-eval-env)`가 안 붙어 있는 상태 — 즉 venv가 활성화되지 않아 시스템 전역 Python의 site-packages(버전 조합이 안 맞는)를 참조.
**해결**: `.\rag-eval-env\Scripts\Activate.ps1`로 venv부터 활성화 후 재실행. 에러 스택의 파일 경로가 `...\rag-eval-env\...`가 아니라 `...\AppData\Local\Programs\Python\...`이면 venv 미활성화가 확실합니다.

### 19.19 QLoRA 학습 중 Triton 커널 컴파일 실패 (`RuntimeError: Failed to find C compiler`)
**증상**: `train_qlora.py` 실행 중 `triton/runtime/build.py`에서 `Failed to find C compiler. Please specify via CC environment variable` 에러로 학습이 0%에서 멈춤.
**원인**: WSL2 Ubuntu에 gcc 등 C 컴파일러가 설치되어 있지 않아, Triton이 GPU 커널(RMSNorm 등)을 런타임에 컴파일하지 못함.
**해결**:
```bash
sudo apt update
sudo apt install build-essential -y
gcc --version  # 확인
```

### 19.20 최신 Ubuntu에서 `deadsnakes` PPA로 Python 3.11 설치 실패
**증상**: `add-apt-repository ppa:deadsnakes/ppa` 실행 후 `apt update`를 해도 저장소가 추가된 흔적이 없고, `python3.11`을 찾을 수 없음.
**원인**: WSL Ubuntu 배포판이 최신 버전(예: `resolute`)일 경우, deadsnakes PPA가 아직 해당 코드네임용 패키지를 제공하지 않을 수 있음.
**해결**: apt/PPA로 씨름하지 말고 Miniconda로 격리된 Python 3.11 환경 구성 (12.2 참고).

### 19.21 Miniconda 설치가 조용히 실패 (`~/miniconda3` 폴더 자체가 생성 안 됨)
**증상**: 설치 스크립트가 끝난 것처럼 보였는데 `conda: command not found`, `ls ~/miniconda3` 결과 `No such file or directory`.
**원인**: 대화형(라이선스 동의, 경로 확인 등 프롬프트 입력) 설치 도중 다른 명령이 섞여 들어가면서 설치가 중간에 끊긴 것으로 추정.
**해결**: 배치 모드(`-b -p`)로 모든 대화형 프롬프트를 건너뛰고 재설치. 설치 명령 실행 후에는 완전히 끝날 때까지 다른 명령을 입력하지 않아야 함:
```bash
bash ~/Miniconda3-latest-Linux-x86_64.sh -b -p $HOME/miniconda3
```

### 19.22 `conda create` 시 `CondaToSNonInteractiveError`
**증상**: `conda create -n qlora python=3.11 -y` 실행 시 `Terms of Service have not been accepted` 에러.
**원인**: 최근 Anaconda 정책 변경으로 `pkgs/main`, `pkgs/r` 채널의 이용약관 동의가 선행되어야 함.
**해결**:
```bash
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
```

### 19.23 여러 터미널(PowerShell/Git Bash/WSL) 혼동으로 명령이 엉뚱하게 실행됨
**증상**: 분명 WSL에서 작업 중인 줄 알았는데 `PS C:\...>`나 `MINGW64` 프롬프트에서 명령이 실행되어 엉뚱한 Python/패키지 경로를 참조하거나, `sudo` 인증 실패, 명령이 이전 입력과 겹쳐 붙는 등의 증상이 발생.
**원인**: WSL Ubuntu, PowerShell, Git Bash가 서로 다른 파일시스템·패키지 환경을 가지는데 터미널 창을 오가며 작업하다 보니 어느 셸에 있는지 놓침.
**해결**: 프롬프트 형태로 항상 구분. WSL Ubuntu는 `(qlora) choi@DESKTOP-...:~/qlora-project$`, PowerShell은 `PS C:\...>`, Git Bash는 `MINGW64` 표시. 명령이 이상하게 합쳐져 실행되면(`unslothpip` 등) `Ctrl+U`로 줄을 비우고 붙여넣기는 `Ctrl+Shift+V` 사용.

### 19.24 GGUF 모델이 추론 시 같은 문구를 무한 반복
**증상**: `ollama run`으로 파인튜닝 모델을 실행하면 "적절히 지시하는 작업을 적절히..." 처럼 같은 구절이 끝없이 반복됨.
**원인**: 두 가지가 겹침 — ① Unsloth가 자동 생성한 Modelfile의 `PARAMETER repeat_penalty`가 `1`(반복 억제 없음)로 설정됨. ② 학습 시 사용한 Alpaca 원본 포맷(`### 지시사항: ... ### 응답:`)이 Ollama Modelfile의 실제 서빙 템플릿(Qwen3 ChatML, `<|im_start|>...<|im_end|>`)과 달라, 모델이 언제 답변을 멈춰야 하는지에 대한 신호가 학습 때와 추론 때 어긋남.
**해결**: `repeat_penalty`를 `1.15` 정도로 조정하고, 학습 데이터 포맷을 `tokenizer.apply_chat_template()`로 Qwen3 ChatML과 일치시켜 재학습 (12.4 참고).

### 19.25 Ollama Modelfile에 `PARAMETER think false` 추가 시 `Error: unknown parameter 'think'`
**증상**: Modelfile에 thinking 비활성화를 고정하려고 `PARAMETER think false`를 추가하면 `ollama create`가 에러를 냄.
**원인**: thinking on/off는 Modelfile의 `PARAMETER`로 지원되는 옵션이 아님.
**해결**: 해당 줄 제거. 대신 실행 시점에 `ollama run 모델명 --think=false` 또는 `--hidethinking` 플래그로 제어.

### 19.26 PowerShell에서 메모장으로 만든 Modelfile을 `ollama create`가 못 찾음
**증상**: `ollama create -f .\Modelfile-base-ko` 실행 시 `Error: no Modelfile or safetensors files found`.
**원인**: 메모장이 저장 시 자동으로 `.txt` 확장자를 붙여 실제 파일명이 `Modelfile-base-ko.txt`가 됨.
**해결**:
```powershell
dir Modelfile-base-ko*   # 실제 파일명 확인
Rename-Item Modelfile-base-ko.txt Modelfile-base-ko
```
또는 애초에 PowerShell에서 직접 생성해 확장자 문제 회피:
```powershell
@"
FROM qwen3:4b
SYSTEM 항상 한국어로만 답변하세요.
"@ | Out-File -Encoding utf8 Modelfile-base-ko -NoNewline
```

### 19.27 SYSTEM 프롬프트로 한국어를 강제해도 `<think>` 블록만 영어로 나옴
**증상**: `SYSTEM 항상 한국어로만 답변하세요`를 지정해도 최종 답변은 한국어인데 `<think>...</think>` 내부 추론 과정은 전부 영어로 생성됨.
**원인**: Qwen3 계열 모델은 SYSTEM 프롬프트가 최종 출력 언어에는 적용되지만, 내부 thinking 채널까지는 강제하지 못하는 것으로 관찰됨.
**해결**: thinking 자체를 끄거나(`--think=false`) 화면 노출만 차단(`--hidethinking`). 정확한 비교 실험이 필요하면 두 방식 중 하나로 통일해 조건을 맞출 것.

### 19.28 리랭커(`sentence-transformers CrossEncoder`)가 `AssertionError: Torch not compiled with CUDA enabled`
**증상**: `rerank_service.py` 실행 시 `CrossEncoder(..., device="cuda")` 초기화 단계에서 CUDA 미지원 에러.
**원인**: `pip install torch`를 `--index-url` 없이 실행하면 Windows에서 기본적으로 CPU 전용 빌드가 설치됨.
**해결**: 리랭커는 가벼운 모델(1.1GB)이라 `device="cpu"`로 전환해도 속도 저하가 크지 않음. GPU가 꼭 필요하면 `pip uninstall torch -y` 후 `pip install torch --index-url https://download.pytorch.org/whl/cu121`로 재설치.

### 19.29 리랭킹 적용 후 pgvector 스토어의 `answer_relevancy`가 오히려 하락
**증상**: RAGAS로 리랭킹 켬/끔 비교 시, OpenSearch는 개선(0.611→0.676)됐지만 pgvector는 악화(0.536→0.484)됨.
**원인 후보**: ① 두 스토어에 동일한 리랭크 임계값(0.5)을 캘리브레이션 없이 적용 ② 청킹 전략 차이(pgvector는 문장 단위, OpenSearch는 별도 방식) ③ 표본 수(스토어당 9~10건)가 작아 통계적 노이즈일 가능성.
**대응**: 원인을 하나로 단정하지 않고 정직하게 혼재 결과로 문서화. 스토어별 임계값 개별 캘리브레이션과 표본 확대가 후속 과제로 남음. (참고: 리랭커에 넘기는 텍스트 포맷—제목 포함 여부 등—을 스토어 간 통일하는 것도 공정한 비교의 전제 조건.)

### 19.30 RAG 텍스트 API 응답이 질문과 무관하게 영어로 나옴
**증상**: 한국어로 질문해도 `/api/rag/ask` 응답이 영어로 나오는 경우 발생 (리랭킹과 무관).
**원인**: 텍스트 RAG 프롬프트(`RagService`, `PgVectorRagService`)에는애초에 언어 지시가 없었음 — "한국어로만 답하라"는 지시문이 실시간 음성 파이프라인 프롬프트에만 있고 텍스트 API 프롬프트엔 누락돼 있었음.
**해결**: 두 서비스의 모든 프롬프트 템플릿(simple/strict, 컨텍스트 있음/없음 분기 포함) 맨 앞에 "반드시 한국어로만 답변하세요. 영어를 사용하지 마세요." 명시.

### 19.31 MCP Inspector 실행 시 `npx: 용어가 인식되지 않습니다`
**증상**: `npx @modelcontextprotocol/inspector` 실행 시 명령을 찾을 수 없다는 에러. `winget install OpenJS.NodeJS.LTS`로 설치 완료 메시지를 봤는데도 계속 같은 에러.
**원인**: Node.js를 설치해도, **이미 열려있던 PowerShell 세션은 갱신된 PATH를 읽지 못함**.
**해결**: 설치 후 PowerShell 창을 완전히 닫고 새로 열어야 함. 그래도 안 되면 재부팅하거나 `$env:Path -split ';' | Select-String -Pattern "nodejs"`로 PATH 등록 여부를 직접 확인.

### 19.32 `start.sh`로 Stable Diffusion/음성 서버 창이 하나도 안 뜸
**증상**: 기존에는 정상적으로 새 mintty 창들이 떴는데, 이번엔 아무 창도 안 뜨고 조용히 끝남.
**원인**: IntelliJ 내장 실행 버튼(▶)으로 셸 스크립트를 돌려서 발생. IntelliJ가 관리하는 제한된 프로세스 환경에서는 `start`(cmd 내장 명령)나 `mintty`(GUI 새 창 실행) 같은, 실제 터미널 세션에 의존하는 명령이 조용히 실패할 수 있음.
**해결**: IntelliJ의 스크립트 실행 버튼을 쓰지 말고, 탐색기에서 "Git Bash Here"나 시작 메뉴에서 Git Bash를 직접 열어 그 안에서 `./start.sh` 실행. IntelliJ는 Spring Boot 앱(`AiApplication`) 자체를 Run하는 용도로만 사용.

### 19.33 MCP 서버의 SSE 엔드포인트 경로를 몰라 Inspector 연결 실패
**증상**: MCP Inspector에서 어떤 URL로 연결해야 할지 알 수 없어 여러 경로(`/mcp`, `/mcp/sse` 등)를 시도.
**원인**: Spring AI MCP webmvc 스타터의 기본 SSE 경로에 대한 정보가 문서마다 표현이 달라 혼동.
**해결**: 브라우저로 직접 `http://localhost:8080/sse`를 열어 `event:endpoint` / `data:/mcp/message?sessionId=...` 형태의 SSE 스트림이 출력되는지로 실제 경로를 확인. (커스터마이징하지 않았다면 `/sse`가 기본값.)

### 19.34 SSE 스트림이 정상 완료됐는데도 프론트엔드에 "연결이 끊겼습니다" 에러 표시
**증상**: `Flux<String>`이 정상적으로 끝나고 답변도 완전히 다 나왔는데, 그 직후 화면에 에러 메시지가 추가로 붙음.
**원인**: 브라우저의 `EventSource`는 서버가 SSE 연결을 닫으면 기본적으로 **자동 재연결**을 시도하며, 이때 `readyState`가 `CLOSED`가 아니라 `CONNECTING`이 되어 `onerror` 핸들러에서 정상 종료와 실제 연결 장애를 구분하기 어려움.
**해결**: 서버 쪽 `Flux`에 명시적 종료 마커(`[[STREAM_DONE]]`)를 `concatWith`로 추가하고, 클라이언트는 이 마커를 받으면 브라우저의 자동 재연결이 시작되기 전에 먼저 `eventSource.close()`를 호출 (15.3 참고).

### 19.35 `docker compose up`이 볼륨 마운트할 설정 파일을 못 찾고 `not a directory` 에러
**증상**: `prometheus.yml`이나 Grafana 프로비저닝 파일을 실제로 프로젝트 폴더에 복사했는데도 `mount ... not a directory: Are you trying to mount a directory onto a file` 에러 발생.
**원인**: 실제 설정 파일을 배치하기 **전에** 먼저 `docker compose up`을 한 번이라도 시도하면, Docker가 호스트 쪽 경로가 없다고 판단해 **자동으로 빈 디렉토리를 생성**함. 이후 진짜 파일을 같은 경로/이름으로 복사해도, 이미 존재하는 디렉토리에 파일을 못 만들어 실제로는 여전히 빈 폴더인 상태로 남음.
**해결**:
```powershell
Test-Path D:\MyAiProject\prometheus.yml -PathType Container   # True면 폴더로 잘못 생성된 것
Remove-Item D:\MyAiProject\prometheus.yml -Recurse -Force
# 이후 실제 파일로 교체
```
같은 이유로 `grafana-provisioning/`, `grafana-dashboards/` 하위 파일들도 같은 문제를 겪을 수 있어 동일하게 확인.

### 19.36 Grafana에 대시보드가 자동으로 안 보임 (`Recent dashboards 0`)
**증상**: 프로비저닝 설정을 다 마쳤는데 Grafana 홈 화면의 "Recent dashboards"에 아무것도 안 뜸.
**원인**: "Recent dashboards"는 프로비저닝 여부와 무관하게, **최근에 실제로 열어본** 대시보드만 보여주는 위젯. 프로비저닝 자체는 정상이어도 처음 접속하면 당연히 비어있음.
**해결**: 왼쪽 사이드바의 **Dashboards** 메뉴를 직접 클릭해 목록에서 확인.

### 19.37 비동기 문서 업로드로 `.txt` 파일을 올리면 항상 PDF 파싱 에러로 실패
**증상**: `POST /api/documents/async/upload`로 텍스트 파일을 올리면 `java.io.IOException: Error: End-of-File, expected line at offset ...`로 항상 실패. Kafka Consumer 로그의 스택 트레이스가 `PdfTextExtractionService.extractText()` → `Loader.loadPDF()`를 가리킴.
**원인**: `PgVectorIngestService.ingestPdf()`가 업로드된 파일의 실제 형식을 확인하지 않고, 이름과 무관하게 무조건 PDFBox로 파싱을 시도하도록 구현되어 있었음. 텍스트 파일을 PDF 헤더로 파싱하려다 실패.
**해결**: 파일 확장자(`.pdf`)와 `Content-Type`(`application/pdf`)을 확인해, PDF일 때만 `PdfTextExtractionService`로 파싱하고 그 외는 UTF-8로 직접 읽도록 분기 추가.
```java
boolean looksLikePdf = lowerName.endsWith(".pdf") || "application/pdf".equals(contentType);
        String text = looksLikePdf
        ? pdfTextExtractionService.extractText(file)
        : new String(file.getBytes(), StandardCharsets.UTF_8);
```
참고: 청킹 시 공백 제외 30자 미만 청크는 노이즈로 간주해 자동 제외되므로 (`isNoiseChunk`), 아주 짧은 테스트 문서는 `chunksIngested: 0`이 정상일 수 있음 — 실제 버그(파싱 실패)와는 구분해서 봐야 함.

---

## 20. 쿠버네티스(Helm)로 전체 스택 배포

### 20.1 이걸 왜 했는가

지금까지는 `docker-compose.yml` 하나로 모든 컴포넌트(OpenSearch, pgvector, Kafka, Redis, Ollama, Spring Boot 등)를 로컬 PC에서 개인 개발용으로만 띄워왔습니다. 이 방식은 "내 컴퓨터에서 잘 돌아가는 것"까지만 증명합니다.

실제 회사의 운영 환경(특히 채용 공고에서 요구하는 "쿠버네티스 경험")은 다음과 같은 질문에 답할 수 있어야 합니다.

- 여러 컴포넌트가 있는 서비스를 **어떻게 하나의 명령으로 배포**하는가?
- 컴포넌트 하나(예: Spring Boot)가 죽었을 때 **자동으로 재시작**되는가?
- 설정값(DB 접속정보, API 키 등)을 **코드와 분리해서 안전하게 관리**하는가?
- 여러 서버로 확장할 때도 **동일한 방식으로 배포**할 수 있는가?

이 질문들에 실제로 답하기 위해, Docker Compose로 돌던 스택을 **Helm(쿠버네티스 패키지 관리 도구)** 차트로 다시 구성해 배포해봤습니다. Docker Compose는 그대로 남겨뒀고(로컬 개발용, GPU 서비스 전용), Helm 차트는 별도로 추가한 것이라 **기존 작업 방식은 전혀 바뀌지 않습니다.**

### 20.2 왜 "전부 다" 쿠버네티스로 옮기지 않았는가 (하이브리드 구조인 이유)

이 프로젝트는 Ollama, Stable Diffusion, 리랭커처럼 **GPU가 있어야 실용적으로 도는 컴포넌트**가 있습니다. 그런데 개발 PC(RTX 3060, VRAM 6GB 단일 GPU)에서 흔히 쓰는 **Minikube**나 **Docker Desktop의 쿠버네티스**는 구조적으로 GPU를 파드(컨테이너) 안으로 전달(패스스루)하는 게 기본 지원되지 않거나, 별도의 복잡한 설정(NVIDIA GPU Operator 등)이 필요합니다.

그래서 이번 작업은 **"GPU가 필요 없는 컴포넌트만" 쿠버네티스(Helm)로 옮기고, GPU가 필요한 컴포넌트(Ollama 등)는 기존 Docker Compose로 남겨서 두 환경을 연결**하는 방식을 택했습니다.

| 구분 | 어디서 도는가 | 이유 |
|---|---|---|
| Spring Boot, OpenSearch, pgvector, Redis, Kafka, Prometheus, Grafana | **쿠버네티스(Helm)** | GPU 불필요, 컨테이너 오케스트레이션의 장점(자동 재시작, 설정 분리 등)을 온전히 누릴 수 있음 |
| Ollama, Stable Diffusion, 리랭커, 음성 서버 | **기존 Docker Compose** | GPU 필요. 로컬 PC의 GPU 패스스루 제약 때문에 쿠버네티스로 옮기면 오히려 더 불안정해짐 |

**장점**: 이렇게 하면 "왜 전체를 다 쿠버네티스로 안 옮겼는가"라는 질문에 "GPU 제약을 파악하고, 무리하게 다 옮기기보다 컴포넌트별 특성에 맞게 배치를 나눈 것"이라고 기술적 근거를 갖고 답할 수 있습니다. 억지로 다 옮겨서 불안정하게 만드는 것보다 훨씬 성숙한 엔지니어링 판단입니다.

**주의사항**: 쿠버네티스 파드 안의 Spring Boot가 Docker Compose로 뜬 Ollama에 접속하려면, 파드 입장에서 "내 컴퓨터(호스트 PC)"를 가리키는 특수 주소가 필요합니다. Docker Desktop 환경에서는 `host.docker.internal`이라는 이름이 이 역할을 하지만, **쿠버네티스 클러스터를 어떤 방식으로 만들었는지(kind / kubeadm)에 따라 이 이름이 자동으로 인식되지 않을 수 있습니다.** 실제로 이번 작업에서도 이 문제를 겪었고, 아래 20.7절에 해결 과정을 정리해뒀습니다.

### 20.3 사전 준비물

| 도구 | 용도 | 설치 확인 방법 |
|---|---|---|
| Docker Desktop (Kubernetes 활성화) | 쿠버네티스 클러스터 자체를 실행 | Docker Desktop 좌측 메뉴 → Kubernetes 탭에서 "Active" 확인 |
| Helm | 쿠버네티스에 여러 리소스를 한 번에 배포하는 패키지 관리 도구 | PowerShell에서 `helm version` |
| kubectl | 쿠버네티스 클러스터에 명령을 내리는 CLI (Docker Desktop 설치 시 자동 포함) | PowerShell에서 `kubectl version` |

Helm이 없다면 관리자 권한 PowerShell에서:
```powershell
winget install Helm.Helm
```
설치 후 **새 PowerShell 창**을 열어야 정상 인식됩니다(환경변수 PATH 갱신 때문).

Docker Desktop에서 Kubernetes를 처음 켤 때는 "Cluster type"으로 **Kubeadm**을 선택하는 걸 권장합니다(로컬 `docker build`로 만든 이미지를 별도 로드 과정 없이 바로 쓸 수 있어서 더 간단합니다).

### 20.4 배포 절차 (처음 한 번)

**1단계 — Spring Boot를 컨테이너 이미지로 빌드**

쿠버네티스는 "소스 코드"가 아니라 "이미지(컨테이너로 실행 가능한 패키지)" 단위로 배포합니다. 그래서 IntelliJ로 직접 실행하는 것과 달리, 먼저 이미지를 만들어야 합니다.

```powershell
cd D:\MyAiProject
docker build -t local-ai-platform/springboot:latest .
```

**2단계 — GPU가 필요한 서비스는 기존 Compose로 먼저 켜기**

```powershell
docker compose up -d ollama
```

(Stable Diffusion, 리랭커, 음성 서버는 각자의 방식대로 별도 실행 — 6~8, 13장 참고)

**3단계 — Helm으로 나머지 전체 스택을 한 번에 배포**

```powershell
cd D:\MyAiProject\helm
helm install ai-platform ./local-ai-platform `
  --set secrets.postgresPassword=원하는비밀번호 `
  --set monitoring.grafana.adminPassword=원하는비밀번호
```

이 명령 한 줄이 Spring Boot, OpenSearch, pgvector, Redis, Kafka, Prometheus, Grafana **7개 컴포넌트를 동시에** 만들어줍니다. Docker Compose로 컨테이너 여러 개를 하나하나 관리하던 것과 달리, Helm은 이걸 하나의 "릴리스"로 묶어서 관리합니다.

### 20.5 평상시 켜고 끄는 방법 (개발자가 아니어도 따라 할 수 있도록)

**전체 상태 확인 (가장 자주 쓰는 명령)**
```powershell
kubectl get pods -n ai-platform
```
표에 `READY`가 `1/1`, `STATUS`가 `Running`이면 정상입니다.

**배포된 걸 끄기 (컴퓨터 종료 전에 필수는 아님 — Docker Desktop만 꺼도 됨)**
```powershell
helm uninstall ai-platform -n ai-platform
```
→ Helm으로 만든 모든 리소스를 한 번에 삭제합니다. (참고: 이건 "완전 삭제"라 데이터도 같이 지워질 수 있으니, 정말 다 지우고 싶을 때만 사용)

**일부만 재시작하고 싶을 때 (예: Spring Boot 코드/설정을 바꾼 뒤)**
```powershell
kubectl rollout restart deployment springboot -n ai-platform
```

**설정 파일(values.yaml, templates 등)을 바꾼 뒤 다시 반영하기**
```powershell
cd D:\MyAiProject\helm
helm upgrade ai-platform ./local-ai-platform --set secrets.postgresPassword=기존값 --set monitoring.grafana.adminPassword=기존값
```
→ `helm install`은 "처음 배포"할 때, `helm upgrade`는 "이미 배포된 걸 바꿀 때" 씁니다. **비밀번호 값은 처음 install 때와 반드시 동일하게** 넣어야 합니다(안 그러면 이미 만들어진 DB의 비밀번호와 안 맞아 접속이 깨질 수 있음).

**로그 확인하기 (문제가 생겼을 때 제일 먼저 볼 것)**
```powershell
kubectl get pods -n ai-platform
```
위 명령으로 파드 이름을 확인한 뒤:
```powershell
kubectl logs <파드이름> -n ai-platform --tail=100
```
`--tail=100`은 "최근 100줄만 보기"라는 뜻입니다. 에러가 계속 반복되면 파드 이름 뒤에 `--previous`를 붙이면 "재시작 직전"의 로그도 볼 수 있습니다.

**실시간으로 상태 변화 지켜보기 (파드가 뜨는 과정을 계속 지켜볼 때)**
```powershell
kubectl get pods -n ai-platform -w
```
`-w`(watch)는 화면이 계속 갱신되며 멈추지 않습니다. **`Ctrl + C`를 누르면 종료**되고 평소 명령창으로 돌아옵니다.

**웹 화면 접속 주소**
- 메인 서비스: `http://localhost:30080`
- Grafana(관측성 대시보드): `http://localhost:30300`

### 20.6 이렇게 했을 때의 장점

1. **한 번의 명령으로 전체 스택 배포**: `docker compose up`을 컴포넌트 개수만큼 반복하거나 순서를 신경 쓸 필요 없이, `helm install` 한 줄로 7개 컴포넌트가 올바른 순서(예: DB가 뜬 다음 Spring Boot가 뜨도록)로 배포됩니다.
2. **장애 시 자동 복구**: 쿠버네티스는 파드가 죽으면 자동으로 다시 띄웁니다(`RESTARTS` 횟수로 확인 가능). Docker Compose 단독으로는 이 복구가 기본 제공되지 않습니다.
3. **설정과 코드의 분리**: DB 비밀번호, API 키 같은 민감 정보를 코드나 이미지에 넣지 않고 `Secret`이라는 쿠버네티스 리소스로 분리 관리합니다. 회사 배포 환경에서 요구하는 기본적인 보안 관행입니다.
4. **환경별로 값만 바꿔서 재사용**: 로컬용(`values.yaml`)과 클라우드용(`values-gcp.yaml`) 설정 파일을 나눠서, 같은 차트를 그대로 GCP 등 다른 환경에도 재사용할 수 있게 설계했습니다.
5. **실무 채용 요건과의 직접적인 연결**: 채용 공고에 자주 나오는 "Docker/Kubernetes 기반 배포 경험"을 로컬에서 GPU 제약까지 고려하며 실제로 겪어본 경험으로 답할 수 있습니다.

### 20.7 주의사항

- **GPU 서비스는 반드시 먼저 켜져 있어야 함**: Compose로 Ollama 등을 먼저 켜지 않으면 Spring Boot 파드가 계속 재시작 루프에 빠집니다. 항상 "Compose GPU 스택 → Helm 배포" 순서를 지켜야 합니다.
- **`host.docker.internal`이 항상 자동으로 되는 건 아님**: 쿠버네티스 클러스터를 만드는 방식(Docker Desktop의 kind vs kubeadm 등)에 따라 파드 안에서 이 주소가 인식 안 될 수 있습니다. 안 되면 파드 안에 들어가 확인:
  ```powershell
  kubectl exec -it <springboot파드이름> -n ai-platform -- sh
  ```
  들어간 뒤 `wget -qO- http://host.docker.internal:11434`로 응답이 오는지 확인.
- **환경변수 이름은 프레임워크 버전에 따라 다름**: Spring Boot 4.x + Spring Data Redis는 `spring.redis.*`가 아니라 `spring.data.redis.*`를 씁니다. Helm의 ConfigMap에 넣는 환경변수 이름이 실제 `application.yml`의 프로퍼티 이름과 정확히 일치해야 하며, 옛날 이름을 쓰면 조용히 무시되고 기본값(`localhost`)으로 접속을 시도합니다. (20.9절 트러블슈팅 참고)
- **비밀번호는 절대 GitHub에 커밋하지 말 것**: `values.yaml`의 `secrets.postgresPassword`, `monitoring.grafana.adminPassword` 기본값은 예시(`changeme`, `admin`)일 뿐입니다. 실제 값은 `--set` 명령줄 인자로만 주입하고, 저장소에는 기본값 그대로 유지해야 합니다.
- **이미지 태그가 같으면 새로 빌드해도 자동 반영 안 될 수 있음**: `docker build`로 이미지를 새로 만들어도 태그(`latest`)가 같으면 쿠버네티스가 "이미 있는 이미지"로 착각할 수 있습니다. `kubectl rollout restart deployment springboot -n ai-platform`으로 강제로 새 파드를 만들어야 확실합니다.
- **K8s의 벡터스토어는 로컬 Compose와 별개의 빈 데이터베이스**: Helm으로 새로 띄운 OpenSearch/pgvector는 처음엔 문서가 하나도 없는 빈 상태입니다. 로컬 Compose에서 이미 업로드해뒀던 문서와는 별개이므로, RAG 질의를 제대로 테스트하려면 문서를 다시 업로드해야 합니다.

### 20.8 Helm 차트 구조

```
D:\MyAiProject\helm\local-ai-platform\
├── Chart.yaml              # 차트 메타정보
├── values.yaml              # 기본 설정값 (로컬용)
├── values-gcp.yaml           # GCP 배포용 오버라이드 예시
└── templates\
    ├── namespace.yaml         # ai-platform 네임스페이스 생성
    ├── config-and-secrets.yaml # 환경변수(ConfigMap) + 민감정보(Secret)
    ├── springboot.yaml         # Spring Boot Deployment + Service
    ├── opensearch.yaml         # OpenSearch StatefulSet + Service
    ├── postgres.yaml           # PostgreSQL(pgvector) StatefulSet + Service
    ├── redis.yaml              # Redis Deployment + Service
    ├── kafka.yaml              # Kafka(KRaft) StatefulSet + Service
    ├── prometheus.yaml         # Prometheus Deployment + Service
    ├── grafana.yaml            # Grafana Deployment + Service
    └── ingress.yaml            # (선택) 외부 진입점
```

### 20.9 이번 배포 과정에서 실제로 겪은 문제 (트러블슈팅)

#### 20.9.1 Redis/OpenSearch 연결 실패 — Spring Boot 프로퍼티 이름과 환경변수 이름 불일치

**증상**: Helm으로 배포했는데 Spring Boot 로그에 계속 `Unable to connect to localhost/<unresolved>:6379` (Redis) 에러가 반복됨. Redis 파드 자체는 `Running` 상태로 정상이었음.

**원인**: Helm의 ConfigMap에 `SPRING_REDIS_HOST`라는 이름으로 환경변수를 넣었는데, Spring Boot 4.x부터는 Spring Data Redis의 프로퍼티가 `spring.redis.*`에서 `spring.data.redis.*`로 바뀌어서 대응하는 환경변수 이름도 `SPRING_DATA_REDIS_HOST`여야 했음. 이름이 틀리면 에러 없이 조용히 무시되고 기본값(`localhost`)을 쓰기 때문에 원인을 바로 알기 어려웠음. 같은 이유로 `OPENSEARCH_HOST`/`OPENSEARCH_PORT`도 실제 `application.yml`의 `spring.ai.vectorstore.opensearch.uris` 프로퍼티와 이름이 안 맞아 무시되고 있었음.

**해결**: `application.yml`을 직접 열어 실제 프로퍼티 이름을 확인한 뒤, ConfigMap의 키 이름을 `SPRING_DATA_REDIS_HOST`, `SPRING_AI_VECTORSTORE_OPENSEARCH_URIS`, `OPENSEARCH_BASE_URL`로 정정.

**교훈**: 쿠버네티스 환경변수로 Spring Boot 설정을 오버라이드할 때는, 코드/설정 파일에 실제로 쓰인 프로퍼티 이름을 반드시 먼저 확인해야 함. "그럴듯한 이름"을 추측해서 넣으면 에러도 없이 조용히 실패한다.

#### 20.9.2 Ollama 스트리밍 호출이 K8s에서만 실패 — 하드코딩된 `localhost` 주소

**증상**: 일반 채팅(`/api/rag/ask`)은 되는데, 스트리밍 응답(`/api/rag/ask/stream`)만 `ClosedChannelException`으로 항상 실패. 파드 안에서 `wget http://host.docker.internal:11434`로 직접 테스트하면 Ollama 응답은 정상적으로 옴 — 즉 네트워크 자체는 문제가 없었음.

**원인**: `OllamaService.java`의 스트리밍 전용 메서드(`generateStream`)가 Spring AI의 설정(`spring.ai.ollama.base-url`)을 전혀 쓰지 않고, 소스 코드에 `"http://localhost:11434/api/generate"`가 **직접 하드코딩**되어 있었음. 일반 채팅은 Spring AI의 `ChatModel`을 통해 호출해서 설정을 제대로 따랐지만, 스트리밍은 실시간 음성 기능을 위해 별도로 직접 구현한 코드라 이 설정 체계를 우회하고 있었음. 로컬 IntelliJ 실행(로컬 자체가 `localhost`이므로 우연히 늘 맞음) 환경에서는 절대 드러나지 않던 버그였음.

**해결**: `OllamaService`의 생성자에 `@Value("${spring.ai.ollama.base-url:http://localhost:11434}")`를 추가해 URL을 주입받도록 수정. 이제 로컬 실행/Docker Compose/쿠버네티스 어떤 환경에서도 설정값 하나로 올바른 주소를 쓰게 됨.

**교훈**: 프레임워크(Spring AI)가 제공하는 설정 경로를 안 타고 별도로 구현한 코드는, 로컬 개발 환경에서는 우연히 잘 동작하다가 배포 환경이 바뀌는 순간(로컬 → 컨테이너 → 쿠버네티스)에만 드러나는 잠재 버그가 되기 쉽다. "왜 이 컴포넌트만 설정을 안 따르지?"라는 질문을 스스로 던지는 습관이 필요.

## 21. CI/CD (GitHub Actions)

### 21.1 이걸 왜 했는가

20장까지 작업으로 "여러 컴포넌트를 하나의 명령으로 배포"하는 건 됐지만, 그 배포용 이미지를 **누가, 언제, 어떻게 만드는지**는 여전히 사람 손에 의존했습니다(직접 `docker build` 실행). 사람이 매번 로컬에서 빌드하면 다음 문제가 생길 수 있습니다.

- 커밋은 했는데 빌드를 깜빡해서, 실제로 동작 안 하는 코드가 저장소에 남아있는 걸 뒤늦게 발견
- "내 컴퓨터에서는 빌드된다"만 확인했지, 완전히 깨끗한 환경(다른 PC, 팀원 PC)에서도 빌드되는지는 확인 안 됨
- 테스트를 매번 손으로 돌려야 해서, 바쁘면 건너뛰게 됨 (실제로 이 프로젝트도 지금까지 `mvn test`를 한 번도 습관적으로 돌린 적이 없었고, Dockerfile도 `-DskipTests`로 테스트를 건너뛰고 있었습니다)

**CI(Continuous Integration, 지속적 통합)**는 "코드가 저장소에 올라올 때마다 자동으로 빌드하고 테스트해서, 문제를 최대한 빨리 발견하는 것"입니다. **CD(Continuous Delivery/Deployment, 지속적 배포)**는 "테스트를 통과한 결과물을 자동으로 배포 가능한 상태로 만드는 것"입니다. 이번 작업은 이 둘을 **GitHub Actions**로 구현했습니다.

### 21.2 무엇을 만들었는가

`.github/workflows/ci.yml` 파일 하나로, GitHub 저장소에 코드가 올라올 때마다 자동으로 아래 순서가 실행됩니다.

```
코드 push/PR
   │
   ▼
[Job 1] build-and-test  (push, PR 둘 다 실행)
   ├─ 소스 체크아웃
   ├─ JDK 21 설치
   ├─ mvn clean verify (컴파일 + 테스트)
   └─ 테스트 결과를 아티팩트로 저장 (실패해도 항상 저장)
   │
   ▼ (성공해야만 다음 단계로)
[Job 2] docker-publish  (main 브랜치에 실제 push된 경우만 실행)
   ├─ ghcr.io(GitHub Container Registry) 로그인
   ├─ Docker 이미지 빌드
   └─ ghcr.io에 자동 푸시 (latest 태그 + 커밋 해시 태그)
```

**왜 두 개의 Job으로 나눴는가**: PR(아직 main에 병합 안 된 코드)일 때는 "빌드/테스트가 되는지"만 확인하면 충분하고, 아직 검증 안 된 코드로 이미지를 만들어 배포할 필요는 없습니다. 반면 main에 실제로 merge(push)된 코드는 검증이 끝난 것이니 바로 배포 이미지를 만들어도 됩니다. 이 구분을 `if: github.event_name == 'push' && github.ref == 'refs/heads/main'` 조건으로 명시했습니다.

### 21.3 실행/확인 방법

**로컬에서 미리 확인하기 (GitHub에 올리기 전 권장)**
```powershell
cd D:\MyAiProject
mvn test
```
`BUILD SUCCESS`가 나와야 GitHub Actions에서도 통과할 가능성이 높습니다. 로컬에서 실패하는 걸 미리 잡으면, GitHub까지 올라가서 실패를 확인하는 시간 낭비를 줄일 수 있습니다.

**GitHub에 반영하기**
```powershell
git add .
git commit -m "커밋 메시지"
git push
```

**실행 결과 확인하기**
GitHub 저장소 페이지 → 상단 **Actions 탭** → 방금 push로 트리거된 워크플로우 클릭. 초록색 체크(✅)면 성공, 빨간색(❌)이면 실패입니다. 실패한 경우 클릭해서 들어가면 어느 단계(Job)에서 어떤 로그로 실패했는지 그대로 보여줍니다.

**배포된 이미지 확인하기**
저장소 메인 페이지(Code 탭) → 오른쪽 사이드바의 **Packages** 섹션 → `local-ai-platform-springboot` 클릭하면 푸시된 태그 목록과 `docker pull` 명령어까지 보여줍니다.

**워크플로우를 실행하지 않고 코드만 올리고 싶을 때**: 없습니다 — `main` 브랜치로 push하면 항상 자동으로 실행됩니다. 굳이 막고 싶다면 커밋 메시지에 `[skip ci]`를 포함하면 GitHub Actions가 해당 push는 건너뜁니다(이 프로젝트에는 아직 별도로 설정하지 않았습니다).

### 21.4 이렇게 했을 때의 장점

1. **사람이 실수로 빌드/배포를 빼먹는 일이 사라짐**: push만 하면 나머지는 자동입니다.
2. **깨끗한 환경에서의 빌드 검증**: GitHub Actions 러너는 매번 완전히 새로운 가상머신이라, "내 컴퓨터에만 있는 설정" 때문에 우연히 빌드되던 문제를 걸러냅니다. 실제로 이번에 이 방식 덕분에 20.9절과는 별개로 **원래부터 있던 깨진 기본 테스트**를 발견했습니다(21.6.1절 참고).
3. **테스트가 실제로 강제됨**: 지금까지 습관적으로 건너뛰던 테스트가, 이제는 push할 때마다 무조건 실행됩니다.
4. **배포 이미지의 출처 추적 가능**: ghcr.io에 올라간 이미지마다 커밋 해시 태그가 붙어서, "이 이미지가 정확히 어느 커밋에서 만들어졌는지" 항상 확인할 수 있습니다.
5. **별도 비용/설정 없이 시작 가능**: `secrets.GITHUB_TOKEN`은 GitHub가 저장소마다 자동으로 발급해주는 토큰이라, ghcr.io에 푸시하기 위해 별도로 계정을 만들거나 키를 발급받을 필요가 없습니다.

### 21.5 주의사항

- **비밀 정보(API 키, 비밀번호)는 저장소에 절대 커밋되면 안 됨**: `application.yml` 같은 설정 파일에 실제 키가 들어있다면 반드시 `.gitignore`에 등록하고, 다른 사람(또는 미래의 본인)이 필요한 설정값을 알 수 있도록 `application.yml.example`처럼 플레이스홀더만 남긴 템플릿 파일을 별도로 커밋해야 합니다. (21.6.2절에서 이번에 실제로 겪었던 상황을 정리했습니다.)
- **`.gitignore`는 정확한 문법으로 작성해야 함**: 줄바꿈이 깨지거나 오타가 있으면 무시 규칙이 조용히 안 먹습니다. `git ls-files <경로>` 명령으로 특정 파일이 실제로 추적되고 있는지 항상 재확인하는 습관이 안전합니다.
- **`git commit --amend`는 아직 push하지 않은 커밋에만 사용**: 이미 원격 저장소(GitHub)에 push된 커밋을 amend하면 커밋 히스토리가 어긋나서 협업 중인 다른 사람에게 문제가 생길 수 있습니다. 이번처럼 "커밋은 했지만 아직 push 전"인 상태에서만 안전하게 쓸 수 있습니다.
- **`docker-publish` Job은 PR에서는 실행되지 않음**: 의도된 동작입니다. 외부 기여자의 PR에서 검증 안 된 이미지가 배포되는 걸 막기 위한 안전장치이니, PR에서 Packages에 새 이미지가 안 보여도 정상입니다.
- **Mockito의 inline mock maker 경고**: 테스트 실행 시 `Mockito is currently self-attaching...` 경고가 뜨는데, 이는 최신 JDK에서 Mockito가 동적으로 에이전트를 로드하는 방식에 대한 사전 경고일 뿐 테스트 실패 원인은 아닙니다. 향후 JDK 버전이 올라가면 Mockito를 빌드에 명시적 에이전트로 등록해야 할 수 있습니다.

### 21.6 이번에 실제로 겪은 문제 (트러블슈팅)

#### 21.6.1 깨진 기본 테스트가 처음으로 CI에서 발견됨

**증상**: 새로 작성한 `OllamaServiceTest`는 3개 모두 통과했는데, `mvn test` 전체 실행은 `BUILD FAILURE`로 끝남. 원인은 `com.example.llm.AiApplicationTests`라는, 프로젝트 생성 당시 자동으로 만들어진 기본 테스트에서 발생.

**원인**: 이 테스트는 `@SpringBootTest`로 전체 애플리케이션 컨텍스트를 띄우려고 시도하는데, 정작 패키지가 `com.example.llm`으로 되어 있어 실제 메인 클래스(`com.ai.llm.AiApplication`)를 찾지 못해 `IllegalStateException`으로 실패. `mvn test`를 지금까지 습관적으로 돌린 적이 없어서(Dockerfile도 `-DskipTests`) 이 문제가 계속 숨어있었음.

**해결**: 이 테스트는 패키지를 고쳐도 전체 컨텍스트(DB, Kafka, Redis, OpenSearch 등 실제 인프라)가 필요해서 CI 환경에서 살리기 어렵다고 판단, 삭제하는 쪽으로 정리.

**교훈**: "빌드가 된다"와 "테스트가 통과한다"는 별개다. CI를 붙이는 과정 자체가 지금까지 아무도 실행하지 않았던 코드 경로(테스트)를 처음으로 실행시켜, 오래 숨어있던 문제를 찾아내는 계기가 됐다.

#### 21.6.2 커밋에 API 키가 평문으로 포함될 뻔한 사건

**증상**: `git commit` 이후 커밋 내역에 `src/main/resources/application.yml`이 포함되어 있었는데, 이 파일 안에 Tavily API 키가 평문으로 하드코딩되어 있었음. 이 저장소는 **Public**이라 그대로 push됐다면 키가 즉시 외부에 노출될 뻔했음.

**원인**: 처음부터 `.gitignore`에 `application.yml`이 등록되어 있지 않아서, 파일이 계속 Git 추적 대상이었음.

**해결**: 다행히 `git push`가 (다른 이유로) 먼저 실패해 원격에는 올라가지 않은 상태였음. 이 틈에 `git rm --cached`로 추적에서 제거하고, `.gitignore`에 등록하고, 실제 값 대신 플레이스홀더가 들어간 `application.yml.example` 템플릿을 만들어 그것만 커밋한 뒤, `git commit --amend`로 문제가 된 커밋 자체를 수정해서 히스토리에 키가 아예 남지 않도록 정리.

(참고로 `.gitignore` 수정 과정에서 PowerShell의 이스케이프 문자(`` `n ``)가 의도대로 해석되지 않아, `application.yml` 앞에 `n`이 붙어 무시 규칙이 처음엔 조용히 실패하는 일도 있었음. `git ls-files <경로>`로 실제 추적 여부를 다시 확인하는 과정에서 발견하고 수정.)

**교훈**: `git commit` 전에는 `git status`/`git diff --cached`로 **이번에 실제로 뭐가 커밋되는지 항상 확인**하는 습관이 필요하다. 특히 새로 생성된 설정 파일(`application.yml` 등)은 `.gitignore` 등록 여부를 프로젝트 초기에 미리 챙겨야, 이런 상황을 애초에 피할 수 있다. `.gitignore`처럼 겉보기엔 사소한 텍스트 파일도, 문법이 깨지면 보안 사고로 이어질 수 있다는 걸 보여준 사례.

---

## 참고: 프로젝트 구조 요약

```
D:\MyAiProject\
├── src\main\java\com\ai\llm\
│   ├── rag\            # RagService, PgVectorRagService, RagController(+캐시)
│   ├── pgvector\        # PgVectorService, PgVectorIngestService, OCR 서비스
│   ├── opensearch\      # OpenSearchService, OpenSearchIndexInitializer
│   ├── ollama\          # OllamaService(생성/스트리밍), 프롬프트 번역
│   ├── voice\           # VoiceService, VoiceController, WebSocketConfig, VoiceWebSocketHandler
│   ├── websearch\       # WebSearchService (Tavily, 캐시 적용)
│   ├── kafka\           # DocumentIngestionEvent/Producer/Consumer, KafkaProducerConfig, IngestionStatusService
│   ├── cache\           # CacheService (Redis)
│   ├── rerank\          # RerankService (리랭크 마이크로서비스 REST 클라이언트)
│   └── mcp\             # RagMcpTools (@McpTool, RAG 파이프라인을 MCP 도구로 노출)
├── src\main\resources\
│   ├── application.yml
│   └── static\index.html
├── docker-compose.yml           # 로컬용 (OpenSearch+pgvector+Ollama+Kafka+Redis+Prometheus+Grafana, GPU)
├── docker-compose.server.yml    # GCP 배포용 (경량, CPU)
├── prometheus.yml               # Prometheus 스크레이프 설정
├── grafana-provisioning\
│   ├── datasources\datasource.yml   # Prometheus 데이터소스 자동 등록
│   └── dashboards\dashboard.yml     # 대시보드 프로비저닝 설정
├── grafana-dashboards\
│   └── rag-observability.json       # 대시보드 정의 (요청 지연시간/캐시 히트율/Kafka lag 등 6개 패널)
├── rag-eval-env\        # RAGAS 평가용 venv (answer_relevancy 평가 완료)
├── rerank-env\          # 리랭크 서비스용 venv (CPU 모드)
├── rerank_service.py    # FastAPI 리랭크 마이크로서비스 (BAAI/bge-reranker-v2-m3, 포트 8002)
├── voice-pipeline\       # STT/TTS Python 서버
├── start-all.ps1 / stop-all.ps1   # -Sd / -Voice / -All 스위치로 선택적 기동
└── start.sh / stop.sh    # Git Bash용 (--sd / --voice / --all, 반드시 진짜 Git Bash 터미널에서 실행)

(WSL2 Ubuntu 내부, Windows와 별도)
~/qlora-project\               # QLoRA 파인튜닝 작업 폴더 (conda env: qlora, Python 3.11)
├── train_qlora.py
├── qwen3-4b-qlora-alpaca\     # LoRA 어댑터 (시험/본 학습 결과)
└── qwen3-4b-qlora-demo_gguf\  # GGUF 변환 결과 + Ollama Modelfile (Windows로 복사 후 등록)

D:\stable-diffusion-webui-docker\   # 별도 저장소
```