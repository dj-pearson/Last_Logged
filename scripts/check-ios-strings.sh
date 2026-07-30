#!/usr/bin/env bash
# Guards two iOS localization invariants:
#   1. The String Catalog exists and is valid JSON.
#   2. No user-facing message is assigned as a bare String literal.
#
# SwiftUI `Text("literal")` is already localizable (it takes a
# LocalizedStringKey) and SWIFT_EMIT_LOC_STRINGS extracts it at build time, so
# those are intentionally not flagged. `String`-typed values are the gap: a
# literal assigned to `errorMessage` is displayed verbatim, untranslated.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CATALOG="$ROOT/LastLogged/Localizable.xcstrings"

test -f "$CATALOG" || { echo "::error::Localizable.xcstrings is missing"; exit 1; }
python3 -c "import json,sys; json.load(open(sys.argv[1]))" "$CATALOG" \
  || { echo "::error::Localizable.xcstrings is not valid JSON"; exit 1; }

hits="$(grep -rnE '(errorMessage|successMessage) = "' "$ROOT/LastLogged" --include='*.swift' || true)"
if [ -n "$hits" ]; then
  echo "::error::User-facing String literals found. Use String(localized:):"
  echo "$hits"
  exit 1
fi

echo "iOS localization checks passed."
