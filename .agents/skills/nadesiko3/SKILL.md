---
name: nadesiko3
description: Official Nadeshiko 3 (なでしこ3) programming language syntax guide, command reference, and Markdown specification converter for AI assistants.
---

# 🌸 なでしこ3 (Nadeshiko 3) AI スキルガイド

本スキルは、日本語プログラミング言語「なでしこ3 (nadesiko3)」の文法、公式命令仕様、および Markdown (`.md`) 仕様書から `.nako3` スクリプトへの相互変換をサポートするための AI 指針です。

---

## 1. なでしこ3の基本文法構造

### ① 条件分岐（もし〜違えば〜ここまで）

```nako
もし、条件ならば、
　　処理1
違えば、
　　処理2
ここまで。
```

### ② 繰り返し（〜を繰り返す / 〜の間繰り返す）

```nako
3回、
　　「こんにちは」を表示。
ここまで。

Nが1から10まで繰り返す、
　　Nを表示。
ここまで。
```

### ③ 関数・処理の定義（〜とは / 〜関数）

```nako
●(AとBを)足算とは
　　A + Bを戻す。
ここまで。
```

### ④ 変数と代入

```nako
名前＝「名無し」
メッセージ＝「こんにちは、{名前}さん！」
メッセージを表示。
```

---

## 2. Markdown (`.md`) 仕様書から `.nako3` への変換ルール

Markdown 形式で書かれた仕様書（特に「もし〜違えば〜ここまで」などの自然言語ロジック）を `.nako3` コードに自動変換する際は、以下のステップを適用します。

1. **見出し構造の変換**: `#` 見出しはコメント `//` またはグループコメントに置き換える。
2. **条件節の正規化**: 「もし〜ならば」「違えば」「ここまで」のインデントと全角スペース/Tabを正しく調整する。
3. **文字列・ダイアログ・表示**: 「〜を発声」「〜を表示」を `表示` や `言` などのなでしこ3標準命令にマッピングする。
4. **構文の検証**: Nullエラー回避、変数宣言の自動解決を行う。

---

## 3. なでしこ3 公式標準命令リファレンス (抜粋)

- **画面・表示**: `表示` (`PRINT`), `言` (`SAY`), `描画`
- **文字列**: `文字数`, `区切`, `置換`, `マークダウンHTML変換` (`plugin_markup`)
- **配列・リスト**: `要素数`, `追加`, `挿入`, `ソート`
- **ファイル・ネットワーク**: `読む`, `保存`, `AJAX送信`
- **システム・ダイアログ**: `尋ねる`, `二択`, `警告`

---

## 4. 公式リソース参照リンク

- **公式マニュアル**: [https://n3s.nadesi.com/](https://n3s.nadesi.com/)
- **公式GitHub**: [https://github.com/kujirahand/nadesiko3](https://github.com/kujirahand/nadesiko3)
- **公式命令辞書データ**: `https://raw.githubusercontent.com/kujirahand/nadesiko3/master/doc/command_list.json`
