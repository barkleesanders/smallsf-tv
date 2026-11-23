#!/bin/bash
set -euo pipefail
export PATH="/opt/homebrew/bin:/usr/bin:/bin:$PATH"
cd "$HOME/smallsf-tv"
printf 'SmallSF-TV.apk.idsig\nrepo-setup.sh\n' >> .gitignore
git rm -q --cached SmallSF-TV.apk.idsig repo-setup.sh
git add .gitignore
git -c user.name="Barklee Sanders" -c user.email="barkleesanders@users.noreply.github.com" commit --amend --reset-author --no-edit
git push -u origin main
if ! gh release view v1.2 >/dev/null 2>&1; then
  gh release create v1.2 SmallSF-TV.apk \
    --title "Small SF TV v1.2" \
    --notes "Fullscreen Fire TV / Android TV app for smallsf.com with full remote controls.

**Install:** download \`SmallSF-TV.apk\` and sideload it (Downloader app or \`adb install\`). Appears in the app row as **Small SF**.

**Remote:** D-pad moves the cursor · OK taps · hold OK + D-pad drags/pans the map · Fast-forward/Rewind zoom · Menu opens Layers · Play/Pause reloads · Back closes a panel, press again to exit.

**v1.2 changes:** keys are now intercepted before the WebView (in v1.1 the D-pad could be swallowed by the page's focus navigation), and zoom uses the site's own camera API. Verified end-to-end on a Fire TV Cube: zoom in/out, Layers open, cursor navigation, and toggling the Boats layer off and back on."
fi
echo FIX_OK
gh repo view barkleesanders/smallsf-tv --json url,visibility --jq '"\(.url) visibility=\(.visibility)"'
gh release view v1.2 --json url,tagName --jq '"\(.url) tag=\(.tagName)"'
