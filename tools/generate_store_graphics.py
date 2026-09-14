#!/usr/bin/env python3
"""Author SVG store art from the app's own vector vocabulary; rasterize with FFmpeg/librsvg."""
from pathlib import Path
import math, os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/store'
C=['#41e8ee','#8a9bff','#c788ff','#ff65bf','#ffbc6b']
# Register the bundled font only for this renderer, without changing host fonts.
font_config=tempfile.TemporaryDirectory(prefix='taero-font-')
config=Path(font_config.name)/'fonts.conf'
config.write_text(f'<fontconfig><include>/etc/fonts/fonts.conf</include><dir>{ROOT}/app/src/main/res/font</dir></fontconfig>')
render_env={**os.environ,'FONTCONFIG_FILE':str(config)}
def svg(w,h,body):
 return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
 <defs><radialGradient id="bg"><stop stop-color="#253779"/><stop offset="1" stop-color="#080c24"/></radialGradient>
 <radialGradient id="pink"><stop stop-color="#ff65bf" stop-opacity=".30"/><stop offset="1" stop-color="#ff65bf" stop-opacity="0"/></radialGradient>
 <linearGradient id="block" x2="0" y2="1"><stop stop-color="#fff" stop-opacity=".30"/><stop offset="1" stop-color="#fff" stop-opacity=".04"/></linearGradient></defs>
 <rect width="100%" height="100%" fill="url(#bg)"/>{body}</svg>'''
def text(x,y,s,size,fill='#f0f5ff',weight=400):
 return f'<text x="{x}" y="{y}" font-family="DotGothic16" font-size="{size}" font-weight="400" fill="{fill}">{s}</text>'
def render(name,source,rgba=False):
 p=OUT/'source'/f'{name}.svg';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
 target=OUT/(name+'.png');target.parent.mkdir(parents=True,exist_ok=True)
 subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(p),'-frames:v','1','-pix_fmt','rgba' if rgba else 'rgb24',str(target)],check=True,env=render_env)
 return target
# The central mark is the exact geometry of the Android adaptive foreground.
icon='''<g transform="scale(4.74074074)"><path fill="#34E7F2" d="M22 32h18v12H22z M45 32h18v12H45z M68 32h18v12H68z M22 49h18v12H22z M68 49h18v12H68z"/>
<path fill="#DFFF70" d="M51 46h6v9h9v6h-9v9h-6v-9h-9v-6h9z"/><path fill="none" stroke="#FF59C7" stroke-width="3" d="M32 83L43 73 M25 70L33 66 M73 75L79 80"/>
<circle cx="47" cy="73" r="4" fill="#fff"/></g>'''
render('shared/icon-512',svg(512,512,icon),True)
for lang in ['ja-JP','en-US']:
 body='<ellipse cx="865" cy="295" rx="375" ry="320" fill="url(#pink)"/>'
 for x in range(20,1024,32):
  for y in range(18,500,32):body+=f'<circle cx="{x}" cy="{y}" r="1" fill="#5b75ac" opacity=".24"/>'
 body+=text(86,112,'REVERSE BREAKOUT',17,'#41e8ee')
 if lang=='ja-JP':
  body+=text(82,190,'ブロック崩しに',39)+text(80,285,'耐えろ！',83,'#dfff70')
  body+=text(86,351,'壊すのはCPU。',23,weight=500)+text(86,387,'守るのは、あなた。',23,weight=500)
 else:
  body+=text(82,200,'Survive',62)+text(78,279,'Breakout!',71,'#dfff70')
  body+=text(86,346,'The CPU breaks them.',22,weight=500)+text(86,382,'You bring them back.',22,weight=500)
 for row in range(5):
  for col in range(8):
   x=555+col*45;y=122+row*40;color=C[row]
   if (row,col) in [(1,5),(2,3),(3,4),(4,6)]:
    body+=f'<rect x="{x}" y="{y}" width="38" height="30" rx="5" fill="#15243f" stroke="{color}" stroke-dasharray="4 4" opacity=".8"/>'
    if (row,col)==(2,3):
     body+=f'<circle cx="{x+19}" cy="{y+15}" r="37" fill="none" stroke="#dfff70" stroke-width="2"/><path d="M{x+9} {y+15}h20 M{x+19} {y+5}v20" stroke="#dfff70" stroke-width="3"/>'
   else:
    body+=f'<rect x="{x}" y="{y}" width="38" height="30" rx="5" fill="{color}" fill-opacity=".22" stroke="{color}" stroke-width="2"/><path d="M{x+6} {y+5}h26" stroke="#fff" opacity=".5"/>'
 body+='<path d="M884 414Q786 387 744 308" fill="none" stroke="#ff65bf" opacity=".18" stroke-width="15"/><path d="M884 414Q786 387 744 308" fill="none" stroke="#ff65bf" stroke-width="4"/><circle cx="744" cy="308" r="8" fill="#fff"/><circle cx="744" cy="308" r="23" fill="#ff65bf" opacity=".16"/>'
 for i in range(28):
  a=i*math.tau/28;r=23+i%5*8;x=744+math.cos(a)*r;y=308+math.sin(a)*r
  body+=f'<path d="M{x:.2f} {y:.2f}l{math.cos(a)*10:.2f} {math.sin(a)*10:.2f}" stroke="{C[i%5]}" stroke-width="{2+i%3}" stroke-linecap="round"/>'
 body+='<rect x="791" y="427" width="115" height="8" rx="4" fill="#41e8ee"/>'
 render(f'{lang}/feature-1024x500',svg(1024,500,body))
print('Rendered icon and two feature graphics.')
