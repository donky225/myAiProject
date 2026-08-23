"""
RAGAS의 evaluate() 실행기가 에러를 삼켜서 NaN만 나오는 문제를 진단하기 위해,
지표 하나를 collected_results.csv의 첫 번째 유효한 행 하나에만 직접 호출해서
실제 예외/트레이스백을 그대로 출력합니다.

사용법:
    python debug_ragas_single.py
"""
import ast
import asyncio
import traceback

import pandas as pd
from langchain_ollama import ChatOllama, OllamaEmbeddings

from ragas.dataset_schema import SingleTurnSample
from ragas.metrics import Faithfulness
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper

OLLAMA_BASE_URL = "http://localhost:11434"
JUDGE_MODEL = "qwen3:4b"
EMBED_MODEL = "qwen3-embedding:0.6b"


def parse_contexts(raw):
    if pd.isna(raw) or raw == "" or raw == "[]":
        return []
    try:
        return ast.literal_eval(raw)
    except (ValueError, SyntaxError):
        return []


async def main():
    df = pd.read_csv("collected_results.csv")
    df["contexts"] = df["contexts"].apply(parse_contexts)
    evaluable = df[df["contexts"].apply(len) > 0].reset_index(drop=True)

    if len(evaluable) == 0:
        print("평가할 데이터가 없습니다.")
        return

    row = evaluable.iloc[0]
    print(f"테스트 대상 질문: {row['question']}")
    print(f"store: {row['store']}")
    print(f"컨텍스트 개수: {len(row['contexts'])}")

    sample = SingleTurnSample(
        user_input=row["question"],
        response=row["answer"],
        retrieved_contexts=row["contexts"],
    )

    evaluator_llm = LangchainLLMWrapper(
        ChatOllama(model=JUDGE_MODEL, base_url=OLLAMA_BASE_URL, temperature=0, format="json")
    )
    evaluator_embeddings = LangchainEmbeddingsWrapper(
        OllamaEmbeddings(model=EMBED_MODEL, base_url=OLLAMA_BASE_URL)
    )

    metric = Faithfulness(llm=evaluator_llm)

    print("\nFaithfulness 지표 직접 호출 중...")
    try:
        score = await metric.single_turn_ascore(sample)
        print(f"\n성공. score = {score}")
    except Exception:
        print("\n에러 발생. 전체 트레이스백:")
        traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(main())
