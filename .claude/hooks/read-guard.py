# PreToolUse(Bash) logic for read-guard.sh. Token hygiene — see AGENTS.md.
#
# Denies a command that would print more than LIMIT lines of file text into the
# context: cat, less/more, sed -n ranges, head -n N, tail -n N / +K. A command whose
# stdout is piped into a filter, or redirected to a file, passes. Silent on allow.
import glob
import json
import os
import re
import shlex
import sys

LIMIT = 300
SEPS = {";", "&&", "||", "&", "|", "|&"}


def lines_of(path):
    try:
        with open(path, "rb") as f:
            return sum(chunk.count(b"\n") for chunk in iter(lambda: f.read(1 << 20), b""))
    except OSError:
        return None


def resolve(words, cwd):
    out = []
    for w in words:
        if w == "-":
            continue
        p = os.path.expanduser(w)
        if not os.path.isabs(p):
            p = os.path.join(cwd, p)
        hits = glob.glob(p) if re.search(r"[*?\[]", p) else [p]
        out += [h for h in hits if os.path.isfile(h)]
    return out


def split_commands(tokens):
    """[(argv, piped_out)] for each simple command; drops redirects. A stdout
    redirect to a file empties argv (nothing is printed); stderr redirects are noise."""
    cmds, cur, piped, silent = [], [], False, False
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in SEPS:
            cmds.append((cur, t in ("|", "|&"), silent))
            cur, silent = [], False
        elif t in (">", ">>", ">&", ">|", "<", "&>"):
            fd = cur[-1] if cur and cur[-1].isdigit() else None
            if fd is not None:
                cur.pop()
            tgt = tokens[i + 1] if i + 1 < len(tokens) else ""
            if t != "<" and fd in (None, "1") and not (t == ">&" and tgt.isdigit()):
                silent = True      # stdout goes to a file
            i += 1                 # skip the target
        else:
            cur.append(t)
        i += 1
    cmds.append((cur, False, silent))
    return cmds


def num(s):
    return int(s) if re.fullmatch(r"\d+", s) else None


def check(argv, cwd):
    """Return total line count to report when argv prints too much, else None."""
    while argv and (re.fullmatch(r"\w+=.*", argv[0]) or argv[0] in ("command", "builtin", "time", "exec")):
        argv = argv[1:]
    if not argv:
        return None
    name, args = os.path.basename(argv[0]), argv[1:]

    if name in ("cat", "less", "more"):
        files = resolve([a for a in args if not a.startswith("-") or a == "-"], cwd)
        counts = [lines_of(f) or 0 for f in files]
        return sum(counts) if sum(counts) > LIMIT else None

    if name == "sed":
        quiet, scripts, rest, i = False, [], [], 0
        while i < len(args):
            a = args[i]
            if a in ("-e", "--expression") and i + 1 < len(args):
                scripts.append(args[i + 1]); i += 1
            elif a.startswith("--expression="):
                scripts.append(a.split("=", 1)[1])
            elif a == "--quiet" or a == "--silent" or (a.startswith("-") and not a.startswith("--") and "n" in a and "i" not in a):
                quiet = True
            elif a.startswith("-"):
                pass
            else:
                rest.append(a)
            i += 1
        if not scripts and rest:
            scripts.append(rest.pop(0))
        if not quiet or not scripts:
            return None
        files = resolve(rest, cwd)
        total = sum(lines_of(f) or 0 for f in files)
        printed = 0
        for part in ";".join(scripts).replace("\n", ";").split(";"):
            part = part.strip()
            m = re.fullmatch(r"(?:(\d+|\$)(?:,(\d+|\$))?)?p", part)
            if not m:
                continue
            a, b = m.group(1), m.group(2)
            if a is None:
                printed += total
            elif b is None:
                printed += 1
            else:
                lo = total if a == "$" else int(a)
                hi = total if b == "$" else int(b)
                printed += max(0, hi - lo + 1)
        return total if files and printed > LIMIT else None

    if name in ("head", "tail"):
        n, plus, files, i = None, False, [], 0
        while i < len(args):
            a = args[i]
            m = re.fullmatch(r"-n([+-]?\d+)|--lines=([+-]?\d+)|-(\d+)", a)
            if a in ("-n", "--lines") and i + 1 < len(args):
                n = args[i + 1]; i += 1
            elif m:
                n = next(g for g in m.groups() if g)
            elif a in ("-f", "-F", "--follow"):
                return None
            elif a.startswith("-") and a != "-":
                pass
            else:
                files.append(a)
            i += 1
        if n is None:
            return None            # default 10 lines
        files = resolve(files, cwd)
        total = sum(lines_of(f) or 0 for f in files)
        if not files:
            return None
        sign = n[0] if n[0] in "+-" else ""
        k = int(n.lstrip("+-"))
        if name == "head":
            printed = max(0, total - k) if sign == "-" else min(k, total)
        else:
            printed = max(0, total - k + 1) if sign == "+" else min(k, total)
        return total if printed > LIMIT else None
    return None


def main():
    try:
        data = json.load(sys.stdin)
        cmd = data.get("tool_input", {}).get("command") or ""
        cwd = data.get("cwd") or os.getcwd()
    except Exception:
        return
    if not cmd or "<<" in cmd:
        return
    for line in cmd.split("\n"):
        try:
            lex = shlex.shlex(line, posix=True, punctuation_chars=True)
            lex.whitespace_split = False
            lex.wordchars += "".join(c for c in map(chr, range(33, 127)) if c not in lex.wordchars and c not in "\"'();<>|&\\")
            lex.commenters = ""
            tokens = list(lex)
        except ValueError:
            continue
        for argv, piped, silent in split_commands(tokens):
            if piped or silent or not argv:
                continue
            n = check(argv, cwd)
            if n:
                reason = f"File has {n} lines: use Read with offset/limit or grep -n"
                print(json.dumps({"hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason}}))
                return


main()
