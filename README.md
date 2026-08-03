# app-files

**Finder, on `mokuroku`.** Browse a directory: sort it, search it, select in
it, inspect what is selected, and propose what to do with it.

This repo owns two things and nothing else: what a file listing's *columns*
are, and how a `fs/browse` provider's rows become items. Sorting, filtering,
selection and command semantics are
[`mokuroku`](https://github.com/kotoba-lang/mokuroku)'s; the window is
[`mokuroku-ui`](https://github.com/kotoba-lang/mokuroku-ui)'s.

Design: [ADR-2608035000](https://github.com/com-junkawasaki/root/blob/main/90-docs/adr/2608035000-app-standard-application-suite-on-a-shared-catalog-kernel.edn).

```text
fs/browse provider  →  app-files.model  →  mokuroku.catalog  →  mokuroku-ui
  (host, capability)     entries→items       sort/select/…        the window
```

## It reads nothing

There is no code path in this repo that opens a directory. `app-files.source`
takes a `browse-fn` the host supplies, and that function is where the granted
`fs/browse` capability is spent. There is deliberately no fallback that reads
the real filesystem when the grant is missing:

```clojure
source/denied   ;; => {:browse/state :denied :browse/capability "fs/browse" …}
```

An empty directory and a refused grant look identical in a list, and only one
of them is the user's to fix. The window says which.

## Three decisions specific to a file listing

**A directory has no size.** Not `0` — that would claim the folder is empty.
Absent, which the kernel sorts last, so sizeless rows sit at the bottom
instead of pushing real answers off screen.

**A dotfile has no extension.** `.gitignore` is a file *named* `.gitignore`,
not a nameless file of kind `gitignore`. Treating the leading dot as an
extension separator files every dotfile under its own Kind, which is exactly
the noise the Kind column exists to remove.

**Hiding dotfiles happens after the source, not inside it.** `mokuroku`'s
operator set has no "starts-with", and inventing one for this app would mean
either a filter that silently matches nothing or a fork of the shared kernel.
The provider is never asked to lie about what a directory contains.

## The bounded Kotoba profile proves confinement

`src/app_files/bounded.kotoba` does **not** re-model the folder tree —
`kotoba-lang/drive` already does that, and a second copy would be a second
source of truth. It proves the property that is specific to *browsing*:

> every entry in a listing produced under a grant for directory `D` has `D` as
> its parent.

`fs/browse` is granted per directory. A provider returning a row parented
elsewhere would hand the app rows it was never authorised to show, rendered
indistinguishably from legitimate ones.
`test/app_files/bounded_conformance.kotoba` runs both polarities — confinement
must *fail* on a listing containing an escaped row and *hold* on a clean one —
so a validator that always answered one way could not pass it.

## Measured, not admired

`test/app_files/design_quality_gate_test.cljc` renders the real `->page`
output for three states and scores the emitted HTML with
[`design-quality`](https://github.com/kotoba-lang/design-quality)'s
deterministic 12-axis HIG/WCAG rubric. The empty state is included on purpose:
it is the screen most likely to ship unstyled, because it is the one nobody
looks at while building.

Measured 2026-08-04 (design-quality `fb766e2`, mokuroku-ui `58f358c`):
**listing 100.00 / multi-selection 100.00 / awaiting-grant 100.00, aggregate
100.00**. Floors are set at 98.0. Raise them when upstream improves;
never lower them to make a regression pass.

Selection is announced with `aria-selected`, not only a colour class — a
class tells the eye and nothing else. That needed an `:attrs` passthrough on
`liquid-glass/list-row`, which was added upstream rather than worked around
here, because every app in the suite with a selectable list needs it.

The window itself comes from
[`mokuroku-ui`](https://github.com/kotoba-lang/mokuroku-ui). This repo carried
its own copy until that was extracted; keeping it would have meant nine apps
each maintaining their own row grid, inspector and status line, and each
fixing the accessibility gap separately or not at all.

## Test

```sh
clojure -M:local:test    # sibling checkouts (12 tests)
clojure -M:test          # pinned git deps
clojure -M:lint

mkdir -p target/kotoba
clojure -M:kotoba compile test/app_files/bounded_conformance.kotoba \
  --source-path src --target js-browser --output target/kotoba/app-files.mjs
clojure -M:kotoba compile test/app_files/bounded_conformance.kotoba \
  --source-path src --target wasm32-browser --output target/kotoba/app-files.wasm
compiler_src="$(clojure -Spath -M:kotoba | tr ':' '\n' | grep '/compiler/' | head -1)"
nbb scripts/verify-kotoba.cljs target/kotoba/app-files.mjs \
  target/kotoba/app-files.wasm "$(dirname "$compiler_src")/runtime/browser-host.mjs"
```
