# 素材と権利の記録

## このゲームのために制作した素材

- 名称：ブロック崩しに耐えろ！ / Survive Breakout!
- 開発・公開者：hatake716。サポート：acesmash@gmail.com。
- ロゴ・アイコン・フィーチャーグラフィック：本リポジトリの図形・配色を元に制作したSVG。再生成元は `tools/generate_store_graphics.py`、編集可能なSVGは `source/`。
- スクリーンショット・映像：本アプリ1.0.3のAPI 35 Androidエミュレーター実画面。入力スクリプトは `StoreCaptureTest.kt`。背景やHUDの合成、ゲーム内部値の演出用変更は行っていない。
- BGM：NEON REGEN FESTIVAL。180 BPM、F→G→Am→Amを軸とするオリジナル旋律。合成電子音、ピアノ、ベース、ドラム。
- BGM・SEの生成：`tools/generate_audio.py`。外部の楽曲、録音、MIDI、サンプル、既存ゲーム音源は使っていない。[音源ノート](../AUDIO.md)
- 動画音声はゲーム同梱音源を再編集したもの。実機収録音ではない。破壊・再生・抽選・警告のログで近似同期し、ランダムピッチとパドル音は完全再現していない。

「ZUN進行」という和声上の参照は、既存楽曲や特定作家の旋律・音源・キャラクターを利用したという意味ではありません。ストア素材では他作品との提携・公認を示す表現を使用していません。

## フォント・ツール

- 画像の日本語・動画字幕：Noto Sans CJK JP。[公式リポジトリとSIL Open Font License 1.1](https://raw.githubusercontent.com/notofonts/noto-cjk/main/Sans/LICENSE)。フォントファイル自体は配布ZIPに同梱しない。
- グラフィックの欧文：システムのDejaVu Sans等をレンダリング時に使用。[DejaVuのライセンス](https://dejavu-fonts.github.io/License.html)。フォントファイルは同梱しない。
- 動画：FFmpegでH.264/AACにエンコード。画像：FFmpegのlibrsvgレンダラー。これらの実行ツールは素材ZIPには同梱しない。
- アプリ実行ライブラリ：AndroidX / Kotlin。リポジトリに記録された依存関係を使用。配布AAB内のライブラリメタデータを保持。

既存の外部写真、ブランドロゴ、ストック動画、他作品の音楽、生成AIによる架空のゲーム画面は使用していません。YouTubeへの投稿時は、このゲーム用オリジナル音源として登録内容を確認してください。自動権利検出で申し立てが発生しないことを保証する資料ではありません。
