#!/usr/bin/env python3
"""Create the local review gallery and accessible screenshot descriptions."""
from pathlib import Path
import html, json
ROOT=Path(__file__).resolve().parents[1]
STORE=ROOT/'docs/store'
alts={
 'ja-JP': ['40個のブロックを守るゲーム画面。空いた場所をタップして再生します。','７秒ごとにCPUのスロットが止まり、効果を自動抽選します。','ネオンの破壊と再生。経過時間と秒数の２乗スコアを上部に表示します。','CPUの玉数・速度・特殊効果と、次の抽選までの時間が分かります。','２本以上の指で触ると「ズルはダメ！」。３秒間ブロックを再生できません。','終了後に生存時間、スコア、再生回数とスロット効果回数を表示します。','上位100件を端末内に保存するランキング。この収録では１件を記録しています。','日本語のタイトル画面。ゲーム開始、遊び方、ランキング、設定を選べます。'],
 'en-US': ['Defend 40 blocks. Tap an empty space with one finger to rebuild it.','The CPU slot selects an effect automatically every seven seconds.','Neon destruction and regeneration. The HUD shows time and survival seconds squared.','Track CPU balls, speed, temporary effects and the countdown to the next slot outcome.','NO CHEATING! Two or more fingers lock block regeneration for three seconds.','The result shows survival time, score, rebuilds and CPU slot effects.','Your best 100 results are stored on this device. This recording contains one result.','English title screen with play, instructions, rankings and settings.']}
css='''*{box-sizing:border-box}body{margin:0;background:#080e20;color:#eef3ff;font:16px/1.7 system-ui,sans-serif}main{max-width:1200px;margin:auto;padding:44px 28px}a{color:#41e8ee}h1{font-size:clamp(28px,4vw,52px);line-height:1.3}h2{font-size:30px;color:#dfff70;margin-top:60px}nav{display:flex;gap:24px;flex-wrap:wrap}.intro{max-width:860px;color:#aebddb}.tag{letter-spacing:.15em;color:#41e8ee}.hero{display:flex;align-items:center;gap:28px}.icon{width:112px;height:112px;border-radius:20px}.feature{width:100%;max-width:1024px;border:1px solid #2d3a60;border-radius:12px}.media{display:grid;grid-template-columns:340px 1fr;gap:32px;margin:24px 0}video{width:100%;max-width:340px;max-height:640px;background:#080e20}details{background:#131d36;border:1px solid #2d3a60;border-radius:12px;padding:16px;margin:12px 0}summary{cursor:pointer;color:#dfff70}pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit}.shots{display:grid;grid-template-columns:repeat(4,1fr);gap:18px}.shots img{width:100%;height:auto;border-radius:12px}.shots figure{margin:0}.shots figcaption{font-size:13px;color:#aebddb}footer{margin-top:60px;padding-top:30px;border-top:1px solid #2d3a60;color:#aebddb}@media(max-width:740px){.media{grid-template-columns:1fr}.shots{grid-template-columns:repeat(2,1fr)}main{padding:24px 18px}.hero{align-items:start}.icon{width:64px;height:64px}}'''
page=f'<!doctype html><html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ブロック崩しに耐えろ！ 公開素材</title><style>{css}</style></head><body><main><div class="hero"><img class="icon" src="shared/icon-512.png" alt="ブロック再生のアイコン"><div><div class="tag">GOOGLE PLAY · MATERIALS 1.0.3</div><h1>ブロック崩しに耐えろ！</h1></div></div><p class="intro">hatake716 / acesmash@gmail.com<br>日本語・英語の実画面、動画、ストア文章の確認用ページです。Google Play・YouTube・公開Webサイトへの掲載は未実施です。</p><nav><a href="#ja-JP">日本語</a><a href="#en-US">English</a><a href="../PLAY_STORE.md">公開手順</a><a href="../privacy/index.html">Privacy</a><a href="../support/index.html">Support</a><a href="../PLAY_DECLARATIONS.md">申告資料</a></nav>'
for locale,label in [('ja-JP','日本語'),('en-US','English')]:
    folder=STORE/locale; paths=sorted((folder/'screenshots').glob('*.png'));assert len(paths)==8
    descriptions=[{'file':f'screenshots/{p.name}','alt':alt} for p,alt in zip(paths,alts[locale])]
    (folder/'screenshot-alt-text.json').write_text(json.dumps(descriptions,ensure_ascii=False,indent=2)+'\n')
    page+=f'<section id="{locale}" lang="{locale}"><h2>{label}</h2><a href="{locale}/feature-1024x500.png"><img class="feature" src="{locale}/feature-1024x500.png" alt="{label} feature graphic"></a><div class="media"><div><video controls preload="metadata" poster="{locale}/screenshots/03-gameplay.png" src="{locale}/preview.mp4"></video><p><a href="{locale}/preview.mp4">MP4</a> · <a href="{locale}/preview.srt">SRT</a> · <a href="{locale}/provenance.json">収録情報</a></p></div><div>'
    for name,title in [('title','アプリ名 / Title'),('short-description','簡単な説明 / Short description'),('full-description','詳しい説明 / Full description'),('release-notes','更新内容 / Release notes'),('youtube-description','動画投稿文 / Video description')]:
        text=(folder/f'{name}.txt').read_text().strip()
        page+=f'<details{" open" if name in ("title","short-description") else ""}><summary>{title} · {len(text)} characters</summary><pre>{html.escape(text)}</pre><a href="{locale}/{name}.txt">TXT</a></details>'
    page+='</div></div><div class="shots">'
    for desc in descriptions:
        url=f'{locale}/{desc["file"]}';alt=html.escape(desc['alt'],quote=True)
        page+=f'<figure><a href="{url}"><img loading="lazy" src="{url}" alt="{alt}"></a><figcaption>{alt}</figcaption></figure>'
    page+='</div></section>'
page+='<footer>Android 1.0.3 · 40 blocks · 7-second CPU slot · Survival seconds²<br>実際のゲーム画面を使用。動画はオリジナル音源の編集ミックスです。<br><a href="CREDITS.md">素材・音源の記録</a> · <a href="asset-manifest.json">ファイルとSHA-256</a></footer></main></body></html>'
(STORE/'index.html').write_text(page)
print('Created bilingual review gallery and 16 alt descriptions.')
