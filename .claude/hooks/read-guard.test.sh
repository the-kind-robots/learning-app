#!/usr/bin/env bash
# Self-test for read-guard.sh. Run: bash .claude/hooks/read-guard.test.sh
set -u
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT
seq 1 500 > "$tmp/big.txt"; seq 1 500 > "$tmp/big 2.txt"; seq 1 50 > "$tmp/small.txt"
fail=0
run() {  # expect(deny|allow) command
  local out
  out=$(jq -nc --arg c "$2" --arg d "$tmp" '{tool_input:{command:$c},cwd:$d}' | bash "$here/read-guard.sh")
  local got=allow; [[ $out == *'"deny"'* ]] && got=deny
  if [[ $got != "$1" ]]; then echo "FAIL want=$1 got=$got: $2"; fail=1; fi
  if [[ $got == allow && -n $out ]]; then echo "FAIL not silent: $2"; fail=1; fi
}
run deny  'cat big.txt'
run deny  'cat big.txt 2>/dev/null'
run deny  "cat 'big 2.txt'"
run deny  'cat "big 2.txt" 2>&1'
run deny  'cat small.txt small.txt small.txt small.txt small.txt small.txt small.txt'
run deny  "cat $tmp/big.txt"
run allow 'cat small.txt'
run allow 'cat big.txt | head -5'
run allow 'cat big.txt | grep 1'
run allow 'cat big.txt > /dev/null'
run allow 'cat missing.txt'
run deny  "sed -n '1,400p' big.txt"
run deny  "sed -n 1,400p big.txt"
run deny  "sed -n '100,\$p' big.txt"
run deny  "sed -n '1,100p;200,450p' big.txt"
run allow "sed -n '10,20p' big.txt"
run allow "sed -n '5p' big.txt"
run allow "sed -n '400,\$p' big.txt"
run allow "sed -n '1,400p' big.txt | wc -l"
run deny  'head -n 400 big.txt'
run deny  'head -400 big.txt'
run deny  'head -n400 big.txt'
run allow 'head -n 20 big.txt'
run allow 'head big.txt'
run allow 'head -n 400 small.txt'
run deny  'tail -n +10 big.txt'
run allow 'tail -n +450 big.txt'
run deny  'tail -n 400 big.txt'
run allow 'tail -n 50 big.txt'
run allow 'tail big.txt'
run allow 'tail -f big.txt'
run deny  'less big.txt'
run deny  'more big.txt'
run allow 'less small.txt'
run deny  'cd /tmp && cat big.txt'
run allow 'ls -la'
run allow 'grep -n foo big.txt'
exit $fail
