CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_vector_store (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSON NOT NULL,
    embedding VECTOR(1536) NOT NULL
);

CREATE INDEX IF NOT EXISTS knowledge_vector_store_embedding_idx
    ON knowledge_vector_store USING HNSW (embedding vector_cosine_ops);
