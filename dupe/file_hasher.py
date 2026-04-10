"""File hashing utilities for duplicate file detection.

Provides functions to calculate MD5 or SHA-256 hashes of files,
reading in chunks to handle large files efficiently without loading
the entire file into memory.
"""

from __future__ import annotations

import hashlib
import os
from typing import Literal

# 64 KiB default chunk size – large enough for good throughput,
# small enough to keep resident memory low even on constrained systems.
DEFAULT_CHUNK_SIZE: int = 64 * 1024  # 64 KiB

HashAlgorithm = Literal["md5", "sha256"]

_SUPPORTED_ALGORITHMS: dict[str, str] = {
    "md5": "md5",
    "sha256": "sha256",
}


def hash_file(
    path: str | os.PathLike[str],
    algorithm: HashAlgorithm = "sha256",
    chunk_size: int = DEFAULT_CHUNK_SIZE,
) -> str:
    """Return the hex-digest hash of the file at *path*.

    Parameters
    ----------
    path:
        Filesystem path to the file to hash.
    algorithm:
        Hash algorithm to use. Supported values are ``"md5"`` and
        ``"sha256"`` (default).
    chunk_size:
        Number of bytes read per iteration.  Defaults to 64 KiB.
        Must be a positive integer.

    Returns
    -------
    str
        The hexadecimal digest string of the file's contents.

    Raises
    ------
    FileNotFoundError
        If *path* does not exist.
    IsADirectoryError
        If *path* is a directory rather than a regular file.
    ValueError
        If *algorithm* is not supported or *chunk_size* is not positive.
    OSError
        If the file cannot be read for any other OS-level reason.

    Examples
    --------
    >>> hash_file("example.txt")                      # SHA-256 by default
    'e3b0c44298fc1c149afbf4c8996fb924...'
    >>> hash_file("example.txt", algorithm="md5")      # MD5
    'd41d8cd98f00b204e9800998ecf8427e'
    """
    # --- Validate arguments ---------------------------------------------------
    if algorithm not in _SUPPORTED_ALGORITHMS:
        supported = ", ".join(sorted(_SUPPORTED_ALGORITHMS))
        raise ValueError(
            f"Unsupported hash algorithm {algorithm!r}. "
            f"Supported algorithms: {supported}"
        )

    if not isinstance(chunk_size, int) or chunk_size <= 0:
        raise ValueError(
            f"chunk_size must be a positive integer, got {chunk_size!r}"
        )

    resolved = os.fspath(path)

    # --- Compute hash ---------------------------------------------------------
    hasher = hashlib.new(_SUPPORTED_ALGORITHMS[algorithm])

    with open(resolved, "rb") as fh:
        while True:
            chunk = fh.read(chunk_size)
            if not chunk:
                break
            hasher.update(chunk)

    return hasher.hexdigest()
