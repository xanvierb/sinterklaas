#!/usr/bin/env bash
set -euo pipefail

git fetch --tags origin
commit=$(git rev-parse --verify HEAD)

printf 'Laatste vijf versietags:\n'
git tag --list 'v[0-9]*' --sort=-version:refname | sed -n '1,5p'
printf '\nTe publiceren commit: '
git show --no-patch --format='%h %s' "$commit"
if [[ -n "$(git status --porcelain)" ]]; then
    printf 'Deze release bevat alleen wijzigingen die al in deze commit staan.\n'
fi

printf 'Nieuwe versie (bijvoorbeeld 1.0.2, leeg om te stoppen): '
if ! IFS= read -r version || [[ -z "$version" ]]; then
    exit 0
fi

version=${version#v}
if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
    printf 'Gebruik een versie in de vorm 1.0.2.\n' >&2
    exit 1
fi

tag="v$version"
ref="refs/tags/$tag"
if git show-ref --verify --quiet "$ref"; then
    printf 'Versietag %s bestaat al.\n' "$tag" >&2
    exit 1
fi

git tag "$tag" "$commit"
if ! git push origin "$ref"; then
    printf 'De lokale tag is aangemaakt. Probeer opnieuw met: git push origin %s\n' "$ref" >&2
    exit 1
fi

printf 'Tag %s is gepusht. Bekijk de publicatie onder Actions op GitHub.\n' "$tag"
