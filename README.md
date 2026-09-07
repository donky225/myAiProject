# Local Multimodal AI Platform

본 프로젝트는 울산 한국동서발전 생성형 AI/LLM 구축 프로젝트에 참여하며 습득한 엔터프라이즈 LLM 아키텍처와 개발 경험을 기반으로, 상용 솔루션에 의존하지 않고 오픈소스 기술로 동일한 핵심 기능과 아키텍처를 직접 재설계하여 구축한 개인 기술 검증 프로젝트입니다.

실제 기업 환경에서 요구되는 문서 기반 RAG, 벡터 검색, 임베딩, 리랭킹, 비동기 데이터 처리, LLM 추론, STT/TTS, 모델 평가 및 운영 환경 등을 오픈소스 기술로 구성하고, 각 컴포넌트 간 연계 구조를 직접 설계·구현하면서 엔터프라이즈 AI 시스템 구축에 필요한 기술을 검증하는 것을 목적으로 했습니다.

로컬 환경(Ollama + Docker)에서 동작하는 종합 AI 파이프라인 , 텍스트 RAG, 이미지 생성, 실시간 음성 대화까지 하나의 웹 UI에서 다루며, 비동기 처리(Kafka)와 캐싱(Redis), 리랭킹, MCP(Model Context Protocol) 서버까지 갖춘 실무형 아키텍처로 확장했습니다. 각 기능을 실제 프로덕션에서 마주치는 문제를 직접 디버깅하며 구축했습니다.

## 목차

- [핵심 기능](#핵심-기능)
- [아키텍처](#아키텍처)
- [기술 스택](#기술-스택)
- [기술별 역할과 연결 구조](#기술별-역할과-연결-구조)
- [빠른 시작](#빠른-시작)
- [상세 기능 설명 및 구현 결과](#상세-기능-설명-및-구현-결과)
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
13. **리랭킹 (Cross-Encoder Reranking)** — 벡터검색 top-10 후보를 BAAI/bge-reranker-v2-m3로 재정렬해 관련성 향상, 별도 FastAPI 마이크로서비스로 분리
14. **MCP(Model Context Protocol) 서버** — Spring AI 2.0 `@McpTool`로 기존 RAG 파이프라인을 MCP 표준 도구로 노출, Claude Desktop 등 MCP 클라이언트가 직접 호출 가능

## 아키텍처

```
                         ┌──────────────────────┐                    ┌───────────────────────┐
                         │   웹 UI (index.html) │                    │ MCP 클라이언트         │
                         └──────────┬───────────┘                    │ (Claude Desktop 등)   │
                        HTTP        │        WebSocket (실시간 음성)  └──────────┬────────────┘
              ┌─────────────────────┼─────────────────────┐              │ SSE
              ▼                     ▼                     ▼              ▼
    ┌──────────────────────────────────────────────────────────────────────────┐
    │                         Spring Boot :8080                                │
    │  REST API (/api/rag/ask 등)          MCP 서버 (@McpTool, /sse)            │
    └──┬───┬───┬───┬───────────────────────────────────────────────────────────┘
       │   │   │   │
       │   │   │   └──────────────┐
       │   │   └──────┐           ▼
       │   ▼          ▼     ┌───────────────────┐
       │ ┌────────┐ ┌─────┐ │ Kafka             │
       │ │pgvector│ │Redis│ │ (비동기 인제스트)  │
       │ └────────┘ └─────┘ └───────────────────┘
       ▼
┌───────────┐  ┌───────────────────────┐  ┌─────────────────────────┐
│OpenSearch │  │  Ollama :11434        │  │ Rerank Service :8002    │
│  :9200    │  │  qwen3:4b (생성/스트림)│  │ (FastAPI, bge-reranker) │
└───────────┘  │  qwen3-embedding:0.6b │  └─────────────────────────┘
               └───────────────────────┘
              브라우저에서 직접 호출 (CORS 허용)
              ┌──────────────────────────┐
              ▼                          ▼
    ┌─────────────────────┐      ┌───────────────────────┐
    │ Stable Diffusion    │      │ Voice Service :8001   │
    │ WebUI :7860 (Docker)│      │ (FastAPI, faster-     │
    │ AUTOMATIC1111       │      │  whisper + MeloTTS)   │
    └─────────────────────┘      └───────────────────────┘
```

**RAG 검색 흐름 (리랭킹 포함):**
```
질문 → 임베딩 → 벡터검색(OpenSearch/pgvector, top-10 후보)
    → Rerank Service(BAAI/bge-reranker-v2-m3)로 재정렬 → 관련성 상위 3건
    → (리랭크 서비스 장애 시 코사인 유사도 순으로 자동 폴백)
    → qwen3:4b 프롬프트에 컨텍스트로 주입 → 답변 생성
```

**MCP 도구 호출 흐름:**
```
MCP 클라이언트 → SSE 연결(/sse) → search_company_documents 도구 호출
    → RagService/PgVectorRagService.askWithContext() (리랭킹 포함, REST API와 동일 로직 재사용)
    → 답변 + 근거 문서를 MCP 표준 응답 형식으로 반환
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
| 리랭킹 | BAAI/bge-reranker-v2-m3 (cross-encoder), FastAPI 마이크로서비스 (별도 포트 8002) |
| 에이전틱 프로토콜 | MCP(Model Context Protocol), Spring AI 2.0 `@McpTool`, MCP Inspector로 검증 |
| 배포 | Docker Compose (로컬 GPU), Kubernetes/Minikube (CPU 데모), GCP Compute Engine (클라우드 경량 데모) |
| 개발 환경 | IntelliJ IDEA(+ devtools), Windows 11, Docker Desktop(WSL2), NVIDIA RTX 3060 (VRAM 6GB) |


## 기술별 역할과 연결 구조

이 프로젝트는 단순히 여러 AI 기술을 나열한 프로젝트가 아니라, **각 기술이 담당하는 문제를 해결하고 서로 연결하여 하나의 AI 서비스로 동작하도록 구성한 로컬 AI 플랫폼**입니다.

각 기술은 다음과 같은 역할을 담당합니다.

```text
문서 입력
   ↓
PDFBox / Tesseract OCR
   ↓
문서 정제 및 Chunking
   ↓
Embedding
   ↓
OpenSearch / pgvector
   ↓
Vector Search
   ↓
Reranker
   ↓
가장 관련성 높은 문서 선택
   ↓
Qwen3 (Ollama)
   ↓
RAG 기반 답변
   ↓
Redis Cache
```

여기에 실제 서비스에서 발생할 수 있는 처리 지연과 확장 문제를 해결하기 위해 Kafka를 연결하고, 최신 정보가 필요한 경우 Tavily를 사용하며, 음성/이미지까지 확장할 수 있도록 STT/TTS와 Stable Diffusion을 연결했습니다.

---

### 1. Ollama + Qwen3

#### 무엇을 하는 기술인가?

**Ollama**는 로컬 컴퓨터에서 LLM을 실행할 수 있도록 해주는 실행 환경이고, **Qwen3**는 실제로 질문을 이해하고 답변을 생성하는 LLM입니다.

이 프로젝트에서는 `qwen3:4b`를 기본 생성 모델로 사용합니다.

쉽게 표현하면:

```text
Ollama = AI 모델을 실행하는 엔진
Qwen3  = 실제로 생각하고 답변하는 AI 모델
```

#### 이 프로젝트에서의 역할

- 사용자의 질문에 대한 답변 생성
- RAG에서 검색된 문서를 Context로 받아 최종 답변 생성
- 이미지 생성 시 한글 프롬프트를 Stable Diffusion에서 사용할 수 있는 형태로 변환
- 실시간 음성 대화에서 STT 결과를 받아 답변 생성
- 최신 정보가 필요한 질문인지 판단하여 Tavily 검색 여부 결정
- QLoRA로 학습한 GGUF 모델을 다시 Ollama에서 서비스

#### 장점

- 외부 LLM API 없이 로컬 환경에서 실행 가능
- 문서나 질문을 외부 AI 서버로 보내지 않아 데이터 보호에 유리
- API 호출 비용 없이 반복적인 개발/테스트 가능
- 모델을 교체하거나 직접 튜닝한 모델을 연결하기 쉬움

#### 다른 기술과 연결했을 때의 시너지

```text
Ollama + RAG
→ 내 문서를 참고해서 답변하는 AI

Ollama + Tavily
→ 최신 인터넷 정보를 참고해서 답변하는 AI

Ollama + STT/TTS
→ 음성으로 대화하는 AI

Ollama + QLoRA
→ 직접 튜닝한 로컬 AI 모델을 서비스

Ollama + Stable Diffusion
→ 텍스트와 이미지를 함께 처리하는 멀티모달 AI
```

---

### 2. Embedding + Vector Database

#### 무엇을 하는 기술인가?

Embedding은 문장이나 문서의 **의미를 숫자로 표현하는 기술**입니다.

예를 들어 다음 두 문장은 단어가 완전히 같지는 않지만 의미가 비슷합니다.

```text
"휴가를 신청하려면 어떻게 해야 하나요?"
"연차 사용 절차를 알려주세요."
```

Embedding을 사용하면 이러한 문장을 숫자 벡터로 변환하고, 벡터 간의 유사도를 계산하여 **의미가 비슷한 문서**를 찾을 수 있습니다.

#### 이 프로젝트에서의 역할

문서를 저장할 때:

```text
문서
 ↓
Chunking
 ↓
Embedding
 ↓
Vector 저장
```

질문할 때:

```text
사용자 질문
 ↓
Embedding
 ↓
Vector Search
 ↓
관련 문서 검색
```

구조로 사용합니다.

#### 장점

일반적인 단어 검색과 달리 **질문과 문서에 동일한 단어가 없어도 의미가 유사하면 검색할 수 있습니다.**

#### 다른 기술과의 시너지

```text
Embedding
    +
OpenSearch / pgvector
    ↓
의미 기반 문서 검색

의미 기반 검색
    +
Reranker
    ↓
빠른 1차 검색 + 정밀한 2차 검색
```

이 구조가 RAG의 검색 품질을 결정하는 핵심 기반이 됩니다.

---

### 3. OpenSearch + pgvector

이 프로젝트에서는 동일한 RAG 기능을 **OpenSearch와 PostgreSQL + pgvector 두 가지 방식으로 구현**했습니다.

#### OpenSearch

검색엔진을 기반으로 Vector 검색을 수행합니다.

```text
문서
 ↓
Embedding Vector
 ↓
OpenSearch KNN
 ↓
유사한 문서 검색
```

대규모 검색 시스템으로 확장하기 좋고, 검색 기능과 Vector 검색을 함께 구성할 수 있다는 장점이 있습니다.

#### PostgreSQL + pgvector

기존 PostgreSQL 데이터베이스에 Vector 기능을 추가하여 AI 검색을 수행합니다.

```text
PostgreSQL
 ├─ 일반 데이터
 ├─ 문서 정보
 ├─ 메타데이터
 └─ Vector
```

처럼 일반적인 서비스 데이터와 AI 검색 데이터를 하나의 DB 생태계에서 관리할 수 있습니다.

#### 두 가지를 구현한 이유

단순히 특정 Vector DB 하나만 사용하는 것보다 서로 다른 저장 방식을 동일한 RAG 파이프라인에서 비교할 수 있습니다.

실제 프로젝트에서는 RAGAS를 이용하여 두 저장소의 응답 품질과 응답 시간을 측정했습니다.

즉,

> **"어떤 기술이 무조건 더 좋다"가 아니라 실제 데이터와 사용 환경에 따라 적합한 저장소를 선택할 수 있도록 비교 가능한 구조를 만든 것**

이 장점입니다.

---

### 4. RAG

#### 무엇을 하는 기술인가?

RAG는 **Retrieval-Augmented Generation**의 약자로, AI가 가지고 있는 지식만 사용하는 것이 아니라 **외부 문서를 먼저 검색하고 그 결과를 참고하여 답변하도록 만드는 구조**입니다.

일반 LLM:

```text
질문 → LLM → 답변
```

RAG:

```text
질문
 ↓
관련 문서 검색
 ↓
검색 결과
 ↓
LLM
 ↓
문서 근거 기반 답변
```

#### 이 프로젝트에서의 역할

회사 문서나 PDF 등의 내용을 Vector DB에 저장한 뒤 사용자가 질문하면 관련 문서를 검색하여 Qwen3에 전달합니다.

#### 장점

- LLM이 학습하지 않은 내부 문서를 활용 가능
- 문서 기반 답변을 만들 수 있음
- 문서를 업데이트하면 모델을 다시 학습시키지 않고 지식 변경 가능
- 특정 기업/업무/서비스에 맞는 AI 검색 시스템으로 확장 가능

#### 핵심 시너지

```text
RAG
 +
Embedding
 +
Vector DB
 +
Reranker
 +
LLM
```

이 연결을 통해

**"문서를 검색할 수 있는 AI" → "검색 결과 중 가장 중요한 내용을 골라 답하는 AI"**

로 발전합니다.

---

### 5. 문서 Chunking + PDFBox + Tesseract OCR

#### 왜 문서를 잘라야 하는가?

수백 페이지의 PDF를 하나의 거대한 텍스트로 저장하면 검색 시 필요한 부분을 정확하게 찾기 어렵습니다.

따라서 문서를 의미 있는 작은 단위인 **Chunk**로 나눕니다.

```text
PDF
 ↓
텍스트 추출
 ↓
문장 단위 Chunking
 ↓
Chunk 1
Chunk 2
Chunk 3
...
```

이 프로젝트에서는 단순 문자 수 기준으로 자르는 대신 **문장 경계를 고려한 Chunking**을 적용했습니다.

#### PDFBox

일반적인 PDF 텍스트를 추출합니다.

#### Tesseract OCR

PDF 안에 텍스트가 아닌 이미지 형태로 들어있는 표나 내용을 OCR로 읽어 텍스트 추출을 보완합니다.

```text
PDF
 ├─ 일반 텍스트 → PDFBox
 └─ 이미지/표   → Tesseract OCR
```

#### 연결했을 때의 장점

```text
PDFBox + OCR
    ↓
더 많은 문서 내용 확보
    ↓
Chunking
    ↓
Embedding
    ↓
Vector DB
    ↓
RAG 검색 품질 향상
```

즉, **RAG의 검색 품질은 검색 기술만으로 결정되는 것이 아니라 처음 문서를 얼마나 잘 추출하고 나누느냐에도 영향을 받기 때문에 문서 처리 단계부터 검색 단계까지 연결하여 설계했습니다.**

---

### 6. Vector Search + Cross-Encoder Reranker

Vector Search는 빠르게 관련 문서를 찾는 데 적합하지만, 검색된 모든 문서가 질문에 정확히 필요한 것은 아닙니다.

그래서 두 단계 검색 구조를 사용합니다.

```text
사용자 질문
 ↓
Vector Search
 ↓
Top-10 후보
 ↓
Cross-Encoder Reranker
 ↓
정밀하게 관련성 평가
 ↓
Top-3
 ↓
LLM
```

#### Vector Search의 역할

**빠르게 넓은 범위에서 후보를 찾습니다.**

#### Reranker의 역할

질문과 문서를 함께 분석하여 **실제로 질문에 도움이 되는 문서인지 다시 판단합니다.**

#### 왜 두 개를 연결하는가?

Reranker만 사용하면 모든 문서를 질문과 하나씩 비교해야 하므로 비용이 커집니다.

반대로 Vector Search만 사용하면 빠르지만 미묘한 문맥 차이를 놓칠 수 있습니다.

따라서:

```text
Vector Search
= 빠른 1차 선별

Reranker
= 느리지만 정확한 2차 선별
```

방식으로 연결하면 **검색 속도와 정밀도를 동시에 고려할 수 있습니다.**

---

### 7. RAG + Redis

Redis는 자주 사용되는 데이터를 메모리에 빠르게 저장하는 캐시입니다.

AI 시스템에서는 동일한 질문이 반복되는 경우가 많기 때문에 효과적입니다.

```text
첫 번째 질문
 ↓
RAG 검색 + LLM 생성
 ↓
Redis 저장

두 번째 동일 질문
 ↓
Redis 확인
 ↓
즉시 응답
```

#### 이 프로젝트에서의 역할

- Tavily 검색 결과 캐싱
- RAG 답변 캐싱
- 반복적인 Vector 검색과 LLM 생성을 줄임

#### 장점

- 응답 속도 향상
- LLM 처리량 감소
- Vector 검색 부하 감소
- 외부 검색 API 호출량 감소

특히 프로젝트에서는 Redis 장애가 발생하더라도 **캐시 기능만 건너뛰고 본래의 RAG 기능은 계속 동작하도록 페일세이프 구조**를 적용했습니다.

즉:

```text
Redis 정상
 → 캐시 사용

Redis 장애
 → 캐시 없이 정상 처리
```

로 구성하여 캐시가 전체 서비스의 장애 지점이 되지 않도록 했습니다.

---

### 8. Kafka + 문서 인제스트

문서 업로드 후 OCR, Chunking, Embedding까지 수행하면 파일 크기와 문서 양에 따라 처리 시간이 길어질 수 있습니다.

이 작업을 사용자 요청과 동시에 처리하면 사용자는 업로드 화면에서 오랫동안 기다려야 합니다.

Kafka를 연결하면:

```text
사용자
 ↓
문서 업로드
 ↓
파일 저장
 ↓
Kafka 이벤트 발행
 ↓
즉시 jobId 응답
```

이후 백그라운드에서:

```text
Kafka Consumer
 ↓
OCR
 ↓
Chunking
 ↓
Embedding
 ↓
Vector DB 저장
 ↓
상태 업데이트
```

를 수행합니다.

#### 장점

- 사용자는 업로드 완료를 즉시 확인할 수 있음
- 시간이 오래 걸리는 작업을 백그라운드에서 처리
- 대용량 문서 처리에 유리
- 작업 상태를 jobId로 관리 가능
- 향후 Consumer를 여러 개로 확장하여 처리량을 높일 수 있음

#### 핵심 시너지

```text
Kafka
 +
OCR
 +
Chunking
 +
Embedding
 +
Vector DB
```

를 연결하여 **문서가 시스템에 들어오는 순간부터 AI가 검색할 수 있는 지식으로 변환되는 전체 인제스트 파이프라인**을 완성했습니다.

---

### 9. Spring Boot + Spring AI + Ollama

Spring Boot는 전체 AI 시스템의 중심 서버 역할을 합니다.

```text
                    Spring Boot
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
     Ollama            Redis             Kafka
       │                 │                 │
     Qwen3          Cache 처리        비동기 처리
       │
       ├──────────── OpenSearch
       ├──────────── pgvector
       ├──────────── Reranker
       ├──────────── Tavily
       └──────────── MCP
```

Spring AI를 사용하여 Java/Spring 환경에서 LLM과 MCP 등의 AI 기능을 기존 백엔드 구조와 연결했습니다.

#### 장점

기존 웹 서비스의 인증, API, 데이터 처리, 예외 처리, 비즈니스 로직과 AI 기능을 하나의 애플리케이션 구조 안에서 통합하기 쉽습니다.

즉, **AI만 따로 만든 것이 아니라 일반적인 기업용 웹 애플리케이션에 AI 기능을 결합할 수 있는 형태**로 구성했습니다.

---

### 10. Python + FastAPI + AI 모델

Java/Spring이 전체 시스템의 중심이지만 일부 AI 모델은 Python 생태계에서 사용하기 편리합니다.

따라서 모델 처리 영역을 FastAPI 서비스로 분리했습니다.

```text
Spring Boot
    │
    ├── REST → Rerank Service :8002
    │
    └── REST → Voice Service :8001
```

#### 장점

- AI 모델과 메인 애플리케이션의 결합도를 낮춤
- Python 기반 AI 모델을 Java 시스템에서 쉽게 사용
- 모델 변경 시 Spring Boot 전체를 수정할 필요가 적음
- AI 기능을 독립적인 서비스로 확장 가능

이는 **Java의 서비스 개발 장점과 Python의 AI 생태계를 함께 사용하는 구조**입니다.

---

### 11. STT + LLM + TTS

음성 AI는 세 가지 핵심 기술을 연결합니다.

```text
사람의 음성
 ↓
STT
 ↓
텍스트
 ↓
LLM
 ↓
답변 텍스트
 ↓
TTS
 ↓
사람이 들을 수 있는 음성
```

프로젝트에서는:

- `faster-whisper` → STT
- `Qwen3` → 답변 생성
- `MeloTTS` → TTS

를 사용합니다.

#### 장점

텍스트 기반 AI를 **음성 기반 AI 서비스로 확장**할 수 있습니다.

여기에 WebSocket과 VAD를 연결하면:

```text
마이크
 ↓
실시간 음량 측정
 ↓
말이 끝났는지 판단
 ↓
STT
 ↓
LLM 스트리밍
 ↓
문장 단위 TTS
 ↓
음성 재생
```

으로 이어지는 실시간 대화 시스템이 됩니다.

---

### 12. WebSocket + VAD + Streaming TTS

일반 HTTP는 요청과 응답을 주고받는 방식이지만 실시간 음성 대화에서는 지속적으로 데이터를 주고받는 구조가 필요합니다.

그래서 WebSocket을 사용합니다.

VAD는 사용자가 말을 끝냈는지 판단하고, LLM Streaming은 답변이 완성될 때까지 기다리지 않고 생성되는 내용을 순차적으로 전달합니다.

프로젝트에서는 문장이 완성될 때마다 TTS를 백그라운드에서 처리하고 브라우저가 오디오를 순서대로 재생합니다.

```text
음성 입력
 ↓
VAD
 ↓
STT
 ↓
LLM Streaming
 ↓
문장 1 ─→ TTS ─→ 재생
문장 2 ─→ TTS ─→ 재생
문장 3 ─→ TTS ─→ 재생
```

#### 장점

전체 답변이 완성될 때까지 기다렸다가 한 번에 음성으로 만드는 것보다 **사용자가 첫 문장을 더 빨리 들을 수 있어 체감 응답속도를 줄일 수 있습니다.**

---

### 13. Ollama + Tavily

로컬 LLM은 내부 지식을 활용하는 데는 좋지만 현재 시점의 정보가 필요한 질문에는 한계가 있습니다.

그래서 질문에 따라 외부 검색을 선택적으로 사용합니다.

```text
사용자 질문
 ↓
Qwen3
 ↓
최신 정보 필요?
 ├─ 아니오 → RAG / LLM
 └─ 예
      ↓
    Tavily
      ↓
  최신 검색 결과
      ↓
    Qwen3
      ↓
    답변
```

#### 장점

**로컬 LLM의 개인정보 보호 장점은 유지하면서 필요한 경우에만 최신 웹 정보를 보강**할 수 있습니다.

또한 Redis와 함께 사용하면 Tavily 검색 결과를 캐싱하여 동일한 검색을 반복하지 않도록 할 수 있습니다.

```text
Tavily
 +
Redis
 ↓
검색 API 호출 감소
```

---

### 14. Stable Diffusion + Ollama

Stable Diffusion은 텍스트를 이미지로 만드는 생성 모델입니다.

이 프로젝트에서는 Qwen3가 한글 입력을 Stable Diffusion에서 사용할 수 있는 프롬프트 형태로 변환한 뒤 이미지 생성을 요청합니다.

```text
사용자
 ↓
한글 이미지 요청
 ↓
Qwen3
 ↓
프롬프트 변환
 ↓
Stable Diffusion
 ↓
이미지
```

#### 장점

LLM의 텍스트 생성 기능에 이미지 생성 기능을 추가하여 **하나의 웹 UI에서 텍스트와 이미지 생성 기능을 함께 제공**할 수 있습니다.

---

### 15. QLoRA + Unsloth + Ollama

QLoRA는 기존 LLM을 적은 GPU 자원으로 추가 학습할 수 있도록 하는 방식이고, Unsloth는 이러한 로컬 파인튜닝을 효율적으로 수행하기 위한 도구입니다.

프로젝트에서는 RTX 3060 6GB 환경에서 Qwen3-4B를 QLoRA 방식으로 학습하고 GGUF로 변환한 뒤 Ollama에서 다시 실행했습니다.

```text
Qwen3-4B
 ↓
Unsloth + QLoRA
 ↓
학습
 ↓
GGUF 변환
 ↓
Ollama
 ↓
서비스
```

#### 장점

단순히 기존 모델을 사용하는 것에서 끝나지 않고:

**모델 선택 → 추가 학습 → 모델 변환 → 실제 서비스 적용**

까지 하나의 흐름으로 연결할 수 있습니다.

이번 실험에서는 콘텐츠 품질이 일관되게 향상되지는 않았지만, 로컬 GPU 환경에서 QLoRA 학습부터 Ollama 서빙까지의 전체 파이프라인을 실제로 검증했다는 점에 의미가 있습니다.

---

### 16. RAGAS + RAG

AI 시스템은 단순히 "답변이 나왔다"만으로 품질을 판단하기 어렵습니다.

따라서 RAGAS를 이용해 RAG 결과를 정량적으로 평가했습니다.

```text
동일 질문 세트
 ↓
OpenSearch RAG
 ↓
평가

동일 질문 세트
 ↓
pgvector RAG
 ↓
평가
```

프로젝트에서는 로컬 Ollama를 평가 모델로 사용하여 외부 평가 API에 의존하지 않는 방식으로 테스트했습니다.

#### 장점

기술을 적용하기 전에:

```text
기존 구조
 ↓
측정
 ↓
기술 적용
 ↓
다시 측정
 ↓
실제 개선 여부 확인
```

이라는 검증 구조를 만들 수 있습니다.

즉, **"새 기술을 사용했으니 좋아졌다"가 아니라 실제 결과를 측정해서 기술의 효과를 확인하는 구조**입니다.

---

### 17. MCP + RAG

MCP는 AI 애플리케이션이 외부 데이터나 기능을 표준화된 방식으로 사용할 수 있도록 하는 프로토콜입니다.

이 프로젝트에서는 기존 RAG 기능을 MCP Tool로 노출했습니다.

```text
MCP Client
    ↓
MCP
    ↓
search_company_documents
    ↓
기존 RAG
    ↓
Vector Search
    ↓
Reranker
    ↓
Qwen3
    ↓
답변
```

중요한 점은 MCP를 위해 RAG를 새로 만든 것이 아니라 **기존 RAG 비즈니스 로직을 그대로 재사용했다는 것**입니다.

#### 장점

- 기존 RAG를 다른 AI Client에서도 사용할 수 있음
- RAG 로직과 MCP 연결 계층을 분리
- 하나의 기능을 REST API와 MCP 양쪽에서 재사용 가능
- 향후 다른 AI Agent와 연결하기 쉬운 구조

즉,

> **기존에 만든 AI 기능을 특정 웹 화면에만 가둬두지 않고 다른 AI 시스템에서도 사용할 수 있는 도구 형태로 확장했다는 점**

이 핵심입니다.

---

### 18. Docker + 전체 AI 인프라

AI 시스템은 하나의 프로그램만 실행하면 되는 구조가 아닙니다.

이 프로젝트에는 다음과 같은 여러 서비스가 함께 동작합니다.

```text
Spring Boot
Ollama
OpenSearch
PostgreSQL
Kafka
Redis
Reranker
Voice Service
Stable Diffusion
```

Docker를 이용하면 각 서비스를 독립적인 컨테이너로 관리할 수 있습니다.

```text
Docker
 ├─ Ollama
 ├─ OpenSearch
 ├─ PostgreSQL
 ├─ Kafka
 ├─ Redis
 ├─ Stable Diffusion
 ├─ Voice Service
 └─ Rerank Service
```

#### 장점

- 개발 환경 구성 단순화
- 서비스별 독립적인 실행/종료 가능
- 환경 차이로 인한 문제 감소
- 여러 AI 구성요소를 하나의 개발 환경에서 통합 관리

---

### 19. Docker + Kubernetes + GCP

Docker로 로컬에서 여러 서비스를 컨테이너화한 뒤 Kubernetes와 GCP 환경까지 확장했습니다.

```text
로컬 개발
 ↓
Docker Compose
 ↓
Kubernetes / Minikube
 ↓
GCP Compute Engine
```

각 환경의 목적은 다릅니다.

- **Docker Compose** → 로컬 통합 개발
- **Kubernetes / Minikube** → 컨테이너 오케스트레이션 구조 학습 및 검증
- **GCP** → 실제 클라우드 환경 배포 검증

이 프로젝트에서는 클라우드 자원 제약을 고려해 GCP 버전에서는 OpenSearch와 GPU 기반 기능을 제외하고 pgvector 중심의 경량화된 구성을 사용했습니다.

이를 통해 **개발 환경의 모든 기능을 그대로 클라우드에 올리는 것이 아니라, 실행 환경의 자원에 맞춰 시스템을 선택적으로 구성하는 경험**까지 포함했습니다.

---

## 기술 간 전체 시너지

이 프로젝트의 가장 중요한 부분은 개별 기술보다 **기술 간 연결**입니다.

### 문서가 AI의 지식이 되는 과정

```text
PDF
 ↓
PDFBox / Tesseract OCR
 ↓
텍스트 추출
 ↓
문장 단위 Chunking
 ↓
Embedding
 ↓
OpenSearch / pgvector
 ↓
AI가 검색할 수 있는 지식 저장소
```

### 사용자가 질문하는 과정

```text
사용자 질문
 ↓
Embedding
 ↓
Vector Search
 ↓
Top-10 후보
 ↓
Reranker
 ↓
Top-3 핵심 문서
 ↓
Qwen3
 ↓
문서 근거 기반 답변
```

### 서비스 성능을 보완하는 과정

```text
질문
 ↓
Redis Cache
 ├─ Cache Hit → 즉시 응답
 └─ Cache Miss
       ↓
    RAG 처리
```

```text
대용량 문서
 ↓
Kafka
 ↓
백그라운드 처리
 ↓
OCR → Chunking → Embedding → Vector DB
```

### 최신 정보를 보완하는 과정

```text
질문
 ↓
Qwen3
 ↓
최신 정보 필요 여부 판단
 ↓
Tavily
 ↓
웹 검색
 ↓
Qwen3
 ↓
최신 정보 기반 답변
```

### 음성 AI로 확장하는 과정

```text
사용자 음성
 ↓
VAD
 ↓
STT
 ↓
Qwen3
 ↓
Streaming
 ↓
문장 단위 TTS
 ↓
음성 응답
```

### 다른 AI 시스템으로 확장하는 과정

```text
MCP Client
 ↓
MCP
 ↓
RAG Tool
 ↓
기존 Vector Search + Reranker + LLM
 ↓
답변
```

### 모델 자체를 개선하는 과정

```text
기존 Qwen3
 ↓
QLoRA / Unsloth
 ↓
추가 학습
 ↓
GGUF
 ↓
Ollama
 ↓
개선된 모델 서비스
```

---

## 왜 이 프로젝트가 하나의 완성된 AI 시스템으로 볼 수 있는가?

이 프로젝트는 단순히 **LLM을 실행하는 것**에서 끝나지 않고 AI 서비스가 실제로 동작하기 위해 필요한 여러 계층을 하나의 흐름으로 연결했습니다.

```text
┌─────────────────────────────────────────────────────────────┐
│                         사용자 경험                          │
│             Web UI / Text / Voice / Image                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                     Spring Boot / Spring AI                 │
│                 API / RAG / MCP / WebSocket                 │
└──────────┬───────────────┬────────────────┬─────────────────┘
           │               │                │
        Redis            Kafka           FastAPI
        Cache          Async Job       AI Services
           │               │                │
           │          ┌────▼─────┐     ┌────┴─────┐
           │          │ Document │     │ Reranker │
           │          │ Pipeline │     │ STT/TTS  │
           │          └────┬─────┘     └──────────┘
           │               │
           │       OCR / Chunking
           │               │
           │           Embedding
           │               │
           └──────────┬────┴───────────────────────┐
                      │                            │
                 OpenSearch                    pgvector
                      │                            │
                      └────────────┬───────────────┘
                                   │
                              Vector Search
                                   │
                                Reranker
                                   │
                              ┌────▼────┐
                              │ Ollama  │
                              │ Qwen3   │
                              └────┬────┘
                                   │
                         ┌─────────┴─────────┐
                         │                   │
                       답변              Tavily
                                             │
                                         최신 정보
```

각 기술의 역할을 정리하면 다음과 같습니다.

| 단계 | 기술 | 해결하는 문제 |
|---|---|---|
| AI 실행 | Ollama + Qwen3 | 로컬에서 AI를 실행 |
| 문서 읽기 | PDFBox + Tesseract | 다양한 형태의 문서에서 정보 추출 |
| 문서 분할 | Chunking | 긴 문서를 검색 가능한 단위로 분리 |
| 의미 변환 | Embedding | 문장의 의미를 숫자로 표현 |
| 지식 저장 | OpenSearch / pgvector | AI가 검색할 수 있는 Vector 저장 |
| 빠른 검색 | Vector Search | 관련 문서를 빠르게 찾음 |
| 정밀 검색 | Reranker | 검색 결과의 관련성을 다시 판단 |
| 답변 생성 | Qwen3 | 검색된 정보를 바탕으로 답변 |
| 성능 향상 | Redis | 반복 요청을 빠르게 처리 |
| 비동기 처리 | Kafka | 오래 걸리는 문서 작업을 백그라운드 처리 |
| 최신 정보 | Tavily | LLM의 부족한 최신 정보 보완 |
| 음성 입력 | faster-whisper | 사람의 음성을 텍스트로 변환 |
| 음성 출력 | MeloTTS | AI 답변을 음성으로 변환 |
| 실시간 처리 | WebSocket + VAD | 자연스러운 음성 대화 구현 |
| 이미지 생성 | Stable Diffusion | 텍스트를 이미지로 변환 |
| 모델 튜닝 | QLoRA + Unsloth | 로컬 환경에서 모델 추가 학습 |
| 품질 검증 | RAGAS | RAG 결과를 정량적으로 평가 |
| AI 연동 | MCP | 다른 AI 시스템이 RAG 기능을 사용 |
| 서비스 분리 | FastAPI | Python 기반 AI 기능을 독립 서비스화 |
| 실행 환경 | Docker | 여러 AI 서비스를 독립적으로 관리 |
| 오케스트레이션 | Kubernetes | 컨테이너 기반 서비스 운영 구조 검증 |
| 클라우드 | GCP | 실제 서버 환경 배포 검증 |

### 최종적으로 얻은 구조

이 프로젝트의 핵심은 다음 세 가지를 하나의 시스템으로 연결한 것입니다.

**① AI 지식**

```text
문서 → OCR → Chunking → Embedding → Vector DB → RAG
```

**② AI 서비스**

```text
RAG → Reranker → Qwen3 → Redis → 사용자
```

**③ AI 확장**

```text
Kafka
+ Tavily
+ STT/TTS
+ Stable Diffusion
+ QLoRA
+ MCP
+ Docker/Kubernetes/GCP
```

따라서 각각의 기술이 독립적으로 존재하는 것이 아니라,

> **문서를 지식으로 만들고 → 필요한 지식을 검색하고 → 가장 중요한 내용을 선별하고 → LLM이 답변하고 → 캐시와 비동기 처리로 서비스를 안정화하고 → 음성·이미지·웹검색·MCP·파인튜닝으로 기능을 확장하는 하나의 AI 파이프라인**

으로 연결되어 있습니다.

이 구조가 이 프로젝트의 가장 큰 장점이며, 단순 LLM 호출 예제가 아닌 **실제 AI 서비스의 전체 구성 요소를 로컬 환경에서 직접 통합하고 검증한 프로젝트**라는 것을 보여줍니다.

## 빠른 시작

전체 설치는 [SETUP.md](./SETUP.md) 참고. 설치 후 매번 켤 때:

```powershell
cd D:\MyAiProject
.\start-all.ps1
```

Docker 인프라(OpenSearch/pgvector/Ollama/Kafka/Redis), Stable Diffusion, 음성 서버를 각각 새 창에서 기동합니다. 이후 **IntelliJ에서 Spring Boot 앱만 직접 Run**하면 `http://localhost:8080`에서 전체 기능을 사용할 수 있습니다.

종료: `.\stop-all.ps1`

## 상세 기능 설명 및 구현 결과

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

### 8. 리랭킹 (Cross-Encoder Reranking)

**이 기술이 뭔가요?**

벡터 검색(임베딩 유사도 기반)은 질문과 문서를 각각 독립적으로 숫자 벡터로 바꾼 뒤 그 벡터 사이의 거리(코사인 유사도)로 관련성을 판단합니다. 이 방식을 **bi-encoder**라고 부르는데, 질문과 문서를 "따로따로" 인코딩하기 때문에 계산이 빨라 대규모 문서에서 후보를 빠르게 추려내는 데는 좋지만, 질문과 문서의 미묘한 문맥적 관련성까지는 못 잡아내는 한계가 있습니다.

**리랭킹(cross-encoder)**은 반대로 질문과 문서를 "쌍으로 묶어서" 함께 모델에 넣고, 그 쌍이 얼마나 관련 있는지 직접 점수를 매깁니다. 문맥을 함께 보기 때문에 훨씬 정확하지만, 문서 하나하나를 질문과 짝지어 다시 계산해야 해서 느립니다.

**그래서 실무에서는 이 둘을 순서대로 씁니다**: 먼저 빠른 bi-encoder(벡터 검색)로 넓은 후보군(top-10)을 빠르게 추리고, 그다음 느리지만 정확한 cross-encoder(리랭커)로 그 후보군 안에서만 다시 정밀하게 순위를 매겨 최종 top-3을 뽑는 방식입니다. "검색→리랭크→생성"은 실무 RAG 시스템의 표준 패턴입니다.

**이 프로젝트에서의 역할**

```
질문 → 벡터검색(top-10 후보, OpenSearch/pgvector) → 리랭커(BAAI/bge-reranker-v2-m3)로 재정렬 → 상위 3건만 LLM 컨텍스트로 사용
```

- 리랭커는 별도 FastAPI 마이크로서비스(`rerank_service.py`, 포트 8002)로 구현해 Spring Boot 메인 앱과 분리했습니다. 리랭커 모델 교체나 튜닝이 메인 애플리케이션 재배포 없이 가능해지고, 기존 `voice-pipeline`(FastAPI, 8001) 서비스와 아키텍처 패턴이 일관됩니다.
- Spring Boot 쪽(`RerankService.java`)은 REST로 후보 문서를 리랭크 서비스에 넘기고 재정렬된 결과를 받아옵니다.
- **페일세이프 설계**: 리랭크 서비스가 죽어 있거나 응답에 실패하면, 예외를 던지지 않고 빈 결과를 반환해 기존 코사인 유사도 방식으로 자동 폴백합니다 (Redis 캐시 장애 시 캐시만 건너뛰고 앱은 정상 동작하게 만든 것과 동일한 철학).

**장점**

- 벡터 검색만 쓸 때보다 검색 결과의 관련성이 대체로 향상됩니다 (아래 실측 참고).
- 벡터 유사도 점수의 스케일이 스토어마다 다른 문제(OpenSearch 0.55 vs pgvector 0.25 임계값)를 리랭커의 0~1 정규화 점수로 어느 정도 통일할 수 있습니다.
- 별도 서비스로 분리했기 때문에, 향후 더 좋은 리랭커 모델이 나와도 메인 앱 코드 변경 없이 교체 가능합니다.

**실측 비교 (RAGAS `answer_relevancy`, 19건)**

| store | 리랭킹 ON | 리랭킹 OFF |
|---|---|---|
| OpenSearch | **0.676** | 0.611 |
| pgvector | 0.484 | **0.536** |

흥미롭게도 리랭킹이 OpenSearch에는 도움이 됐지만 pgvector에는 오히려 손해였습니다. 원인 후보로 ① pgvector 쪽 리랭크 임계값이 OpenSearch와 동일한 값(0.5)으로 캘리브레이션 없이 적용된 점, ② 두 스토어의 청킹 전략 차이, ③ 표본 수(스토어당 9~10건)가 작아 노이즈일 가능성을 검토했습니다. **"리랭킹이 항상 모든 검색 백엔드에 균일하게 도움이 되는 건 아니며, 구성요소 하나를 바꿀 때마다 A/B로 실측 검증해야 한다"**는 것 자체를 정직하게 보여주는 결과로 남겨두었습니다 (표본을 늘리거나 스토어별 임계값을 따로 잡는 건 향후 개선 과제).

### 9. MCP(Model Context Protocol) 서버

**이 기술이 뭔가요?**

MCP는 Anthropic이 제안한 개방형 표준 프로토콜로, LLM 애플리케이션(Claude Desktop, 각종 AI 에이전트 등)이 외부 데이터소스나 도구를 **일관된 방식**으로 호출할 수 있게 해줍니다. MCP 이전에는 "이 LLM 앱에 내 문서 검색 기능을 연결하려면" 그 앱이 지원하는 방식에 맞춰 매번 커스텀 연동을 짜야 했는데, MCP는 이걸 USB처럼 표준화합니다 — 도구를 "MCP 서버"로 한 번 노출해두면, MCP를 지원하는 어떤 클라이언트든 별도 연동 코드 없이 그 도구를 찾아서 호출할 수 있습니다.

**이 프로젝트에서의 역할**

기존에 이미 만들어둔 RAG 파이프라인(벡터검색 → 리랭킹 → LLM 생성)을 **MCP 도구로 노출**했습니다.

```
MCP 클라이언트(Claude Desktop, MCP Inspector 등)
    ↓ SSE (http://localhost:8080/sse)
Spring AI 2.0 @McpTool (RagMcpTools.java)
    ↓ 그대로 재사용
RagService / PgVectorRagService (리랭킹·캐싱·폴백 로직 전부 포함)
```

- Spring AI 2.0의 네이티브 `@McpTool` 어노테이션(`spring-ai-starter-mcp-server-webmvc`)을 사용해, 별도 언어나 프레임워크 추가 없이 기존 Java/Spring 스택 그대로 구현했습니다.
- `search_company_documents`라는 단일 도구로 기존 `askWithContext()` 로직(리랭킹 포함)을 그대로 노출합니다 — MCP 계층은 얇은 어댑터일 뿐, 검색/생성 로직은 전혀 새로 만들지 않았습니다.
- 8080 포트의 기존 웹 애플리케이션에 얹혀 동작하므로, 별도 프로세스나 포트 없이 REST API와 MCP 도구가 같은 앱에서 공존합니다.
- 공식 **MCP Inspector**(Anthropic 제공 테스트 도구)로 Claude Desktop 설정 없이도 프로토콜 연결·도구 목록·실제 호출까지 독립적으로 검증했습니다.

**장점**

- **표준 준수**: 이 프로젝트의 RAG 기능이 Claude Desktop뿐 아니라 MCP를 지원하는 어떤 AI 클라이언트에도 별도 연동 코드 없이 연결됩니다. "커스텀 REST API 하나"가 아니라 "업계 표준 프로토콜을 구현한 도구 서버"라는 점이 차별화됩니다.
- **관심사 분리**: MCP 도구 계층이 비즈니스 로직(RagService)을 감싸는 얇은 어댑터로만 존재해, 프로토콜이 바뀌어도 핵심 로직은 그대로 재사용됩니다.
- **에이전틱 AI 생태계 대응**: 최근 채용 시장에서 MCP/에이전틱 AI 경험에 대한 언급이 빠르게 늘고 있는데, 실제로 프로토콜을 구현하고 공식 도구로 검증까지 한 경험은 이력서상의 키워드 나열과는 다른 실질적 증빙이 됩니다.

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
| 리랭커(`sentence-transformers CrossEncoder`)가 `AssertionError: Torch not compiled with CUDA enabled` | `pip install torch`가 Windows에서 기본 CPU 전용 빌드를 설치함(`--index-url` 없이 설치 시) | 리랭커는 가벼운 모델(1.1GB)이라 `device="cpu"`로 전환. GPU가 꼭 필요하면 `--index-url https://download.pytorch.org/whl/cu121`로 재설치 |
| 리랭킹 적용 후 pgvector 스토어의 `answer_relevancy`가 오히려 하락 | OpenSearch와 pgvector에 동일한 리랭크 임계값(0.5)을 캘리브레이션 없이 적용, 표본 수(스토어당 9~10건)도 작아 노이즈 가능성 있음 | 원인 규명 중 — 스토어별 임계값 개별 캘리브레이션 및 표본 확대가 향후 과제. 억지로 결론을 포장하지 않고 정직한 혼재 결과로 문서화 |
| RAG 텍스트 API 응답이 질문과 무관하게 영어로 나옴 | 텍스트 RAG 프롬프트(`RagService`, `PgVectorRagService`)에 언어 지시가 애초에 없었음 (음성 파이프라인에만 있었던 지시문이 텍스트 API에는 누락) | 두 서비스의 모든 프롬프트 템플릿에 "반드시 한국어로만 답변하세요" 명시 |
| MCP Inspector 실행 시 `npx: 용어가 인식되지 않습니다` | 이 프로젝트는 Java/Spring 스택이라 Node.js가 설치돼 있지 않았음 | `winget install OpenJS.NodeJS.LTS`로 설치 후 **터미널을 완전히 새로 열어야** PATH가 반영됨 |
| `start.sh`로 Stable Diffusion/음성 서버가 하나도 안 뜸 | IntelliJ의 내장 실행 버튼으로 셸 스크립트를 돌리면 `start`(cmd 내장 명령)나 `mintty`(GUI 새 창 실행)가 IntelliJ의 제한된 프로세스 환경에서 정상 동작하지 않음 | IntelliJ 밖의 진짜 Git Bash 터미널 창을 직접 열어서 `./start.sh` 실행 |
| MCP 서버 엔드포인트 경로를 몰라 Inspector 연결 실패 | Spring AI MCP webmvc 스타터의 기본 SSE 경로가 문서마다 다르게 언급되어 혼동 | 브라우저로 직접 `http://localhost:8080/sse`를 열어 SSE 스트림(`event:endpoint` 응답)이 나오는지로 실제 경로 확인 |

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
- [x] **리랭킹 (Cross-Encoder Reranking)** — BAAI/bge-reranker-v2-m3, OpenSearch/pgvector 양쪽 통합 완료 (결과: [상세 기능 설명 8번](#8-리랭킹-cross-encoder-reranking))
- [x] **MCP(Model Context Protocol) 서버** — Spring AI 2.0 `@McpTool`, MCP Inspector로 검증 완료 (결과: [상세 기능 설명 9번](#9-mcpmodel-context-protocol-서버))
- [ ] **클라우드(GCP) 배포 최종 마무리** — 서버 재기동 확인 및 정식 코드 동기화 남음
- [ ] 관측성 (Prometheus + Grafana) — 요청 지연시간, 캐시 히트율, Kafka consumer lag 등 메트릭 시각화

