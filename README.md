# Hello Reader 📱✨

> **Hello Reader comes with ABSOLUTELY NO WARRANTY, to the extent permitted by applicable law.**
This accessibility service is under development and is not yet complete.
Implementation changes and updates may occur at the developer's discretion to improve quality.

## 🔒 プライバシー ＆ セキュリティ方針 (Watchdogの安全性)

- **個人情報の収集・外部送信は一切行いません**
  本アプリは完全なローカル（端末内）で動作し、画面の読み上げ内容、電話帳データ、操作ログ等を外部サーバーへ送信・収集することは一切ありません。
- **Watchdog（監視プロセス）の目的と安全性**
  独立常駐している Watchdog サービス（`:watchdog`）は、メインサービスが完全フリーズして音声読み上げが途絶えた場合に、緊急復帰案内（TalkBack切替案内やモールス信号振動）を行うためだけに動いています。データ収集目的では一切ありませんので安心してご利用いただけます。

---
**Hello Reader** は、視覚障害者および盲ろう当事者のアクセシビリティ向上を目的とした、Google AOSP TalkBack 100%互換の次世代 Android アクセシビリティ・スクリーンリーダーサービスです。

---

## 🌟 主な特徴と機能

1. **TalkBack 完全準拠のノード走査 ＆ 音声アナウンス**
   - ホーム画面・アプリアイコン・ドック・各種Androidアプリ内の全UI要素を100%スムーズに解読・巡回。
   - 発声テキストは TalkBack 公式標準日本語（「〜しました」「〜できません」）に統一。

2. **Pixel 最先端機能 ＆ 特殊ウィジェットの朗読**
   - Google Pixel ホーム画面の「Geminiに相談」「Gemini Live（ライブで話す）」マイクアイコンを完全解読。
   - Google フォト等の本格アプリ内部ノードをスムーズに音読＆操作可能。

3. **電話着信時の発信者識別 ＆ 応答解読**
   - 電話帳 (`ContactsContract`) と連動し、着信時に相手の名前（例: 「〇〇さんからの着信です」）や電話番号を自動朗読。
   - Pixel の「AIで代行応答（Call Screen / Call Assist）」画面のボタンもスワイプ巡回に対応。

4. **TalkBack OFF時 自前TTS初期設定ガイダンス**
   - TalkBack がまだ無効化されている初回起動時でも、アプリ単体（`MainActivity`）が自前TTSで音声案内。
   - ダイアログ表示前の具体的位置案内（「画面中央右下に許可ボタンがあります」）や「音量上下キー3秒長押しによるTalkBack有効化」のアドバイスを親切に音読。

5. **音連射防止 (150ms / 600ms デバウンス ＆ 自動再帰ガード)**
   - スクロール時や端到達時の `SoundPool` 音連射バグを100%完治。クリアで快適な音響フィードバックを実現。

---

## 💡 開発者向け特徴

- **日本語プログラミング言語「なでしこ」を設計図に採用**
  複雑な読み上げ条件や緊急復帰ロジックの設計図として、定評がある日本語プログラミング言語「なでしこ」形式の仕様書（`.nako3`）を採用。直感的な日本語（「もし〜違えば〜ここまで」）でロジックを整理・管理しています。
- **Kotlin FIRST による安全＆高速な実装**
  Android標準のアクセシビリティガイドラインに準拠し、Nullエラー（NullPointerException）を徹底的に回避するKotlinネイティブコードで構築しています。
- **独立プロセス（:watchdog）による音声トラブル自動復帰**
  メインプロセスとは別に独立したウォッチドッグサービスを常駐させ、万が一のフリーズ時にも自動でTalkBack起動案内やモールス信号振動による復帰案内を行います。

### 💡 なでしこユーザーの皆様へ

当プロジェクトの仕様書（`.nako3`）は、Androidアプリの設計図として作成されています。
中身にはAndroid標準ガイドラインに準拠したモジュール名、関数名、ロジックが含まれており、なでしこ単体で実行するためのスクリプトではないため、そのまま実行するとエラーになります。

実際の動作プログラムは、Android開発者ガイドラインに従って `app/src/main/` 配下に Kotlin コードとして配置・実装されています。

## 🚀 ビルド ＆ インストール方法

本プロジェクトは全自動化スクリプトが整備されています。

### 統合実行スクリプト (`run_all.ps1`)

```powershell
# クリーンビルド ＋ デプロイ ＋ ログキャッチ ＋ 終了キー待ち
.\run_all.ps1 -c -l 1 -w
```

#### 引数オプション一覧

- **`-c` (`-Clean`)**: `gradlew clean` を事前に実行。
- **`-l 1` (`-Log 1`)**: Hello Reader (Android) 専用リアルタイム Logcat を起動。
- **`-l 2` (`-Log 2`)**: TalkBack 専用 Logcat を起動。
- **`-w` (`-Wait`)**: 完了時に Enter キー待ちの一時停止。

### 実機インストールと手動起動

```powershell
# ビルド
.\tools\build.ps1

# 実機デプロイ ＆ アクセシビリティ自動有効化
.\tools\deploy.ps1

# 初回設定画面 (MainActivity) の起動
adb shell am start -n com.stomato.helloreader/.MainActivity
```

---

## 🛠️ 動作要件・対応環境

- **対応OS**: Android 11.0 (API Level 30) ～ Android 15/16 (API Level 35/36+)
- **対応チャネル**: Canary Channel / QPR Beta Channel / Stable Channel
- **16KB ELF ページアライメント**: 0x4000 (16KB) アライメント済

---

## 📜 プロジェクトポリシー ＆ コード貢献

本プロジェクトは、Debian / GNU Linux のオープンソース精神（ボランティア主義・相互リスペクト・開発者の自由）を尊重し、開発者の持続可能性とベストエフォート型のサポートを原則として運営されています。

- **プロジェクトポリシー**: [docs/Project Policy.md](docs/Project%20Policy.md)
- **コード貢献 (PR / パッチ)**: GitHub Pull Request (PR) および `git format-patch` によるパッチ送信を大歓迎します。具体的な作成・送信手順は [Project Policy (コード貢献方法)](docs/Project%20Policy.md#5-コード貢献方法-how-to-contribute) を参照してください。

---

## 📄 ライセンス ＆ 免責事項 (License & Disclaimer)

- **プロジェクト全体**: [The Clear BSD License](LICENSE) (Copyright (c) 2026 stomato463-crypto)
- **引用音源・リソース**: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) (Google AOSP TalkBack Resources / Google LLC)

### 免責事項 (NO WARRANTY)

> **Hello Reader comes with ABSOLUTELY NO WARRANTY, to the extent permitted by applicable law.**
>
> `Hello Reader` は、適用される法律が認める限りにおいて**絶対にいかなる保証も伴わずに**提供されるオープンソースソフトウェアです。本ソフトウェアの使用によって生じた直接的・間接的な損害について、開発者および貢献者は一切の賠償責任を負いません。
