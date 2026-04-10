# Dupe

A command-line tool to find duplicate files in a directory.

## Installation

```bash
pip install .
```

For development:

```bash
pip install -e ".[dev]"
```

## Usage

```bash
# Scan a directory recursively for duplicate files
dupe /path/to/directory

# Scan only the top-level directory (no recursion)
dupe /path/to/directory --no-recursive
```

### Example Output

```
Scanning for duplicates in: /path/to/directory

Found 2 group(s) of duplicates (5 files total):

Group 1 (hash: a1b2c3d4e5f6...):
  - /path/to/directory/file1.txt
  - /path/to/directory/subdir/file1_copy.txt

Group 2 (hash: 9f8e7d6c5b4a...):
  - /path/to/directory/photo.jpg
  - /path/to/directory/backup/photo.jpg
  - /path/to/directory/old/photo.jpg
```

## How It Works

1. Walks the directory tree and groups files by size (quick filter).
2. For files with matching sizes, computes SHA-256 hashes.
3. Reports groups of files that share identical content.

## Development

```bash
# Run linting
flake8 dupe/

# Run tests
pytest tests/
```
