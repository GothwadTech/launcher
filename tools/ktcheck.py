#!/usr/bin/env python3
"""Balance checker for Kotlin sources with a real scanner.

Handles: nested block comments, line comments, char literals, string literals
including `${ ... }` templates (which may contain nested strings, braces and
comments). Counts (), {}, [] outside of all of those.
"""
import os
import sys

PAIRS = {'(': ')', '{': '}', '[': ']'}
CLOSE = {')': '(', '}': '{', ']': '['}


def scan(src):
    counts = {'(': 0, ')': 0, '{': 0, '}': 0, '[': 0, ']': 0}
    stack = []
    i, n = 0, len(src)
    line = 1

    def err(msg):
        return f"line {line}: {msg}"

    while i < n:
        c = src[i]
        if c == '\n':
            line += 1
            i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            depth = 1
            i += 2
            while i < n and depth:
                if src[i] == '\n':
                    line += 1
                if src[i] == '/' and i + 1 < n and src[i + 1] == '*':
                    depth += 1
                    i += 2
                    continue
                if src[i] == '*' and i + 1 < n and src[i + 1] == '/':
                    depth -= 1
                    i += 2
                    continue
                i += 1
            if depth:
                return [err('unterminated block comment')]
            continue
        if c == "'":
            i += 1
            if i < n and src[i] == '\\':
                i += 2
            else:
                i += 1
            if i < n and src[i] == "'":
                i += 1
            continue
        if c == '"':
            triple = src.startswith('"""', i)
            i += 3 if triple else 1
            problems = []
            while i < n:
                ch = src[i]
                if ch == '\n':
                    line += 1
                    i += 1
                    continue
                if ch == '\\' and not triple:
                    i += 2
                    continue
                if triple and src.startswith('"""', i):
                    i += 3
                    break
                if not triple and ch == '"':
                    i += 1
                    break
                if src.startswith('${', i):
                    # template expression: scan a balanced { } block as code
                    depth = 0
                    j = i + 1
                    while j < n:
                        cj = src[j]
                        if cj == '\n':
                            line += 1
                        if cj == '{':
                            depth += 1
                        elif cj == '}':
                            depth -= 1
                            if depth == 0:
                                j += 1
                                break
                        elif cj == '"':
                            # nested string inside the template
                            j += 1
                            while j < n and src[j] != '"':
                                if src[j] == '\\':
                                    j += 1
                                if src[j] == '\n':
                                    line += 1
                                j += 1
                        j += 1
                    if depth != 0:
                        problems.append(err('unbalanced ${} template'))
                    i = j
                    continue
                if ch == '$' and i + 1 < n and (src[i + 1].isalnum() or src[i + 1] == '_'):
                    i += 2
                    while i < n and (src[i].isalnum() or src[i] == '_' or src[i] == '.'):
                        i += 1
                    continue
                i += 1
            else:
                problems.append(err('unterminated string literal'))
            if problems:
                return problems
            continue
        if c in PAIRS:
            counts[c] += 1
            stack.append((c, line))
            i += 1
            continue
        if c in CLOSE:
            counts[c] += 1
            if not stack:
                return [err(f'unexpected closing {c}')]
            o, oline = stack.pop()
            if o != CLOSE[c]:
                return [err(f'{c} closes {o} opened on line {oline}')]
            i += 1
            continue
        i += 1

    problems = []
    for o, oline in stack:
        problems.append(f'unclosed {o} opened on line {oline}')
    return problems


def main(root):
    bad = 0
    files = 0
    for dirpath, _dirs, names in os.walk(root):
        for name in sorted(names):
            if not name.endswith('.kt'):
                continue
            path = os.path.join(dirpath, name)
            files += 1
            problems = scan(open(path, encoding='utf-8').read())
            if problems:
                bad += 1
                print(f'{path}')
                for p in problems[:6]:
                    print(f'    {p}')
    print(f'\nscanned {files} .kt files, {bad} with problems')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src'))
