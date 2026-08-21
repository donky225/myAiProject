# 새 PC 환경 구성 가이드

이 문서는 다른 Windows PC에서 Local LLM RAG 프로젝트를 처음부터 동일하게 재현하기 위한 절차입니다. 위에서 아래로 순서대로 진행하면 됩니다.

## 0. 사전 준비물 설치

### 0.1 Git
https://git-scm.com/download/win 에서 설치. 설치 중 옵션은 기본값 그대로 진행.

### 0.2 JDK 21 (GraalVM)
IntelliJ를 설치하면 IDE 내에서 JDK를 함께 받을 수 있습니다. IntelliJ 설치 후 프로젝트를 열 때 `File > Project Structure > SDK`에서 GraalVM JDK 21을 다운로드하면 됩니다. 별도 수동 설치가 필요하면:
```powershell
winget install GraalVM.GraalVM.JDK21
```

### 0.3 IntelliJ IDEA
https://www.jetbrains.com/idea/download 에서 Community 또는 Ultimate 설치.

### 0.4 Docker Desktop
https://www.docker.com/products/docker-desktop 에서 설치 후 재부팅. 설치 시 WSL2 백엔드 사용을 선택합니다. GPU를 쓰는 Ollama 컨테이너를 실행하려면 NVIDIA GPU 드라이버와 최신 Docker Desktop이 필요합니다 (GPU 없이도 CPU 모드로는 동작하나 속도가 느립니다).

설치 후 확인:
```powershell
docker --version
docker compose version
```

### 0.5 Python 3.10+
https://www.python.org/downloads/windows/ 에서 설치. 설치 시 "Add python.exe to PATH" 체크박스를 반드시 선택합니다.

확인:
```powershell
python --version
```

### 0.6 PowerShell 실행 정책 (venv 활성화에 필요)
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

## 1. 프로젝트 소스 받기

```powershell
cd D:\
git clone <저장소 URL> demo
cd demo
```

(Git 저장소가 아직 없다면, 기존 PC의 `D:\demo` 폴더를 그대로 복사해서 옮겨도 됩니다. 단 `target/`, `rag-eval-env/` 폴더는 제외하고 복사하는 것을 권장합니다 — 새로 생성되는 폴더라 용량만 차지합니다.)

## 2. 인프라 컨테이너 기동

프로젝트 루트(`D:\demo`)의 `docker-compose.yml`에는 opensearch, postgres(pgvector), ollama, ollama-init 서비스가 정의되어 있습니다. 앱은 IntelliJ에서 로컬 실행할 것이므로 인프라만 기동합니다.

```powershell
docker compose up -d opensearch postgres ollama ollama-init
```

`ollama-init` 컨테이너가 `qwen3:4b`, `qwen3-embedding:0.6b` 모델을 자동으로 pull합니다. 모델 용량이 크므로 인터넷 속도에 따라 수 분에서 수십 분이 걸릴 수 있습니다.

### 기동 상태 확인
```powershell
docker ps
```
`local-opensearch`, `rag-postgres`, `local-ollama` 세 컨테이너가 떠 있어야 하고, `rag-postgres`는 `(healthy)` 상태여야 합니다. `ollama-init`은 모델 pull이 끝나면 `Exited (0)`로 표시되는 것이 정상입니다.

모델이 정상적으로 받아졌는지 직접 확인하려면:
```powershell
docker exec -it local-ollama ollama list
```
`qwen3:4b`와 `qwen3-embedding:0.6b`가 목록에 보여야 합니다.

## 3. 애플리케이션 빌드 및 실행

`pom.xml`, `application.yml`은 프로젝트에 이미 포함되어 있으므로 별도 설정 없이 그대로 사용합니다. (핵심 설정: `spring.ai.vectorstore.pgvector` — HNSW/cosine/1024차원, `spring.ai.vectorstore.opensearch`, `spring.ai.ollama` 모델 지정)

IntelliJ에서:
1. `File > Open` 으로 `D:\demo` 폴더 열기
2. Maven 프로젝트로 자동 인식 대기 (최초 1회는 의존성 다운로드로 시간 소요)
3. `DemoApplication.java` 우클릭 → Run

또는 커맨드라인:
```powershell
cd D:\demo
.\mvnw.cmd spring-boot:run
```

### 기동 확인
콘솔 로그에서 아래가 에러 없이 찍히면 성공입니다.
```
PgVectorStore : Initializing PGVectorStore schema for table: vector_store
HikariPool-1 - Start completed.
Started DemoApplication in ... seconds
```

브라우저에서 `http://localhost:8080` 접속 시 테스트 웹 UI가 나오면 정상입니다.

### pgvector 스키마 직접 확인 (선택)
```powershell
docker exec -it rag-postgres psql -U rag_user -d rag_db
```
psql 안에서:
```sql
\d vector_store
```
`embedding` 컬럼이 `vector(1024)` 타입이고 `hnsw (embedding vector_cosine_ops)` 인덱스가 있어야 합니다.

## 4. 문서 인제스트 (테스트 데이터 구축)

`http://localhost:8080` 웹 UI에서 PDF/TXT 파일 업로드, 또는 폴더 업로드로 여러 문서를 한 번에 넣을 수 있습니다. store 드롭다운에서 OpenSearch/pgvector를 선택해 같은 문서를 양쪽에 넣어두면 비교 테스트가 가능합니다.

curl.exe로 직접 업로드하려면 (PowerShell의 기본 `curl` 별칭이 아닌 `curl.exe`를 명시):
```powershell
curl.exe -X POST http://localhost:8080/api/pgvector/ingest -F "file=@경로\파일.pdf"
curl.exe -X POST http://localhost:8080/api/documents/upload -F "file=@경로\파일.pdf"
```

## 5. RAG 질의 테스트

한글/공백이 포함된 질문은 curl.exe에서 `-G --data-urlencode`로 인코딩해야 합니다.
```powershell
curl.exe -G "http://localhost:8080/api/rag/ask" --data-urlencode "question=질문 내용" --data-urlencode "store=pgvector"
```

또는 웹 UI(`http://localhost:8080`)에서 직접 질문/store 선택 후 테스트하는 것이 더 간편합니다.

## 6. Python 평가 환경 구성 (RAGAS)

```powershell
cd D:\demo
python -m venv rag-eval-env
.\rag-eval-env\Scripts\Activate.ps1
```
프롬프트 앞에 `(rag-eval-env)`가 붙으면 활성화 성공입니다.

```powershell
pip install ragas langchain-community langchain-ollama pandas requests
```

### 평가 데이터 수집 실행
`questions.json`, `collect_results.py`가 `D:\demo`에 있어야 합니다 (Git 저장소에 포함되어 있다면 별도 작업 불필요).
```powershell
python collect_results.py
```
`collected_results.csv`가 생성되면 완료입니다.

## 7. Kubernetes(Minikube) 데모 (선택)

```powershell
winget install Kubernetes.minikube
winget install Kubernetes.kubectl
minikube start --driver=docker --cpus=4 --memory=10000
kubectl apply -f k8s/
minikube service rag-app -n rag-demo
```
GPU 가속은 minikube에서 구조적으로 지원되지 않으므로(NVML 초기화 실패), 이 경로는 CPU 기반 매니페스트 데모 용도로만 사용합니다.

## 트러블슈팅 빠른 참고

| 증상 | 원인 | 해결 |
|---|---|---|
| `Connection refused: localhost:5432` | postgres 컨테이너 미기동 | `docker compose up -d postgres` 로 개별 확인 |
| `curl : Malformed input to a URL function` | 한글/공백 URL 미인코딩 | `curl.exe -G --data-urlencode` 사용 |
| `Invoke-WebRequest` 관련 파라미터 에러 | PowerShell의 `curl`이 `Invoke-WebRequest` 별칭임 | `curl.exe`로 명시 호출 |
| venv 활성화 시 `PSSecurityException` | PowerShell 실행 정책 제한 | `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser` |
| Maven 의존성 관련 빨간 줄 | IntelliJ가 의존성을 아직 못 받음 | Maven 탭 → Reload All Maven Projects |
