# Dupe

A Python library for detecting duplicate files by content hashing.

## Features

- Calculate **MD5** or **SHA-256** hashes of files
- Efficient chunked reading — handles files of any size without loading them fully into memory
- Accepts both `str` and `os.PathLike` paths

## Installation

```bash
pip install -e .
```

## Quick Start

```python
from dupe import hash_file

# SHA-256 (default)
digest = hash_file("path/to/file.txt")

# MD5
digest = hash_file("path/to/file.txt", algorithm="md5")

# Custom chunk size (default is 64 KiB)
digest = hash_file("large_video.mp4", chunk_size=1024 * 1024)
```

## API Reference

### `hash_file(path, algorithm="sha256", chunk_size=65536)`

| Parameter    | Type                    | Default    | Description                          |
|-------------|-------------------------|------------|--------------------------------------|
| `path`      | `str \| os.PathLike`    | —          | Path to the file to hash             |
| `algorithm` | `"md5" \| "sha256"`     | `"sha256"` | Hash algorithm                       |
| `chunk_size`| `int`                   | `65536`    | Bytes read per iteration             |

**Returns:** Hexadecimal digest string.

**Raises:**
- `FileNotFoundError` — file does not exist
- `IsADirectoryError` — path is a directory
- `ValueError` — unsupported algorithm or invalid chunk size

## Running Tests

```bash
pip install pytest
pytest
```
