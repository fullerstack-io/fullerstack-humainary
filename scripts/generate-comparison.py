#!/usr/bin/env python3
"""
Generate benchmark comparison table between Fullerstack and Humainary.

Parses JMH JSON results and compares against Humainary baselines from BENCHMARKS.md.
Updates BENCHMARK-COMPARISON.md with the results, merging with existing data.

Usage:
    ./scripts/generate-comparison.py <json_results_file> [--update-md] [--print-table]
"""

import json
import re
import sys
from pathlib import Path
from datetime import datetime
from typing import Dict, Tuple, Set

# Project paths
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
HUMAINARY_BENCHMARKS = PROJECT_ROOT / "substrates-api-java" / "BENCHMARKS.md"
COMPARISON_FILE = PROJECT_ROOT / "fullerstack-substrates" / "docs" / "BENCHMARK-COMPARISON.md"

# All known benchmark groups
ALL_GROUPS = [
    "CircuitOps", "ConduitOps", "CortexOps", "FlowOps", "NameOps",
    "PipeOps", "ReservoirOps", "ScopeOps", "StateOps", "SubscriberOps"
]


def parse_jmh_json(json_file: Path) -> Dict[str, float]:
    """Parse JMH JSON results into benchmark -> score mapping."""
    with open(json_file) as f:
        data = json.load(f)

    results = {}
    for benchmark in data:
        full_name = benchmark["benchmark"]
        parts = full_name.split(".")
        if len(parts) >= 2:
            short_name = f"{parts[-2]}.{parts[-1]}"
        else:
            short_name = full_name

        score = benchmark["primaryMetric"]["score"]
        results[short_name] = score

    return results


def parse_humainary_baselines(benchmarks_md: Path) -> Dict[str, float]:
    """Parse Humainary baseline results from BENCHMARKS.md."""
    baselines = {}

    if not benchmarks_md.exists():
        print(f"Warning: {benchmarks_md} not found")
        return baselines

    with open(benchmarks_md) as f:
        content = f.read()

    pattern = r'i\.h\.(?:substrates|serventis)\.jmh\.(?:[\w.]+\.)?(\w+)\.(\w+)\s+avgt\s+\d+\s+([\d.]+(?:≈\s*10⁻³)?)\s*±'

    for match in re.finditer(pattern, content):
        group_name = match.group(1)
        benchmark_name = match.group(2)
        score_str = match.group(3)

        if "10⁻³" in score_str or "≈" in score_str:
            score = 0.001
        else:
            try:
                score = float(score_str)
            except ValueError:
                continue

        short_name = f"{group_name}.{benchmark_name}"
        baselines[short_name] = score

    return baselines


def parse_existing_comparison(comparison_file: Path) -> Dict[str, float]:
    """Parse existing Fullerstack results from BENCHMARK-COMPARISON.md."""
    existing = {}

    if not comparison_file.exists():
        return existing

    with open(comparison_file) as f:
        content = f.read()

    # Match table rows: | benchmark_name | humainary | fullerstack | diff | winner |
    # Skip header rows and group headers (which have empty cells)
    pattern = r'\| (\w+) \| [\d.N/A]+ \| ([\d.]+) \|'

    current_group = None
    for line in content.split('\n'):
        # Check for group header: | **GroupName** | | | | |
        group_match = re.match(r'\| \*\*(\w+)\*\* \|', line)
        if group_match:
            current_group = group_match.group(1)
            continue

        # Check for benchmark row
        if current_group:
            row_match = re.match(r'\| (\w+) \| [\d.N/A]+ \| ([\d.]+) \|', line)
            if row_match:
                benchmark_name = row_match.group(1)
                score_str = row_match.group(2)
                try:
                    score = float(score_str)
                    full_name = f"{current_group}.{benchmark_name}"
                    existing[full_name] = score
                except ValueError:
                    continue

    return existing


def get_groups_from_results(results: Dict[str, float]) -> Set[str]:
    """Extract unique group names from benchmark results."""
    groups = set()
    for name in results:
        group = name.split(".")[0]
        if group in ALL_GROUPS:
            groups.add(group)
    return groups


def calculate_diff(fullerstack: float, humainary: float) -> Tuple[str, str]:
    """Calculate percentage difference and determine winner."""
    if humainary == 0 or humainary < 0.001:
        if fullerstack < 0.001:
            return "0%", "Tie"
        return "+N/A", "Humainary"

    diff_pct = ((fullerstack - humainary) / humainary) * 100

    if abs(diff_pct) < 5:
        return f"{diff_pct:+.0f}%", "Tie"
    elif diff_pct < 0:
        return f"**{diff_pct:.0f}%**", "**Fullerstack**"
    else:
        return f"+{diff_pct:.0f}%", "Humainary"


def format_score(score: float) -> str:
    """Format score for display."""
    if score < 0.01:
        return f"{score:.3f}"
    elif score < 1:
        return f"{score:.2f}"
    elif score < 100:
        return f"{score:.1f}"
    else:
        return f"{score:.1f}"


def generate_comparison_table(
    fullerstack_results: Dict[str, float],
    humainary_baselines: Dict[str, float]
) -> Tuple[str, Dict[str, int]]:
    """Generate markdown comparison table."""

    lines = []
    stats = {"fullerstack_wins": 0, "humainary_wins": 0, "ties": 0, "total": 0}

    # Header
    lines.append("| Benchmark | Humainary (ns) | Fullerstack (ns) | Diff | Winner |")
    lines.append("|-----------|---------------:|----------------:|-----:|:------:|")

    # Group benchmarks by category
    grouped = {}
    for name in fullerstack_results:
        group = name.split(".")[0]
        if group not in grouped:
            grouped[group] = []
        grouped[group].append(name)

    # Sort groups by predefined order, then alphabetically for unknown groups
    def group_sort_key(g):
        if g in ALL_GROUPS:
            return (0, ALL_GROUPS.index(g))
        return (1, g)

    for group in sorted(grouped.keys(), key=group_sort_key):
        # Add group header
        lines.append(f"| **{group}** | | | | |")

        for name in sorted(grouped[group]):
            fs_score = fullerstack_results[name]
            hum_score = humainary_baselines.get(name)

            benchmark_short = name.split(".")[-1]

            if hum_score is not None:
                diff, winner = calculate_diff(fs_score, hum_score)

                if "Fullerstack" in winner:
                    stats["fullerstack_wins"] += 1
                elif "Humainary" in winner:
                    stats["humainary_wins"] += 1
                else:
                    stats["ties"] += 1
                stats["total"] += 1

                lines.append(
                    f"| {benchmark_short} | {format_score(hum_score)} | {format_score(fs_score)} | {diff} | {winner} |"
                )
            else:
                lines.append(
                    f"| {benchmark_short} | N/A | {format_score(fs_score)} | - | - |"
                )
                stats["total"] += 1

    return "\n".join(lines), stats


def generate_full_report(
    fullerstack_results: Dict[str, float],
    humainary_baselines: Dict[str, float],
    updated_groups: Set[str]
) -> str:
    """Generate full markdown report."""

    table, stats = generate_comparison_table(fullerstack_results, humainary_baselines)

    date_str = datetime.now().strftime("%Y-%m-%d %H:%M")
    groups_str = ", ".join(sorted(updated_groups)) if updated_groups else "All"

    report = f"""# Benchmark Comparison: Fullerstack vs Humainary

**Last Updated:** {date_str}
**Groups Updated:** {groups_str}
**JDK:** 25.0.1, OpenJDK 64-Bit Server VM

## Summary

| Metric | Count | % |
|--------|------:|--:|
| **Fullerstack Wins** | {stats['fullerstack_wins']} | {stats['fullerstack_wins']*100//max(stats['total'],1)}% |
| **Humainary Wins** | {stats['humainary_wins']} | {stats['humainary_wins']*100//max(stats['total'],1)}% |
| **Ties** | {stats['ties']} | {stats['ties']*100//max(stats['total'],1)}% |
| **Total** | {stats['total']} | 100% |

## Full Comparison Table

{table}

---

**Legend:**
- **Diff** = ((Fullerstack - Humainary) / Humainary x 100)
- **Winner** = Lower time (faster) wins
- Bold values indicate significant wins (>5% difference)

**Note:** Humainary benchmarks run on Apple M4 (10 cores, 16GB). Fullerstack on Azure VM. Hardware differences may affect comparisons.
"""
    return report


def merge_results(
    new_results: Dict[str, float],
    existing_results: Dict[str, float]
) -> Tuple[Dict[str, float], Set[str]]:
    """Merge new results with existing, returning merged results and updated groups."""

    # Get groups that were updated
    updated_groups = get_groups_from_results(new_results)

    # Start with existing results
    merged = existing_results.copy()

    # Remove all benchmarks from updated groups (replace with new data)
    for group in updated_groups:
        keys_to_remove = [k for k in merged if k.startswith(f"{group}.")]
        for k in keys_to_remove:
            del merged[k]

    # Add new results
    merged.update(new_results)

    return merged, updated_groups


def update_comparison_md(
    new_results: Dict[str, float],
    humainary_baselines: Dict[str, float]
):
    """Update BENCHMARK-COMPARISON.md, merging with existing data."""

    # Parse existing results
    existing_results = parse_existing_comparison(COMPARISON_FILE)

    # Merge new results with existing
    merged_results, updated_groups = merge_results(new_results, existing_results)

    # Generate report
    report = generate_full_report(merged_results, humainary_baselines, updated_groups)

    # Ensure parent directory exists
    COMPARISON_FILE.parent.mkdir(parents=True, exist_ok=True)

    with open(COMPARISON_FILE, 'w') as f:
        f.write(report)

    print(f"Updated {COMPARISON_FILE}")
    print(f"  Updated groups: {', '.join(sorted(updated_groups))}")
    print(f"  Total benchmarks: {len(merged_results)}")


def print_summary(stats: Dict[str, int]):
    """Print summary to console."""
    total = stats['total']
    if total == 0:
        print("No benchmarks compared.")
        return

    print("\n" + "=" * 60)
    print("BENCHMARK COMPARISON SUMMARY")
    print("=" * 60)
    print(f"  Fullerstack Wins: {stats['fullerstack_wins']:3d} ({stats['fullerstack_wins']*100//total}%)")
    print(f"  Humainary Wins:   {stats['humainary_wins']:3d} ({stats['humainary_wins']*100//total}%)")
    print(f"  Ties:             {stats['ties']:3d} ({stats['ties']*100//total}%)")
    print(f"  Total:            {stats['total']:3d}")
    print("=" * 60)


def main():
    if len(sys.argv) < 2:
        print("Usage: generate-comparison.py <json_results_file> [--update-md] [--print-table]")
        sys.exit(1)

    # Parse arguments
    update_md = "--update-md" in sys.argv
    print_table = "--print-table" in sys.argv
    json_file = None

    for arg in sys.argv[1:]:
        if not arg.startswith("--"):
            json_file = Path(arg)
            break

    if not json_file or not json_file.exists():
        print(f"Error: JSON file not found: {json_file}")
        sys.exit(1)

    # Parse new results
    print(f"Parsing Fullerstack results from {json_file}...")
    new_results = parse_jmh_json(json_file)
    print(f"  Found {len(new_results)} benchmarks")

    # Get updated groups
    updated_groups = get_groups_from_results(new_results)
    print(f"  Groups: {', '.join(sorted(updated_groups))}")

    print(f"Parsing Humainary baselines from {HUMAINARY_BENCHMARKS}...")
    humainary_baselines = parse_humainary_baselines(HUMAINARY_BENCHMARKS)
    print(f"  Found {len(humainary_baselines)} baselines")

    # For display, show only the new results
    table, stats = generate_comparison_table(new_results, humainary_baselines)

    if print_table:
        print("\n" + table)

    print_summary(stats)

    if update_md:
        update_comparison_md(new_results, humainary_baselines)
    else:
        print(f"\nTo update {COMPARISON_FILE}, run with --update-md flag")


if __name__ == "__main__":
    main()
