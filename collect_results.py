"""
questions.json의 질문들을 OpenSearch / pgvector 두 경로로 각각 던져서
answer, contexts, 응답시간을 수집하고 결과를 CSV로 저장합니다.

사용법:
    python collect_results.py
"""
import json
import os
import time
import requests
import pandas as pd

BASE_URL = "http://localhost:8080/api/rag/evaluate"
STORES = ["opensearch", "pgvector"]
QUESTIONS_FILE = "questions.json"

# 리랭킹 켬/끔 비교용: OUTPUT_FILE 환경변수로 출력 파일명을 다르게 지정 가능
# (rerank.enabled는 application.yml 설정이라 API로 못 바꾸므로, Spring Boot를
#  켬/끔 상태로 각각 재시작한 뒤 이 스크립트를 두 번 실행해 결과를 따로 모음)
#   RERANK_LABEL=on  python collect_results.py   → collected_results_on.csv
#   RERANK_LABEL=off python collect_results.py   → collected_results_off.csv
#   (라벨 없이 실행하면 기존과 동일하게 collected_results.csv)
RERANK_LABEL = os.environ.get("RERANK_LABEL", "")
OUTPUT_FILE = f"collected_results_{RERANK_LABEL}.csv" if RERANK_LABEL else "collected_results.csv"


def load_questions(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def call_evaluate(question, store):
    params = {"question": question, "store": store}
    start = time.time()
    try:
        res = requests.get(BASE_URL, params=params, timeout=120)
        res.raise_for_status()
        data = res.json()
        client_elapsed = time.time() - start
        return {
            "question": question,
            "store": store,
            "answer": data.get("answer", ""),
            "contexts": data.get("contexts", []),
            "server_elapsed_ms": data.get("elapsedMillis", None),
            "client_elapsed_s": round(client_elapsed, 2),
            "error": None,
        }
    except Exception as e:
        return {
            "question": question,
            "store": store,
            "answer": "",
            "contexts": [],
            "server_elapsed_ms": None,
            "client_elapsed_s": round(time.time() - start, 2),
            "error": str(e),
        }


def main():
    questions = load_questions(QUESTIONS_FILE)
    results = []

    total = len(questions) * len(STORES)
    count = 0

    for q in questions:
        for store in STORES:
            count += 1
            print(f"[{count}/{total}] ({store}) {q['question'][:40]}...")
            result = call_evaluate(q["question"], store)
            result["ground_truth"] = q.get("ground_truth", "")
            results.append(result)

    df = pd.DataFrame(results)
    df.to_csv(OUTPUT_FILE, index=False, encoding="utf-8-sig")
    print(f"\n완료. {OUTPUT_FILE} 에 {len(df)}건 저장됨.")

    errors = df[df["error"].notna()]
    if len(errors) > 0:
        print(f"\n경고: {len(errors)}건에서 에러 발생:")
        for _, row in errors.iterrows():
            print(f"  - [{row['store']}] {row['question'][:30]}... : {row['error']}")


if __name__ == "__main__":
    main()