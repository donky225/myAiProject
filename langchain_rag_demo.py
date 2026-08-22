"""
Spring Boot를 거치지 않고, Python + LangChain에서 직접 pgvector 테이블에 붙어
RAG(검색+생성) 파이프라인을 재현하는 실습 스크립트입니다.

Java 쪽(PgVectorService)이 하던 일을 Python으로 동일하게 구현합니다:
  1. 질문을 Ollama 임베딩 모델로 벡터화
  2. postgres의 vector_store 테이블에서 코사인 유사도로 top-K 검색 (raw SQL)
  3. 검색된 컨텍스트 + 질문을 LangChain의 LCEL 체인으로 묶어 Ollama 생성 모델에 전달

사용법:
    python langchain_rag_demo.py "회사의 임직원 수는 몇 명인가요?"
"""
import sys

import psycopg2
from langchain_ollama import ChatOllama, OllamaEmbeddings
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough

# ── 설정 (Java 쪽 application.yml과 동일한 값) ──
OLLAMA_BASE_URL = "http://localhost:11434"
CHAT_MODEL = "qwen3:4b"
EMBED_MODEL = "qwen3-embedding:0.6b"

PG_CONN = dict(
    host="localhost",
    port=5432,
    dbname="rag_db",
    user="rag_user",
    password="rag_password",
)

TOP_K = 5
RELEVANCE_THRESHOLD = 0.25  # pgvector 실측 기준 (Java PgVectorRagService와 동일)


def search_pgvector(question: str, embeddings: OllamaEmbeddings):
    """질문을 임베딩한 뒤, postgres에 직접 SQL을 날려 유사한 청크를 검색합니다."""
    query_vector = embeddings.embed_query(question)
    vector_literal = "[" + ",".join(str(x) for x in query_vector) + "]"

    with psycopg2.connect(**PG_CONN) as conn:
        with conn.cursor() as cur:
            # pgvector의 <=> 연산자는 코사인 거리(0=완전 동일, 2=정반대)를 반환합니다.
            # 유사도 점수(1에 가까울수록 유사)로 환산해 함께 조회합니다.
            cur.execute(
                """
                SELECT content, 1 - (embedding <=> %s::vector) AS score
                FROM vector_store
                ORDER BY embedding <=> %s::vector
                LIMIT %s
                """,
                (vector_literal, vector_literal, TOP_K),
            )
            rows = cur.fetchall()

    return [(content, score) for content, score in rows]


def build_rag_chain(llm: ChatOllama):
    """LangChain LCEL(파이프 연산자) 문법으로 프롬프트-생성 체인을 구성합니다."""
    prompt = ChatPromptTemplate.from_template(
        """다음 참고 문서를 활용할 수 있으면 활용해서 질문에 답변해줘. \
참고 문서와 관련 없는 질문이면 너의 일반 지식으로 답변해도 돼.

[참고 문서]
{context}

[질문]
{question}
"""
    )
    return prompt | llm | StrOutputParser()


def main():
    if len(sys.argv) < 2:
        print('사용법: python langchain_rag_demo.py "질문 내용"')
        sys.exit(1)

    question = sys.argv[1]

    embeddings = OllamaEmbeddings(model=EMBED_MODEL, base_url=OLLAMA_BASE_URL)
    llm = ChatOllama(model=CHAT_MODEL, base_url=OLLAMA_BASE_URL, temperature=0.2)

    print(f"질문: {question}")
    print("pgvector 검색 중...")
    hits = search_pgvector(question, embeddings)

    relevant = [(content, score) for content, score in hits if score >= RELEVANCE_THRESHOLD]

    print(f"검색된 {len(hits)}건 중 임계값({RELEVANCE_THRESHOLD}) 통과 {len(relevant)}건")
    for content, score in relevant:
        print(f"  - score={score:.3f} | {content[:60]}...")

    if relevant:
        context = "\n".join(f"- {content}" for content, _ in relevant)
    else:
        context = "(관련 문서 없음)"

    chain = build_rag_chain(llm)

    print("\n답변 생성 중...")
    answer = chain.invoke({"context": context, "question": question})

    print("\n=== 답변 ===")
    print(answer)


if __name__ == "__main__":
    main()