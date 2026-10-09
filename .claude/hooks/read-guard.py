# PreToolUse(Bash). Token hygiene — see AGENTS.md.
#
# One rule: deny when the first command of a pipeline is cat/less/more/head/tail/sed,
# its stdout is neither piped nor redirected to a file, and it names a regular file
# of more than LIMIT lines. head/tail with an explicit -n N <= LIMIT and sed -n 'A,Bp'
# spanning <= LIMIT lines pass unread.
import glob
import json
import os
import re
import shlex
import sys

LIMIT = 300
READERS = {"cat", "less", "more", "head", "tail", "sed"}
SEPARATORS = {";", "&&", "||", "&", "|", "|&"}
PIPES = {"|", "|&"}
STDOUT_REDIRECTS = {">", ">>", ">|", "&>", "&>>"}
REDIRECTS = STDOUT_REDIRECTS | {"<", ">&", "<&"}
GLOB = re.compile(r"[*?\[]")
COUNT = re.compile(r"-n(\d+)|--lines=(\d+)|-(\d+)")
RANGE = re.compile(r"(\d+)(?:,(\d+))?p")
WORDCHARS = "".join(c for c in map(chr, range(33, 127)) if c not in "\"'();<>|&\\")


def tokenize(line):
    lex = shlex.shlex(line, posix=True, punctuation_chars=True)
    lex.whitespace_split = False
    lex.wordchars += WORDCHARS
    lex.commenters = ""
    return list(lex)


def commands(tokens):
    """Yield argv of each command that is first in its pipeline and whose stdout is not
    redirected to a file. `2>/dev/null` and `2>&1` are not stdout redirects."""
    argv, silent, mid_pipe, skip = [], False, False, False
    for t in tokens + [";"]:
        if skip:
            skip = False
        elif t in SEPARATORS:
            if argv and not (silent or mid_pipe or t in PIPES):
                yield argv
            mid_pipe = t in PIPES
            argv, silent = [], False
        elif t == "<":
            pass                    # its target is read like an argument
        elif t in REDIRECTS:
            fd = argv.pop() if argv and argv[-1].isdigit() else "1"
            silent = silent or (t in STDOUT_REDIRECTS and fd == "1")
            skip = True
        else:
            argv.append(t)


def line_count(path):
    """Lines in path, counting stops once LIMIT is exceeded."""
    n = 0
    with open(path, "rb") as f:
        for _ in f:
            n += 1
            if n > LIMIT:
                break
    return n


def explicit_count(args):
    for i, a in enumerate(args):
        if a in ("-n", "--lines") and i + 1 < len(args):
            return args[i + 1]
        m = COUNT.fullmatch(a)
        if m:
            return next(g for g in m.groups() if g)
    return None


def narrow_range(args):
    """sed -n 'A,Bp' / 'Ap' printing at most LIMIT lines: the reading this guard asks for."""
    m = next((RANGE.fullmatch(a) for a in args if RANGE.fullmatch(a)), None)
    if not m or "-n" not in args:
        return False
    start, end = int(m.group(1)), int(m.group(2) or m.group(1))
    return 0 <= end - start < LIMIT


def oversized(argv, cwd):
    name, args = os.path.basename(argv[0]), argv[1:]
    if name not in READERS:
        return None
    if name in ("head", "tail"):
        n = explicit_count(args)
        if n is None or (n.isdigit() and int(n) <= LIMIT):
            return None
    if name == "sed" and narrow_range(args):
        return None
    for a in args:
        if a.startswith("-") or a.startswith("--"):
            continue
        p = os.path.join(cwd, os.path.expanduser(a))
        for path in glob.glob(p) if GLOB.search(p) else [p]:
            if os.path.isfile(path) and line_count(path) > LIMIT:
                return path
    return None


def main():
    try:
        data = json.load(sys.stdin)
        cmd = data["tool_input"]["command"]
        cwd = data.get("cwd") or os.getcwd()
    except Exception:
        return
    if not cmd or "<<" in cmd:
        return
    for line in cmd.split("\n"):
        try:
            tokens = tokenize(line)
        except ValueError:
            continue
        for argv in commands(tokens):
            path = oversized(argv, cwd)
            if path:
                print(json.dumps({"hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": f"{path} has over {LIMIT} lines: use Read with offset/limit or grep -n"}}))
                return


main()
