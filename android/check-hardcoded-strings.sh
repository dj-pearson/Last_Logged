#!/usr/bin/env bash
# Fails when a user-facing string literal is passed to a UI function instead of
# being read from strings.xml.
#
# Deliberately NOT flagged: animation labels (`label = "ctaScale"`), Log.* calls,
# and semantics test tags — those are developer-facing identifiers.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$ROOT/app/src/main/java/com/pearsonmedia/lastlogged"

# A capital letter after the quote is the heuristic for "prose, not an id".
PATTERN='(Text|SectionHeader|setTitle|setSubtitle|setContentTitle|setContentText|createChooser)\([^)]*"[A-Z]'

hits="$(grep -rnE "$PATTERN" "$SRC/ui" "$SRC/service" --include='*.kt' \
          | grep -v stringResource \
          | grep -v getString \
          | grep -v 'Log\.' || true)"

if [ -n "$hits" ]; then
  echo "::error::Hardcoded user-facing strings found. Move them to res/values/strings.xml:"
  echo "$hits"
  exit 1
fi

echo "No hardcoded user-facing strings."
