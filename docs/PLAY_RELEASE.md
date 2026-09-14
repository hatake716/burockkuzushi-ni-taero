# 1.0.4 公開準備・検証記録

確認日：2026-09-14。アプリ本体はフォント変更コミット `303754db860b363e4e65b890ae2dd41c881d6130` の1.0.4です。素材作成ではアプリのソースを変更せず、同じビルド成果物を使用しました。

パッケージ `io.github.hatake716.taero`、versionName `1.0.4`、versionCode `5`。

## このバージョンで追加したもの

日本語・英数字のフォントをDotGothic16に統一しました。タイトル、プレイ中の表示、通知、遊び方、設定、ランキングに適用しています。ゲームルール・音楽・言語選択・保存形式は変更していません。日英の実画面16枚、紹介動画2本、フィーチャーグラフィックも新しいフォントで更新しました。

## 確認結果

| 確認 | 結果と範囲 |
|---|---|
| JVMロジック | 35件成功 |
| Android UI | API 35の専用エミュレーターで日本語13件・英語13件成功。入力、同時タップ、複合効果、保存、設定・ポリシーなど |
| 言語の契約 | 3件成功。日本語優先、その他の言語→英語、保存状態の維持。設定・保存状態を含む |
| メディア収録 | 同じエミュレーターで日英各1回、通常ゲームを開始し自然な終了まで完了。内部のゲーム値を変更せず、タッチ入力だけで操作 |
| Lint | `lintDebug`、`lintRelease` 成功 |
| ビルド | debug APK / AndroidTest APK / release APK / AAB 成功 |
| 署名済みAPK | APK v2・v3署名検証成功。エミュレーターで署名済みAPKの起動と実表示を確認 |
| AAB | bundletool 1.18.3のvalidate成功、jarsignerの署名検証成功 |
| マニフェスト | minSdk 26 / targetSdk 36。INTERNETなし。VIBRATEとアプリ専用AndroidX保護権限のみ |
| ネイティブライブラリ | APK/AABに `.so` なし |
| ストア素材 | 日英の文字数、16 PNGのサイズ・色形式と元スクリーンショットとのRGBピクセル一致、3グラフィック、MP4の全編デコード・映像/音声形式、代替テキスト、ローカルリンク、アプリと公開テキストのポリシー一致を検証 |
| 目視 | グラフィック、実画面、動画の複数場面・字幕と署名済みAPKのタイトルを確認 |
| Webページ | [プライバシーポリシー](https://hatake716.github.io/burockkuzushi-ni-taero/privacy/)はGitHub Pagesで公開済み。9月14日にログイン不要のHTTPS 200、公開HTMLとローカルのSHA-256一致、日英本文とアプリ内ポリシーの一致を再確認。ブラウザーによる表示確認は未実施 |
| 実機 | 前のフォント変更作業でPixel 10aを1.0.4へデータを保持して更新し起動確認。今回の素材収録では実機を操作していない |
| Google Play | Consoleアップロード、Play経由のインストール、審査・公開は未実施 |

targetSdk 36は、確認時点のモバイル新規アプリ・更新向けの要件に合わせています。[公式の対象API要件](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)

動画の音声はオリジナルBGM・SEの編集ミックスで、端末の録音音声ではありません。出力30fpsはエンコード形式であり、すべての端末で30fps以上の動作を保証する測定結果ではありません。音量は約−14 LUFSを目安に調整し、AAC後のピークも0 dBFS未満で確認しています。

## 配布ファイル

元リポジトリでは `artifacts/play-1.0.4/release/`、まとめZIPでは `release/` にあります。

| ファイル | 用途 | SHA-256 |
|---|---|---|
| `taero-1.0.4-play.aab` | Google Playへアップロード | `cd6ab2ce3bc079fe9937b14a8830acd924b36303558659793127c4e908711e31` |
| `taero-1.0.4-release.apk` | 新規インストール・同じ証明書の配布版更新 | `ce8c579d9b7a034ece550263aeba9d6df01e04d6232fa457a1d86ec6fb6a712e` |
| `upload-certificate.pem` | アップロード証明書の公開部分 | ZIP内の `SHA256SUMS` を参照 |
| `mapping.txt` | この署名済みAAB内から取り出したR8マッピング | ZIP内の `SHA256SUMS` を参照 |

ZIPは `artifacts/SurviveBreakout-GooglePlay-1.0.4.zip`。スクリーンショットだけのZIPは `artifacts/SurviveBreakout-Screenshots-1.0.4.zip`。ストア素材ごとの仕様・ハッシュは [asset-manifest.json](store/asset-manifest.json)、ZIP内全ファイルのハッシュは `SHA256SUMS` にあります。ファイルを変更した場合は再生成してください。

## 署名と鍵の保管

アップロード鍵：RSA 4096 bit、alias `taero-upload`。
証明書SHA-256：`3c591e6f73948ec857e74647db8f6dead42cbad922fa4886a4b0e5695acf954c`。
Android用の自己署名証明書です。jarsignerに出る自己署名・CAチェーン・タイムスタンプ未使用の警告は記録しています。署名検証自体は成功しています。

秘密鍵とパスワードは `/home/takeshi/.local/share/taero-signing/` に保存しています。ディレクトリ0700、秘密ファイル0600。**Git・素材ZIPには含めていません。** このディレクトリは開発者が安全な場所にバックアップし、今後の更新で継続利用してください。公開してよいものは `upload-certificate.pem` の公開証明書だけです。

既存アプリがPlay Consoleに登録済みの場合は、この証明書が登録済みアップロード証明書と一致するかを先に確認してください。Play App Signingを利用すると、端末へ届くAPKのアプリ署名鍵がアップロード鍵と別になる場合があります。

既存の実機に入っている開発用APKはAndroid Debug鍵です。今回の配布用APKとは署名が異なるため、そのまま上書きできません。実機のアプリやデータを消して署名を合わせる操作は行っていません。AABも端末へ直接インストールするファイルではありません。

## 元リポジトリからの再生成

JDK 17、Android SDK 36、Python 3、NumPy、FFmpeg（librsvg・drawtext・libx264付き）、アプリに同梱するDotGothic16を利用します。

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest
python3 tools/sign_play_artifacts.py
python3 tools/generate_store_graphics.py
# 専用エミュレーターでStoreCaptureTestを実行してraw.mp4とcapture.jsonを保存後：
python3 tools/generate_store_media.py ja
python3 tools/generate_store_media.py en
python3 tools/prepare_store_index.py
python3 tools/check_store_assets.py
python3 tools/package_play_release.py
```

`StoreCaptureTest` は通常のテスト実行ではスキップし、`-e storeCapture true -e class io.github.hatake716.taero.StoreCaptureTest` を指定した専用エミュレーターだけで動きます。ゲームの乱数を固定していないため、収録結果・スコア・動画時間は実行ごとに変わります。収録用の元MP4とログはGit管理外の `artifacts/play-1.0.4/capture/` に保存しています。

バージョンを更新したときは、署名・素材生成・パッケージ化スクリプトの対象バージョン、文書、検証済みハッシュも合わせて更新します。パッケージ内の資料だけではアプリを再ビルドできないため、再生成には元リポジトリを使ってください。
