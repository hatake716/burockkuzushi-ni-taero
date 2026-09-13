#!/usr/bin/env python3
"""Generate the public page from the same Japanese/English policy as the app."""
from pathlib import Path
from html import escape
ROOT=Path(__file__).resolve().parents[1]
URL='https://hatake716.github.io/burockkuzushi-ni-taero/privacy/'
css='''
:root{color-scheme:dark;--bg:#080e20;--panel:#111b32;--text:#eef3ff;--muted:#b3c0d9;--cyan:#55e3ee;--lime:#dfff70;--line:#30415e}
*{box-sizing:border-box}html{scroll-padding-top:24px}body{margin:0;background:var(--bg);color:var(--text);font:17px/1.85 system-ui,-apple-system,"Noto Sans JP",sans-serif;overflow-wrap:anywhere}
a{color:var(--cyan);text-underline-offset:4px}a:hover{color:var(--lime)}a:focus-visible{outline:3px solid var(--lime);outline-offset:5px;border-radius:3px}
.wrap{max-width:900px;margin:auto;padding:0 28px}.brand{border-top:4px solid var(--cyan);padding-top:42px;padding-bottom:26px;border-bottom:1px solid var(--line)}.brand-name{font-size:clamp(21px,4.5vw,30px);font-weight:800;line-height:1.5;letter-spacing:.02em}.brand-en{color:var(--muted);font-size:16px}.eyebrow{color:var(--lime);font-size:12px;letter-spacing:.16em;margin:0 0 12px}
nav{display:flex;gap:12px;flex-wrap:wrap;padding:24px 0 10px}nav a{display:block;padding:8px 22px;border:1px solid var(--line);border-radius:30px;text-decoration:none;background:var(--panel);font-weight:650}
h1,.policy-title{font-size:clamp(26px,5.5vw,38px);line-height:1.4;margin:34px 0 22px;color:var(--text)}h2,h3{font-size:21px;line-height:1.5;color:var(--lime);margin:36px 0 12px}p{margin:12px 0 24px}.meta{white-space:pre-line;background:var(--panel);border-left:3px solid var(--cyan);padding:18px 22px;border-radius:0 12px 12px 0;color:var(--muted);font-size:15px}.intro{font-size:18px}.policy{padding-bottom:40px}.policy+.policy{border-top:1px solid var(--line);padding-top:18px}footer{padding:30px 0 48px;border-top:1px solid var(--line);color:var(--muted);font-size:14px}.skip{position:absolute;left:24px;top:-100px;background:var(--panel);padding:8px 16px}.skip:focus{top:8px}
@media(max-width:520px){.wrap{padding:0 20px}.brand{padding-top:30px}body{font-size:16px}.intro{font-size:17px}.meta{padding:16px}h2,h3{font-size:20px}}
@media print{:root{--bg:white;--panel:#f5f5f5;--text:black;--muted:#333;--cyan:#075867;--lime:#263a00;--line:#aaa}nav,.skip{display:none}.wrap{max-width:none}h2,h3{break-after:avoid}a{color:inherit}.policy+.policy{break-before:page}}
'''
page=f'''<!doctype html>
<html lang="ja"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>プライバシーポリシー | ブロック崩しに耐えろ！ / Survive Breakout!</title>
<meta name="description" content="ブロック崩しに耐えろ！ / Survive Breakout! のプライバシーポリシー。開発者 hatake716。日本語・English。">
<meta name="theme-color" content="#080e20"><link rel="canonical" href="{URL}">
<style>{css}</style></head><body>
<a class="skip" href="#ja">本文へ / Skip to policy</a>
<div class="wrap"><header class="brand"><p class="eyebrow">HATAKE716 · PRIVACY</p><div class="brand-name">ブロック崩しに耐えろ！</div><div class="brand-en">Survive Breakout!</div></header>
<nav aria-label="言語 / Languages"><a href="#ja" lang="ja">日本語</a><a href="#en" lang="en">English</a></nav><main>
'''
for locale,language in [('ja-JP','ja'),('en-US','en')]:
    blocks=(ROOT/f'docs/store/{locale}/privacy-policy.txt').read_text().strip().split('\n\n')
    first=blocks.pop(0).split('\n');title=first.pop(0);heading='h1' if language=='ja' else 'h2'
    page+=f'<article class="policy" id="{language}" lang="{language}" aria-labelledby="title-{language}">\n<{heading} class="policy-title" id="title-{language}">{escape(title)}</{heading}>\n<p class="meta">{escape(chr(10).join(first))}</p>\n'
    for i,block in enumerate(blocks):
        if '\n' in block:
            name,body=block.split('\n',1);h='h2' if language=='ja' else 'h3'
            page+=f'<section><{h}>{escape(name)}</{h}><p>{escape(body)}</p></section>\n'
        else:page+=f'<p{chr(32)+"class=\"intro\"" if i==0 else ""}>{escape(block)}</p>\n'
    page+='</article>\n'
page+='''</main><footer>hatake716 · <a href="mailto:acesmash@gmail.com">acesmash@gmail.com</a><br><a href="#ja">日本語</a> · <a href="#en">English</a></footer></div></body></html>\n'''
(ROOT/'docs/privacy/index.html').write_text(page)
print('Generated bilingual policy HTML from app-matched policy text.')
