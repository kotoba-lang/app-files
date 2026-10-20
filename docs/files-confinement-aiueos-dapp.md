# app-files（confinement）— itonami（dapp）上の分散型設計

状態: 第 1 スライス実装（2026-10-20）。正本はこの文書。
追跡: Linear JUN-128（親 JUN-116）。

## 位置づけ

app-files は Finder —— ディレクトリ一覧を mokuroku items として扱う
（`fs/browse` capability の grant は 1 ディレクトリ）。**grant の
confinement**（一覧に grant 外の行を混ぜない）が中核の判定。

## このスライス: confinement 判定コア

| 層 | 実体 | 状態 |
|---|---|---|
| 判定コア guest | `aiueos/files_confinement.cljk` — confined（**direct child のみ**。descendant / prefix 兄弟 / ルートは外）/ confine-listing（**escapee が 1 つでもあれば一覧拒否**）/ extension-code（ドットファイルは拡張子無し）/ command-admissible（**open / reveal / copy-path / quicklook のみ** —— rename / trash は実装まで出さない） | **実装済み・実測** |
| 記述子 | `aiueos/files-confinement.edn` | **実装済み** |
| parity | `test/app_files/confinement_parity_guest_test.cljk` — **実物の source.cljk / model.cljk**（mokuroku + amu + kotoba-sema を classpath に）で confinement_test の fixture と同じ行を照合 | **実装済み・実測** |

実測: `amu test aiueos/files_confinement.cljk` → **15/15**（jvm-kir / js / wasm）。
parity → 2 tests / 12 assertions / 0 failures。

## 正本の正確な規則（guest はこれに追従 —— 明文の設計意図つき）

- **direct child のみが grant 内**: 「/w の一覧に /w/src/main.clj は頼んでいない
  もの。パスが grant の下でも」。比較は親ディレクトリの**等値**であって
  prefix ではない（/wother/x は /w の外）。末尾スラッシュは非意味。
- **escapee があれば一覧は拒否**（fail closed）。`bounded_validate.kotoba`
  の `confined?`（抽象モデル版）と実列版の 2 つの表明が互いに正直でいるよう
  テストが保つ —— guest は第 3 の表明として同じ規則を持つ。
- extension は最後のドットより後・小文字。**ドットファイルは拡張子無し**
  （「.gitignore を拡張子 gitignore の名無しファイルと数えると Kind 列が
  ノイズになる」）。
- コマンドは実装があるものだけ（「何もしないボタンは壊れたアプリに見える」）。

## 未実装境界

- rename / trash の判定（provider が実装した時点で追加）。
- page / source の UI 面判定。mokuroku ソート・フィルタの判定。
- mon（DNA）化、aiueos session 載せ（fleet 共通）。
