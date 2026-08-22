# Local Multimodal AI Platform

로컬 환경(Ollama + Docker)에서 동작하는 종합 AI 파이프라인 포트폴리오 프로젝트입니다. 텍스트 RAG, 이미지 생성, 음성 인식/합성까지 하나의 웹 UI에서 다루며, 각 기능을 실제 프로덕션에서 마주치는 문제(검색 정확도, 인코딩, 라이브러리 호환성 등)를 직접 디버깅하며 구축했습니다.

## 목차

- [핵심 기능](#핵심-기능)
- [아키텍처](#아키텍처)
- [기술 스택](#기술-스택)
- [빠른 시작](#빠른-시작)
- [상세 기능 설명](#상세-기능-설명)
- [주요 기술적 의사결정 및 트러블슈팅](#주요-기술적-의사결정-및-트러블슈팅)
- [로드맵](#로드맵)

## 핵심 기능

1. **듀얼 벡터스토어 RAG** — OpenSearch(KNN)와 PostgreSQL/pgvector(HNSW) 두 가지로 동일한 RAG 파이프라인을 구현, 검색 방식을 선택해 비교 가능
2. **문서 업로드 & OCR 보완** — PDF/TXT 업로드, 문장 경계 인식 청킹, PDFBox가 놓치는 표 데이터는 Tesseract OCR로 자동 보완
3. **RAG 정량 평가** — RAGAS(faithfulness, answer_relevancy)로 두 벡터스토어의 답변 품질을 정량 비교
4. **이미지 생성** — 로컬 Stable Diffusion(AUTOMATIC1111 WebUI, Docker)로 텍스트→이미지 생성. 한글 프롬프트는 로컬 LLM이 자동으로 영어 번역 후 전달
5. **음성 인식(STT)** — faster-whisper 기반, 어떤 오디오 포맷(m4a/mp3/wav)이든 업로드하면 텍스트로 전사
6. **음성 합성(TTS)** — MeloTTS(한국어) 기반, 텍스트 직접 입력 또는 PDF/TXT 파일 업로드 시 내용을 추출해 음성 파일로 생성
7. **Python + LangChain 독립 구현** — Spring Boot 없이 Python에서 직접 pgvector에 접속해 RAG를 재현하는 별도 스크립트
8. **Docker Compose / Kubernetes 양쪽 배포 경험** — 로컬 GPU 가속은 Docker Compose, 매니페스트 데모는 Minikube

## 아키텍처

```
                         ┌─────────────────────┐
                         │   웹 UI (index.html) │
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │  Spring Boot :8080    │
                         │  (RAG / 이미지 프록시 /│
                         │   STT·TTS 프록시)      │
                         └──┬────────┬───────┬───┘
                            │        │       │
              ┌─────────────┘        │       └──────────────┐
              ▼                      ▼                      ▼
    ┌──────────────────┐  ┌──────────────────┐   ┌─────────────────────┐
    │ OpenSearch :9200  │  │  pgvector(PG)     │   │  Ollama :11434       │
    │ (KNN 벡터검색)     │  │  :5432 (HNSW)     │   │  qwen3:4b (생성)      │
    └──────────────────┘  └──────────────────┘   │  qwen3-embedding:0.6b│
                                                    └─────────────────────┘
              브라우저에서 직접 호출 (CORS 허용)
              ┌──────────────────────────┐
              ▼                          ▼
    ┌──────────────────┐      ┌──────────────────────┐
    │ Stable Diffusion   │      │ Voice Service :8001   │
    │ WebUI :7860 (Docker)│      │ (FastAPI, faster-     │
    │ AUTOMATIC1111       │      │  whisper + MeloTTS)   │
    └──────────────────┘      └──────────────────────┘
```

**RAG 답변 생성 흐름:**
```
질문 → qwen3-embedding:0.6b(벡터화) → 벡터검색(OpenSearch or pgvector)
     → 관련성 임계값 필터 → qwen3:4b(답변 생성, 근거 명시) → 답변
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| LLM / 임베딩 | Ollama (`qwen3:4b`, `qwen3-embedding:0.6b`) |
| 벡터 스토어 | OpenSearch 3 (KNN, cosine, lucene) / PostgreSQL 16 + pgvector (HNSW, cosine) |
| 백엔드 | Spring Boot 4.1.0, Spring AI 2.0.0, Java 21 (GraalVM) |
| 문서 처리 | Apache PDFBox, Tesseract OCR(Tess4J) |
| 이미지 생성 | Stable Diffusion 1.5 (AUTOMATIC1111 WebUI, Docker, GPU) |
| 음성 인식 | faster-whisper (CTranslate2 기반) |
| 음성 합성 | MeloTTS (한국어 지원) |
| 음성 서비스 API | FastAPI + Uvicorn (Python) |
| 평가 | RAGAS + LangChain(Ollama 연동) |
| 배포 | Docker Compose (GPU 가속), Kubernetes/Minikube (CPU 데모) |
| 개발 환경 | IntelliJ IDEA, Windows 11 / PowerShell, Docker Desktop(WSL2), NVIDIA RTX 3060 (VRAM 6GB) |

## 빠른 시작

전체 설치 과정은 [SETUP.md](./SETUP.md)를 참고하세요. 이미 설치가 끝난 상태에서 매번 켤 때는:

```powershell
cd D:\MyAiProject
.\start-all.ps1
```

이 스크립트가 Docker 인프라(OpenSearch/pgvector/Ollama), Stable Diffusion WebUI, 음성 서버(STT/TTS)를 각각 새 창에서 자동으로 기동합니다. 이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**하면 `http://localhost:8080`에서 전체 기능을 사용할 수 있습니다.

종료할 때:
```powershell
.\stop-all.ps1
```

## 상세 기능 설명

### 1. 듀얼 벡터스토어 RAG

```
GET /api/rag/ask?question={질문}&store=opensearch
GET /api/rag/ask?question={질문}&store=pgvector
GET /api/rag/evaluate?question={질문}&store={store}   (평가용, 검색된 컨텍스트까지 JSON으로 반환)
```

- **OpenSearch 경로**: `RestClient`로 직접 호출, 인덱스 매핑을 세밀하게 제어 (knn_vector, cosinesimil, lucene engine)
- **pgvector 경로**: Spring AI의 `VectorStore` 표준 추상화 사용, 스키마 자동 생성(HNSW, cosine)
- 관련성 임계값은 두 스토어의 점수 스케일이 달라 별도로 관리 (OpenSearch 0.55, pgvector 0.25 — 실측 기반)

### 2. 문서 업로드 & 청킹 & OCR

```
POST /api/documents/upload      (OpenSearch, PDF/TXT)
POST /api/pgvector/ingest       (pgvector, PDF/TXT)
```

- 문장 경계를 인식하는 청킹(소수점 "20.79%"를 문장 끝으로 오인하지 않도록 정규식 lookahead 처리)
- 목차의 점선 줄("....................") 등 정보 없는 청크는 인제스트 단계에서 자동 필터링
- PDFBox가 복잡한 표 레이아웃에서 텍스트 추출에 실패하는 페이지는 Tesseract OCR로 자동 보완 (`PdfTextExtractionService`)
- 웹 UI에서 폴더 단위 업로드도 지원 (PDF/TXT만 자동 필터링, 처리 결과 요약 표시)

### 3. RAG 정량 평가 (RAGAS)

```powershell
cd D:\MyAiProject
.\rag-eval-env\Scripts\Activate.ps1
python collect_results.py      # 두 스토어에 질문 세트를 던져 answer/context/응답시간 수집
python evaluate_ragas.py       # faithfulness, answer_relevancy 계산 및 store별 비교
```

로컬 `qwen3:4b`를 평가자(judge) LLM으로 사용하여 OpenAI API 없이 완전히 로컬에서 평가합니다.

### 4. 이미지 생성 (Stable Diffusion)

웹 UI에서 한글로 프롬프트를 입력하면:
1. 백엔드가 `qwen3:4b`로 한글→영어 번역 (`POST /api/image/translate-prompt`)
2. 번역된 프롬프트를 브라우저가 Stable Diffusion API(`:7860/sdapi/v1/txt2img`)에 직접 호출 (CORS 허용 설정됨)
3. 결과 이미지와 번역된 프롬프트를 함께 표시

### 5. 음성 인식 (STT)

```
POST /api/voice/stt   (multipart, m4a/mp3/wav/ogg/flac 등)
```
어떤 포맷이든 서버가 ffmpeg로 16kHz mono WAV로 통일 변환 후 `faster-whisper`(small 모델, CPU)로 전사합니다.

### 6. 음성 합성 (TTS)

```
POST /api/voice/tts   (text 파라미터 또는 PDF/TXT 파일 업로드)
```
텍스트를 직접 입력하거나 PDF/TXT를 업로드하면(OCR 파이프라인 재사용), MeloTTS가 내부적으로 문장 단위로 쪼개 합성한 뒤 이어붙인 WAV 파일을 반환합니다. 웹 UI에서 오디오 재생 및 다운로드 버튼 제공.

### 7. Python + LangChain 독립 구현

```powershell
python langchain_rag_demo.py "질문 내용"
```
Spring Boot를 거치지 않고 Python에서 `psycopg2`로 pgvector에 직접 SQL(`<=>` 코사인 거리 연산자)을 날려 검색하고, LangChain의 LCEL(`prompt | llm | StrOutputParser`)로 생성까지 재현합니다.

## 주요 기술적 의사결정 및 트러블슈팅

가장 자주 겪었거나 중요한 이슈만 요약했습니다. 전체 목록과 상세 해결 과정은 [SETUP.md](./SETUP.md)의 트러블슈팅 섹션을 참고하세요.

| 이슈 | 원인 | 해결 |
|---|---|---|
| OpenSearch KNN 검색 결과 부정확 | 인덱스가 기본값(L2 거리)으로 생성됨 | 인덱스 매핑에 cosine similarity 명시적 설정 |
| pgvector 검색 결과가 항상 비어있음 | OpenSearch(0.55)와 동일 임계값을 적용했으나 pgvector 점수 스케일이 다름(실측 0.25~0.36) | 스토어별 독립 임계값 적용 |
| 표 데이터가 검색되지 않음 | PDFBox가 복잡한 표 레이아웃 텍스트 추출 실패 | Tesseract OCR로 페이지별 보완 추출 |
| 폴더명 변경 후 Ollama 모델 전부 유실 | Docker Compose가 프로젝트(폴더)명 기준으로 볼륨을 구분 | 모델 재pull, 통합 compose 파일 하나로 정리 |
| RAGAS 실행 시 `vertexai` 모듈 에러 | ragas 0.4.3과 최신 langchain-community 간 호환성 깨짐 | ragas<0.4로 다운그레이드 |
| Python 로그에 한글이 깨짐 | JVM stdout 인코딩과 Windows 콘솔 코드페이지 불일치 | VM 옵션 `-Dstdout.encoding=UTF-8` 추가 |
| `pkg_resources` ModuleNotFoundError | setuptools 81+ 부터 pkg_resources 기본 제거 | `pip install "setuptools<81"` |
| `unidic download` 실행 시 Windows에서 크래시 | 내부 `plac` 라이브러리가 Unix 전용 fork 방식 시도 | `unidic-lite`로 대체 후 dicdir 파일 복사 |
| `eunjeon` 빌드 실패 | Python 3.11용 사전빌드 wheel 없음 → 소스 컴파일 필요 | Visual C++ Build Tools 설치 |
| Stable Diffusion Docker 빌드 실패 | `Stability-AI/stablediffusion` 원본 저장소 접근 불가(삭제/비공개 전환) | 커뮤니티 포크 저장소로 Dockerfile 수정 |

## 로드맵

- [x] 듀얼 벡터스토어 RAG 파이프라인
- [x] OCR 기반 표 데이터 보완
- [x] 이미지 생성 (Stable Diffusion + 한글 번역 체이닝)
- [x] STT/TTS 파이프라인 (기본 동작 검증)
- [ ] RAGAS 평가 결과 기반 OpenSearch vs pgvector 정량 비교 리포트 완성
- [ ] 프롬프트 개선 A/B 테스트 (할루시네이션 억제 프롬프트 적용 및 비교)
- [ ] STT-LLM-TTS 실시간 스트리밍 파이프라인 (WebSocket, 문장 단위 파이프라이닝)
- [ ] QLoRA 기반 로컬 파인튜닝 (8GB VRAM, unsloth)
- [ ] 클라우드(AWS/GCP) 배포
