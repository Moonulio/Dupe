"""Command-line interface for the duplicate file finder."""

import os
import sys

import click

from dupe.finder import find_duplicates


@click.command()
@click.argument("directory", type=click.Path(exists=True, file_okay=False))
@click.option(
    "--no-recursive",
    is_flag=True,
    default=False,
    help="Only scan the top-level directory (skip subdirectories).",
)
def main(directory, no_recursive):
    """Find and list duplicate files in DIRECTORY.

    Scans all files in the given directory, computes their SHA-256 hashes,
    and reports any files that have identical content.
    """
    directory = os.path.abspath(directory)
    click.echo(f"Scanning for duplicates in: {directory}")

    recursive = not no_recursive
    duplicates = find_duplicates(directory, recursive=recursive)

    if not duplicates:
        click.echo("No duplicate files found.")
        sys.exit(0)

    total_groups = len(duplicates)
    total_files = sum(len(paths) for paths in duplicates.values())

    click.echo(
        f"\nFound {total_groups} group(s) of duplicates"
        f" ({total_files} files total):\n"
    )

    items = duplicates.items()
    for group_num, (file_hash, paths) in enumerate(items, start=1):
        short_hash = file_hash[:12]
        click.echo(f"Group {group_num} (hash: {short_hash}...):")
        for path in sorted(paths):
            click.echo(f"  - {path}")
        click.echo()


if __name__ == "__main__":
    main()
