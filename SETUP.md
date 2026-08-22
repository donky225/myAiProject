# 전체 환경 설치 가이드 (Windows)

이 문서는 개발 경험이 없어도 순서대로 명령어를 따라 입력하면 이 프로젝트의 전체 기능(RAG, 이미지 생성, 음성 인식/합성)을 동일하게 재현할 수 있도록 작성되었습니다. 각 단계마다 **왜 필요한지**와 **실제로 겪었던 오류 및 해결법**을 함께 적었습니다.

기준 환경: Windows 11, NVIDIA GPU(VRAM 6GB 기준으로 설명하며, 다르면 일부 설정값 조정 필요)

---

## 목차

1. [사전 준비물 설치](#1-사전-준비물-설치)
2. [프로젝트 소스 받기](#2-프로젝트-소스-받기)
3. [메인 인프라 기동 (OpenSearch, pgvector, Ollama)](#3-메인-인프라-기동)
4. [Spring Boot 애플리케이션 실행](#4-spring-boot-애플리케이션-실행)
5. [Python 평가 환경 (RAGAS)](#5-python-평가-환경-ragas)
6. [이미지 생성 환경 (Stable Diffusion)](#6-이미지-생성-환경-stable-diffusion)
7. [음성 인식/합성 환경 (STT/TTS)](#7-음성-인식합성-환경-stttts)
8. [전체 한 번에 실행하기](#8-전체-한-번에-실행하기)
9. [자주 겪는 오류와 해결법 총정리](#9-자주-겪는-오류와-해결법-총정리)

---

## 1. 사전 준비물 설치

### 1.1 Git
https://git-scm.com/download/win 에서 다운로드 후 기본 옵션으로 설치.

### 1.2 IntelliJ IDEA
https://www.jetbrains.com/idea/download 에서 Community 또는 Ultimate 설치. 설치 후 프로젝트를 열면 IDE 내에서 JDK 21(GraalVM)을 자동으로 받을 수 있습니다.

### 1.3 Docker Desktop
https://www.docker.com/products/docker-desktop 에서 설치 후 **재부팅**. 설치 시 "Use WSL 2 instead of Hyper-V" 옵션을 선택합니다. GPU를 쓰는 컨테이너(Ollama, Stable Diffusion)를 실행하려면 최신 NVIDIA 드라이버가 필요합니다 (WSL2용 별도 드라이버는 필요 없고, 일반 Windows용 드라이버가 WSL2까지 자동 지원합니다).

설치 확인:
```powershell
docker --version
docker compose version
nvidia-smi
```
`nvidia-smi`에서 GPU 정보가 출력되지 않으면 드라이버를 최신 버전으로 업데이트해야 합니다.

### 1.4 Python 3.11
https://www.python.org/downloads/windows/ 에서 설치. **설치 화면에서 "Add python.exe to PATH" 체크박스를 반드시 선택**하세요.

확인:
```powershell
python --version
```

### 1.5 FFmpeg (오디오 변환에 필요)
```powershell
winget install Gyan.FFmpeg
```
**설치 후 반드시 열려있는 PowerShell 창을 모두 닫고 새로 열어야** PATH가 반영됩니다.

확인 (새 창에서):
```powershell
ffmpeg -version
```

### 1.6 Visual C++ Build Tools (일부 Python 패키지 컴파일에 필요)
```powershell
winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --quiet --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```
용량이 크고(수 GB) 설치에 10~30분 정도 걸릴 수 있습니다. 설치 중 **UAC(사용자 계정 컨트롤) 팝업이 다른 창 뒤에 숨을 수 있으니**, `Alt+Tab`으로 확인 후 "예"를 눌러 승인해주세요.

### 1.7 PowerShell 실행 정책 완화 (Python 가상환경 활성화에 필요)
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

Git 저장소가 없다면 기존 PC의 `D:\MyAiProject` 폴더를 복사해서 옮겨도 됩니다. 단 `target/`, `voice-pipeline/voice-pipeline-env/`, `rag-eval-env/` 폴더는 용량만 크고 새로 생성되는 폴더이니 제외하고 복사하는 것을 권장합니다.

> **주의**: 프로젝트 폴더 이름을 나중에 바꾸면 Docker 볼륨(특히 Ollama에 받아둔 모델)이 새로 생성되어 기존에 받은 모델이 전부 사라집니다. 폴더명은 처음에 정한 대로 유지하는 것을 권장합니다.

---

## 3. 메인 인프라 기동

프로젝트 루트의 `docker-compose.yml`에는 OpenSearch, PostgreSQL(pgvector), Ollama가 정의되어 있습니다.

```powershell
cd D:\MyAiProject
docker compose up -d opensearch postgres ollama ollama-init
```

`ollama-init` 컨테이너가 `qwen3:4b`, `qwen3-embedding:0.6b` 모델을 자동으로 받습니다 (용량이 커서 수 분~수십 분 소요).

### 기동 확인
```powershell
docker ps
```
`local-opensearch`, `rag-postgres`, `local-ollama` 세 컨테이너가 보이고, `rag-postgres`가 `(healthy)` 상태여야 합니다.

모델이 실제로 받아졌는지 확인:
```powershell
docker exec -it local-ollama ollama list
```
`qwen3:4b`와 `qwen3-embedding:0.6b`가 보여야 합니다.

---

## 4. Spring Boot 애플리케이션 실행

IntelliJ에서 `D:\MyAiProject` 폴더를 열고, Maven이 의존성을 자동으로 받을 때까지 기다립니다 (최초 1회는 시간이 좀 걸립니다).

### 콘솔 한글 깨짐 방지 설정 (최초 1회)
`Run` → `Edit Configurations` → 실행 구성 선택 → VM 옵션에 추가:
```
-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
```

`AiApplication`을 우클릭 → Run.

### 기동 확인
콘솔에 에러 없이 아래와 같은 로그가 뜨면 성공입니다.
```
PgVectorStore : Initializing PGVectorStore schema for table: vector_store
HikariPool-1 - Start completed.
Started AiApplication in ... seconds
```

브라우저에서 `http://localhost:8080` 접속 시 웹 UI가 나오면 정상입니다.

---

## 5. Python 평가 환경 (RAGAS)

```powershell
cd D:\MyAiProject
python -m venv rag-eval-env
.\rag-eval-env\Scripts\Activate.ps1
```
프롬프트 앞에 `(rag-eval-env)`가 붙는지 확인.

```powershell
pip install "ragas<0.4" langchain-community langchain-ollama pandas requests psycopg2-binary
```

> **주의**: `ragas` 최신 버전(0.4.x)은 `langchain-community`와 호환성 문제로 `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'` 에러가 납니다. 반드시 `ragas<0.4`로 설치하세요. (자세한 내용은 9장 참고)

### 평가 실행
```powershell
python collect_results.py
python evaluate_ragas.py
```

---

## 6. 이미지 생성 환경 (Stable Diffusion)

메인 프로젝트와 **별도 폴더**에 설치합니다.

```powershell
cd D:\
git clone https://github.com/AbdBarho/stable-diffusion-webui-docker.git
cd stable-diffusion-webui-docker
```

### CORS 허용 설정 (브라우저에서 직접 API 호출을 위해 필요)
`docker-compose.override.yml` 파일을 새로 만들고 아래 내용을 저장합니다.
```yaml
services:
  auto:
    environment:
      - CLI_ARGS=--api --cors-allow-origins=http://localhost:8080
```

### 모델 다운로드
```powershell
docker compose --profile download up --build
```
콘솔 끝에 `download-1 exited with code 0`이 뜨면 성공입니다. 중간에 네트워크 오류로 실패하면 **같은 명령을 한 번 더 실행**하면 대부분 해결됩니다.

> **알려진 이슈**: `Stability-AI/stablediffusion` 원본 GitHub 저장소가 삭제/비공개 전환되어 빌드가 실패할 수 있습니다. 이 경우 9장의 해결법을 참고해 Dockerfile을 수정해야 합니다.

### WebUI 기동
```powershell
docker compose --profile auto up --build
```
`Running on local URL: http://0.0.0.0:7860` 로그가 뜨면 `http://localhost:7860`으로 접속해 확인합니다.

---

## 7. 음성 인식/합성 환경 (STT/TTS)

```powershell
mkdir D:\MyAiProject\voice-pipeline
cd D:\MyAiProject\voice-pipeline
python -m venv voice-pipeline-env
.\voice-pipeline-env\Scripts\Activate.ps1
```

### 7.1 필수 패키지 설치
```powershell
pip install "setuptools<81"
pip install faster-whisper
pip install fastapi uvicorn python-multipart
pip install git+https://github.com/myshell-ai/MeloTTS.git
```

> `setuptools<81`이 필요한 이유: 최신 setuptools(81+)는 `pkg_resources` 모듈을 기본 제거했는데, `librosa`(MeloTTS 의존성)가 아직 이 구식 API를 사용하기 때문입니다.

### 7.2 일본어 사전 데이터 준비 (MeloTTS 내부 의존성)

MeloTTS는 한국어만 써도 내부적으로 일본어 처리기(MeCab)를 함께 로딩합니다. 공식 다운로드 명령(`python -m unidic download`)이 **Windows에서 크래시하는 알려진 버그**가 있어, 아래처럼 우회합니다.

```powershell
pip install unidic-lite
New-Item -ItemType Directory -Force -Path ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir"
Copy-Item -Path ".\voice-pipeline-env\Lib\site-packages\unidic_lite\dicdir\*" -Destination ".\voice-pipeline-env\Lib\site-packages\unidic\dicdir\" -Recurse -Force
```

### 7.3 한국어 형태소 분석기 설치
```powershell
pip install eunjeon
```
이 패키지는 C++ 컴파일이 필요해서, **1.6단계의 Visual C++ Build Tools가 반드시 먼저 설치되어 있어야** 합니다. 설치 후 새 PowerShell 창에서 다시 시도해야 컴파일러가 인식됩니다.

### 7.4 서버 파일 배치 및 실행
`voice_service.py`, `test_stt.py`, `test_tts.py`를 `D:\MyAiProject\voice-pipeline`에 저장합니다.

단독 테스트:
```powershell
python test_stt.py my_voice.m4a
python test_tts.py "안녕하세요 테스트입니다"
```

API 서버 기동:
```powershell
uvicorn voice_service:app --host 0.0.0.0 --port 8001
```
콘솔에 순서대로 `STT 모델 로딩 중...` → `TTS 모델 로딩 중...` → `모든 모델 로딩 완료. 서버 준비됨.`이 뜨면 성공입니다 (최초 실행 시 한국어 TTS 모델을 HuggingFace에서 자동 다운로드하므로 시간이 걸릴 수 있습니다).

확인:
```powershell
curl.exe http://localhost:8001/health
```
`{"status":"ok"}`가 나오면 정상입니다.

---

## 8. 전체 한 번에 실행하기

매번 4개 창을 따로 여는 게 번거로우니, `start-all.ps1` 스크립트로 Spring Boot를 제외한 나머지를 한 번에 기동합니다.

`start-all.ps1`, `stop-all.ps1`을 `D:\MyAiProject`에 저장한 뒤:

```powershell
cd D:\MyAiProject
.\start-all.ps1
```

이 스크립트는:
1. Docker 인프라(OpenSearch/pgvector/Ollama) 기동
2. Stable Diffusion WebUI를 새 PowerShell 창에서 기동
3. 음성 서버(uvicorn)를 새 PowerShell 창에서 기동

를 순서대로 실행합니다. 그 다음 **IntelliJ에서 Spring Boot 앱만 직접 Run**하면 전체 시스템이 준비됩니다.

### 매번 켤 때 순서 (요약)
```powershell
cd D:\MyAiProject
.\start-all.ps1
```
→ IntelliJ에서 `AiApplication` Run
→ 1~2분 기다린 후 `http://localhost:8080` 접속

### 끌 때
```powershell
cd D:\MyAiProject
.\stop-all.ps1
```
그리고 음성 서버(uvicorn) 창은 직접 닫거나 그 창에서 `Ctrl+C`를 눌러주세요. IntelliJ의 Spring Boot 앱도 정지 버튼으로 꺼주세요.

---

## 9. 자주 겪는 오류와 해결법 총정리

### 9.1 `Connection refused: localhost:5432` (또는 다른 포트)
**원인**: 해당 컨테이너가 기동되지 않았거나, 예전에 다른 폴더에서 별도로 띄운 컨테이너와 이름/볼륨이 충돌.
**해결**: `docker ps`로 컨테이너 상태 확인 후 `docker compose up -d <서비스명>`으로 개별 기동. 예전 독립 compose 파일이 있다면 그 폴더에서 `docker compose down`으로 먼저 정리.

### 9.2 PowerShell에서 `curl`이 이상하게 동작함
**원인**: PowerShell의 `curl`은 실제 curl이 아니라 `Invoke-WebRequest`의 별칭이라 옵션 문법이 다름.
**해결**: `curl.exe`로 명시적으로 호출. 한글/공백 포함 URL은 `curl.exe -G <URL> --data-urlencode "key=value"` 형식 사용.

### 9.3 OpenSearch 벡터 검색 시 `Field 'embedding' is not knn_vector type`
**원인**: 인덱스를 `DELETE`한 뒤 매핑 없이 문서를 업로드하면 동적 매핑으로 재생성되어 `knn_vector` 타입이 사라짐.
**해결**: 인덱스 삭제 후 반드시 아래처럼 매핑을 먼저 지정해서 재생성:
```
PUT http://localhost:9200/rag_documents
{
  "settings": { "index": { "knn": true } },
  "mappings": {
    "properties": {
      "embedding": {
        "type": "knn_vector",
        "dimension": 1024,
        "method": { "name": "hnsw", "space_type": "cosinesimil", "engine": "lucene" }
      }
    }
  }
}
```

### 9.4 pgvector 검색 결과가 항상 비어있음
**원인**: OpenSearch 기준 임계값(0.55)을 그대로 pgvector에 적용. pgvector(Spring AI)의 코사인 유사도 점수 스케일이 OpenSearch와 달라 실측 0.25~0.36 수준으로 나옴.
**해결**: pgvector 전용 임계값을 별도로 낮게 설정 (실측 기반 0.25 권장). `/api/pgvector/debug-search` 같은 진단 엔드포인트로 원본 점수를 직접 확인하며 조정.

### 9.5 문서에 있는 표 데이터(숫자 집계 등)가 검색되지 않음
**원인**: PDFBox의 `PDFTextStripper`가 복잡한 표 레이아웃에서 텍스트 추출에 실패 (해당 페이지만 텍스트가 비정상적으로 짧게 추출됨).
**해결**: 페이지별 추출 텍스트 길이가 기준치 미만이면 그 페이지만 이미지로 렌더링 후 Tesseract OCR로 재시도하는 하이브리드 추출 로직 적용.

### 9.6 프로젝트 폴더 이름을 바꿨더니 Ollama 모델이 다 사라짐
**원인**: Docker Compose는 프로젝트(폴더)명을 기준으로 볼륨을 구분. 폴더명이 바뀌면 새 볼륨으로 인식되어 기존 볼륨의 모델 데이터에 접근하지 못함.
**해결**: `docker exec -it local-ollama ollama pull qwen3:4b`, `ollama pull qwen3-embedding:0.6b`로 재다운로드. 이후 폴더명은 유지.

### 9.7 `ModuleNotFoundError: No module named 'langchain_community.chat_models.vertexai'`
**원인**: `ragas` 0.4.x가 최신 `langchain-community`와 호환되지 않는 내부 import를 가지고 있음.
**해결**: `pip uninstall ragas -y` 후 `pip install "ragas<0.4"`로 재설치.

### 9.8 RAGAS 평가 결과가 전부 `NaN`으로 나옴
**원인**: `evaluate()` 내부 실행기가 개별 항목의 예외를 조용히 삼킴.
**해결**: `evaluate(..., raise_exceptions=True)` 옵션 추가, 또는 지표를 단일 샘플에 직접 호출(`metric.single_turn_ascore()`)해서 실제 에러 확인.

### 9.9 콘솔 로그에 한글이 깨져서 출력됨 (예: `������`)
**원인**: JVM의 표준출력 인코딩과 Windows 콘솔 코드페이지(CP949)가 불일치.
**해결**: IntelliJ 실행 구성의 VM 옵션에 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` 추가. `System.out.printf` 대신 SLF4J 로거 사용 권장.

### 9.10 `ModuleNotFoundError: No module named 'pkg_resources'`
**원인**: `setuptools` 81버전부터 `pkg_resources`가 기본 제거됨. `librosa` 등 구식 라이브러리가 여전히 이를 사용.
**해결**: `pip install "setuptools<81"` (단순히 `pip install setuptools`만 하면 최신 버전이 깔려 문제가 재발하니 버전 명시 필수).

### 9.11 `python -m unidic download` 실행 시 `ValueError: cannot find context for 'fork'`
**원인**: 내부적으로 쓰는 `plac` 라이브러리가 Unix 전용 `fork` 멀티프로세싱 방식을 시도하다 Windows에서 실패하는 알려진 버그.
**해결**: `unidic-lite` 설치 후, 그 사전 데이터를 `unidic` 패키지가 기본으로 바라보는 `dicdir` 폴더에 직접 복사 (7.2 참고). 공식 다운로더를 우회.

### 9.12 `eunjeon` 설치 시 `Microsoft Visual C++ 14.0 or greater is required`
**원인**: `eunjeon`은 Python 3.11용 사전빌드 wheel이 없어 소스에서 직접 컴파일해야 함.
**해결**: Visual C++ Build Tools 설치 (1.6 참고) 후 새 터미널에서 재시도.

### 9.13 `faster-whisper` 실행 시 `Library cublas64_12.dll is not found`
**원인**: GPU(CUDA)로 실행하려면 별도의 CUDA 툴킷(cuBLAS)이 Windows에 설치되어 있어야 하는데 없음. Docker의 GPU 패스스루는 컨테이너 안에서만 적용되고 로컬 Python 프로세스에는 적용되지 않음.
**해결**: `small` 모델 기준 CPU로도 충분히 빠르므로(실시간 대비 수 배속) `device="cpu"`로 설정. GPU를 꼭 쓰고 싶다면 `pip install nvidia-cublas-cu12 nvidia-cudnn-cu12`로 필요한 DLL만 추가 설치.

### 9.14 m4a 파일 변환 시 `UnicodeDecodeError: 'cp949' codec can't decode byte...`
**원인**: ffmpeg의 진행 로그를 캡처하는 과정에서 인코딩이 시스템 기본값(CP949)과 맞지 않음.
**해결**: `subprocess.run(..., encoding="utf-8", errors="ignore")`로 명시.

### 9.15 Stable Diffusion Docker 빌드 시 `fatal: could not read Username for 'https://github.com'`
**원인**: `Stability-AI/stablediffusion` 원본 저장소가 삭제되었거나 비공개로 전환됨 (커뮤니티에서도 동일 이슈 다수 보고).
**해결**: `services/AUTOMATIC1111/Dockerfile`에서 해당 저장소 URL을 커뮤니티 포크(`w-e-w/stablediffusion`)로 교체 후 재빌드.

### 9.16 웹 UI에서 이미지 생성 시 CORS 에러
**원인**: 브라우저가 `localhost:8080`에서 `localhost:7860`(다른 포트=다른 origin)으로 직접 요청을 보내는데 서버가 이를 허용하지 않음.
**해결**: `docker-compose.override.yml`에 `--cors-allow-origins=http://localhost:8080` 옵션 추가 (6장 참고).

### 9.17 TTS 요청 시 `500 Internal Server Error: "exceptions must derive from BaseException"`
**원인**: 실제 에러가 Python 서버 안에서 감춰짐. 코드에서 예외를 잡아 메시지만 전달하고 원본 트레이스백을 출력하지 않음.
**해결**: 서버 코드에 `traceback.print_exc()`를 추가해 콘솔에 전체 스택트레이스를 출력하도록 수정 → 실제 원인(예: 한국어 형태소 분석기 미설치) 확인 후 해당 패키지 설치.

### 9.18 긴 텍스트를 TTS로 변환할 때 잘리는 문제
**원인**: 백엔드에서 안전장치로 걸어둔 문자수 제한.
**해결**: MeloTTS는 내부적으로 이미 문장 단위로 텍스트를 쪼개 순차 합성 후 이어붙이므로, 별도의 청킹 로직 없이 백엔드의 제한값만 넉넉하게 늘리면 해결됩니다.

---

## 참고: 프로젝트 구조 요약

```
D:\MyAiProject\                    # 메인 Spring Boot 프로젝트
├── src\main\java\com\ai\llm\
│   ├── rag\                       # RagService, PgVectorRagService, RagController 등
│   ├── pgvector\                  # PgVectorService, PgVectorIngestService, OCR 서비스
│   ├── opensearch\                # OpenSearchService
│   ├── ollama\                    # OllamaService, 프롬프트 번역
│   └── voice\                     # VoiceService, VoiceController (STT/TTS 프록시)
├── src\main\resources\
│   ├── application.yml
│   └── static\index.html          # 통합 테스트 웹 UI
├── docker-compose.yml             # OpenSearch/Postgres/Ollama
├── rag-eval-env\                  # RAGAS 평가용 Python venv
├── voice-pipeline\                # STT/TTS Python 서버
│   ├── voice-pipeline-env\
│   └── voice_service.py
├── start-all.ps1
└── stop-all.ps1

D:\stable-diffusion-webui-docker\  # 별도 저장소 (이미지 생성)
```
