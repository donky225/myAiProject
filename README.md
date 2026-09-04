# Local Multimodal AI Platform

로컬 환경(Ollama + Docker)에서 동작하는 종합 AI 파이프라인 포트폴리오 프로젝트입니다. 텍스트 RAG, 이미지 생성, 실시간 음성 대화까지 하나의 웹 UI에서 다루며, 비동기 처리(Kafka)와 캐싱(Redis)까지 갖춘 실무형 아키텍처로 확장했습니다. 각 기능을 실제 프로덕션에서 마주치는 문제를 직접 디버깅하며 구축했습니다.

## 목차

- [핵심 기능](#핵심-기능)
- [아키텍처](#아키텍처)
- [기술 스택](#기술-스택)
- [빠른 시작](#빠른-시작)
- [상세 기능 설명](#상세-기능-설명)
- [클라우드 배포](#클라우드-배포)
- [주요 기술적 의사결정 및 트러블슈팅](#주요-기술적-의사결정-및-트러블슈팅)
- [로드맵](#로드맵)

## 핵심 기능

1. **듀얼 벡터스토어 RAG** — OpenSearch(KNN)와 PostgreSQL/pgvector(HNSW) 두 가지로 동일한 RAG 파이프라인을 구현
2. **문서 업로드 & OCR 보완** — 문장 경계 인식 청킹, PDFBox가 놓치는 표 데이터는 Tesseract OCR로 자동 보완
3. **비동기 문서 인제스트 (Kafka)** — 업로드 API는 즉시 응답하고, OCR/청킹/임베딩은 Kafka Consumer가 백그라운드에서 처리. jobId로 진행 상황 조회 가능
4. **응답 캐싱 (Redis)** — 동일 질문/검색어 반복 시 재계산 없이 즉시 응답, 장애 시에도 캐시 없이 정상 동작하는 페일세이프 설계
5. **할루시네이션 억제 프롬프트** — 문서 근거 명시, 정보 없을 시 명확히 밝히기
6. **이미지 생성** — 로컬 Stable Diffusion(Docker)으로 텍스트→이미지 생성, 한글 프롬프트 자동 번역
7. **음성 인식/합성 (파일 기반)** — faster-whisper(STT), MeloTTS(TTS, 한국어)
8. **실시간 음성 대화 (WebSocket)** — 마이크 발화 → 무음 감지 → STT → (필요시 실시간 웹 검색) → LLM 스트리밍 → 문장 단위 TTS 파이프라이닝 → 브라우저 순차 재생까지 이어지는 완전한 저지연 대화 파이프라인
9. **실시간 웹 검색 보강** — 질문이 최신 정보를 요구하는지 LLM이 판단 후 Tavily API로 검색
10. **Python + LangChain 독립 구현**
11. **Docker Compose / Kubernetes / 클라우드(GCP) 배포 경험**
12. **QLoRA 로컬 파인튜닝 (Unsloth)** — Qwen3-4B를 RTX 3060 6GB VRAM에서 QLoRA로 파인튜닝, GGUF 변환 후 Ollama 서빙까지 연결

## 아키텍처

```
                         ┌─────────────────────┐
                         │   웹 UI (index.html) │
                         └──────────┬───────────┘
                        HTTP        │        WebSocket (실시간 음성)
              ┌─────────────────────┼─────────────────────┐
              ▼                     ▼                     ▼
    ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────┐
    │  Spring Boot :8080 │  │  /ws/voice        │  │ Tavily Search API    │
    └──┬───┬───┬───┬────┘  └──────────────────┘  └─────────────────────┘
       │   │   │   │
       │   │   │   └──────────────┐
       │   │   └──────┐           ▼
       │   ▼          ▼    ┌─────────────┐
       │ ┌────────┐ ┌────┐ │ Kafka        │
       │ │pgvector │ │Redis│ │ (비동기 인제스트)│
       │ └────────┘ └────┘ └─────────────┘
       ▼
┌──────────┐  ┌─────────────────────┐
│OpenSearch │  │  Ollama :11434       │
│  :9200    │  │  qwen3:4b (생성/스트림)│
└──────────┘  │  qwen3-embedding:0.6b│
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

**비동기 문서 인제스트 흐름 (Kafka):**
```
업로드 API → 파일 임시 저장 → Kafka에 이벤트 발행 → 즉시 202 응답(jobId)
    Consumer가 백그라운드에서: OCR → 청킹 → 임베딩 → pgvector 저장 → 상태 갱신
    클라이언트는 GET /api/documents/async/status/{jobId} 로 진행 상황 폴링
```

**실시간 음성 대화 흐름:**
```
마이크 입력 → 브라우저 Web Audio API로 음량 측정(VAD) → 무음 감지 시 캡처 중단 + 전사 요청
    → faster-whisper 전사 → (검색 필요 시 Tavily 검색) → qwen3:4b 스트리밍 생성
    → 문장 부호마다 텍스트 즉시 전송 + 해당 문장 TTS를 백그라운드(세션별 순서 보장)로 합성
    → 브라우저가 도착 순서대로 오디오 큐에 쌓아 자동 순차 재생
    → 답변 완료 시 연결 종료, 다음 질문은 마이크 버튼 재클릭으로 새로 시작
```

## 기술 스택

| 영역 | 기술 |
|---|---|
| LLM / 임베딩 | Ollama (`qwen3:4b`, `qwen3-embedding:0.6b`) |
| 벡터 스토어 | OpenSearch 3 (KNN, cosine) / PostgreSQL 16 + pgvector (HNSW, cosine) |
| 백엔드 | Spring Boot 4.1.0, Spring AI 2.0.0, Java 21 (GraalVM) |
| 비동기 메시징 | Apache Kafka (공식 이미지, KRaft 모드) |
| 캐싱 | Redis 7 |
| 실시간 통신 | Spring WebSocket, Web Audio API(브라우저 VAD) |
| 실시간 정보 검색 | Tavily Search API |
| 문서 처리 | Apache PDFBox, Tesseract OCR(Tess4J) |
| 이미지 생성 | Stable Diffusion 1.5 (AUTOMATIC1111 WebUI, Docker, GPU) |
| 음성 인식/합성 | faster-whisper, MeloTTS (한국어) + FastAPI |
| 평가 | RAGAS + LangChain(Ollama 연동), 로컬 완결 평가 |
| 파인튜닝 | Unsloth (QLoRA, 4bit), WSL2 Ubuntu + Miniconda(Python 3.11) 격리 환경 |
| 배포 | Docker Compose (로컬 GPU), Kubernetes/Minikube (CPU 데모), GCP Compute Engine (클라우드 경량 데모) |
| 개발 환경 | IntelliJ IDEA(+ devtools), Windows 11, Docker Desktop(WSL2), NVIDIA RTX 3060 (VRAM 6GB) |

## 빠른 시작

전체 설치는 [SETUP.md](./SETUP.md) 참고. 설치 후 매번 켤 때:

```powershell
cd D:\MyAiProject
.\start-all.ps1
```

Docker 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis), Stable Diffusion, 음성 서버를 각각 새 창에서 기동합니다. 이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**하면 `http://localhost:8080`에서 전체 기능을 사용할 수 있습니다.

종료: `.\stop-all.ps1`

## 상세 기능 설명

### 1. 듀얼 벡터스토어 RAG
```
GET /api/rag/ask?question={질문}&store={opensearch|pgvector}
```
관련성 임계값은 두 스토어의 점수 스케일이 달라 별도 관리 (OpenSearch 0.55, pgvector 0.25). 동일 질문 반복 시 Redis 캐시(TTL 5분)로 즉시 응답.

### 2. 비동기 문서 인제스트 (Kafka)
```
POST /api/documents/async/upload   (multipart: file, store)  → {jobId, status: "QUEUED"}
GET  /api/documents/async/status/{jobId}                     → {status, message, chunksIngested}
```
대용량 PDF(OCR 포함 처리에 수 분 소요)도 업로드 즉시 응답을 받고, 진행 상황을 폴링으로 확인할 수 있습니다. 기존 동기 업로드 엔드포인트(`/api/pgvector/ingest` 등)도 그대로 유지되어 웹 UI는 기존 방식으로 계속 동작합니다.

### 3. 실시간 음성 대화 (WebSocket)
```
WS /ws/voice
```
1. `MediaRecorder`로 250ms 단위 오디오 스트리밍
2. 브라우저 `Web Audio API`로 실시간 음량 측정 → 무음 800ms 이상 지속 시 캡처 중단 + 서버에 전사 요청
3. 서버가 faster-whisper로 전사 → 검색 필요 여부를 짧은 LLM 호출로 판단 → 필요 시 Tavily 검색
4. `qwen3:4b` 스트리밍 생성, 문장 부호마다 텍스트 즉시 전송
5. 각 문장의 TTS 합성은 세션 전용 단일 스레드 실행기에서 순서를 지키며 백그라운드 처리 → 완성되는 대로 오디오 전송
6. 브라우저는 도착 순서대로 재생 큐에 쌓아 자동 이어재생
7. 답변 완료 시 연결 정리, 다음 질문은 마이크 버튼 재클릭

### 4. 캐싱 (Redis)
- Tavily 검색 결과: 검색어 기준 30분 TTL (무료 API 할당량 절약)
- RAG 답변: (store+질문) 기준 5분 TTL
- Redis 장애 시에도 캐시만 건너뛰고 앱은 정상 동작 (페일세이프 설계)

### 5. 이미지 생성 / 파일 기반 STT·TTS
README 상단 아키텍처 참고. 한글 프롬프트는 `qwen3:4b`가 자동 번역 후 Stable Diffusion API 호출.

### 6. RAG 정량 평가 (RAGAS: OpenSearch vs pgvector)

동일 질문 세트에 대해 두 벡터스토어의 답변 품질과 응답 속도를 RAGAS로 정량 비교했습니다.

- **평가 방식**: OpenAI API 대신 로컬 Ollama(`qwen3:4b` 채점, `qwen3-embedding:0.6b` 임베딩)로 완전 오프라인 평가
- **사용 지표**: `answer_relevancy`만 채택. `faithfulness`는 다단계 체인 호출에서 엄격한 JSON 출력을 요구하는데, 로컬 소형 모델(4b)로는 안정적으로 만족시키기 어려워 제외
- **표본**: 총 19건(OpenSearch 10건 / pgvector 9건), 문서와 무관한 질문(날씨, 코드 요청 등 컨텍스트가 비는 경우)은 평가에서 제외

| store | answer_relevancy | 평균 응답시간 |
|---|---|---|
| OpenSearch | 0.568 | 32.6초 |
| pgvector | 0.504 | 30.4초 |

**해석**: 관련성은 OpenSearch가 근소 우위, 응답 속도는 pgvector가 근소 우위로 전형적인 trade-off 패턴을 보였습니다. 표본 수(19건)가 적어 두 스토어 간 차이를 통계적으로 단정하기보다는 경향성 확인 수준으로 해석하는 것이 적절합니다.

### 7. QLoRA 로컬 파인튜닝 (Unsloth)

RTX 3060 6GB VRAM 환경에서 Unsloth로 `Qwen3-4B`를 QLoRA(4bit)로 파인튜닝하고, GGUF로 변환해 기존 Ollama 서빙 파이프라인에 그대로 연결했습니다. 도메인 특화가 아닌 **QLoRA 기법 자체의 시연**이 목적이라, 공개 데이터셋을 사용했습니다.

**환경 및 설정**
- **환경**: WSL2 Ubuntu(리눅스 네이티브가 Windows보다 CUDA 툴체인/Triton 호환성이 안정적) + Miniconda(Python 3.11 격리 환경)
- **베이스 모델**: `unsloth/Qwen3-4B-unsloth-bnb-4bit`
- **데이터셋**: `yahma/alpaca-cleaned` (공개 instruction 데이터셋)
- **LoRA 설정**: r=16, target_modules 전체 attention/MLP projection, `gradient_checkpointing="unsloth"`, `optim="adamw_8bit"`
- **학습 포맷**: `tokenizer.apply_chat_template()`로 Qwen3 고유 ChatML 포맷(`<|im_start|>...<|im_end|>`) 사용, `enable_thinking=False`로 학습해 불필요한 reasoning 제거

**리소스 사용량 (실측)**

| 항목 | 값 |
|---|---|
| VRAM 사용량 (최대) | 4.06~4.10 GB / 6.0 GB |
| 학습 스텝 | 200 step (batch 2 × grad_accum 4 = 실질 배치 8) |
| GGUF 최종 크기 | 2.5 GB (Q4_K_M 양자화) |

**Before/After 비교 (베이스 `qwen3:4b` vs 파인튜닝 모델, 동일 질문)**

같은 질문 3개를 두 모델에 동일하게 던져 비교한 결과:

| 관찰 항목 | 베이스 모델 | 파인튜닝 모델 |
|---|---|---|
| thinking 과정 | 길고 전부 영어로 노출됨 | 학습 시 비활성화되어 `<think></think>` 비어있음 → 응답 속도 체감 향상 |
| 리스트형 질문 형식 준수 | 정확히 간결한 리스트 유지 | Alpaca 데이터셋의 서술형 문체가 학습되어 항목마다 장황한 설명 추가 |
| 사실 기반 실용성 (이메일 예시 등) | 구체적 예시 문구까지 제공 | 일반론 위주, 간헐적 언어 혼입(한자 등) 관찰됨 |

**결론**: 200 step, 소량 데이터의 QLoRA는 응답 스타일(thinking 제거, 문체)에는 뚜렷한 영향을 줬지만, 콘텐츠 품질 면에서 일관된 개선을 보이지는 않았고 오히려 일부 케이스에서 언어 혼입 같은 부작용도 관찰되었습니다. 이는 실제 프로덕션 품질 향상보다는 **QLoRA 파이프라인 자체(학습→GGUF 변환→Ollama 서빙)를 실증하는 데 목적을 둔 결과**로, 정직한 한계로 문서화했습니다.

## 클라우드 배포

GCP Compute Engine 무료 체험($300 크레딧, 90일)에 경량화된 버전을 배포했습니다.

- **경량화 내용**: OpenSearch 제외(pgvector만 사용), Ollama GPU 예약 제거(CPU 추론)
- **제외된 기능**: Stable Diffusion(GPU 필수), 음성 파이프라인(리소스/복잡도)
- **핵심 이슈**: OpenSearch가 없는 환경에서 `OpenSearchIndexInitializer`의 `@PostConstruct`가 예외를 던져 앱 전체가 기동 실패하던 버그를 발견, 예외를 흡수하도록 수정하여 해결 (자세한 내용은 SETUP.md 참고)

배포 절차는 [SETUP.md의 클라우드 배포 섹션](./SETUP.md)을 참고하세요.

## 주요 기술적 의사결정 및 트러블슈팅

전체 목록은 [SETUP.md](./SETUP.md)를 참고하세요. 최근 주요 이슈만 요약합니다.

| 이슈 | 원인 | 해결 |
|---|---|---|
| GCP 서버에서 앱이 기동 실패 | OpenSearch 없는 환경에서 인덱스 초기화 로직이 예외를 던져 컨텍스트 전체가 죽음 | `@PostConstruct` 로직을 try-catch로 감싸 연결 실패를 경고 로그로 흡수 |
| `bitnami/kafka:3.7` 이미지를 찾을 수 없음 | Bitnami의 무료 태그 정책 변경으로 구버전 태그 삭제 | 공식 `apache/kafka:latest` 이미지로 전환 |
| Kafka `KafkaTemplate` 빈을 찾을 수 없음 | Boot가 자동 생성하는 템플릿이 와일드카드 타입이라 구체 제네릭 타입과 불일치 | `ProducerFactory`/`KafkaTemplate`을 정확한 타입으로 명시적 등록 |
| Kafka Consumer Group이 생성되지 않음(`GroupIdNotFoundException`) | `@KafkaListener`는 인식되었으나 리스너 컨테이너가 기동되지 않음 | 설정 클래스에 `@EnableKafka` 추가 |
| 실시간 대화가 계속 영어로 응답 | 프롬프트에 언어 지시 없이 질문을 그대로 전달 | "한국어로만 답하라"는 지시문 명시 |
| 마이크 발화 중 요청이 여러 번 겹침 | VAD가 반복 트리거되며 이전 답변과 새 요청이 뒤섞임 | 발화 종료 즉시 캡처만 중단(소켓 유지) → 답변 완료 후 완전 종료하는 "한 번에 한 질문" 흐름으로 변경 |
| Oracle Cloud 가입 반복 실패 | VPN/카드/전화번호 등 복합적 원인으로 추정되는 사기 방지 로직 | GCP 무료 체험으로 전환 |
| RAGAS 평가가 항목마다 정확히 timeout(600s)에 걸려 전부 실패 | Windows 기본 `ProactorEventLoop`가 `langchain_ollama`의 비동기 HTTP 클라이언트와 충돌해 요청이 응답 후에도 콜백 없이 무한 대기 — 개별 Ollama REST 호출은 정상이라 원인 특정이 까다로웠음 | 스크립트 시작 시 `asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())`로 `SelectorEventLoop` 강제 전환 |
| QLoRA 학습 중 Triton 커널 컴파일 실패 (`Failed to find C compiler`) | WSL2 Ubuntu에 `build-essential`(gcc)이 설치되지 않아 Triton이 런타임 커널을 컴파일 못함 | `sudo apt install build-essential -y`로 gcc 설치 |
| Windows에서 Python 3.11 확보 실패 (`add-apt-repository ppa:deadsnakes` 무반응) | 사용 중인 Ubuntu 배포판(최신 버전)에 대해 deadsnakes PPA가 아직 패키지를 제공하지 않음 | apt/PPA 대신 Miniconda로 격리된 Python 3.11 conda 환경 구성 |
| `conda create` 시 `CondaToSNonInteractiveError` | 최근 Anaconda 정책 변경으로 `pkgs/main`, `pkgs/r` 채널의 ToS(이용약관) 동의가 선행되어야 함 | `conda tos accept --override-channels --channel <채널 URL>`로 두 채널 모두 동의 후 재시도 |
| `qwen3-4b-qlora-demo` 첫 GGUF 모델이 추론 시 같은 문구를 무한 반복 | ① Unsloth 자동 생성 Modelfile의 `repeat_penalty`가 `1`(반복 억제 없음)로 설정됨 ② 학습 시 사용한 Alpaca 원본 포맷(`### 지시사항:`)이 Ollama Modelfile의 실제 서빙 템플릿(Qwen3 ChatML, `<\|im_start\|>...<\|im_end\|>`)과 불일치해 종료 신호가 어긋남 | `repeat_penalty`를 `1.15`로 조정 + 학습 데이터 포맷을 `tokenizer.apply_chat_template()`로 Qwen3 고유 ChatML과 일치시켜 재학습 |
| Ollama Modelfile에 `PARAMETER think false` 추가 시 `Error: unknown parameter 'think'` | thinking 비활성화는 Modelfile의 `PARAMETER`로 지원되지 않고, CLI 플래그(`--think=false`, `--hidethinking`) 또는 API의 `think` 필드로만 제어 가능 | Modelfile에서 해당 줄 제거, 대신 `ollama run` 실행 시 `--think=false`/`--hidethinking` 플래그 사용 |
| PowerShell에서 메모장으로 만든 Modelfile을 `ollama create`가 못 찾음 (`no Modelfile or safetensors files found`) | 메모장이 저장 시 자동으로 `.txt` 확장자를 붙여 실제 파일명이 의도와 다름 | `dir 파일명*`으로 실제 저장된 이름 확인 후 `Rename-Item`으로 수정, 또는 PowerShell의 `Out-File`로 직접 생성해 확장자 문제 회피 |
| PowerShell 콘솔에 한글 입력/출력이 깨져 보임 | 콘솔 코드페이지가 UTF-8이 아닌 상태 (모델 자체 응답은 정상, 화면 표시만 깨짐) | `chcp 65001` 및 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` 설정 |
| 한국어 시스템 프롬프트를 줘도 `<think>` 블록만 영어로 생성 | Qwen3 계열 모델은 SYSTEM 프롬프트가 최종 출력 언어엔 적용되지만 내부 thinking 채널까지는 강제하지 못함 | thinking 자체를 `--think=false`로 비활성화하거나 `--hidethinking`으로 화면 노출만 차단 |

## 로드맵

- [x] 듀얼 벡터스토어 RAG 파이프라인
- [x] OCR 기반 표 데이터 보완
- [x] 이미지 생성 (Stable Diffusion + 한글 번역 체이닝)
- [x] STT/TTS 파이프라인 (파일 기반)
- [x] 프롬프트 개선 (할루시네이션 억제)
- [x] **실시간 STT-LLM-TTS 스트리밍 파이프라인** — 문장 단위 TTS 파이프라이닝, 재생 큐, 웹검색 보강까지 완성
- [x] **Kafka 비동기 인제스트 파이프라인**
- [x] **Redis 캐싱 (검색/답변)**
- [x] **RAGAS 평가 결과 기반 OpenSearch vs pgvector 정량 비교** — `answer_relevancy` 지표로 19건 평가 완료 (결과: [상세 기능 설명 6번](#6-rag-정량-평가-ragas-opensearch-vs-pgvector))
- [x] **QLoRA 기반 로컬 파인튜닝 (unsloth)** — Qwen3-4B, RTX 3060 6GB VRAM에서 완료 (결과: [상세 기능 설명 7번](#7-qlora-로컬-파인튜닝-unsloth))
- [ ] **클라우드(GCP) 배포 최종 마무리** — 서버 재기동 확인 및 정식 코드 동기화 남음

