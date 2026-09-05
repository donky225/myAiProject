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
15. [전체 한 번에 실행하기](#15-전체-한-번에-실행하기)
16. [클라우드 배포 (GCP)](#16-클라우드-배포-gcp)
17. [자주 겪는 오류와 해결법 총정리](#17-자주-겪는-오류와-해결법-총정리)

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

## 15. 전체 한 번에 실행하기

`start-all.ps1`, `stop-all.ps1`을 `D:\MyAiProject`에 저장:
```powershell
cd D:\MyAiProject
.\start-all.ps1
```
Docker 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis), Stable Diffusion, 음성 서버를 새 창에서 기동. 이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**.

종료: `.\stop-all.ps1`

---

## 16. 클라우드 배포 (GCP)

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

## 17. 자주 겪는 오류와 해결법 총정리

### 17.1 `Connection refused` (인프라 포트)
컨테이너 미기동 또는 볼륨 충돌. `docker ps` 확인 후 개별 기동.

### 17.2 PowerShell `curl` 이상 동작
`curl.exe`로 명시 호출, 한글/공백은 `-G --data-urlencode` 사용.

### 17.3 OpenSearch `Field 'embedding' is not knn_vector type`
인덱스 삭제 후 매핑 없이 재생성됨. 매핑을 먼저 지정해 재생성 (README 참고).

### 17.4 pgvector 검색 결과가 항상 비어있음
OpenSearch 임계값(0.55)을 그대로 적용하면 안 됨. pgvector 전용 낮은 임계값(0.25) 적용.

### 17.5 `pkg_resources` / `unidic download` / `eunjeon` 빌드 오류
각각 `setuptools<81` 고정, `unidic-lite`로 우회, Visual C++ Build Tools 설치로 해결 (7장 참고).

### 17.6 `faster-whisper`의 `cublas64_12.dll` 오류
CUDA 툴킷 미설치. `device="cpu"`로 설정 (small 모델은 CPU도 충분).

### 17.7 GCP 서버에서 앱이 기동조차 안 됨 (`OpenSearchIndexInitializer`)
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

### 17.8 `bitnami/kafka:3.7` 이미지를 찾을 수 없음
Bitnami 무료 태그 정책 변경으로 구버전 삭제. **공식 `apache/kafka:latest`로 전환**.

### 17.9 Kafka `KafkaTemplate` 빈을 찾을 수 없음
Boot 자동 생성 템플릿이 와일드카드(`<?,?>`) 타입이라 구체 제네릭 타입(`KafkaTemplate<String, DocumentIngestionEvent>`)과 불일치. `ProducerFactory`/`KafkaTemplate`을 정확한 타입으로 직접 빈 등록.

### 17.10 Kafka Consumer Group이 생성되지 않음 (`GroupIdNotFoundException`)
**원인**: `@KafkaListener`는 인식되지만 리스너 컨테이너 자체가 기동 안 됨 (콘솔에 Producer 로그만 있고 Consumer 로그가 전혀 없는 게 증거).
**해결**: `@Configuration` 클래스에 **`@EnableKafka`** 추가. 이게 없으면 어노테이션만 있고 실제로는 아무 것도 동작 안 함.

### 17.11 Oracle Cloud 가입 반복 실패
VPN 끄기, 시크릿 모드, 전화번호/카드 정보 재확인. 반복 실패 시 GCP 무료 체험으로 전환 추천.

### 17.12 GCP 방화벽 메뉴를 못 찾음
Compute Engine 메뉴 안에 없고 **VPC 네트워크 → 방화벽**(별도 최상위 메뉴)에 있습니다. 검색이 안 되면 URL로 직접 이동: `console.cloud.google.com/networking/firewalls/list`

### 17.13 실시간 대화가 계속 영어로 응답
프롬프트에 "한국어로만 답하라"는 지시문 명시적으로 포함.

### 17.14 마이크 발화 중 요청이 여러 번 겹침
발화 종료 감지 즉시 오디오 캡처만 중단(소켓 유지) → 답변 완료 후 완전 종료하는 "한 번에 한 질문" 흐름으로 변경.

### 17.15 `index.html` 수정이 재시작 없이는 반영 안 됨
`spring-boot-devtools` 추가로 라이브 리로드 활성화 (5장 참고).

### 17.16 RAGAS 평가가 항목마다 정확히 timeout(600초)에 걸려 전부 실패
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

### 17.17 `evaluate_ragas.py` 실행 시 `KeyError: 'faithfulness'`
**원인**: `evaluate()` 호출의 `metrics=[]`에는 `AnswerRelevancy()`만 넣어놓고, 마지막 summary 집계 코드에는 `["faithfulness", "answer_relevancy"]` 두 컬럼을 그대로 참조해 컬럼 불일치 발생.
**해결**: 둘 중 하나로 통일. `faithfulness`를 실제로 채점하지 않는다면 summary 쪽에서도 제거:
```python
summary = scored_df.groupby("store")[["answer_relevancy"]].mean()
```

### 17.18 venv 미활성화 상태로 실행 시 엉뚱한 `ModuleNotFoundError`
**증상**: `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'` 등, 분명 설치했는데 없다는 에러.
**원인**: 프롬프트에 `(rag-eval-env)`가 안 붙어 있는 상태 — 즉 venv가 활성화되지 않아 시스템 전역 Python의 site-packages(버전 조합이 안 맞는)를 참조.
**해결**: `.\rag-eval-env\Scripts\Activate.ps1`로 venv부터 활성화 후 재실행. 에러 스택의 파일 경로가 `...\rag-eval-env\...`가 아니라 `...\AppData\Local\Programs\Python\...`이면 venv 미활성화가 확실합니다.

### 17.19 QLoRA 학습 중 Triton 커널 컴파일 실패 (`RuntimeError: Failed to find C compiler`)
**증상**: `train_qlora.py` 실행 중 `triton/runtime/build.py`에서 `Failed to find C compiler. Please specify via CC environment variable` 에러로 학습이 0%에서 멈춤.
**원인**: WSL2 Ubuntu에 gcc 등 C 컴파일러가 설치되어 있지 않아, Triton이 GPU 커널(RMSNorm 등)을 런타임에 컴파일하지 못함.
**해결**:
```bash
sudo apt update
sudo apt install build-essential -y
gcc --version  # 확인
```

### 17.20 최신 Ubuntu에서 `deadsnakes` PPA로 Python 3.11 설치 실패
**증상**: `add-apt-repository ppa:deadsnakes/ppa` 실행 후 `apt update`를 해도 저장소가 추가된 흔적이 없고, `python3.11`을 찾을 수 없음.
**원인**: WSL Ubuntu 배포판이 최신 버전(예: `resolute`)일 경우, deadsnakes PPA가 아직 해당 코드네임용 패키지를 제공하지 않을 수 있음.
**해결**: apt/PPA로 씨름하지 말고 Miniconda로 격리된 Python 3.11 환경 구성 (12.2 참고).

### 17.21 Miniconda 설치가 조용히 실패 (`~/miniconda3` 폴더 자체가 생성 안 됨)
**증상**: 설치 스크립트가 끝난 것처럼 보였는데 `conda: command not found`, `ls ~/miniconda3` 결과 `No such file or directory`.
**원인**: 대화형(라이선스 동의, 경로 확인 등 프롬프트 입력) 설치 도중 다른 명령이 섞여 들어가면서 설치가 중간에 끊긴 것으로 추정.
**해결**: 배치 모드(`-b -p`)로 모든 대화형 프롬프트를 건너뛰고 재설치. 설치 명령 실행 후에는 완전히 끝날 때까지 다른 명령을 입력하지 않아야 함:
```bash
bash ~/Miniconda3-latest-Linux-x86_64.sh -b -p $HOME/miniconda3
```

### 17.22 `conda create` 시 `CondaToSNonInteractiveError`
**증상**: `conda create -n qlora python=3.11 -y` 실행 시 `Terms of Service have not been accepted` 에러.
**원인**: 최근 Anaconda 정책 변경으로 `pkgs/main`, `pkgs/r` 채널의 이용약관 동의가 선행되어야 함.
**해결**:
```bash
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/main
conda tos accept --override-channels --channel https://repo.anaconda.com/pkgs/r
```

### 17.23 여러 터미널(PowerShell/Git Bash/WSL) 혼동으로 명령이 엉뚱하게 실행됨
**증상**: 분명 WSL에서 작업 중인 줄 알았는데 `PS C:\...>`나 `MINGW64` 프롬프트에서 명령이 실행되어 엉뚱한 Python/패키지 경로를 참조하거나, `sudo` 인증 실패, 명령이 이전 입력과 겹쳐 붙는 등의 증상이 발생.
**원인**: WSL Ubuntu, PowerShell, Git Bash가 서로 다른 파일시스템·패키지 환경을 가지는데 터미널 창을 오가며 작업하다 보니 어느 셸에 있는지 놓침.
**해결**: 프롬프트 형태로 항상 구분. WSL Ubuntu는 `(qlora) choi@DESKTOP-...:~/qlora-project$`, PowerShell은 `PS C:\...>`, Git Bash는 `MINGW64` 표시. 명령이 이상하게 합쳐져 실행되면(`unslothpip` 등) `Ctrl+U`로 줄을 비우고 붙여넣기는 `Ctrl+Shift+V` 사용.

### 17.24 GGUF 모델이 추론 시 같은 문구를 무한 반복
**증상**: `ollama run`으로 파인튜닝 모델을 실행하면 "적절히 지시하는 작업을 적절히..." 처럼 같은 구절이 끝없이 반복됨.
**원인**: 두 가지가 겹침 — ① Unsloth가 자동 생성한 Modelfile의 `PARAMETER repeat_penalty`가 `1`(반복 억제 없음)로 설정됨. ② 학습 시 사용한 Alpaca 원본 포맷(`### 지시사항: ... ### 응답:`)이 Ollama Modelfile의 실제 서빙 템플릿(Qwen3 ChatML, `<|im_start|>...<|im_end|>`)과 달라, 모델이 언제 답변을 멈춰야 하는지에 대한 신호가 학습 때와 추론 때 어긋남.
**해결**: `repeat_penalty`를 `1.15` 정도로 조정하고, 학습 데이터 포맷을 `tokenizer.apply_chat_template()`로 Qwen3 ChatML과 일치시켜 재학습 (12.4 참고).

### 17.25 Ollama Modelfile에 `PARAMETER think false` 추가 시 `Error: unknown parameter 'think'`
**증상**: Modelfile에 thinking 비활성화를 고정하려고 `PARAMETER think false`를 추가하면 `ollama create`가 에러를 냄.
**원인**: thinking on/off는 Modelfile의 `PARAMETER`로 지원되는 옵션이 아님.
**해결**: 해당 줄 제거. 대신 실행 시점에 `ollama run 모델명 --think=false` 또는 `--hidethinking` 플래그로 제어.

### 17.26 PowerShell에서 메모장으로 만든 Modelfile을 `ollama create`가 못 찾음
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

### 17.27 SYSTEM 프롬프트로 한국어를 강제해도 `<think>` 블록만 영어로 나옴
**증상**: `SYSTEM 항상 한국어로만 답변하세요`를 지정해도 최종 답변은 한국어인데 `<think>...</think>` 내부 추론 과정은 전부 영어로 생성됨.
**원인**: Qwen3 계열 모델은 SYSTEM 프롬프트가 최종 출력 언어에는 적용되지만, 내부 thinking 채널까지는 강제하지 못하는 것으로 관찰됨.
**해결**: thinking 자체를 끄거나(`--think=false`) 화면 노출만 차단(`--hidethinking`). 정확한 비교 실험이 필요하면 두 방식 중 하나로 통일해 조건을 맞출 것.

### 17.28 리랭커(`sentence-transformers CrossEncoder`)가 `AssertionError: Torch not compiled with CUDA enabled`
**증상**: `rerank_service.py` 실행 시 `CrossEncoder(..., device="cuda")` 초기화 단계에서 CUDA 미지원 에러.
**원인**: `pip install torch`를 `--index-url` 없이 실행하면 Windows에서 기본적으로 CPU 전용 빌드가 설치됨.
**해결**: 리랭커는 가벼운 모델(1.1GB)이라 `device="cpu"`로 전환해도 속도 저하가 크지 않음. GPU가 꼭 필요하면 `pip uninstall torch -y` 후 `pip install torch --index-url https://download.pytorch.org/whl/cu121`로 재설치.

### 17.29 리랭킹 적용 후 pgvector 스토어의 `answer_relevancy`가 오히려 하락
**증상**: RAGAS로 리랭킹 켬/끔 비교 시, OpenSearch는 개선(0.611→0.676)됐지만 pgvector는 악화(0.536→0.484)됨.
**원인 후보**: ① 두 스토어에 동일한 리랭크 임계값(0.5)을 캘리브레이션 없이 적용 ② 청킹 전략 차이(pgvector는 문장 단위, OpenSearch는 별도 방식) ③ 표본 수(스토어당 9~10건)가 작아 통계적 노이즈일 가능성.
**대응**: 원인을 하나로 단정하지 않고 정직하게 혼재 결과로 문서화. 스토어별 임계값 개별 캘리브레이션과 표본 확대가 후속 과제로 남음. (참고: 리랭커에 넘기는 텍스트 포맷—제목 포함 여부 등—을 스토어 간 통일하는 것도 공정한 비교의 전제 조건.)

### 17.30 RAG 텍스트 API 응답이 질문과 무관하게 영어로 나옴
**증상**: 한국어로 질문해도 `/api/rag/ask` 응답이 영어로 나오는 경우 발생 (리랭킹과 무관).
**원인**: 텍스트 RAG 프롬프트(`RagService`, `PgVectorRagService`)에는애초에 언어 지시가 없었음 — "한국어로만 답하라"는 지시문이 실시간 음성 파이프라인 프롬프트에만 있고 텍스트 API 프롬프트엔 누락돼 있었음.
**해결**: 두 서비스의 모든 프롬프트 템플릿(simple/strict, 컨텍스트 있음/없음 분기 포함) 맨 앞에 "반드시 한국어로만 답변하세요. 영어를 사용하지 마세요." 명시.

### 17.31 MCP Inspector 실행 시 `npx: 용어가 인식되지 않습니다`
**증상**: `npx @modelcontextprotocol/inspector` 실행 시 명령을 찾을 수 없다는 에러. `winget install OpenJS.NodeJS.LTS`로 설치 완료 메시지를 봤는데도 계속 같은 에러.
**원인**: Node.js를 설치해도, **이미 열려있던 PowerShell 세션은 갱신된 PATH를 읽지 못함**.
**해결**: 설치 후 PowerShell 창을 완전히 닫고 새로 열어야 함. 그래도 안 되면 재부팅하거나 `$env:Path -split ';' | Select-String -Pattern "nodejs"`로 PATH 등록 여부를 직접 확인.

### 17.32 `start.sh`로 Stable Diffusion/음성 서버 창이 하나도 안 뜸
**증상**: 기존에는 정상적으로 새 mintty 창들이 떴는데, 이번엔 아무 창도 안 뜨고 조용히 끝남.
**원인**: IntelliJ 내장 실행 버튼(▶)으로 셸 스크립트를 돌려서 발생. IntelliJ가 관리하는 제한된 프로세스 환경에서는 `start`(cmd 내장 명령)나 `mintty`(GUI 새 창 실행) 같은, 실제 터미널 세션에 의존하는 명령이 조용히 실패할 수 있음.
**해결**: IntelliJ의 스크립트 실행 버튼을 쓰지 말고, 탐색기에서 "Git Bash Here"나 시작 메뉴에서 Git Bash를 직접 열어 그 안에서 `./start.sh` 실행. IntelliJ는 Spring Boot 앱(`AiApplication`) 자체를 Run하는 용도로만 사용.

### 17.33 MCP 서버의 SSE 엔드포인트 경로를 몰라 Inspector 연결 실패
**증상**: MCP Inspector에서 어떤 URL로 연결해야 할지 알 수 없어 여러 경로(`/mcp`, `/mcp/sse` 등)를 시도.
**원인**: Spring AI MCP webmvc 스타터의 기본 SSE 경로에 대한 정보가 문서마다 표현이 달라 혼동.
**해결**: 브라우저로 직접 `http://localhost:8080/sse`를 열어 `event:endpoint` / `data:/mcp/message?sessionId=...` 형태의 SSE 스트림이 출력되는지로 실제 경로를 확인. (커스터마이징하지 않았다면 `/sse`가 기본값.)

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
├── docker-compose.yml           # 로컬용 (OpenSearch+pgvector+Ollama+Kafka+Redis, GPU)
├── docker-compose.server.yml    # GCP 배포용 (경량, CPU)
├── rag-eval-env\        # RAGAS 평가용 venv (answer_relevancy 평가 완료)
├── rerank-env\          # 리랭크 서비스용 venv (CPU 모드)
├── rerank_service.py    # FastAPI 리랭크 마이크로서비스 (BAAI/bge-reranker-v2-m3, 포트 8002)
├── voice-pipeline\       # STT/TTS Python 서버
├── start-all.ps1 / stop-all.ps1
└── start.sh / stop.sh    # Git Bash용 (반드시 진짜 Git Bash 터미널에서 실행 — IntelliJ 내장 실행 버튼 사용 금지)

(WSL2 Ubuntu 내부, Windows와 별도)
~/qlora-project\               # QLoRA 파인튜닝 작업 폴더 (conda env: qlora, Python 3.11)
├── train_qlora.py
├── qwen3-4b-qlora-alpaca\     # LoRA 어댑터 (시험/본 학습 결과)
└── qwen3-4b-qlora-demo_gguf\  # GGUF 변환 결과 + Ollama Modelfile (Windows로 복사 후 등록)

D:\stable-diffusion-webui-docker\   # 별도 저장소
```
