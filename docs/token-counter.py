#!/usr/bin/env python3
"""
面试项目 Token 用量统计脚本。
自动统计并更新 docs/token-usage-log.md。

用法:
  python3 token-counter.py              # 统计并更新日志
  python3 token-counter.py --detail     # 显示每个会话明细
  python3 token-counter.py --check      # 仅打印统计，不更新日志
"""

import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

CLAUDE_PROJECT_DIR = os.path.expanduser(
    "~/.claude/projects/-Users-chenhaiwei-IdeaProjects-highway-tech-agent"
)
PROJECT_DIR = "/Users/chenhaiwei/IdeaProjects/highway-tech-agent"
LOG_FILE = os.path.join(PROJECT_DIR, "docs/token-usage-log.md")
PROJECT_START_DATE = "2026-05-16"


def parse_timestamp(ts):
    """解析时间戳，支持 ISO 格式和毫秒时间戳"""
    if ts is None:
        return None
    if isinstance(ts, str):
        return datetime.fromisoformat(ts.replace("Z", "+00:00"))
    return datetime.fromtimestamp(ts / 1000, tz=timezone.utc)


def count_tokens(jsonl_path, start_date=None):
    """统计单个 JSONL 文件的 token 用量"""
    total_input = 0
    total_output = 0
    total_cache_creation = 0
    total_cache_read = 0
    call_count = 0
    first_ts = None
    last_ts = None

    try:
        with open(jsonl_path, "r") as f:
            for line in f:
                try:
                    d = json.loads(line)
                    ts = d.get("timestamp")
                    dt = parse_timestamp(ts)
                    if dt:
                        if first_ts is None:
                            first_ts = dt
                        last_ts = dt
                        if start_date and dt < start_date:
                            continue

                    if d.get("type") == "assistant":
                        msg = d.get("message", {})
                        if isinstance(msg, dict):
                            usage = msg.get("usage", {})
                            if usage:
                                call_count += 1
                                total_input += usage.get("input_tokens", 0)
                                total_output += usage.get("output_tokens", 0)
                                total_cache_creation += usage.get(
                                    "cache_creation_input_tokens", 0
                                )
                                total_cache_read += usage.get(
                                    "cache_read_input_tokens", 0
                                )
                except (json.JSONDecodeError, KeyError, TypeError):
                    continue
    except FileNotFoundError:
        pass

    return {
        "call_count": call_count,
        "input_tokens": total_input,
        "output_tokens": total_output,
        "cache_creation_tokens": total_cache_creation,
        "cache_read_tokens": total_cache_read,
        "first_ts": first_ts,
        "last_ts": last_ts,
    }


def collect_stats(start_date):
    """收集所有会话的统计"""
    jsonl_files = list(Path(CLAUDE_PROJECT_DIR).glob("*.jsonl"))
    sessions = []
    grand_total = {
        "call_count": 0,
        "input_tokens": 0,
        "output_tokens": 0,
        "cache_creation_tokens": 0,
        "cache_read_tokens": 0,
    }

    for jsonl_path in sorted(jsonl_files):
        result = count_tokens(jsonl_path, start_date=start_date)
        if result["call_count"] > 0:
            sessions.append({"session_id": jsonl_path.stem, **result})
            for key in grand_total:
                grand_total[key] += result[key]

    return sessions, grand_total


def update_log(sessions, grand_total):
    """更新 token-usage-log.md"""
    today = datetime.now().strftime("%Y-%m-%d")
    effective_total = grand_total["input_tokens"] + grand_total["output_tokens"]
    effective_input = grand_total["input_tokens"] - grand_total["cache_read_tokens"]

    # 按日期分组会话
    daily = {}
    for s in sessions:
        if s["first_ts"]:
            date_key = s["first_ts"].strftime("%Y-%m-%d")
        else:
            date_key = "unknown"
        if date_key not in daily:
            daily[date_key] = {
                "session_count": 0,
                "call_count": 0,
                "input_tokens": 0,
                "output_tokens": 0,
            }
        daily[date_key]["session_count"] += 1
        daily[date_key]["call_count"] += s["call_count"]
        daily[date_key]["input_tokens"] += s["input_tokens"]
        daily[date_key]["output_tokens"] += s["output_tokens"]

    # 生成日志内容
    lines = [
        "# 面试项目 Token 用量日志",
        "",
        "> 由 `docs/token-counter.py` 自动生成，每次运行脚本时更新。",
        f"> 统计范围：{PROJECT_START_DATE} 起，至项目开发完成止。",
        "",
        "## 累计统计",
        "",
        f"- **项目开始日期**: {PROJECT_START_DATE}",
        f"- **最后更新**: {today}",
        f"- **会话数**: {len(sessions)}",
        f"- **API 调用次数**: {grand_total['call_count']:,}",
        f"- **Input Tokens**: {grand_total['input_tokens']:,}",
        f"- **Output Tokens**: {grand_total['output_tokens']:,}",
        f"- **Cache Creation Tokens**: {grand_total['cache_creation_tokens']:,}",
        f"- **Cache Read Tokens**: {grand_total['cache_read_tokens']:,}",
        f"- **有效总计**: {effective_input + grand_total['output_tokens']:,}",
        f"- **状态**: 开发中",
        "",
        "## 每日统计",
        "",
        "| 日期 | 会话数 | API调用 | Input Tokens | Output Tokens | 有效总计 |",
        "|------|--------|---------|-------------|--------------|---------|",
    ]

    for date_key in sorted(daily.keys()):
        d = daily[date_key]
        eff = d["input_tokens"] + d["output_tokens"]
        lines.append(
            f"| {date_key} | {d['session_count']} | {d['call_count']:,} | "
            f"{d['input_tokens']:,} | {d['output_tokens']:,} | {eff:,} |"
        )

    lines.extend(
        [
            "",
            "## 会话明细",
            "",
            "| 会话ID | 日期 | API调用 | Input | Output |",
            "|--------|------|---------|-------|--------|",
        ]
    )

    for s in sessions:
        date_str = s["first_ts"].strftime("%m-%d %H:%M") if s["first_ts"] else "N/A"
        lines.append(
            f"| {s['session_id'][:8]}... | {date_str} | "
            f"{s['call_count']:,} | {s['input_tokens']:,} | {s['output_tokens']:,} |"
        )

    lines.extend(["", "---", "", "*运行 `python3 docs/token-counter.py` 更新统计*"])

    content = "\n".join(lines) + "\n"
    with open(LOG_FILE, "w") as f:
        f.write(content)

    return content


def print_detail(sessions, grand_total):
    """打印明细到终端"""
    for s in sessions:
        date_str = s["first_ts"].strftime("%Y-%m-%d %H:%M") if s["first_ts"] else "N/A"
        print(f"\n会话: {s['session_id'][:8]}...")
        print(f"  时间: {date_str}")
        print(f"  调用次数: {s['call_count']:,}")
        print(f"  Input tokens:  {s['input_tokens']:>12,}")
        print(f"  Output tokens: {s['output_tokens']:>12,}")

    effective = grand_total["input_tokens"] + grand_total["output_tokens"]
    print(f"\n{'='*50}")
    print(f"总计: {effective:,} tokens")
    print(f"  会话数: {len(sessions)}")
    print(f"  API 调用: {grand_total['call_count']:,}")
    print(f"  Input: {grand_total['input_tokens']:,}")
    print(f"  Output: {grand_total['output_tokens']:,}")


def main():
    import argparse

    parser = argparse.ArgumentParser(description="面试项目 Token 用量统计")
    parser.add_argument("--detail", action="store_true", help="显示会话明细")
    parser.add_argument("--check", action="store_true", help="仅打印，不更新日志")
    args = parser.parse_args()

    start_date = datetime.strptime(PROJECT_START_DATE, "%Y-%m-%d").replace(
        tzinfo=timezone.utc
    )
    sessions, grand_total = collect_stats(start_date)

    if args.detail:
        print_detail(sessions, grand_total)

    if not args.check:
        content = update_log(sessions, grand_total)
        print(f"日志已更新: {LOG_FILE}")
        effective = grand_total["input_tokens"] + grand_total["output_tokens"]
        print(f"有效总计: {effective:,} tokens")
        print(f"API 调用: {grand_total['call_count']:,}")
    else:
        effective = grand_total["input_tokens"] + grand_total["output_tokens"]
        print(f"有效总计: {effective:,} tokens")


if __name__ == "__main__":
    main()
