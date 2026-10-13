#!/usr/bin/env python3
"""Static checks for the Kotlin sources.

1. Balance check with a real scanner: nested block comments, line comments, char
   literals, string literals including `${ ... }` templates (which may contain
   nested strings, braces and comments). Counts (), {}, [] outside of all of those.
2. A short list of "compiles fine, dies at runtime" pitfalls - see PITFALLS.

Typical use (this project is often edited without an Android SDK, so the compiler
is not available - that is what makes these checks worth having):

    python3 tools/ktcheck.py [root]      # default root: app/src
"""
import os
import re
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


def mask_literals(src):
    """Blank out comments and string/char literal *contents*, keeping length and newlines.

    Naive brace counting on the result is then safe: a ``{`` inside a comment, a string or a
    ``${ ... }`` template is replaced by a space instead of being counted.

    Unterminated literals are left as-is for the balance scanner to report.
    """
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith('//', i):
            while i < n and src[i] != '\n':
                out.append(' ')
                i += 1
            continue
        if src.startswith('/*', i):
            depth = 1
            out.append('  ')
            i += 2
            while i < n and depth:
                if src.startswith('/*', i):
                    depth += 1
                    out.append('  ')
                    i += 2
                elif src.startswith('*/', i):
                    depth -= 1
                    out.append('  ')
                    i += 2
                else:
                    out.append('\n' if src[i] == '\n' else ' ')
                    i += 1
            continue
        if c in ('"', "'"):
            triple = src.startswith(c * 3, i)
            delim = c * 3 if triple else c
            out.append(' ' * len(delim))
            i += len(delim)
            while i < n:
                if not triple and src[i] == '\\':
                    out.append('  ')
                    i += 2
                    continue
                if not triple and src[i] == '\n':
                    break
                if src.startswith(delim, i):
                    out.append(' ' * len(delim))
                    i += len(delim)
                    break
                out.append('\n' if src[i] == '\n' else ' ')
                i += 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)


# --------------------------------------------------------------------------- pitfalls
#
# Patterns that compile cleanly and then die at runtime. This is where the bugs that cannot
# be caught by "does it compile" live, and those are the expensive ones for a launcher: if
# the default HOME app cannot start, the TV shows a crash dialog over a black screen.
#
# The one encoded here: **`applicationContext` is null while an `Application` is being
# attached**. `LoadedApk.makeApplication()` assigns `mApplication = app` only after
# `Instrumentation.newApplication()` - which calls `Application.attachBaseContext()` -
# returns, and `ContextImpl.getApplicationContext()` is literally
# `mPackageInfo.getApplication()`. v1.0.79 read a SharedPreferences file from
# `GothwadApplication.attachBaseContext` (through `DensityAdapter.wrapContext` ->
# `hydrate` -> `prefs`) and died on every cold start. See docs/ISSUE_LOG.md.
#
# The check is name-based and best effort (no type resolution): from the body of every
# `attachBaseContext` override of an `Application` subclass, it follows calls to functions
# *defined in the scanned tree* and reports the chain when one of them reads
# `applicationContext`. A report is meant to be read by a human, not obeyed blindly.
APPLICATION_SUPERTYPE = 'Application'
ATTACH_OVERRIDE = 'attachBaseContext'
FORBIDDEN_TOKENS = ('applicationContext',)

CLASS_HEADER = re.compile(r'\bclass\s+\w+[^\n{]*')
FUN_DEF = re.compile(r'\bfun\s+(?:<[^>]*>\s*)?(\w+)\s*\(')
# A call, optionally on a receiver: `name(`, `Type.name(` / `object.name(` / `pkg.Type.name(`.
CALL = re.compile(r'(?:(?P<recv>[A-Za-z_][\w.]*)\s*(?:\.|\?\.)\s*)?(?P<name>\w+)\s*\(')
# Bare calls to these are almost always the stdlib equivalents, not our own function.
SCOPE_FUNCTIONS = {'apply', 'also', 'let', 'run', 'with', 'use', 'takeIf', 'takeUnless'}
LINE_OF = lambda masked, pos: masked.count('\n', 0, pos) + 1


def _match_paren(masked, open_pos):
    """Index just past the ')' matching the '(' at [open_pos], or -1."""
    depth = 0
    i = open_pos
    while i < len(masked):
        if masked[i] == '(':
            depth += 1
        elif masked[i] == ')':
            depth -= 1
            if depth == 0:
                return i + 1
        i += 1
    return -1


def _body(masked, pos, decl_start):
    """(start, end) of the function body at/after [pos], or (-1, -1).

    Best effort: a block body is brace-matched; an expression body (`fun x() = ...`) is taken
    as the rest of the line plus the more-indented continuation lines under it.
    """
    limit = min(len(masked), pos + 400)
    brace = masked.find('{', pos, limit)
    eq = masked.find('=', pos, limit)
    if brace < 0 and eq < 0:
        return -1, -1

    if brace >= 0 and (eq < 0 or brace < eq):
        depth = 0
        i = brace
        while i < len(masked):
            if masked[i] == '{':
                depth += 1
            elif masked[i] == '}':
                depth -= 1
                if depth == 0:
                    return brace, i
            i += 1
        return brace, len(masked)

    end = masked.find('\n', eq)
    if end < 0:
        return eq, len(masked)
    # Indentation of the *line* the declaration starts on (not the offset of `fun`), so the
    # continuation lines of an expression body are the ones indented deeper than it.
    line_start = masked.rfind('\n', 0, decl_start) + 1
    k = line_start
    while k < len(masked) and masked[k] in ' \t':
        k += 1
    decl_indent = k - line_start
    i = end
    while i + 1 < len(masked):
        nxt_start = i + 1
        j = nxt_start
        while j < len(masked) and masked[j] in ' \t':
            j += 1
        if j == nxt_start or (j - nxt_start) <= decl_indent:
            break
        nxt = masked.find('\n', j)
        if nxt < 0:
            return eq, len(masked)
        end = nxt
        i = nxt
    return eq, end


def collect_functions(masked):
    """name -> list of (line, body_start, body_end) for every `fun name(...)` in one file."""
    found = {}
    for match in FUN_DEF.finditer(masked):
        close = _match_paren(masked, masked.index('(', match.end() - 1))
        if close < 0:
            continue
        start, end = _body(masked, close, match.start())
        if start < 0:
            continue
        found.setdefault(match.group(1), []).append((LINE_OF(masked, match.start()), start, end))
    return found


def calls_in(body):
    """Function names called in [body] that could be defined in this repository.

    Only bare calls and calls on a Class/Object receiver are returned: a member call on a
    variable (`context.getSharedPreferences(...)`, `it.apply { ... }`) and the stdlib scope
    functions can never be one of our functions, and following them is what turned the first
    version of this check into noise.
    """
    names = []
    for match in CALL.finditer(body):
        receiver, name = match.group('recv'), match.group('name')
        if receiver is not None:
            if not receiver.split('.')[-1][:1].isupper():
                continue
        elif name in SCOPE_FUNCTIONS:
            continue
        if name not in names:
            names.append(name)
    return names


def attach_root_bodies(masked):
    """(line, body_start, body_end) of every `attachBaseContext` override of an Application."""
    roots = []
    pattern = re.compile(r'\boverride\s+fun\s+' + re.escape(ATTACH_OVERRIDE) + r'\s*\(')
    for match in pattern.finditer(masked):
        headers = [h for h in CLASS_HEADER.finditer(masked[:match.start()])]
        if not headers:
            continue
        if not re.search(r'\b' + re.escape(APPLICATION_SUPERTYPE) + r'\b', headers[-1].group(0)):
            continue  # an Activity's attachBaseContext can read applicationContext safely
        close = _match_paren(masked, masked.index('(', match.end() - 1))
        if close < 0:
            continue
        start, end = _body(masked, close, match.start())
        if start >= 0:
            roots.append((LINE_OF(masked, match.start()), start, end))
    return roots


def _report(line, chain=None, where_path=None, where_line=None):
    message = (
        f'line {line}: `applicationContext` is read from an '
        f'Application.attachBaseContext() call chain. It is null there (the Application is '
        f'attached before LoadedApk.mApplication is assigned), so this is a '
        f'NullPointerException on every cold start. Pass and use the context argument.'
    )
    if chain:
        message += (' Call chain: ' + ' -> '.join(chain)
                    + f' ({os.path.basename(where_path)}:{where_line}).')
    return message


def pitfall_problems(files):
    """Tree-wide version of the pitfalls above.

    [files] is an iterable of (path, source). Returns {path: [problem, ...]}.
    """
    masked_by_path = {}
    per_file = {}
    for path, src in files:
        masked_by_path[path] = mask_literals(src)
        per_file[path] = collect_functions(masked_by_path[path])

    def resolve(name, caller_path):
        """Definitions of [name] as seen from [caller_path].

        Same file first: a bare `prefs(...)` inside `DensityAdapter.kt` is that file's
        `prefs`, not a same-named function of another class.
        """
        local = per_file.get(caller_path, {}).get(name)
        if local:
            return [(caller_path, line, start, end) for line, start, end in local]
        found = []
        for path, defs in per_file.items():
            for line, start, end in defs.get(name, ()):
                found.append((path, line, start, end))
        return found

    def walk(line, body, path):
        for token in FORBIDDEN_TOKENS:
            if token in body:
                return _report(line)
        queue = [(name, [name], path) for name in calls_in(body)]
        seen = set()
        while queue:
            name, chain, caller = queue.pop(0)
            for dpath, dline, ds, de in resolve(name, caller):
                if (dpath, ds) in seen:
                    continue
                seen.add((dpath, ds))
                dbody = masked_by_path[dpath][ds:de]
                if any(token in dbody for token in FORBIDDEN_TOKENS):
                    return _report(line, chain, dpath, dline)
                if len(chain) < 6:
                    queue.extend((c, chain + [c], dpath) for c in calls_in(dbody))
        return None

    problems = {}
    for path, masked in masked_by_path.items():
        for line, start, end in attach_root_bodies(masked):
            report = walk(line, masked[start:end], path)
            if report:
                problems.setdefault(path, []).append(report)
    return problems


def main(root):
    sources = []
    for dirpath, _dirs, names in os.walk(root):
        for name in sorted(names):
            if name.endswith('.kt'):
                path = os.path.join(dirpath, name)
                sources.append((path, open(path, encoding='utf-8').read()))

    problems_by_file = {path: scan(src) for path, src in sources}
    for path, found in pitfall_problems(sources).items():
        problems_by_file.setdefault(path, []).extend(found)

    bad = 0
    for path, _src in sources:
        problems = problems_by_file.get(path) or []
        if not problems:
            continue
        bad += 1
        print(f'{path}')
        for p in problems[:6]:
            print(f'    {p}')
    print(f'\nscanned {len(sources)} .kt files, {bad} with problems')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else 'app/src'))
