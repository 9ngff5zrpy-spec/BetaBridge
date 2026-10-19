#!/bin/sh
cd -- "$(dirname -- "$0")" || exit 1
python3 install.py "$@"
status=$?
if [ "$status" -ne 0 ] && [ -t 0 ]; then
  printf '\nPress Enter to close...'
  read -r answer
fi
exit "$status"
