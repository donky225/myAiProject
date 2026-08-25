"""
pgvector의 vector_store 테이블에 저장된 청크들을 기반으로,
qwen3:4b에게 각 청크마다 질문-답변 쌍을 생성시켜 파인튜닝용 시드 데이터셋을 만듭니다.

생성된 데이터는 그대로 학습에 쓰지 말고, 반드시 사람이 직접 검토/수정한 뒤 사용하세요.
(모델이 스스로 만든 질문/답변이라 품질이 들쭉날쭉할 수 있습니다.)

사용법:
    python generate_finetune_seed.py
"""
import json
import random

import psycopg2
from langchain_ollama import ChatOllama

PG_CONN = dict(
    host="localhost",
    port=5432,
    dbname="rag_db",
    user="rag_user",
    password="rag_password",
)

OLLAMA_BASE_URL = "http://localhost:11434"
JUDGE_MODEL = "qwen3:4b"

OUTPUT_FILE = "finetune_seed.jsonl"
SAMPLE_SIZE = 150  # 전체 청크 중 몇 개를 뽑아 Q&A를 만들지

QA_GEN_PROMPT = """다음은 회사 사업보고서의 일부 내용입니다. 이 내용을 바탕으로,
사람이 실제로 물어볼 법한 질문 1개와 그에 대한 정확한 답변 1개를 만들어주세요.

규칙:
- 질문은 아래 문서 내용에 명시적으로 답이 있는 것만 만드세요.
- 답변은 반드시 문서 내용에 근거해서 작성하세요. 추측하지 마세요.
- 아래 JSON 형식으로만 응답하세요. 다른 설명은 붙이지 마세요.

[문서 내용]
{content}

[출력 형식]
{{"question": "...", "answer": "..."}}
"""


def fetch_chunks(sample_size: int):
    with psycopg2.connect(**PG_CONN) as conn:
        with conn.cursor() as cur:
            cur.execute("SELECT content FROM vector_store")
            rows = [r[0] for r in cur.fetchall()]
    random.shuffle(rows)
    return rows[:sample_size]


def main():
    llm = ChatOllama(model=JUDGE_MODEL, base_url=OLLAMA_BASE_URL, temperature=0.3)

    chunks = fetch_chunks(SAMPLE_SIZE)
    print(f"총 {len(chunks)}개 청크로 Q&A 생성 시작")

    results = []
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        for i, content in enumerate(chunks):
            # 너무 짧은 청크(목차, 페이지번호 등)는 건너뜀
            if len(content.strip()) < 100:
                continue

            prompt = QA_GEN_PROMPT.format(content=content)
            try:
                response = llm.invoke(prompt).content.strip()
                # 모델이 코드블록으로 감싸는 경우 제거
                response = response.replace("```json", "").replace("```", "").strip()
                qa = json.loads(response)

                if "question" in qa and "answer" in qa:
                    record = {
                        "instruction": qa["question"],
                        "input": "",
                        "output": qa["answer"],
                        "source_chunk": content[:200],
                    }
                    f.write(json.dumps(record, ensure_ascii=False) + "\n")
                    results.append(record)
                    print(f"[{i+1}/{len(chunks)}] 생성됨: {qa['question'][:40]}")
            except (json.JSONDecodeError, KeyError):
                print(f"[{i+1}/{len(chunks)}] 파싱 실패, 건너뜀")
                continue

    print(f"\n완료. {len(results)}개 Q&A 쌍을 {OUTPUT_FILE}에 저장했습니다.")
    print("주의: 학습 전에 반드시 이 파일을 열어 내용을 직접 검토/수정하세요.")


if __name__ == "__main__":
    main()