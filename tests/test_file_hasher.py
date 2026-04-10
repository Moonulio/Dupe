"""Tests for dupe.file_hasher."""

from __future__ import annotations

import hashlib
import os
import pathlib
import tempfile

import pytest

from dupe.file_hasher import DEFAULT_CHUNK_SIZE, hash_file

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _write_tmp(content: bytes, tmp_path: str) -> str:
    """Write *content* to a temporary file and return its path."""
    fd, path = tempfile.mkstemp(dir=tmp_path)
    os.write(fd, content)
    os.close(fd)
    return path


def _expected_hash(content: bytes, algorithm: str) -> str:
    """Return the expected hex-digest for *content*."""
    return hashlib.new(algorithm, content).hexdigest()


# ---------------------------------------------------------------------------
# Basic hashing tests
# ---------------------------------------------------------------------------

class TestHashFile:
    """Core functionality tests."""

    def test_sha256_empty_file(self, tmp_path: str) -> None:
        path = _write_tmp(b"", str(tmp_path))
        result = hash_file(path, algorithm="sha256")
        assert result == _expected_hash(b"", "sha256")

    def test_md5_empty_file(self, tmp_path: str) -> None:
        path = _write_tmp(b"", str(tmp_path))
        result = hash_file(path, algorithm="md5")
        assert result == _expected_hash(b"", "md5")

    def test_sha256_small_file(self, tmp_path: str) -> None:
        data = b"hello, world!"
        path = _write_tmp(data, str(tmp_path))
        result = hash_file(path, algorithm="sha256")
        assert result == _expected_hash(data, "sha256")

    def test_md5_small_file(self, tmp_path: str) -> None:
        data = b"hello, world!"
        path = _write_tmp(data, str(tmp_path))
        result = hash_file(path, algorithm="md5")
        assert result == _expected_hash(data, "md5")

    def test_default_algorithm_is_sha256(self, tmp_path: str) -> None:
        data = b"default algorithm check"
        path = _write_tmp(data, str(tmp_path))
        assert hash_file(path) == _expected_hash(data, "sha256")

    def test_identical_content_produces_same_hash(self, tmp_path: str) -> None:
        data = b"duplicate content"
        path_a = _write_tmp(data, str(tmp_path))
        path_b = _write_tmp(data, str(tmp_path))
        assert hash_file(path_a) == hash_file(path_b)

    def test_different_content_produces_different_hash(self, tmp_path: str) -> None:
        path_a = _write_tmp(b"file A", str(tmp_path))
        path_b = _write_tmp(b"file B", str(tmp_path))
        assert hash_file(path_a) != hash_file(path_b)


# ---------------------------------------------------------------------------
# Large-file / chunked-reading tests
# ---------------------------------------------------------------------------

class TestLargeFile:
    """Verify correct behaviour when files span multiple chunks."""

    def test_file_larger_than_default_chunk(self, tmp_path: str) -> None:
        """File > DEFAULT_CHUNK_SIZE must still produce the correct hash."""
        size = DEFAULT_CHUNK_SIZE * 3 + 7  # not a multiple of chunk size
        data = os.urandom(size)
        path = _write_tmp(data, str(tmp_path))
        assert hash_file(path) == _expected_hash(data, "sha256")

    def test_custom_chunk_size(self, tmp_path: str) -> None:
        data = b"a" * 500
        path = _write_tmp(data, str(tmp_path))
        # Use a tiny chunk size to exercise multiple iterations.
        assert hash_file(path, chunk_size=13) == _expected_hash(data, "sha256")

    def test_chunk_size_equals_file_size(self, tmp_path: str) -> None:
        data = b"exact chunk"
        path = _write_tmp(data, str(tmp_path))
        assert hash_file(path, chunk_size=len(data)) == _expected_hash(data, "sha256")

    def test_chunk_size_larger_than_file(self, tmp_path: str) -> None:
        data = b"small"
        path = _write_tmp(data, str(tmp_path))
        assert hash_file(path, chunk_size=1024 * 1024) == _expected_hash(data, "sha256")


# ---------------------------------------------------------------------------
# PathLike support
# ---------------------------------------------------------------------------

class TestPathTypes:
    """Ensure various path types are accepted."""

    def test_pathlib_path(self, tmp_path: pathlib.Path) -> None:
        file_path = tmp_path / "pathlib_test.bin"
        file_path.write_bytes(b"pathlib")
        assert hash_file(file_path) == _expected_hash(b"pathlib", "sha256")

    def test_string_path(self, tmp_path: str) -> None:
        path = _write_tmp(b"string path", str(tmp_path))
        assert hash_file(path) == _expected_hash(b"string path", "sha256")


# ---------------------------------------------------------------------------
# Error handling
# ---------------------------------------------------------------------------

class TestErrorHandling:
    """Validation and error-path tests."""

    def test_file_not_found(self) -> None:
        with pytest.raises(FileNotFoundError):
            hash_file("/nonexistent/path/to/file.bin")

    def test_directory_raises(self, tmp_path: str) -> None:
        with pytest.raises(IsADirectoryError):
            hash_file(str(tmp_path))

    def test_unsupported_algorithm(self, tmp_path: str) -> None:
        path = _write_tmp(b"x", str(tmp_path))
        with pytest.raises(ValueError, match="Unsupported hash algorithm"):
            hash_file(path, algorithm="sha512")  # type: ignore[arg-type]

    def test_invalid_chunk_size_zero(self, tmp_path: str) -> None:
        path = _write_tmp(b"x", str(tmp_path))
        with pytest.raises(ValueError, match="chunk_size must be a positive integer"):
            hash_file(path, chunk_size=0)

    def test_invalid_chunk_size_negative(self, tmp_path: str) -> None:
        path = _write_tmp(b"x", str(tmp_path))
        with pytest.raises(ValueError, match="chunk_size must be a positive integer"):
            hash_file(path, chunk_size=-1)
