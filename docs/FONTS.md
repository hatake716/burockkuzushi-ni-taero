# ゲーム内フォント

1.0.4から、日本語・英数字の表示を **DotGothic16** に統一しています。16ドットの形を使うレトロな字形で、タイトル、スコア、時間、スロット、通知、ボタン、遊び方、設定、ランキング、アプリ内プライバシーポリシーに適用します。

Canvasでは同梱フォントを使い、数字を含めて同じ書体に揃えています。人工的な太字化でドットの隙間を潰さないよう、元のRegular字形を使います。既存の文字幅に応じた縮小処理は維持しています。Android標準ダイアログのタイトルやスクロール後の項目にも同じ書体を適用します。

## 配布元・ライセンス

- フォント：DotGothic16 Regular
- 著作権：Copyright 2020 The DotGothic16 Project Authors
- [公式プロジェクト](https://github.com/fontworks-fonts/DotGothic16)
- [取得したフォント](https://raw.githubusercontent.com/google/fonts/d5ef175583bb5f7a3b01bc6c4603dd4a1f445f34/ofl/dotgothic16/DotGothic16-Regular.ttf)
- [SIL Open Font License 1.1](https://raw.githubusercontent.com/google/fonts/d5ef175583bb5f7a3b01bc6c4603dd4a1f445f34/ofl/dotgothic16/OFL.txt)
- 取得日：2026-09-14
- フォントSHA-256：`3ad9af88726d42b40f7f365f0dcac785af73cf20ea6f1d5b44e57cc21150b8f1`
- 同梱先：`app/src/main/res/font/dot_gothic.ttf`
- ライセンス同梱先：`app/src/main/assets/licenses/DotGothic16-OFL.txt`

フォントファイルは配布元のものを変更せずに同梱しています。ゲームボーイ風という指定に合わせた書体選択であり、任天堂のフォントデータを利用したものではありません。日本語・英語の全文字列と、スコア・一時停止等で使う記号のUnicode文字マップを確認し、欠落はありません。

既存のGoogle Play素材パッケージ1.0.3とそのスクリーンショット・動画は、当時のフォントを記録したものです。1.0.4を公開する際は新しい画面でストア素材を更新してください。
