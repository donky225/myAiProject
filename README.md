# Local Multimodal AI Platform

로컬 환경(Ollama + Docker)에서 동작하는 종합 AI 파이프라인 포트폴리오 프로젝트입니다. 텍스트 RAG, 이미지 생성, 실시간 음성 대화까지 하나의 웹 UI에서 다루며, 각 기능을 실제 프로덕션에서 마주치는 문제(검색 정확도, 인코딩, 라이브러리 호환성, 실시간 스트리밍 등)를 직접 디버깅하며 구축했습니다.

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
3. **할루시네이션 억제 프롬프트** — 문서 근거 명시, 정보 없을 시 명확히 밝히기 등 규칙을 프롬프트에 반영, 실제 질의응답으로 정확도 검증 완료
4. **이미지 생성** — 로컬 Stable Diffusion(AUTOMATIC1111 WebUI, Docker)로 텍스트→이미지 생성. 한글 프롬프트는 로컬 LLM이 자동으로 영어 번역 후 전달
5. **음성 인식(STT)** — faster-whisper 기반, 어떤 오디오 포맷(m4a/mp3/wav)이든 업로드하면 텍스트로 전사
6. **음성 합성(TTS)** — MeloTTS(한국어) 기반, 텍스트 직접 입력 또는 PDF/TXT 파일 업로드 시 내용을 추출해 음성 파일로 생성
7. **실시간 음성 대화 (WebSocket)** — 마이크로 말하면 무음 감지로 발화 구간을 자동 판단하고, STT 전사 → (필요시 실시간 웹 검색으로 정보 보강) → LLM 스트리밍 응답 → 문장 단위 실시간 표시까지 이어지는 저지연 파이프라인
8. **실시간 웹 검색 보강** — 질문이 최신 정보(날씨, 뉴스 등)를 요구하는지 LLM이 먼저 판단하고, 필요하면 Tavily API로 검색한 결과를 답변에 반영
9. **Python + LangChain 독립 구현** — Spring Boot 없이 Python에서 직접 pgvector에 접속해 RAG를 재현하는 별도 스크립트
10. **Docker Compose / Kubernetes 양쪽 배포 경험** — 로컬 GPU 가속은 Docker Compose, 매니페스트 데모는 Minikube

## 아키텍처

```
                         ┌─────────────────────┐
                         │   웹 UI (index.html) │
                         └──────────┬───────────┘
                        HTTP        │        WebSocket (실시간 음성)
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
    ┌──────────────────┐  ┌──────────────────┐     ┌─────────────────────┐
    │Spring Boot :8080 │  │  /ws/voice        │   │ Tavily Search API    │
    │ (RAG/이미지 프록시)│  │  (VAD 결과 수신,   │    │ (실시간 정보 검색)     │
    │                    │  │   STT→LLM→문장분리)│  └─────────────────────┘
    └──┬────────┬───────┬┘  └──────────────────┘
       │        │       │
┌──────┘        │       └──────────────┐
▼               ▼                      ▼
┌──────────┐ ┌──────────┐   ┌─────────────────────┐
│OpenSearch │ │ pgvector │   │  Ollama :11434       │
│  :9200    │ │  :5432   │   │  qwen3:4b (생성/스트림)│
└──────────┘ └──────────┘   │  qwen3-embedding:0.6b│
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

**실시간 음성 대화 흐름:**
```
마이크 입력 → 브라우저에서 Web Audio API로 음량 측정(VAD)
           → 무음 감지 시 "발화 종료" 신호를 WebSocket으로 전송
           → 서버가 그동안 모은 오디오를 faster-whisper로 전사
           → (검색 필요 여부를 LLM이 짧게 판단 → 필요시 Tavily 검색)
           → qwen3:4b 스트리밍 생성 → 문장 부호마다 즉시 클라이언트로 전송
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| LLM / 임베딩 | Ollama (`qwen3:4b`, `qwen3-embedding:0.6b`) |
| 벡터 스토어 | OpenSearch 3 (KNN, cosine, lucene) / PostgreSQL 16 + pgvector (HNSW, cosine) |
| 백엔드 | Spring Boot 4.1.0, Spring AI 2.0.0, Java 21 (GraalVM) |
| 실시간 통신 | Spring WebSocket, Web Audio API(브라우저 VAD) |
| 실시간 정보 검색 | Tavily Search API |
| 문서 처리 | Apache PDFBox, Tesseract OCR(Tess4J) |
| 이미지 생성 | Stable Diffusion 1.5 (AUTOMATIC1111 WebUI, Docker, GPU) |
| 음성 인식 | faster-whisper (CTranslate2 기반) |
| 음성 합성 | MeloTTS (한국어 지원) |
| 음성 서비스 API | FastAPI + Uvicorn (Python) |
| 평가 | RAGAS + LangChain(Ollama 연동) — *현재 후순위* |
| 배포 | Docker Compose (GPU 가속), Kubernetes/Minikube (CPU 데모) |
| 개발 환경 | IntelliJ IDEA(+ devtools 라이브 리로드), Windows 11 / PowerShell, Docker Desktop(WSL2), NVIDIA RTX 3060 (VRAM 6GB) |

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
- 할루시네이션 억제 프롬프트 적용: 문서 근거만 사용, 근거 없으면 명확히 밝히기, 답변 끝에 근거 한 줄 요약

### 2. 문서 업로드 & 청킹 & OCR

```
POST /api/documents/upload      (OpenSearch, PDF/TXT)
POST /api/pgvector/ingest       (pgvector, PDF/TXT)
```

- 문장 경계를 인식하는 청킹(소수점 "20.79%"를 문장 끝으로 오인하지 않도록 정규식 lookahead 처리)
- 목차의 점선 줄("....................") 등 정보 없는 청크는 인제스트 단계에서 자동 필터링
- PDFBox가 복잡한 표 레이아웃에서 텍스트 추출에 실패하는 페이지는 Tesseract OCR로 자동 보완 (`PdfTextExtractionService`)
- 웹 UI에서 폴더 단위 업로드도 지원 (PDF/TXT만 자동 필터링, 처리 결과 요약 표시)

### 3. 이미지 생성 (Stable Diffusion)

웹 UI에서 한글로 프롬프트를 입력하면:
1. 백엔드가 `qwen3:4b`로 한글→영어 번역 (`POST /api/image/translate-prompt`)
2. 번역된 프롬프트를 브라우저가 Stable Diffusion API(`:7860/sdapi/v1/txt2img`)에 직접 호출 (CORS 허용 설정됨)
3. 결과 이미지와 번역된 프롬프트를 함께 표시

### 4. 음성 인식/합성 (파일 기반)

```
POST /api/voice/stt              (multipart, m4a/mp3/wav/ogg/flac 등)
POST /api/voice/tts              (text 파라미터 또는 PDF/TXT 파일 업로드)
POST /api/voice/extract-text     (PDF/TXT에서 텍스트만 추출해 미리보기)
```

- 어떤 포맷이든 서버가 ffmpeg로 16kHz mono WAV로 통일 변환 후 STT 수행
- TTS 대상 파일 업로드 시 텍스트를 먼저 추출해 화면에 보여주고, 사용자가 확인/수정 후 생성 가능
- 특수문자(`=` 등 한국어 g2p가 처리 못 하는 기호)는 자동으로 정리(sanitize) 후 합성
- MeloTTS가 내부적으로 문장 단위 분할·합성·이어붙이기를 수행하므로 긴 텍스트도 그대로 처리 가능

### 5. 실시간 음성 대화 (WebSocket 스트리밍)

```
WS /ws/voice
```

브라우저 ↔ 서버가 지속 연결을 유지하며 다음 순서로 동작합니다.

1. **오디오 스트리밍**: `MediaRecorder`로 250ms 단위 오디오 청크를 서버에 실시간 전송
2. **무음 감지(VAD)**: 브라우저에서 `Web Audio API`(AnalyserNode)로 실시간 음량을 측정, 일정 시간(800ms) 조용해지면 "발화 종료" 신호 전송
3. **STT**: 서버가 그동안 모은 오디오를 faster-whisper로 전사
4. **검색 필요 여부 판단**: 짧은 LLM 호출로 "SEARCH: 검색어" 또는 "NONE"만 출력하도록 강제해 실시간 정보 필요 여부 판별
5. **웹 검색 보강** (필요시): Tavily API로 검색, 결과를 컨텍스트로 삼아 프롬프트 구성
6. **LLM 스트리밍 답변**: Ollama의 스트리밍 응답을 토큰 단위로 받으며, 문장 부호(`. ! ?`)가 나올 때마다 그 문장만 즉시 클라이언트로 전송 — 전체 답변을 기다리지 않고 문장 단위로 화면에 표시됨

> **진행 상황**: 텍스트 문장 단위 스트리밍까지 구현 완료. 문장별 TTS 음성 합성 및 브라우저 순차 재생(완전한 음성 대화 경험)은 다음 단계로 진행 중입니다.

### 6. Python + LangChain 독립 구현

```powershell
python langchain_rag_demo.py "질문 내용"
```
Spring Boot를 거치지 않고 Python에서 `psycopg2`로 pgvector에 직접 SQL(코사인 거리 연산자)을 날려 검색하고, LangChain의 LCEL(`prompt | llm | StrOutputParser`)로 생성까지 재현합니다.

## 주요 기술적 의사결정 및 트러블슈팅

가장 자주 겪었거나 중요한 이슈만 요약했습니다. 전체 목록과 상세 해결 과정은 [SETUP.md](./SETUP.md)의 트러블슈팅 섹션을 참고하세요.

| 이슈 | 원인 | 해결 |
|---|---|---|
| OpenSearch KNN 검색 결과 부정확 | 인덱스가 기본값(L2 거리)으로 생성됨 | 인덱스 매핑에 cosine similarity 명시적 설정 |
| pgvector 검색 결과가 항상 비어있음 | OpenSearch(0.55)와 동일 임계값을 적용했으나 pgvector 점수 스케일이 다름(실측 0.25~0.36) | 스토어별 독립 임계값 적용 |
| 표 데이터가 검색되지 않음 | PDFBox가 복잡한 표 레이아웃 텍스트 추출 실패 | Tesseract OCR로 페이지별 보완 추출 |
| 표에서 잘못된 소계를 답변으로 채택 | 표 구조가 OCR 텍스트에서 평면화되어 어느 숫자가 최종 합계인지 모호함 | 알려진 한계로 문서화, 원본 PDF 직접 대조로 검증하는 습관화 |
| Stable Diffusion Docker 빌드 실패 | `Stability-AI/stablediffusion` 원본 저장소 접근 불가(삭제/비공개 전환) | 커뮤니티 포크 저장소로 Dockerfile 수정 |
| 웹 UI에서 이미지 생성 시 CORS 에러 | 브라우저가 다른 포트(다른 origin)로 직접 요청 | `--cors-allow-origins` 옵션 추가 |
| TTS 특정 텍스트에서 `KeyError: '='` | 뉴스 기사 특유의 "지역=통신사" 표기를 한국어 g2p가 처리 못 함 | TTS 전 텍스트에서 지원 안 되는 특수문자를 정리(sanitize) |
| WebSocket 컴파일 시 `release version 21 not supported` | IntelliJ가 Maven 설정과 별개로 자체 SDK로 빌드 | "Maven에 IDE 빌드/실행 작업 위임" 체크 |
| `index.html` 수정이 재시작 없인 반영 안 됨 | 정적 리소스가 빌드 시점에만 복사되고 브라우저도 캐싱 | `spring-boot-devtools` 추가로 라이브 리로드 활성화 |
| 실시간 대화가 계속 영어로 응답 | 프롬프트에 언어 지시 없이 질문을 그대로 LLM에 전달 | "한국어로만 답하라"는 지시문을 프롬프트에 명시적으로 포함 |

## 로드맵

- [x] 듀얼 벡터스토어 RAG 파이프라인
- [x] OCR 기반 표 데이터 보완
- [x] 이미지 생성 (Stable Diffusion + 한글 번역 체이닝)
- [x] STT/TTS 파이프라인 (파일 기반, 기본 동작 검증)
- [x] 프롬프트 개선 (할루시네이션 억제 규칙 적용, 정성적 검증 완료)
- [ ] **실시간 STT-LLM-TTS 스트리밍 파이프라인 마무리** — WebSocket/VAD/LLM 스트리밍/문장분리/웹검색까지 완료, 문장별 TTS 음성 합성 및 브라우저 순차 재생 구현 남음
- [ ] QLoRA 기반 로컬 파인튜닝 (8GB VRAM, unsloth)
- [ ] 클라우드(AWS/GCP) 배포
- [ ] RAGAS 평가 결과 기반 OpenSearch vs pgvector 정량 비교 리포트 완성 *(로컬 LLM의 구조화 출력 불안정성으로 후순위 조정)*
