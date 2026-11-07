#!/bin/bash
# Pool Print release script (docs/release.md, Phase 4). Run it through make:
#
#   make release         scripts/release.sh build [--windows-msi <file>]   tests, signed APK, DMG, checks, checksums, notes
#   make release-draft   scripts/release.sh draft   a *draft* GitHub release with those files (gh)
#   scripts/release.sh check-msi <file>             only the Msi checks, e.g. on a downloaded workflow artifact
#
# The Windows Msi can't be built on a Mac (jpackage, D-31): run the manual "Windows" workflow, download the Msi and pass it
# with --windows-msi (make release WINDOWS_MSI=<file>). Without it the release is Android + macOS only, and says so.
#
# The version comes from gradle.properties (Phase 2). Output: build/release/<version>/.
# Runs on the owner's Mac (D-22). Never prints signing values; Gradle reads them from local.properties itself.
# The ci-* commands are the same checks for the optional release workflow (Phase 9, .github/workflows/release.yml).
# Bash 3.2 compatible (macOS /bin/bash).

set -euo pipefail

# The release certificate from docs/release.md, Phase 1.5. Public: it is in every APK we ship.
RELEASE_CERT_SHA256=0260ccfe30cadbf9d5f65bd5a4eb160704d5557f3d978a607d0e86ff7532a269
APP_ID=com.yahyafati.poolprint
MAC_ARCH=arm64 # D-22: Apple Silicon only
MAC_APP_NAME="Pool Print.app"
APK_BUILT=androidApp/build/outputs/apk/release/androidApp-release.apk
DMG_BUILT_DIR=desktopApp/build/compose/binaries/main-release/dmg
# A real Msi carries the bundled runtime (tens of MB); anything much smaller is an error page or a partial download.
MSI_MIN_BYTES=10485760
# The OLE/CFB header every .msi starts with (D0 CF 11 E0 A1 B1 1A E1), as `od -An -tx1` prints it, spaces removed.
MSI_MAGIC=d0cf11e0a1b11ae1
NOTES_TEMPLATE=scripts/release-notes.template.md
# The line the owner deletes after reviewing the notes; `draft` refuses while it is there.
NOTES_REVIEW_MARKER='<!-- Edit:'
# Same rule as buildSrc/PoolPrintVersion.kt.
VERSION_RE='^[1-9][0-9]*\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-rc\.[1-9][0-9]*)?$'

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

die() {
    echo >&2
    echo "release: $*" >&2
    exit 1
}
step() { printf '\n==> %s\n' "$*"; }
info() { echo "    $*"; }

# Set by `build --windows-msi <file>` (empty: no Windows file in this release).
WINDOWS_MSI_SRC=
MOUNT_DIR=
cleanup() {
    if [ -n "$MOUNT_DIR" ]; then
        hdiutil detach "$MOUNT_DIR" -quiet >/dev/null 2>&1 || hdiutil detach "$MOUNT_DIR" -force -quiet >/dev/null 2>&1 || true
        rmdir "$MOUNT_DIR" 2>/dev/null || true
    fi
}
trap cleanup EXIT

# --- Version ----------------------------------------------------------------------------------------------------

gradle_property() {
    # Last assignment wins, as in java.util.Properties.
    grep -E "^[[:space:]]*$1[[:space:]]*=" gradle.properties | tail -n 1 | cut -d= -f2- | tr -d '[:space:]'
}

read_version() {
    VERSION=$(gradle_property poolprint.versionName)
    VERSION_CODE=$(gradle_property poolprint.versionCode)
    grep -Eq "$VERSION_RE" <<<"$VERSION" ||
        die "poolprint.versionName in gradle.properties must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N, got '$VERSION'."
    grep -Eq '^[1-9][0-9]*$' <<<"$VERSION_CODE" ||
        die "poolprint.versionCode in gradle.properties must be a whole number from 1, got '$VERSION_CODE'."
    MAC_VERSION=${VERSION%%-*}
    WIN_VERSION=${VERSION%%-*} # buildSrc windowsPackageVersion: the same, without -rc.N
    TAG="v$VERSION"
    case "$VERSION" in
        *-rc.*) IS_RC=1 ;;
        *) IS_RC= ;;
    esac
    OUT_DIR="build/release/$VERSION"
    APK_NAME="PoolPrint-$VERSION-android.apk"
    DMG_NAME="PoolPrint-$VERSION-macos-$MAC_ARCH.dmg"
    MSI_NAME="PoolPrint-$VERSION-windows-x64.msi"
}

# --- Checks (each one stops the release) ------------------------------------------------------------------------

require_macos_arm64() {
    [ "$(uname -s)" = Darwin ] || die "Releases are built on macOS (the DMG needs jpackage on a Mac)."
    [ "$(uname -m)" = "$MAC_ARCH" ] ||
        die "This Mac is $(uname -m); releases ship a $MAC_ARCH DMG (D-22) and jpackage can't cross-build."
}

require_clean_tree() {
    local dirty
    dirty=$(git status --porcelain)
    [ -z "$dirty" ] || die "The working tree isn't clean. Commit or stash first:
$dirty"
}

fetch_tags() {
    # Best effort: GitHub creates the tag when a release is published, so the checkout may not know it yet.
    if ! git fetch --quiet --tags origin 2>/dev/null; then
        info "warning: couldn't fetch from origin; checking local tags only."
    fi
}

require_new_tag() {
    if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
        die "Tag $TAG already exists. Bump poolprint.versionName and poolprint.versionCode in gradle.properties."
    fi
}

find_build_tools() {
    local sdk="" bt
    if [ -f local.properties ]; then
        sdk=$(grep -E '^sdk\.dir=' local.properties | tail -n 1 | cut -d= -f2- || true)
    fi
    [ -n "$sdk" ] || sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    [ -n "$sdk" ] && [ -d "$sdk/build-tools" ] ||
        die "Android SDK not found (sdk.dir in local.properties, ANDROID_HOME or ANDROID_SDK_ROOT)."
    bt=$(ls "$sdk/build-tools" | sort -V | tail -n 1)
    BUILD_TOOLS="$sdk/build-tools/$bt"
    [ -x "$BUILD_TOOLS/apksigner" ] && [ -x "$BUILD_TOOLS/aapt2" ] ||
        die "apksigner/aapt2 not found in $BUILD_TOOLS."
}

# check_apk <apk>: signed with the release certificate, by exactly one signer, with the expected id and version.
check_apk() {
    local apk=$1 certs digests badging
    [ -f "$apk" ] || die "APK not found: $apk"
    certs=$("$BUILD_TOOLS/apksigner" verify --print-certs "$apk" 2>&1) ||
        die "apksigner rejects the APK:
$certs"
    digests=$(echo "$certs" | sed -n 's/^Signer #[0-9]* certificate SHA-256 digest: //p')
    if [ "$digests" != "$RELEASE_CERT_SHA256" ]; then
        die "APK is not signed with the release key.
    expected: $RELEASE_CERT_SHA256
    found:    $(echo "$digests" | tr '\n' ' ')
Set the poolprint.signing.* keys in local.properties (docs/release.md, Phase 1) and build from the main checkout."
    fi
    info "certificate: $(echo "$certs" | sed -n 's/^Signer #1 certificate DN: //p'), SHA-256 matches"
    # The whole output, then its first line: `| head -n 1` would SIGPIPE aapt2 and trip pipefail.
    badging=$("$BUILD_TOOLS/aapt2" dump badging "$apk" 2>/dev/null) || die "aapt2 can't read the APK: $apk"
    badging=${badging%%$'\n'*}
    grep -q "name='$APP_ID'" <<<"$badging" || die "APK package isn't $APP_ID: $badging"
    grep -q "versionCode='$VERSION_CODE'" <<<"$badging" ||
        die "APK versionCode doesn't match gradle.properties ($VERSION_CODE): $badging"
    grep -q "versionName='$VERSION'" <<<"$badging" ||
        die "APK versionName doesn't match gradle.properties ($VERSION): $badging"
    info "package: $APP_ID, versionCode $VERSION_CODE, versionName $VERSION"
}

# check_app <Pool Print.app>: identifier, architecture, version and an intact (ad-hoc) signature.
check_app() {
    local app=$1 sig plist id short
    [ -d "$app" ] || die "App bundle not found: $app"
    sig=$(codesign -dv "$app" 2>&1) || die "codesign can't read the app's signature:
$sig"
    grep -qx "Identifier=$APP_ID" <<<"$sig" || die "App identifier isn't $APP_ID:
$sig"
    grep -q "^Format=app bundle with Mach-O thin ($MAC_ARCH)$" <<<"$sig" ||
        die "App isn't a $MAC_ARCH-only bundle:
$(echo "$sig" | grep '^Format=')"
    codesign --verify --deep --strict "$app" 2>/dev/null || die "The app's signature doesn't verify (codesign --verify --deep --strict)."
    plist="$app/Contents/Info.plist"
    id=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$plist")
    short=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$plist")
    [ "$id" = "$APP_ID" ] || die "Info.plist CFBundleIdentifier is '$id', expected $APP_ID."
    [ "$short" = "$MAC_VERSION" ] || die "Info.plist version is '$short', expected $MAC_VERSION."
    info "app: $APP_ID, $MAC_ARCH, version $short, $(echo "$sig" | sed -n 's/^Signature=//p') signature verifies"
}

# check_dmg <dmg>: mounts read-only and checks the Pool Print.app inside it (the bundle users actually get).
check_dmg() {
    local dmg=$1
    [ -f "$dmg" ] || die "DMG not found: $dmg"
    MOUNT_DIR=$(mktemp -d "${TMPDIR:-/tmp}/poolprint-dmg.XXXXXX")
    hdiutil attach -readonly -nobrowse -noautoopen -mountpoint "$MOUNT_DIR" "$dmg" -quiet ||
        die "The DMG doesn't mount: $dmg"
    [ -d "$MOUNT_DIR/$MAC_APP_NAME" ] || die "The DMG doesn't contain $MAC_APP_NAME."
    info "DMG mounts and contains $MAC_APP_NAME"
    check_app "$MOUNT_DIR/$MAC_APP_NAME"
    cleanup
    MOUNT_DIR=
}

# check_msi <msi> [copy]: what can be checked without Windows. Not a regular .msi, a download that stopped early, an HTML error
# page or a zip (the workflow artifact is zipped: unzip it first) all fail here. It can't tell a good Msi from a bad one:
# installing it is on the Windows checklist (docs/release.md).
check_msi() {
    local msi=$1 base size magic named
    local by_name=${2:-name} # "copy": the stable release name has no version to compare, the source name was checked
    [ -f "$msi" ] || die "Msi not found: $msi"
    base=$(basename "$msi")
    case "$base" in
        *.msi | *.MSI) ;;
        *) die "Not an .msi file name: $base (if this is the workflow artifact, unzip it first)." ;;
    esac
    size=$(wc -c <"$msi" | tr -d '[:space:]')
    [ "$size" -ge "$MSI_MIN_BYTES" ] ||
        die "$base is only $size bytes; a real Msi is tens of MB. Incomplete download?"
    magic=$(od -An -tx1 -N8 "$msi" | tr -d '[:space:]')
    [ "$magic" = "$MSI_MAGIC" ] ||
        die "$base doesn't start with the Windows Installer (OLE/CFB) header D0 CF 11 E0 A1 B1 1A E1; found: $magic"
    if [ "$by_name" = copy ]; then
        info "msi: $base, $size bytes, Windows Installer header"
        return
    fi
    # The Compose plugin names it 'Pool Print-<MAJOR.MINOR.PATCH>.msi'. A different version means another commit's Msi.
    named=$(sed -n 's/^.*-\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\)\.[mM][sS][iI]$/\1/p' <<<"$base")
    if [ -z "$named" ]; then
        info "warning: can't read the version from the file name '$base'; make sure it was built from this commit (Msi version $WIN_VERSION)."
    elif [ "$named" != "$WIN_VERSION" ]; then
        die "$base looks like version $named, but gradle.properties says the Msi version is $WIN_VERSION. Build the Msi from this commit."
    else
        info "msi: $base, $size bytes, Windows Installer header, version $named matches"
    fi
}

# --- Release files ----------------------------------------------------------------------------------------------

# write_notes <file> <1 if a Windows Msi ships, else empty>: the template with the version, file names, certificate and the
# commits since the last release.
write_notes() {
    local out=$1 since range changes
    # In CI the tag being released already exists on HEAD; skip it so the notes start at the previous release.
    since=$(git describe --tags --abbrev=0 --match 'v[0-9]*' --exclude '*-rc.*' --exclude "$TAG" HEAD 2>/dev/null || true)
    if [ -n "$since" ]; then
        range="$since..HEAD"
    else
        range=HEAD
        since="the first commit"
    fi
    changes=$(mktemp "${TMPDIR:-/tmp}/poolprint-changes.XXXXXX")
    git log --no-merges --reverse --pretty='- %s' "$range" >"$changes"
    [ -s "$changes" ] || echo "- …" >"$changes"
    # All substituted values are checked (version pattern, fixed names, hex digest, tag), so they are safe in awk.
    awk -v version="$VERSION" -v apk="$APK_NAME" -v dmg="$DMG_NAME" -v msi="$MSI_NAME" -v cert="$RELEASE_CERT_SHA256" \
        -v since="$since" -v rc="$IS_RC" -v changes="$changes" -v win="$2" '
        # Lines starting with {{WIN}} are kept (marker removed) only when a Windows Msi ships, {{NOWIN}} lines only when not.
        index($0, "{{WIN}}") == 1 { if (!win) next; sub(/^\{\{WIN\}\}/, "") }
        index($0, "{{NOWIN}}") == 1 { if (win) next; sub(/^\{\{NOWIN\}\}/, "") }
        $0 == "{{CHANGES}}" { while ((getline line < changes) > 0) print line; close(changes); next }
        $0 == "{{PRERELEASE_NOTE}}" {
            if (rc) { print ""; print "**Release candidate** for testing, published as a pre-release. Expect rough edges." }
            next
        }
        {
            gsub(/\{\{VERSION\}\}/, version); gsub(/\{\{APK\}\}/, apk); gsub(/\{\{DMG\}\}/, dmg); gsub(/\{\{MSI\}\}/, msi)
            gsub(/\{\{CERT_SHA256\}\}/, cert); gsub(/\{\{SINCE\}\}/, since)
            print
        }' "$NOTES_TEMPLATE" >"$out"
    rm -f "$changes"
    if grep -q '{{' "$out"; then die "Unfilled placeholder in $out (check $NOTES_TEMPLATE)."; fi
}

# stage_release_files <apk> <dmg> <commit> <step labels ×3>: build/release/<version>/ with the stable file names,
# the notices, SHA256SUMS.txt and release-notes.md. Shared by `build` and `ci-package`. A Windows Msi (WINDOWS_MSI_SRC)
# is included when there is one; without it the release is Android + macOS only.
stage_release_files() {
    local apk=$1 dmg=$2 commit=$3 kept_notes= win= sums
    sums=("$APK_NAME" "$DMG_NAME")
    if [ -n "$WINDOWS_MSI_SRC" ]; then
        win=1
        sums+=("$MSI_NAME")
    fi
    sums+=(THIRD_PARTY_NOTICES.md)
    step "$4 Copying the release files into $OUT_DIR"
    # Keep notes the owner already edited for this version; delete the file to regenerate it.
    if [ -f "$OUT_DIR/release-notes.md" ]; then
        kept_notes=$(mktemp "${TMPDIR:-/tmp}/poolprint-notes.XXXXXX")
        cp "$OUT_DIR/release-notes.md" "$kept_notes"
    fi
    rm -rf "$OUT_DIR"
    mkdir -p "$OUT_DIR"
    cp "$apk" "$OUT_DIR/$APK_NAME"
    cp "$dmg" "$OUT_DIR/$DMG_NAME"
    if [ -n "$win" ]; then cp "$WINDOWS_MSI_SRC" "$OUT_DIR/$MSI_NAME"; fi
    cp THIRD_PARTY_NOTICES.md "$OUT_DIR/THIRD_PARTY_NOTICES.md"
    # Not uploaded: tells `draft` which commit these files come from (the tag goes there).
    echo "$commit" >"$OUT_DIR/.release-commit"
    # The copies are what ships, so check them again.
    check_apk "$OUT_DIR/$APK_NAME"
    if [ -n "$win" ]; then check_msi "$OUT_DIR/$MSI_NAME" copy; fi
    info "copied ${sums[*]}"

    step "$5 Writing SHA256SUMS.txt"
    (cd "$OUT_DIR" && shasum -a 256 "${sums[@]}" >SHA256SUMS.txt && sed 's/^/    /' SHA256SUMS.txt)

    step "$6 Writing release-notes.md"
    if [ -n "$kept_notes" ]; then
        mv "$kept_notes" "$OUT_DIR/release-notes.md"
        info "kept your existing release-notes.md (delete it and rerun to regenerate)"
        if [ -n "$win" ] && ! grep -qF "$MSI_NAME" "$OUT_DIR/release-notes.md"; then
            info "warning: the kept release-notes.md doesn't mention $MSI_NAME; delete it and rerun to get the Windows rows and install steps."
        elif [ -z "$win" ] && grep -qF "$MSI_NAME" "$OUT_DIR/release-notes.md"; then
            info "warning: the kept release-notes.md mentions $MSI_NAME, but this release has no Msi; delete it and rerun."
        fi
    else
        write_notes "$OUT_DIR/release-notes.md" "$win"
        info "drafted from $NOTES_TEMPLATE with the commits since the last release"
    fi
}

cmd_build() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --windows-msi)
                [ $# -ge 2 ] && [ -n "$2" ] || die "--windows-msi needs the path of the Msi."
                WINDOWS_MSI_SRC=$2
                shift 2
                ;;
            --windows-msi=*)
                WINDOWS_MSI_SRC=${1#--windows-msi=}
                [ -n "$WINDOWS_MSI_SRC" ] || die "--windows-msi needs the path of the Msi."
                shift
                ;;
            *) die "unknown option for build: $1 (usage: $0 build [--windows-msi <file>])" ;;
        esac
    done
    read_version
    echo "Pool Print release $VERSION (versionCode $VERSION_CODE, macOS/Windows package $MAC_VERSION, tag $TAG)"

    step "1/8 Checking the checkout"
    require_macos_arm64
    require_clean_tree
    fetch_tags
    require_new_tag
    find_build_tools
    local commit
    commit=$(git rev-parse HEAD)
    info "commit $commit, tree clean, $TAG is new"
    if [ -n "$WINDOWS_MSI_SRC" ]; then
        # Before the long build: a wrong file should not cost ten minutes.
        check_msi "$WINDOWS_MSI_SRC"
        info "Windows Msi will be included; it must come from commit $commit (the workflow run's summary shows its commit)."
    else
        info "WARNING: no Windows Msi (--windows-msi). This release will be Android + macOS only."
    fi

    step "2/8 Running every test suite (make test-all)"
    make --no-print-directory test-all

    step "3/8 Building the release APK and DMG"
    ./gradlew :androidApp:assembleRelease :desktopApp:packageReleaseDmg

    step "4/8 Checking the APK"
    check_apk "$APK_BUILT"

    step "5/8 Checking the DMG and app bundle"
    local dmg_built="$DMG_BUILT_DIR/Pool Print-$MAC_VERSION.dmg"
    check_dmg "$dmg_built"

    stage_release_files "$APK_BUILT" "$dmg_built" "$commit" 6/8 7/8 8/8

    echo
    echo "Done: $OUT_DIR"
    ls -l "$OUT_DIR" | sed '1d; s/^/    /'
    echo
    if [ -z "$WINDOWS_MSI_SRC" ]; then
        echo "NOTE: there is NO Windows file in this release. To add it: run the 'Windows' workflow on GitHub for commit $commit,"
        echo "download the msi artifact, unzip it and run: make release WINDOWS_MSI=<path to the .msi>  (the Windows checks in"
        echo "docs/release.md are the gate for it)."
        echo
    fi
    echo "Next: edit $OUT_DIR/release-notes.md (then delete its '$NOTES_REVIEW_MARKER …' line), push $commit, and run"
    echo "    make release-draft"
}

cmd_draft() {
    read_version
    local commit pre= assets
    [ -d "$OUT_DIR" ] || die "$OUT_DIR doesn't exist. Run 'make release' first."
    for f in "$APK_NAME" "$DMG_NAME" THIRD_PARTY_NOTICES.md SHA256SUMS.txt release-notes.md .release-commit; do
        [ -f "$OUT_DIR/$f" ] || die "$OUT_DIR/$f is missing. Run 'make release' again."
    done
    command -v gh >/dev/null || die "The GitHub CLI (gh) isn't installed: brew install gh"
    gh auth status >/dev/null 2>&1 || die "gh isn't logged in: gh auth login"
    assets=("$OUT_DIR/$APK_NAME" "$OUT_DIR/$DMG_NAME")
    # The Msi is optional; when `make release` copied one, it ships (and its checksum is in SHA256SUMS.txt).
    if [ -f "$OUT_DIR/$MSI_NAME" ]; then
        assets+=("$OUT_DIR/$MSI_NAME")
    else
        info "WARNING: no $MSI_NAME in $OUT_DIR; this draft will have no Windows download."
    fi
    assets+=("$OUT_DIR/THIRD_PARTY_NOTICES.md" "$OUT_DIR/SHA256SUMS.txt")

    step "Checking the release files"
    (cd "$OUT_DIR" && shasum -a 256 -c SHA256SUMS.txt | sed 's/^/    /') || die "Checksums don't match; run 'make release' again."
    if [ -f "$OUT_DIR/$MSI_NAME" ]; then
        grep -qF "  $MSI_NAME" "$OUT_DIR/SHA256SUMS.txt" || die "$MSI_NAME is not listed in SHA256SUMS.txt; run 'make release' again."
        check_msi "$OUT_DIR/$MSI_NAME" copy
    fi
    if grep -qF "$NOTES_REVIEW_MARKER" "$OUT_DIR/release-notes.md"; then
        die "Review $OUT_DIR/release-notes.md first, then delete its '$NOTES_REVIEW_MARKER …' line."
    fi
    if grep -q '…' "$OUT_DIR/release-notes.md"; then
        info "warning: release-notes.md still contains '…' placeholders."
    fi

    step "Checking the commit and tag"
    commit=$(tr -d '[:space:]' <"$OUT_DIR/.release-commit")
    grep -Eq '^[0-9a-f]{40}$' <<<"$commit" || die "$OUT_DIR/.release-commit is damaged; run 'make release' again."
    git fetch --quiet --tags origin || die "Couldn't fetch from origin."
    require_new_tag
    [ -n "$(git branch -r --contains "$commit" 2>/dev/null)" ] ||
        die "Commit $commit (the one these files were built from) isn't pushed to GitHub yet. Push it first."
    if gh release view "$TAG" >/dev/null 2>&1; then
        die "A GitHub release $TAG already exists (maybe an earlier draft). Delete it on GitHub, or bump the version."
    fi
    info "commit $commit is on origin; no tag or release $TAG yet"

    if [ -n "$IS_RC" ]; then pre=--prerelease; fi
    step "Creating the draft release $TAG${pre:+ (pre-release)}"
    gh release create "$TAG" --draft $pre --target "$commit" --title "Pool Print $VERSION" \
        --notes-file "$OUT_DIR/release-notes.md" "${assets[@]}"
    echo
    echo "Draft created; only you can see it. Review it on GitHub and publish it there (that creates the tag $TAG)."
}

# check-msi <file>: the Msi checks of `build --windows-msi`, on their own (e.g. on a downloaded workflow artifact).
cmd_check_msi() {
    read_version
    [ $# -eq 1 ] || die "usage: $0 check-msi <file.msi>"
    check_msi "$1"
}

# --- CI (.github/workflows/release.yml, docs/release.md Phase 9) ------------------------------------------------
# The same checks as `build`, split over the workflow's jobs: the APK is built on Linux, the DMG on a macOS arm64
# runner, and the draft is created from both. Only the workflow calls these.

# ci-check-tag <tag>: the pushed tag is v<versionName> from gradle.properties, and its commit is on origin/main.
cmd_ci_check_tag() {
    read_version
    local tag=${1:-}
    [ "$tag" = "$TAG" ] ||
        die "Tag '$tag' doesn't match gradle.properties (poolprint.versionName=$VERSION, so the tag must be $TAG)."
    git merge-base --is-ancestor HEAD refs/remotes/origin/main ||
        die "The tagged commit $(git rev-parse HEAD) isn't on main. Tag a commit that is pushed to main."
    info "tag $TAG matches gradle.properties (versionCode $VERSION_CODE) and is on main"
}

# ci-check-apk [apk]: release certificate, package, version (default: the Gradle output).
cmd_ci_check_apk() {
    read_version
    find_build_tools
    check_apk "${1:-$APK_BUILT}"
}

# ci-check-dmg [dmg]: mounts it and checks the bundle (default: the Gradle output). macOS arm64 only.
cmd_ci_check_dmg() {
    read_version
    require_macos_arm64
    check_dmg "${1:-$DMG_BUILT_DIR/Pool Print-$MAC_VERSION.dmg}"
}

# ci-package <apk> <dmg>: build/release/<version>/ from the checked binaries, as `build` steps 6–8 do.
cmd_ci_package() {
    read_version
    find_build_tools
    [ $# -eq 2 ] || die "usage: $0 ci-package <apk> <dmg>"
    [ -f "$2" ] || die "DMG not found: $2"
    stage_release_files "$1" "$2" "$(git rev-parse HEAD)" 1/3 2/3 3/3
}

# ci-draft: a draft (pre-)release for the existing tag with the files from ci-package. Publishing stays manual.
cmd_ci_draft() {
    read_version
    local pre= f existing
    for f in "$APK_NAME" "$DMG_NAME" THIRD_PARTY_NOTICES.md SHA256SUMS.txt release-notes.md; do
        [ -f "$OUT_DIR/$f" ] || die "$OUT_DIR/$f is missing; run ci-package first."
    done
    (cd "$OUT_DIR" && shasum -a 256 -c SHA256SUMS.txt | sed 's/^/    /') || die "Checksums don't match."
    # The releases API lists drafts too (the workflow's token may write), so a rerun can't make a second draft.
    existing=$(gh api "repos/{owner}/{repo}/releases" --paginate --jq ".[] | select(.tag_name == \"$TAG\") | .html_url")
    [ -z "$existing" ] || die "A GitHub release for $TAG already exists: $existing"
    if [ -n "$IS_RC" ]; then pre=--prerelease; fi
    step "Creating the draft release $TAG${pre:+ (pre-release)}"
    gh release create "$TAG" --draft $pre --verify-tag --title "Pool Print $VERSION" \
        --notes-file "$OUT_DIR/release-notes.md" \
        "$OUT_DIR/$APK_NAME" "$OUT_DIR/$DMG_NAME" "$OUT_DIR/THIRD_PARTY_NOTICES.md" "$OUT_DIR/SHA256SUMS.txt"
}

# Sourcing the script (for tests) defines the functions without running anything.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
    cmd=${1:-}
    [ $# -eq 0 ] || shift
    case "$cmd" in
        build) cmd_build "$@" ;;
        draft) cmd_draft ;;
        check-msi) cmd_check_msi "$@" ;;
        ci-check-tag) cmd_ci_check_tag "$@" ;;
        ci-check-apk) cmd_ci_check_apk "$@" ;;
        ci-check-dmg) cmd_ci_check_dmg "$@" ;;
        ci-package) cmd_ci_package "$@" ;;
        ci-draft) cmd_ci_draft ;;
        *) die "usage: $0 build [--windows-msi <file>]|draft|check-msi <file> (or: make release, make release-draft); ci-* commands are for the release workflow" ;;
    esac
fi
