# 전체 환경 설치 가이드 (Windows)

이 문서는 개발 경험이 없어도 순서대로 명령어를 따라 입력하면 이 프로젝트의 전체 기능(RAG, 이미지 생성, 음성 인식/합성, 실시간 음성 대화)을 동일하게 재현할 수 있도록 작성되었습니다. 각 단계마다 **왜 필요한지**와 **실제로 겪었던 오류 및 해결법**을 함께 적었습니다.

기준 환경: Windows 11, NVIDIA GPU(VRAM 6GB 기준으로 설명하며, 다르면 일부 설정값 조정 필요)

---

## 목차

1. [사전 준비물 설치](#1-사전-준비물-설치)
2. [프로젝트 소스 받기](#2-프로젝트-소스-받기)
3. [메인 인프라 기동 (OpenSearch, pgvector, Ollama)](#3-메인-인프라-기동)
4. [Spring Boot 애플리케이션 실행](#4-spring-boot-애플리케이션-실행)
5. [개발 편의 설정 (라이브 리로드)](#5-개발-편의-설정-라이브-리로드)
6. [이미지 생성 환경 (Stable Diffusion)](#6-이미지-생성-환경-stable-diffusion)
7. [음성 인식/합성 환경 (STT/TTS)](#7-음성-인식합성-환경-stttts)
8. [실시간 음성 대화 (WebSocket)](#8-실시간-음성-대화-websocket)
9. [Python 평가 환경 (RAGAS, 후순위)](#9-python-평가-환경-ragas-후순위)
10. [전체 한 번에 실행하기](#10-전체-한-번에-실행하기)
11. [자주 겪는 오류와 해결법 총정리](#11-자주-겪는-오류와-해결법-총정리)

---

## 1. 사전 준비물 설치

### 1.1 Git
https://git-scm.com/download/win 에서 다운로드 후 기본 옵션으로 설치.

### 1.2 IntelliJ IDEA
https://www.jetbrains.com/idea/download 에서 Community 또는 Ultimate 설치. 설치 후 프로젝트를 열면 IDE 내에서 JDK 21(GraalVM)을 자동으로 받을 수 있습니다.

### 1.3 Docker Desktop
https://www.docker.com/products/docker-desktop 에서 설치 후 **재부팅**. 설치 시 "Use WSL 2 instead of Hyper-V" 옵션을 선택합니다. GPU를 쓰는 컨테이너(Ollama, Stable Diffusion)를 실행하려면 최신 NVIDIA 드라이버가 필요합니다.

확인:
```powershell
docker --version
docker compose version
nvidia-smi
```

### 1.4 Python 3.11
https://www.python.org/downloads/windows/ 에서 설치. **"Add python.exe to PATH" 체크박스 필수.**

### 1.5 FFmpeg
```powershell
winget install Gyan.FFmpeg
```
설치 후 **모든 PowerShell 창을 닫고 새로 열어야** PATH가 반영됩니다.

### 1.6 Visual C++ Build Tools
```powershell
winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```
용량이 크고 설치에 10~30분 걸릴 수 있습니다. UAC 팝업이 다른 창 뒤에 숨을 수 있으니 `Alt+Tab`으로 확인 후 승인.

### 1.7 PowerShell 실행 정책 완화
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

---

## 2. 프로젝트 소스 받기

```powershell
cd D:\
git clone <저장소 URL> MyAiProject
cd MyAiProject
```

> **주의**: 프로젝트 폴더 이름을 나중에 바꾸면 Docker 볼륨(특히 Ollama에 받아둔 모델)이 새로 생성되어 기존에 받은 모델이 전부 사라집니다. 폴더명은 처음에 정한 대로 유지하세요.

---

## 3. 메인 인프라 기동

```powershell
cd D:\MyAiProject
docker compose up -d opensearch postgres ollama ollama-init
```

`ollama-init` 컨테이너가 `qwen3:4b`, `qwen3-embedding:0.6b` 모델을 자동으로 받습니다.

확인:
```powershell
docker ps
docker exec -it local-ollama ollama list
```

---

## 4. Spring Boot 애플리케이션 실행

IntelliJ에서 `D:\MyAiProject` 폴더를 열고 Maven 의존성이 받아질 때까지 기다립니다.

### 4.1 "Maven에 IDE 빌드/실행 작업 위임" 체크 (중요)

```
설정(Ctrl+Alt+S) → 빌드, 실행, 배포 → 빌드 도구 → Maven → 러너
→ "Maven에 IDE 빌드/실행 작업 위임" 체크 → 적용
```

이 옵션이 꺼져 있으면 IntelliJ가 Maven 설정을 무시하고 자체 SDK로 빌드하다가 `release version 21 not supported` 같은 에러가 날 수 있습니다.

### 4.2 콘솔 한글 깨짐 방지 설정

`Run` → `Edit Configurations` → 실행 구성 선택 → VM 옵션에 추가:
```
-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

### 4.3 실행

`AiApplication`을 우클릭 → Run. 콘솔에 `Started AiApplication in ... seconds`가 뜨면 성공. `http://localhost:8080` 접속 확인.

---

## 5. 개발 편의 설정 (라이브 리로드)

`index.html` 등 정적 리소스를 고칠 때마다 앱을 재시작하지 않아도 되도록 설정합니다.

### 5.1 pom.xml에 devtools 추가
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### 5.2 application.yml에 캐시 비활성화
```yaml
spring:
  web:
    resources:
      cache:
        period: 0
      chain:
        cache: false
```

### 5.3 IntelliJ 자동 빌드 설정
```
설정 → 빌드, 실행, 배포 → 컴파일러 → "Build project automatically" 체크
Ctrl+Shift+A → Registry... → compiler.automake.allow.when.app.running 체크
```

설정 후 앱을 한 번만 재시작하면, 이후 `index.html`을 저장만 해도 자동 반영됩니다(브라우저에서 `Ctrl+F5`로 하드 리프레시 권장).

---

## 6. 이미지 생성 환경 (Stable Diffusion)

메인 프로젝트와 **별도 폴더**에 설치합니다.

```powershell
cd D:\
git clone https://github.com/AbdBarho/stable-diffusion-webui-docker.git
cd stable-diffusion-webui-docker
```

### CORS 허용 설정
`docker-compose.override.yml` 새로 생성:
```yaml
services:
  auto:
    environment:
      - CLI_ARGS=--api --cors-allow-origins=http://localhost:8080
```

### 모델 다운로드 및 기동
```powershell
docker compose --profile download up --build
docker compose --profile auto up --build
```
`http://localhost:7860` 접속 확인. 빌드 실패 시 11장 참고.

---

## 7. 음성 인식/합성 환경 (STT/TTS)

```powershell
mkdir D:\MyAiProject\voice-pipeline
cd D:\MyAiProject\voice-pipeline
python -m venv voice-pipeline-env
.\voice-pipeline-env\Scripts\Activate.ps1
```

### 7.1 필수 패키지
```powershell
pip install "setuptools<81"
pip install faster-whisper
pip install fastapi uvicorn python-multipart
pip install git+https://github.com/myshell-ai/MeloTTS.git
```

### 7.2 일본어 사전 데이터 (MeloTTS 내부 의존성)
```powershell
pip install unidic-lite
New-Item -ItemType Directory -Force -Path ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir"
Copy-Item -Path ".\voice-pipeline-env\Lib\site-packages\unidic_lite\dicdir\*" -Destination ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir\" -Recurse -Force
```

### 7.3 한국어 형태소 분석기
```powershell
pip install eunjeon
```
Visual C++ Build Tools(1.6)가 먼저 설치되어 있어야 합니다.

### 7.4 서버 실행
`voice_service.py`를 `D:\MyAiProject\voice-pipeline`에 저장 후:
```powershell
uvicorn voice_service:app --host 0.0.0.0 --port 8001
```
확인: `curl.exe http://localhost:8001/health`

---

## 8. 실시간 음성 대화 (WebSocket)

파일 업로드 방식이 아니라, 마이크로 실시간 대화하는 기능입니다.

### 8.1 pom.xml에 WebSocket 의존성 추가
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 8.2 실시간 웹 검색을 위한 Tavily API 키 발급

1. https://tavily.com 가입 (무료 티어: 월 1,000회)
2. API 키 발급 (`tvly-`로 시작)
3. `application.yml`에 최상위 레벨로 추가 (`spring:` 블록 안이 아님에 주의):
```yaml
tavily:
  api-key: tvly-여기에-발급받은-키
```

### 8.3 서버 코드 배치
`WebSocketConfig.java`, `VoiceWebSocketHandler.java`(`com.ai.llm.voice`), `WebSearchService.java`(`com.ai.llm.websearch`), `OllamaService`에 `generateStream()` 메서드 추가.

### 8.4 동작 확인
1. 앱 재시작 (Java 파일 변경 시 devtools가 자동 재시작)
2. 웹 UI에서 "🎤 마이크 시작" 클릭 → 마이크 권한 허용
3. 몇 마디 말하고 잠깐 멈추기 → 무음 감지 → STT 전사 → LLM 스트리밍 답변이 문장 단위로 화면에 표시되는지 확인
4. "오늘 날씨 어때?" 같은 질문으로 웹 검색 보강이 동작하는지 확인 (로그에 `🔍 웹 검색 중`이 뜨는지)

### 8.5 무음 감지(VAD) 임계값 튜닝

마이크 감도는 환경마다 달라서, 화면의 실시간 음량 표시를 보고 조정이 필요할 수 있습니다.

```javascript
const SILENCE_THRESHOLD = 13;  // 조용할 때와 말할 때 음량 수치의 중간값으로 설정
```

조용할 때/말할 때 각각의 음량 수치를 화면에서 확인 후, 그 중간값으로 조정하세요.

---

## 9. Python 평가 환경 (RAGAS, 후순위)

> 로컬 LLM(`qwen3:4b`)이 RAGAS가 요구하는 구조화된 JSON 출력을 안정적으로 못 만들어내는 문제로 현재 **후순위**로 미뤄둔 기능입니다. 참고용으로만 남겨둡니다.

```powershell
cd D:\MyAiProject
python -m venv rag-eval-env
.\rag-eval-env\Scripts\Activate.ps1
pip install "ragas<0.4" langchain-community langchain-ollama pandas requests psycopg2-binary
python collect_results.py
python evaluate_ragas.py
```

---

## 10. 전체 한 번에 실행하기

`start-all.ps1`, `stop-all.ps1`을 `D:\MyAiProject`에 저장한 뒤:

```powershell
cd D:\MyAiProject
.\start-all.ps1
```

Docker 인프라, Stable Diffusion, 음성 서버(uvicorn)를 각각 새 창에서 기동합니다. 그 다음 **IntelliJ에서 Spring Boot 앱만 직접 Run**하면 준비 완료.

종료:
```powershell
.\stop-all.ps1
```
(음성 서버 창은 직접 닫거나 `Ctrl+C`)

---

## 11. 자주 겪는 오류와 해결법 총정리

### 11.1 `Connection refused: localhost:5432` (또는 다른 포트)
컨테이너 미기동 또는 볼륨 충돌. `docker ps` 확인 후 `docker compose up -d <서비스명>`.

### 11.2 PowerShell `curl` 이상 동작
`curl.exe`로 명시 호출. 한글/공백 URL은 `curl.exe -G <URL> --data-urlencode "key=value"`.

### 11.3 OpenSearch `Field 'embedding' is not knn_vector type`
인덱스 삭제 후 매핑 없이 재생성됨. 삭제 후 반드시 매핑을 먼저 지정해 재생성:
```
PUT http://localhost:9200/rag_documents
{
  "settings": { "index": { "knn": true } },
  "mappings": {
    "properties": {
      "embedding": {
        "type": "knn_vector", "dimension": 1024,
        "method": { "name": "hnsw", "space_type": "cosinesimil", "engine": "lucene" }
      }
    }
  }
}
```

### 11.4 pgvector 검색 결과가 항상 비어있음
OpenSearch 임계값(0.55)을 그대로 적용하면 안 됨. pgvector 전용 낮은 임계값(실측 0.25) 별도 적용.

### 11.5 표 데이터(숫자 집계 등)가 검색되지 않음
PDFBox가 복잡한 표 레이아웃에서 추출 실패. 페이지별 텍스트 길이가 기준 미만이면 Tesseract OCR로 재시도하는 하이브리드 추출 적용.

### 11.6 프로젝트 폴더 이름 변경 후 Ollama 모델 전부 사라짐
Docker Compose는 폴더명 기준으로 볼륨을 구분. `ollama pull`로 재다운로드, 이후 폴더명 유지.

### 11.7 `ModuleNotFoundError: ...langchain_community.chat_models.vertexai`
ragas 0.4.x와 최신 langchain-community 비호환. `pip install "ragas<0.4"`.

### 11.8 RAGAS 평가 결과가 전부 NaN
`evaluate()`가 예외를 조용히 삼킴. `raise_exceptions=True` 옵션으로 실제 에러 확인. (근본 원인: 로컬 소형 모델이 RAGAS의 JSON 출력 요구를 못 지킴 — `ChatOllama(..., format="json")`으로 일부 완화 가능하나 완전한 해결은 아님)

### 11.9 콘솔 로그 한글 깨짐
`-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` VM 옵션 추가.

### 11.10 `ModuleNotFoundError: No module named 'pkg_resources'`
`pip install "setuptools<81"` (버전 명시 필수).

### 11.11 `python -m unidic download` 시 `cannot find context for 'fork'`
Windows에서 `plac` 라이브러리 버그. `unidic-lite` 설치 후 dicdir 파일 직접 복사로 우회.

### 11.12 `eunjeon` 설치 시 `Microsoft Visual C++ 14.0 or greater is required`
Visual C++ Build Tools 설치 후 새 터미널에서 재시도.

### 11.13 `faster-whisper` 실행 시 `Library cublas64_12.dll is not found`
CUDA 툴킷 미설치. `device="cpu"`로 설정 (small 모델은 CPU도 충분히 빠름).

### 11.14 m4a 변환 시 `UnicodeDecodeError: 'cp949' codec...`
`subprocess.run(..., encoding="utf-8", errors="ignore")`로 명시.

### 11.15 Stable Diffusion Docker 빌드 시 `fatal: could not read Username for 'https://github.com'`
`Stability-AI/stablediffusion` 저장소 접근 불가. `services/AUTOMATIC1111/Dockerfile`에서 커뮤니티 포크(`w-e-w/stablediffusion`)로 교체.

### 11.16 이미지 생성 시 CORS 에러
`docker-compose.override.yml`에 `--cors-allow-origins=http://localhost:8080` 추가.

### 11.17 TTS `500 Internal Server Error: "exceptions must derive from BaseException"`
서버 코드에 `traceback.print_exc()` 추가해 실제 원인 확인 (보통 한국어 형태소 분석기 미설치).

### 11.18 TTS 긴 텍스트가 잘림
MeloTTS가 내부적으로 문장 단위 분할·합성을 하므로, 백엔드의 인위적 문자수 제한만 넉넉하게 늘리면 해결.

### 11.19 TTS 특정 텍스트에서 `KeyError: '='`
한국어 g2p가 인식 못 하는 특수문자(뉴스 기사의 "지역=통신사" 표기 등). TTS 전 텍스트를 정리(sanitize)하는 필터 함수 추가, 안전한 문자만 남기고 나머지는 공백 치환.

### 11.20 WebSocket 관련 컴파일 시 `java: error: release version 21 not supported`
IntelliJ가 Maven 설정과 무관하게 자체 SDK로 빌드. `설정 → Maven → 러너 → "Maven에 IDE 빌드/실행 작업 위임"` 체크로 해결.

### 11.21 `index.html` 수정이 재시작 없이는 반영 안 됨
정적 리소스는 빌드 시점에만 복사되고 브라우저도 캐싱함. `spring-boot-devtools` 추가로 라이브 리로드 활성화 (5장 참고).

### 11.22 YAML `mapping values are not allowed here` 에러
새 설정 블록(`tavily` 등)을 추가하다가 들여쓰기가 기존 블록 안으로 잘못 들어가거나 탭 문자가 섞임. 새 최상위 블록은 `spring:`과 같은 레벨(들여쓰기 0칸)에 위치해야 하며, YAML은 탭을 허용하지 않으므로 스페이스만 사용.

### 11.23 실시간 대화가 계속 영어로 응답
질문을 그대로 LLM에 전달해서 언어가 통제 안 됨. 프롬프트에 "반드시 한국어로만 답하라"는 지시문을 명시적으로 포함.

### 11.24 무음 감지(VAD)가 전혀 반응하지 않음
임계값이 실제 배경 소음보다 낮거나 높게 설정됨. 화면의 실시간 음량 표시로 조용할 때/말할 때 수치를 직접 확인한 뒤, 그 중간값으로 `SILENCE_THRESHOLD`를 재설정.

---

## 참고: 프로젝트 구조 요약

```
D:\MyAiProject\                    # 메인 Spring Boot 프로젝트
├── src\main\java\com\ai\llm\
│   ├── rag\                       # RagService, PgVectorRagService, RagController 등
│   ├── pgvector\                  # PgVectorService, PgVectorIngestService, OCR 서비스
│   ├── opensearch\                # OpenSearchService
│   ├── ollama\                    # OllamaService (생성/스트리밍), 프롬프트 번역
│   ├── voice\                     # VoiceService, VoiceController, WebSocketConfig,
│   │                              #   VoiceWebSocketHandler (실시간 음성)
│   └── websearch\                 # WebSearchService (Tavily 연동)
├── src\main\resources\
│   ├── application.yml
│   └── static\index.html          # 통합 테스트 웹 UI
├── docker-compose.yml             # OpenSearch/Postgres/Ollama
├── rag-eval-env\                  # RAGAS 평가용 Python venv (후순위)
├── voice-pipeline\                # STT/TTS Python 서버
│   ├── voice-pipeline-env\
│   └── voice_service.py
├── start-all.ps1
└── stop-all.ps1

D:\stable-diffusion-webui-docker\  # 별도 저장소 (이미지 생성)
```
