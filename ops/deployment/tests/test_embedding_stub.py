import unittest

from ops.deployment.embedding_stub import (
    EMBEDDING_DIMENSIONS,
    build_embedding_response,
    deterministic_embedding,
)


class EmbeddingStubTest(unittest.TestCase):
    def test_returns_one_deterministic_vector_per_input(self):
        response = build_embedding_response({
            "model": "release-gate-embedding",
            "input": ["alpha", "beta"],
        })

        self.assertEqual([0, 1], [item["index"] for item in response["data"]])
        self.assertTrue(all(
            len(item["embedding"]) == EMBEDDING_DIMENSIONS
            for item in response["data"]
        ))
        self.assertEqual(
            deterministic_embedding("alpha"),
            response["data"][0]["embedding"],
        )
        self.assertNotEqual(
            response["data"][0]["embedding"],
            response["data"][1]["embedding"],
        )

    def test_rejects_invalid_input_without_echoing_it(self):
        with self.assertRaisesRegex(ValueError, "input must be"):
            build_embedding_response({"model": "release-gate-embedding", "input": []})


if __name__ == "__main__":
    unittest.main()
