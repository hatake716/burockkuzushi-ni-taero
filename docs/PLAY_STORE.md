# Google Play 公開素材ガイド — 1.0.3

作成・公式仕様の確認日：2026-09-13。開発者 **hatake716**、問い合わせ **acesmash@gmail.com**。

日本語・英語のストア文章、実画面16枚、グラフィック、音付き紹介動画2本、署名付きAAB/APK、公開用HTMLを準備しています。Play Consoleへのアップロード、IARC質問票の提出、審査申請、Google Playでの公開は実施していません。

## 最初に開くもの

- [素材プレビュー](store/index.html)：画像・動画・文章の一覧。
- [申告の入力資料](PLAY_DECLARATIONS.md)：データセーフティ、広告、アクセス、年齢区分の判断材料。
- [検証・署名・再生成](PLAY_RELEASE.md)：テスト結果、署名と端末へのインストールの区別。
- [入力用JSON](store/console-settings.json)：人が転記するための参考データです。Play Console公式のインポート形式ではありません。`null` は未決定・未公開です。

## ストア情報

| 項目 | 入力・ファイル |
|---|---|
| パッケージ | `io.github.hatake716.taero` |
| バージョン | `1.0.3` / versionCode `4` |
| デフォルト言語 | 日本語 `ja-JP` |
| 追加翻訳 | 英語 `en-US` |
| 日本語名 / 英語名 | ブロック崩しに耐えろ！ / Survive Breakout! |
| アプリ / カテゴリ | ゲーム / アーケード |
| 連絡先メール | `acesmash@gmail.com` |
| タグ候補 | アーケード、シングルプレーヤー、オフライン。Consoleに存在する選択肢で確認 |
| アプリ名 | 各言語の `title.txt` |
| 簡単な説明 | 各言語の `short-description.txt` |
| 詳しい説明 | 各言語の `full-description.txt` |
| リリースノート | 各言語の `release-notes.txt` |

文章は [store/ja-JP](store/ja-JP) と [store/en-US](store/en-US) にあります。アプリ名30文字、簡単な説明80文字、詳しい説明4,000文字の範囲内で検証します。[Googleのストア情報仕様](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en)

## 画像のアップロード順

| 素材 | 仕様・ファイル |
|---|---|
| 高解像度アイコン | `store/shared/icon-512.png`、512 × 512、RGBA PNG |
| フィーチャーグラフィック | 各言語 `feature-1024x500.png`、1024 × 500、RGB PNG |
| スマートフォンのスクリーンショット | 各言語 `screenshots/`、1080 × 1920、RGB PNG、8枚 |
| 編集元 | `store/source/` 内のSVG |

スクリーンショットはファイル名の01→08の順で登録します。最初の4枚がゲームプレイ、5枚目が同時タップ警告、6枚目が結果、7枚目が端末内ランキング、8枚目がタイトルです。代替テキストは各言語の `screenshot-alt-text.json` にあります。端末タイプはスマートフォンです。未検証のタブレット・TV・Wear OS用として転用しません。

API 35エミュレーター上の1.0.3を通常のタッチ入力で操作して収録しました。ゲーム内部の玉数・時間・スコア・抽選結果を撮影用に書き換えていません。スクリーンショットは内容を変更せず、PNGのカラー形式だけを変換しています。表示値はその実行での記録であり、世界ランキングではありません。各画像は[Googleのプレビュー素材仕様](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)に合わせています。

## 紹介動画

各言語の `preview.mp4` は縦1080 × 1920、H.264、30fps出力、AACステレオ。日本語約36.6秒、英語約25.2秒です。`preview.srt` が字幕、`youtube-title.txt` と `youtube-description.txt` が投稿文です。

全体を通して実際の画面を表示し、画面の外に字幕帯を付けました。映像の早回し、架空のスコア、異なるプレイの継ぎ合わせはありません。音声は同梱のオリジナルBGM・SEから編集用に再構成したミックスです。SEの時刻は収録ログからの近似で、端末音声の録音ではありません。詳細は各言語の `provenance.json` に記録しています。

Play Consoleの動画欄にはMP4を直接入れず、YouTubeの動画URLを登録します。チャンネルに動画を公開または限定公開でアップロードし、埋め込みを許可、動画の広告・年齢制限を外した状態を確認してURLを転記します。このパッケージには実際のYouTube公開URLはまだありません。[Googleの紹介動画仕様](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)

## 公開前の操作

1. プライバシーポリシーは公開済みです。Consoleの該当欄に `https://hatake716.github.io/burockkuzushi-ni-taero/privacy/` を入力してください。[公開ページ](https://hatake716.github.io/burockkuzushi-ni-taero/privacy/)はログイン不要のHTTPS 200と本文一致を確認済みです。任意のサポートページは [support/index.html](support/index.html) を別途公開できます。
2. 既存のアプリ登録がある場合はパッケージ名・アップロード証明書・versionCodeを照合します。新規登録ならこのAABのアップロード鍵を継続利用します。
3. 日本語・英語のストア文章、共通アイコン、各言語の画像を登録します。動画を使用する場合は上記YouTube URLも登録します。
4. [申告資料](PLAY_DECLARATIONS.md)を参照し、Consoleの実際の質問に回答します。対象年齢、配信国、価格、IARC回答と評価は開発者が決定する項目です。架空の評価や公開日を入力しません。
5. `release/taero-1.0.3-play.aab` をテストトラックにアップロードし、Play経由の配信で動作を確認します。アカウントに表示される本人確認・テスト要件を完了してから製品版を申請します。

画像・動画・文章は用意できていますが、公開URLの設定とアカウント上の申告・テスト・審査は別の作業です。公式情報は更新されるため、申請時点でもConsoleの表示を確認してください。

## プライバシーポリシーの更新

公開元は `gh-pages` ブランチの `/privacy/index.html` です。`main` の編集だけでは公開ページは更新されません。本文の元データは各言語の `privacy-policy.txt` とアプリ内文字列で一致させます。`python3 tools/generate_privacy_page.py` でHTMLを生成し、公開が必要なときに `python3 tools/publish_privacy_page.py` を実行します。Pagesのデプロイ完了後、公開URLの本文一致を確認してください。今回の公開記録は [publication.json](store/publication.json) にあります。
