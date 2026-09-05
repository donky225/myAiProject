"""
리랭킹 마이크로서비스 — BAAI/bge-reranker-v2-m3 (cross-encoder, 다국어/한국어 지원)

벡터 검색(OpenSearch/pgvector)이 넘긴 top-N 후보 문서를 쿼리와 함께 재평가해
더 정확한 순서로 재정렬합니다. 기존 voice-pipeline과 동일하게 FastAPI로 구현해
Spring Boot 메인 앱과는 독립적으로 기동/교체 가능합니다.

실행:
    pip install fastapi uvicorn sentence-transformers torch
    uvicorn rerank_service:app --host 0.0.0.0 --port 8002

Docker Compose 등록 시 GPU 필요하면 device="cuda", 아니면 "cpu"로 설정
(cross-encoder는 4B급 생성 모델보다 훨씬 가벼워 CPU로도 충분히 빠름).
"""
import logging
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import CrossEncoder

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("rerank-service")

MODEL_NAME = "BAAI/bge-reranker-v2-m3"
DEVICE = "cpu"  # 리랭커는 가벼워 CPU로도 충분히 빠름. GPU 쓰려면 "cuda"로 바꾸고
# pip install torch --index-url https://download.pytorch.org/whl/cu121 로 재설치 필요

app = FastAPI(title="Rerank Service", version="1.0")

logger.info(f"리랭커 모델 로드 중: {MODEL_NAME} (device={DEVICE})")
# activation_fn=Sigmoid: raw logit 대신 0~1 사이 확률값으로 정규화해서
# 자바 쪽에서 RELEVANCE_THRESHOLD(0.55)와 같은 방식으로 임계값을 적용할 수 있게 함
import torch
model = CrossEncoder(MODEL_NAME, device=DEVICE, max_length=512, activation_fn=torch.nn.Sigmoid())
logger.info("리랭커 모델 로드 완료")


class Document(BaseModel):
    id: str
    text: str


class RerankRequest(BaseModel):
    query: str
    documents: List[Document]
    top_k: int = 5


class RerankedDocument(BaseModel):
    id: str
    text: str
    score: float


class RerankResponse(BaseModel):
    results: List[RerankedDocument]


@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_NAME, "device": DEVICE}


@app.post("/rerank", response_model=RerankResponse)
def rerank(req: RerankRequest):
    if not req.documents:
        return RerankResponse(results=[])

    pairs = [[req.query, doc.text] for doc in req.documents]
    scores = model.predict(pairs)  # 높을수록 관련성 높음

    scored_docs = [
        RerankedDocument(id=doc.id, text=doc.text, score=float(score))
        for doc, score in zip(req.documents, scores)
    ]
    scored_docs.sort(key=lambda d: d.score, reverse=True)

    top_k = min(req.top_k, len(scored_docs))
    return RerankResponse(results=scored_docs[:top_k])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)