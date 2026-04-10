"""Core logic for finding duplicate files."""

import hashlib
import os
from collections import defaultdict


def compute_file_hash(filepath, chunk_size=8192):
    """Compute the SHA-256 hash of a file.

    Args:
        filepath: Path to the file to hash.
        chunk_size: Number of bytes to read at a time.

    Returns:
        Hex digest string of the file's SHA-256 hash.
    """
    sha256 = hashlib.sha256()
    with open(filepath, "rb") as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            sha256.update(chunk)
    return sha256.hexdigest()


def find_duplicates(directory, recursive=True):
    """Find all duplicate files in a directory.

    Files are grouped by size first (quick filter), then by SHA-256 hash.

    Args:
        directory: Path to the directory to scan.
        recursive: Whether to scan subdirectories.

    Returns:
        A dict mapping hash -> list of file paths for groups with duplicates.
    """
    size_map = defaultdict(list)

    if recursive:
        for root, _dirs, files in os.walk(directory):
            for filename in files:
                filepath = os.path.join(root, filename)
                try:
                    file_size = os.path.getsize(filepath)
                    size_map[file_size].append(filepath)
                except OSError:
                    continue
    else:
        for entry in os.scandir(directory):
            if entry.is_file():
                try:
                    file_size = entry.stat().st_size
                    size_map[file_size].append(entry.path)
                except OSError:
                    continue

    hash_map = defaultdict(list)
    for size, paths in size_map.items():
        if len(paths) < 2:
            continue
        for filepath in paths:
            try:
                file_hash = compute_file_hash(filepath)
                hash_map[file_hash].append(filepath)
            except OSError:
                continue

    return {h: paths for h, paths in hash_map.items() if len(paths) >= 2}
