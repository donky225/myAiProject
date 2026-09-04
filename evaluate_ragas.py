"""
collected_results.csv를 읽어 RAGAS로 faithfulness / answer_relevancy를 계산하고,
OpenSearch vs pgvector 비교 결과를 콘솔과 CSV로 출력합니다.

평가용 LLM/임베딩은 OpenAI가 아닌 로컬 Ollama(qwen3:4b, qwen3-embedding:0.6b)를 사용합니다.

사용법:
    python evaluate_ragas.py
"""
import asyncio
import sys

if sys.platform == "win32":
    # Windows 기본 ProactorEventLoop는 langchain_ollama가 사용하는
    # aiohttp/httpx 비동기 클라이언트와 충돌해 요청이 응답 후에도
    # 콜백이 걸리지 않고 무한 대기(hang)하는 경우가 있음.
    # SelectorEventLoop로 강제 전환하여 회피.
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())

import ast
import pandas as pd

from langchain_ollama import ChatOllama, OllamaEmbeddings
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas import evaluate, EvaluationDataset
from ragas.metrics import Faithfulness, AnswerRelevancy
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from ragas.run_config import RunConfig

INPUT_FILE = "collected_results.csv"
OUTPUT_FILE = "ragas_scores.csv"

OLLAMA_BASE_URL = "http://localhost:11434"
JUDGE_MODEL = "qwen3:4b"
EMBED_MODEL = "qwen3-embedding:0.6b"


def parse_contexts(raw):
    """CSV에 문자열로 저장된 파이썬 리스트를 실제 리스트로 변환."""
    if pd.isna(raw) or raw == "" or raw == "[]":
        return []
    try:
        return ast.literal_eval(raw)
    except (ValueError, SyntaxError):
        return []


def main():
    df = pd.read_csv(INPUT_FILE)
    df["contexts"] = df["contexts"].apply(parse_contexts)

    # RAGAS의 faithfulness 지표는 컨텍스트가 있어야 의미가 있습니다.
    # 문서와 무관한 질문(날씨, 코드 요청 등)은 컨텍스트가 비어있는 게 정상이므로 평가에서 제외합니다.
    evaluable = df[df["contexts"].apply(len) > 0].copy()
    skipped = df[df["contexts"].apply(len) == 0]

    print(f"전체 {len(df)}건 중 평가 대상 {len(evaluable)}건 (컨텍스트 없는 {len(skipped)}건은 제외)")
    if len(skipped) > 0:
        print("제외된 질문:")
        for _, row in skipped.iterrows():
            print(f"  - [{row['store']}] {row['question'][:40]}")

    if len(evaluable) == 0:
        print("평가할 데이터가 없습니다. collect_results.py를 먼저 실행하세요.")
        return

    # RAGAS 0.2+ 필드명 규격: user_input, response, retrieved_contexts, (reference)
    eval_rows = []
    for _, row in evaluable.iterrows():
        eval_rows.append({
            "user_input": row["question"],
            "response": row["answer"],
            "retrieved_contexts": row["contexts"],
        })

    dataset = EvaluationDataset.from_list(eval_rows)

    evaluator_llm = LangchainLLMWrapper(
        ChatOllama(model=JUDGE_MODEL, base_url=OLLAMA_BASE_URL, temperature=0, format="json")
    )
    evaluator_embeddings = LangchainEmbeddingsWrapper(
        OllamaEmbeddings(model=EMBED_MODEL, base_url=OLLAMA_BASE_URL)
    )

    print("\nRAGAS 평가 실행 중... (로컬 LLM 채점이라 다소 시간이 걸립니다)")
    result = evaluate(
        dataset=dataset,
        metrics=[AnswerRelevancy()],
        llm=evaluator_llm,
        embeddings=evaluator_embeddings,
        raise_exceptions=False,
        run_config=RunConfig(timeout=600, max_workers=1),
    )

    scored_df = result.to_pandas()
    scored_df["store"] = evaluable["store"].values
    scored_df["server_elapsed_ms"] = evaluable["server_elapsed_ms"].values

    scored_df.to_csv(OUTPUT_FILE, index=False, encoding="utf-8-sig")
    print(f"\n상세 결과 저장: {OUTPUT_FILE}")

    print("\n=== store별 평균 점수 ===")
    summary = scored_df.groupby("store")[["answer_relevancy"]].mean()
    summary["avg_response_ms"] = scored_df.groupby("store")["server_elapsed_ms"].mean()
    print(summary.round(3))


if __name__ == "__main__":
    main()