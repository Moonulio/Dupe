"""Tests for the duplicate file finder."""

import os
import tempfile

from dupe.finder import compute_file_hash, find_duplicates


def test_compute_file_hash_identical_files():
    """Two files with the same content should have the same hash."""
    with tempfile.TemporaryDirectory() as tmpdir:
        file1 = os.path.join(tmpdir, "file1.txt")
        file2 = os.path.join(tmpdir, "file2.txt")
        content = b"hello world"
        for path in (file1, file2):
            with open(path, "wb") as f:
                f.write(content)
        assert compute_file_hash(file1) == compute_file_hash(file2)


def test_compute_file_hash_different_files():
    """Two files with different content should have different hashes."""
    with tempfile.TemporaryDirectory() as tmpdir:
        file1 = os.path.join(tmpdir, "file1.txt")
        file2 = os.path.join(tmpdir, "file2.txt")
        with open(file1, "wb") as f:
            f.write(b"hello")
        with open(file2, "wb") as f:
            f.write(b"world")
        assert compute_file_hash(file1) != compute_file_hash(file2)


def test_find_duplicates_with_dupes():
    """Should detect duplicate files."""
    with tempfile.TemporaryDirectory() as tmpdir:
        file1 = os.path.join(tmpdir, "a.txt")
        file2 = os.path.join(tmpdir, "b.txt")
        file3 = os.path.join(tmpdir, "c.txt")
        with open(file1, "wb") as f:
            f.write(b"duplicate content")
        with open(file2, "wb") as f:
            f.write(b"duplicate content")
        with open(file3, "wb") as f:
            f.write(b"unique content")

        duplicates = find_duplicates(tmpdir)
        assert len(duplicates) == 1
        group = list(duplicates.values())[0]
        assert len(group) == 2
        assert set(group) == {file1, file2}


def test_find_duplicates_no_dupes():
    """Should return empty dict when no duplicates exist."""
    with tempfile.TemporaryDirectory() as tmpdir:
        file1 = os.path.join(tmpdir, "a.txt")
        file2 = os.path.join(tmpdir, "b.txt")
        with open(file1, "wb") as f:
            f.write(b"content one")
        with open(file2, "wb") as f:
            f.write(b"content two")

        duplicates = find_duplicates(tmpdir)
        assert len(duplicates) == 0


def test_find_duplicates_recursive():
    """Should find duplicates across subdirectories."""
    with tempfile.TemporaryDirectory() as tmpdir:
        subdir = os.path.join(tmpdir, "sub")
        os.makedirs(subdir)
        file1 = os.path.join(tmpdir, "a.txt")
        file2 = os.path.join(subdir, "b.txt")
        content = b"same content in both"
        for path in (file1, file2):
            with open(path, "wb") as f:
                f.write(content)

        duplicates = find_duplicates(tmpdir, recursive=True)
        assert len(duplicates) == 1

        duplicates_flat = find_duplicates(tmpdir, recursive=False)
        assert len(duplicates_flat) == 0


def test_find_duplicates_empty_directory():
    """Should return empty dict for an empty directory."""
    with tempfile.TemporaryDirectory() as tmpdir:
        duplicates = find_duplicates(tmpdir)
        assert len(duplicates) == 0
