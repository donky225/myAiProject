# Local LLM RAG

로컬 환경(Ollama)에서 동작하는 RAG(Retrieval-Augmented Generation) 파이프라인을 처음부터 직접 구축한 포트폴리오 프로젝트입니다. 문서 업로드부터 청킹, 임베딩, 벡터 검색, LLM 답변 생성까지 전체 과정을 Spring Boot 기반 백엔드로 구현했습니다.

## 목적

실무형 AI/백엔드 엔지니어링 역량을 보여주기 위한 end-to-end 데모입니다. 상용 서비스가 아닌, 아래 역량을 실제로 동작하는 코드로 증명하는 것을 목표로 합니다.

- LLM 및 임베딩 모델 연동 (Ollama)
- 벡터 검색 기반 RAG 파이프라인 설계
- 서로 다른 벡터스토어(OpenSearch, PostgreSQL/pgvector) 비교 구현
- Docker Compose / Kubernetes(Minikube) 양쪽 배포 경험
- 트러블슈팅 및 근본 원인 분석 능력

## 아키텍처

```
사용자 질문
    │
    ▼
qwen3-embedding:0.6b  ── 질문을 1024차원 벡터로 변환
    │
    ▼
벡터 검색 (pgvector 또는 OpenSearch 중 선택)
    │  ── 코사인 유사도 기반 유사 청크 검색
    ▼
관련성 필터 (임계값 0.55)
    │  ── 임계값 미만이면 컨텍스트 없이 LLM 일반 지식으로 답변
    ▼
qwen3:4b  ── 검색된 문서 + 질문으로 프롬프트 구성 후 답변 생성
    │
    ▼
최종 답변
```

임베딩 모델과 생성 모델의 역할이 분리되어 있습니다. `qwen3-embedding:0.6b`는 텍스트를 벡터로 바꿔 검색을 가능하게 하는 역할만 하고, 실제 자연어 이해와 답변 작성은 `qwen3:4b`가 담당합니다.

## 기술 스택

| 영역 | 기술 |
|---|---|
| LLM / 임베딩 | Ollama (`qwen3:4b`, `qwen3-embedding:0.6b`) |
| 벡터 스토어 | OpenSearch 3 (KNN, cosine, lucene engine) / PostgreSQL 16 + pgvector (HNSW, cosine) |
| 백엔드 | Spring Boot 4.1.0, Spring AI 2.0.0, Java 21 |
| 문서 처리 | Apache PDFBox, Apache POI |
| 배포 | Docker Compose (GPU 가속 실사용), Kubernetes/Minikube (CPU 기반 매니페스트 데모) |
| 개발 환경 | IntelliJ IDEA, Windows / PowerShell, Docker Desktop |

## 핵심 기능

### 1. 듀얼 벡터스토어 구조
동일한 RAG 파이프라인을 OpenSearch와 pgvector 두 가지 벡터스토어로 각각 구현하여, API 호출 시 `store` 파라미터로 선택할 수 있습니다.

- **OpenSearch 경로**: Spring AI 추상화 대신 `RestClient`를 직접 사용해 기존 인덱스 매핑을 세밀하게 제어
- **pgvector 경로**: Spring AI의 `VectorStore` 표준 추상화를 사용해 스키마 자동 생성 및 관리

두 방식을 나란히 구현함으로써 "프레임워크 표준 활용"과 "세밀한 제어" 양쪽 접근을 모두 다뤄본 경험을 보여줍니다.

```
GET /api/rag/ask?question={질문}&store=opensearch
GET /api/rag/ask?question={질문}&store=pgvector
```

### 2. 문서 업로드 및 청킹
PDF/TXT 문서를 업로드하면 자동으로 텍스트 추출 → 청킹 → 임베딩 → 벡터스토어 저장까지 처리합니다.

```
POST /api/documents/upload      (OpenSearch)
POST /api/pgvector/ingest       (pgvector)
```

실제 596청크 분량의 사업보고서 PDF로 검증했으며, 서로 다른 주제의 문서가 섞인 상태에서도 관련 문서 검색과 무관한 질문 처리가 정상 동작함을 확인했습니다.

### 3. 관련성 임계값 기반 폴백
검색된 문서의 유사도가 임계값(0.55) 미만이면 컨텍스트 없이 LLM의 일반 지식으로 답변합니다. 관련 없는 질문에 억지로 문서를 끼워 맞추는 것을 방지합니다.

### 4. 웹 테스트 UI
질문/업로드 각각에 대해 벡터스토어를 선택할 수 있는 간단한 웹 UI(`static/index.html`)를 통해 브라우저에서 바로 두 방식을 비교 테스트할 수 있습니다.

## 실행 방법

### 인프라 기동 (Docker Compose)

```powershell
docker compose up -d opensearch postgres ollama ollama-init
```

`ollama-init`은 `qwen3:4b`, `qwen3-embedding:0.6b` 모델을 자동으로 pull합니다.

### 애플리케이션 실행

IntelliJ에서 `DemoApplication`을 로컬 실행하거나:

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080` 접속 시 테스트 웹 UI가 표시됩니다.

### Kubernetes(Minikube) 데모 배포

```powershell
minikube start --driver=docker --cpus=4 --memory=10000
kubectl apply -f k8s/
minikube service rag-app -n rag-demo
```

GPU 패스스루는 중첩 가상화 구조(Docker Desktop WSL2 → minikube 노드 컨테이너 → 내부 containerd)에서 구조적으로 지원되지 않아, Kubernetes는 CPU 기반 매니페스트 데모 용도로, GPU 가속이 필요한 실사용은 Docker Compose로 역할을 분리했습니다.

## 주요 기술적 의사결정 및 트러블슈팅

| 이슈 | 원인 | 해결 |
|---|---|---|
| KNN 검색 결과가 부정확함 | OpenSearch 인덱스가 기본값(L2 거리)으로 생성되어 cosine similarity가 아닌 유클리드 거리로 랭킹됨 | 인덱스 매핑에 cosine similarity를 명시적으로 설정 |
| 한글 텍스트가 임베딩 전에 깨짐 | PowerShell `Invoke-RestMethod`가 한글을 ASCII 물음표로 변형 (콘솔 출력이 아닌 문자열 자체가 손상, 문자열 동등성 비교로 확인) | 인코딩 경로를 우회하는 방식으로 요청 처리 |
| pgvector 도입 시 `VectorStore` 빈 충돌 가능성 | OpenSearch/pgvector Spring AI 스타터가 동시에 클래스패스에 존재 | 기존 OpenSearch 경로가 Spring AI 추상화를 쓰지 않는 점을 활용해 `@SpringBootApplication(exclude = OpenSearchVectorStoreAutoConfiguration.class)`로 제외 |
| K8s에서 앱이 OpenSearch보다 먼저 기동 시도 | Docker Compose의 `depends_on`이 Kubernetes에는 없음 | `initContainers`로 의존 서비스 준비를 대기 |
| GPU 리소스가 Kubernetes 파드에 잡히지 않음 | minikube 노드 내부 containerd가 nvidia 런타임으로 구성되지 않음 (NVML 초기화 실패) | GPU 실사용은 Docker Compose로, K8s는 CPU 기반 데모로 역할 분리 |

## 로드맵

- [ ] OpenSearch vs pgvector 응답 품질/속도 정량 비교
- [ ] 할루시네이션 감소 (임계값 튜닝, 그라운딩 프롬프트 강화, `qwen3:8b` 업그레이드 검토)
- [ ] CosyVoice 2 TTS 연동 (STT-LLM-TTS 실시간 음성 대화 파이프라인)
- [ ] 클라우드 배포 (AWS/GCP)
- [ ] LangChain/LangGraph, MCP, Agent 워크플로우 실습
