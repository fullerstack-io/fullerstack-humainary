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

# Benchmark group organization
SUBSTRATES_CORE = [
    "CircuitOps", "ConduitOps", "CortexOps", "FlowOps", "NameOps",
    "PipeOps", "ReservoirOps", "ScopeOps", "StateOps", "SubscriberOps"
]

# Serventis modules organized by semiotic layer
SERVENTIS_SDK = [  # Universal Primitives
    "CycleOps", "OperationOps", "OutcomeOps", "SignalSetOps",
    "SituationOps", "StatusOps", "SurveyOps", "SystemOps", "TrendOps"
]
SERVENTIS_TOOL = [  # Measurement Instruments
    "CounterOps", "GaugeOps", "LogOps", "ProbeOps", "SensorOps"
]
SERVENTIS_DATA = [  # Data Structures
    "CacheOps", "PipelineOps", "QueueOps", "StackOps"
]
SERVENTIS_FLOW = [  # Flow Control
    "BreakerOps", "FlowOps", "RouterOps", "ValveOps"
]
SERVENTIS_SYNC = [  # Synchronization
    "AtomicOps", "LatchOps", "LockOps"
]
SERVENTIS_POOL = [  # Resource Management
    "ExchangeOps", "LeaseOps", "PoolOps", "ResourceOps"
]
SERVENTIS_EXEC = [  # Execution
    "ProcessOps", "ServiceOps", "TaskOps", "TimerOps", "TransactionOps"
]
SERVENTIS_ROLE = [  # Coordination
    "ActorOps", "AgentOps"
]

# All Serventis groups combined
SERVENTIS_ALL = (SERVENTIS_SDK + SERVENTIS_TOOL + SERVENTIS_DATA +
                 SERVENTIS_FLOW + SERVENTIS_SYNC + SERVENTIS_POOL +
                 SERVENTIS_EXEC + SERVENTIS_ROLE)

# All groups for legacy compatibility
ALL_GROUPS = SUBSTRATES_CORE + SERVENTIS_ALL

# Section definitions for markdown output
SECTIONS = [
    ("Substrates Core", SUBSTRATES_CORE),
    ("Serventis SDK (Universal Primitives)", SERVENTIS_SDK),
    ("Serventis Tool (Measurement)", SERVENTIS_TOOL),
    ("Serventis Data (Data Structures)", SERVENTIS_DATA),
    ("Serventis Flow (Flow Control)", SERVENTIS_FLOW),
    ("Serventis Sync (Synchronization)", SERVENTIS_SYNC),
    ("Serventis Pool (Resource Management)", SERVENTIS_POOL),
    ("Serventis Exec (Execution)", SERVENTIS_EXEC),
    ("Serventis Role (Coordination)", SERVENTIS_ROLE),
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


def get_qualified_name(group: str, benchmark: str) -> str:
    """Get fully qualified benchmark name: Category.Module.Group.test"""
    test_name = benchmark.split(".")[-1]

    if group in SUBSTRATES_CORE:
        return f"Substrates.{group}.{test_name}"
    elif group in SERVENTIS_SDK:
        return f"Serventis.SDK.{group}.{test_name}"
    elif group in SERVENTIS_TOOL:
        return f"Serventis.Tool.{group}.{test_name}"
    elif group in SERVENTIS_DATA:
        return f"Serventis.Data.{group}.{test_name}"
    elif group in SERVENTIS_FLOW:
        return f"Serventis.Flow.{group}.{test_name}"
    elif group in SERVENTIS_SYNC:
        return f"Serventis.Sync.{group}.{test_name}"
    elif group in SERVENTIS_POOL:
        return f"Serventis.Pool.{group}.{test_name}"
    elif group in SERVENTIS_EXEC:
        return f"Serventis.Exec.{group}.{test_name}"
    elif group in SERVENTIS_ROLE:
        return f"Serventis.Role.{group}.{test_name}"
    else:
        return f"Other.{group}.{test_name}"


def generate_comparison_table(
    jctools_results: Dict[str, float],
    folded_results: Dict[str, float],
    humainary_baselines: Dict[str, float]
) -> Tuple[str, Dict[str, int]]:
    """Generate markdown comparison table with fully qualified benchmark names."""

    lines = []
    stats = {"jctools_wins": 0, "folded_wins": 0, "humainary_wins": 0, "ties": 0, "total": 0}

    # Header
    lines.append("| Benchmark | Humainary (ns) | JCtools (ns) | Folded (ns) | Best Fullerstack | vs Humainary |")
    lines.append("|-----------|---------------:|-------------:|------------:|:----------------:|:------------:|")

    # Collect all benchmark names from both result sets
    all_names = set(jctools_results.keys()) | set(folded_results.keys())

    # Group benchmarks by category
    grouped = {}
    for name in all_names:
        group = name.split(".")[0]
        if group not in grouped:
            grouped[group] = []
        grouped[group].append(name)

    # Sort groups by predefined order
    def group_sort_key(g):
        if g in ALL_GROUPS:
            return (0, ALL_GROUPS.index(g))
        return (1, g)

    for group in sorted(grouped.keys(), key=group_sort_key):
        for name in sorted(grouped[group]):
            jc_score = jctools_results.get(name)
            fold_score = folded_results.get(name)
            hum_score = humainary_baselines.get(name)
            qualified_name = get_qualified_name(group, name)

            # Format scores
            jc_str = format_score(jc_score) if jc_score is not None else "-"
            fold_str = format_score(fold_score) if fold_score is not None else "-"
            hum_str = format_score(hum_score) if hum_score is not None else "N/A"

            # Determine best Fullerstack (lower is better)
            if jc_score is not None and fold_score is not None:
                if jc_score < fold_score * 0.95:  # JCtools is >5% faster
                    best_fs = "**JCtools**"
                    best_score = jc_score
                    stats["jctools_wins"] += 1
                elif fold_score < jc_score * 0.95:  # Folded is >5% faster
                    best_fs = "**Folded**"
                    best_score = fold_score
                    stats["folded_wins"] += 1
                else:
                    best_fs = "Tie"
                    best_score = min(jc_score, fold_score)
                    stats["ties"] += 1
            elif jc_score is not None:
                best_fs = "JCtools"
                best_score = jc_score
            elif fold_score is not None:
                best_fs = "Folded"
                best_score = fold_score
            else:
                best_fs = "-"
                best_score = None

            # Compare best Fullerstack vs Humainary
            if best_score is not None and hum_score is not None:
                diff, winner = calculate_diff(best_score, hum_score)
                stats["total"] += 1
            else:
                diff = "-"
                winner = "-"

            lines.append(
                f"| {qualified_name} | {hum_str} | {jc_str} | {fold_str} | {best_fs} | {diff} {winner} |"
            )

    return "\n".join(lines), stats


def generate_full_report(
    jctools_results: Dict[str, float],
    folded_results: Dict[str, float],
    humainary_baselines: Dict[str, float],
    updated_groups: Set[str]
) -> str:
    """Generate full markdown report."""

    table, stats = generate_comparison_table(jctools_results, folded_results, humainary_baselines)

    date_str = datetime.now().strftime("%Y-%m-%d %H:%M")
    groups_str = ", ".join(sorted(updated_groups)) if updated_groups else "All"

    # Calculate Fullerstack vs Humainary stats
    fs_wins = stats.get('fullerstack_wins', 0)
    hum_wins = stats.get('humainary_wins', 0)
    total = stats['total']

    report = f"""# Benchmark Comparison: Fullerstack vs Humainary

**Last Updated:** {date_str}
**Groups Updated:** {groups_str}

## Hardware Configuration

| | Humainary | Fullerstack |
|---|-----------|-------------|
| **Platform** | Apple M4 Mac | Azure VM (GitHub Codespaces) |
| **CPU** | Apple M4 (10 cores) | Intel Xeon (shared vCPUs) |
| **Memory** | 16GB unified | 8GB |
| **JDK** | 25.0.1 OpenJDK | 25.0.1 OpenJDK |

> **Note:** Direct performance comparisons are not meaningful due to different hardware.
> These benchmarks are useful for comparing **relative performance** between circuit types
> and identifying **performance characteristics** of each implementation.

## Summary

### Circuit Comparison (JCtools vs Folded)

| Metric | Count | % |
|--------|------:|--:|
| **JCtools Wins** | {stats['jctools_wins']} | {stats['jctools_wins']*100//max(total,1)}% |
| **Folded Wins** | {stats['folded_wins']} | {stats['folded_wins']*100//max(total,1)}% |
| **Ties** | {stats['ties']} | {stats['ties']*100//max(total,1)}% |
| **Total** | {total} | 100% |

## Benchmarks
{table}

---

**Legend:**
- **JCtools** = MPSC queue with wait-free producer path (default circuit)
- **Folded** = Linked job cascading for deep chains
- **Best Fullerstack** = Which circuit is faster (>5% difference = win)
- **vs Humainary** = Best Fullerstack result compared to Humainary baseline
- Bold values indicate significant wins (>5% difference)
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
    jctools_results: Dict[str, float],
    folded_results: Dict[str, float],
    humainary_baselines: Dict[str, float]
):
    """Update BENCHMARK-COMPARISON.md with both circuit results."""

    # Get all updated groups from both result sets
    jc_groups = get_groups_from_results(jctools_results)
    fold_groups = get_groups_from_results(folded_results)
    updated_groups = jc_groups | fold_groups

    # Generate report
    report = generate_full_report(jctools_results, folded_results, humainary_baselines, updated_groups)

    # Ensure parent directory exists
    COMPARISON_FILE.parent.mkdir(parents=True, exist_ok=True)

    with open(COMPARISON_FILE, 'w') as f:
        f.write(report)

    total_benchmarks = len(set(jctools_results.keys()) | set(folded_results.keys()))
    print(f"Updated {COMPARISON_FILE}")
    print(f"  Updated groups: {', '.join(sorted(updated_groups))}")
    print(f"  Total benchmarks: {total_benchmarks}")


def print_summary(stats: Dict[str, int]):
    """Print summary to console."""
    total = stats['total']
    if total == 0:
        print("No benchmarks compared.")
        return

    jc_wins = stats.get('jctools_wins', 0)
    fold_wins = stats.get('folded_wins', 0)
    ties = stats.get('ties', 0)

    print("\n" + "=" * 60)
    print("CIRCUIT COMPARISON SUMMARY (JCtools vs Folded)")
    print("=" * 60)
    print(f"  JCtools Wins:     {jc_wins:3d} ({jc_wins*100//max(total,1)}%)")
    print(f"  Folded Wins:      {fold_wins:3d} ({fold_wins*100//max(total,1)}%)")
    print(f"  Ties:             {ties:3d} ({ties*100//max(total,1)}%)")
    print(f"  Total:            {total:3d}")
    print("=" * 60)


def main():
    if len(sys.argv) < 2:
        print("Usage: generate-comparison.py --jctools <file> --folded <file> [--update-md] [--print-table]")
        print("       generate-comparison.py <json_file> [--update-md] [--print-table]  (legacy single-file mode)")
        sys.exit(1)

    # Parse arguments
    update_md = "--update-md" in sys.argv
    print_table = "--print-table" in sys.argv

    jctools_file = None
    folded_file = None
    legacy_file = None

    args = sys.argv[1:]
    i = 0
    while i < len(args):
        arg = args[i]
        if arg == "--jctools" and i + 1 < len(args):
            jctools_file = Path(args[i + 1])
            i += 2
        elif arg == "--folded" and i + 1 < len(args):
            folded_file = Path(args[i + 1])
            i += 2
        elif not arg.startswith("--"):
            legacy_file = Path(arg)
            i += 1
        else:
            i += 1

    # Handle legacy single-file mode
    if legacy_file and not jctools_file and not folded_file:
        jctools_file = legacy_file
        print("(Legacy mode: treating single file as JCtools results)")

    # Parse results
    jctools_results = {}
    folded_results = {}

    if jctools_file and jctools_file.exists():
        print(f"Parsing JCtools results from {jctools_file}...")
        jctools_results = parse_jmh_json(jctools_file)
        print(f"  Found {len(jctools_results)} benchmarks")
    elif jctools_file:
        print(f"Warning: JCtools file not found: {jctools_file}")

    if folded_file and folded_file.exists():
        print(f"Parsing Folded results from {folded_file}...")
        folded_results = parse_jmh_json(folded_file)
        print(f"  Found {len(folded_results)} benchmarks")
    elif folded_file:
        print(f"Warning: Folded file not found: {folded_file}")

    if not jctools_results and not folded_results:
        print("Error: No valid result files provided")
        sys.exit(1)

    # Get updated groups
    all_results = {**jctools_results, **folded_results}
    updated_groups = get_groups_from_results(all_results)
    print(f"  Groups: {', '.join(sorted(updated_groups))}")

    print(f"Parsing Humainary baselines from {HUMAINARY_BENCHMARKS}...")
    humainary_baselines = parse_humainary_baselines(HUMAINARY_BENCHMARKS)
    print(f"  Found {len(humainary_baselines)} baselines")

    # Generate table
    table, stats = generate_comparison_table(jctools_results, folded_results, humainary_baselines)

    if print_table:
        print("\n" + table)

    print_summary(stats)

    if update_md:
        update_comparison_md(jctools_results, folded_results, humainary_baselines)
    else:
        print(f"\nTo update {COMPARISON_FILE}, run with --update-md flag")


if __name__ == "__main__":
    main()
