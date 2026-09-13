# Play Console 申告資料

対象：`io.github.hatake716.taero`、1.0.3 / 4。確認日：2026-09-13。
この文書は実装を調べた回答用資料です。Consoleへ送信済みの申告でも、Google/IARCが確定した評価でもありません。

## データセーフティ

| 質問・項目 | 実装に基づく回答材料 |
|---|---|
| ユーザーデータを収集するか | アプリから端末外への送信なし。現実装の回答候補は「いいえ」 |
| ユーザーデータを第三者と共有するか | なし。広告、解析、独自クラッシュ送信SDKを搭載しない |
| ローカル保存 | 生存時間、スコア、日時、再生・抽選回数、上位100件、途中の盤面・玉・効果、設定、端末内のランダムなゲームID |
| 保存目的 | ランキング、途中からの再開、音・振動・演出の設定維持 |
| 保存場所 | アプリ専用SharedPreferences `taero.v1`。サーバーなし |
| 削除 | Androidのアプリ情報からストレージ消去、またはアンインストール |
| アカウント作成 | なし。ログインやユーザープロフィールなし |
| アカウント削除URL | アカウント機能がないため該当なし。プライバシーポリシーURLの要否とは別 |
| 通信中の暗号化 | アプリがデータを送信しないため通信経路自体なし。「暗号化して送信している」とは記入しない |
| 年齢・位置・端末識別子 | 収集しない。端末言語のみ端末内でUI選択に使用 |

Googleは端末内だけで処理する情報をデータセーフティの「収集」に含めないと説明しています。対象は実装・SDKの実際の動作です。[データセーフティの定義](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)

現行のrelease APKは `VIBRATE` とAndroidX由来のアプリ専用 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` を持ちます。`INTERNET`、広告ID、カメラ、位置、マイク、写真、連絡先の権限はありません。後者のアプリ専用権限は保護用で、データの収集目的ではありません。自動バックアップは無効です。端末移行時のOS側の挙動は提供元仕様に従います。

問い合わせメールをユーザーが自主的に送った場合、hatake716はメールアドレスと内容を問い合わせ対応に使います。アプリ内でメールを収集・自動送信する機能はありません。メール、Google Play、OS、Webホストの処理は各提供元のポリシーが適用されます。

## プライバシーポリシー

- 開発者：hatake716
- 連絡先：acesmash@gmail.com
- 公開用ファイル：[日本語・英語HTML](privacy/index.html)
- アプリ内：タイトル → 音と演出 / Sound & effects → プライバシー / Privacy
- ストアに入力するURL：未公開。HTTPSの閲覧可能なページを設置してから入力

データを収集しないアプリでも、公開されたポリシーとアプリ内のポリシー表示またはリンクが必要です。本バージョンにアプリ内表示を追加しました。[Googleのユーザーデータ方針](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en)

## 広告・アクセス・購入

| 項目 | 回答材料 |
|---|---|
| 広告を含む | いいえ |
| アプリ内購入 | なし |
| アプリへのアクセス | 全機能をログイン・契約・招待コード・地域認証なしで利用可能 |
| 審査用アカウント | 不要 |
| 利用に必要な機器 | Android 8.0以降のタッチ操作端末。ネット接続不要 |
| ニュース・医療・金融・政府関連 | いずれの機能も提供しない |
| ユーザー間の通信・UGC | チャット、投稿、プロフィール、外部共有、オンラインランキングなし |

## 審査担当者向け操作説明

日本語：起動して「神になって耐える →」をタップ。CPUが破壊した「＋」の空きブロックを指１本でタップして離すと再生できます。同時に２本以上で触ると３秒間再生禁止になります。CPUのスロットは７秒ごとに効果を自動抽選します。全40ブロックがなくなると終了し、結果を端末内に保存します。右上の一時停止から再開できます。「音と演出」から音・振動・光・プライバシーポリシーを確認できます。

English: Launch the app and tap “Become a god. Survive! →”. Tap and release an empty + block with one finger to rebuild it. Touching with multiple fingers locks rebuilding for three seconds. The CPU slot selects an effect automatically every seven seconds. The game ends when all 40 blocks have been destroyed; results are saved locally. Use the pause control to pause/resume. Sound & effects contains audio, vibration, reduced effects, and the privacy policy. No account or network connection is required.

## IARC・対象年齢：回答前に確認する事実

- 抽選表示にスロットの見た目を使います。７秒ごとにCPUの玉数・速度・貫通・分裂・リセットを選ぶゲーム上の仕組みです。
- 賭け金、チップの購入、課金による抽選、現金や物品の賞品、換金はありません。プレイヤーが賭ける仕組みもありません。
- 人物・動物の殺傷、血液、性的描写、薬物、強い罵倒を表現しません。抽象的なブロックとネオンの破壊演出があります。
- 点滅・発光・粒子・振動・電子音を使用します。音・振動・控えめな光と粒子を設定可能です。
- 「スロットなので必ず特定の区分」「現金がないので必ず全年齢」とは決めません。Consoleのギャンブル模倣・カジノ要素等の実際の設問に、この見た目と挙動を踏まえて回答してください。
- 対象年齢層は開発者が選択し、IARCのコンテンツレーティングは質問票で取得します。子ども向けを選ぶ場合は該当するポリシーを別途確認します。

[IARCの質問・評価](https://support.google.com/googleplay/android-developer/answer/9898843?hl=en)、[コンテンツ評価の考え方](https://support.google.com/googleplay/android-developer/answer/6161080?hl=en)

## アカウント上で未決定・未実施

プライバシーポリシー公開URL、任意のサポートページURL、YouTube動画URL、価格、配信国、対象年齢層、IARC回答・評価、開発者確認、該当アカウントのテスト要件、Playトラックへのアップロードと審査申請。`console-settings.json` の未確定欄を推測で埋めていません。
