#!/usr/bin/env python3
"""Validate Play material dimensions, text, media, links and public metadata."""
from pathlib import Path
import hashlib, json, re, struct, subprocess, xml.etree.ElementTree as ET
from html.parser import HTMLParser

ROOT=Path(__file__).resolve().parents[1];STORE=ROOT/'docs/store'
def png(path,width,height,color):
    b=path.read_bytes();assert b[:8]==b'\x89PNG\r\n\x1a\n'
    w,h,depth,mode=struct.unpack('>IIBB',b[16:26]);assert (w,h,depth,mode)==(width,height,8,color),(path,w,h,depth,mode)
    return {'width':w,'height':h,'color_type':mode}

class Links(HTMLParser):
    def __init__(self):super().__init__();self.links=[]
    def handle_starttag(self,tag,attrs):
        for k,v in attrs:
            if k in ('href','src','poster') and v and not v.startswith(('https:','http:','mailto:','#')):self.links.append(v.split('#')[0])

class PolicyText(HTMLParser):
    def __init__(self):super().__init__();self.language=None;self.text={};self.h1=0
    def handle_starttag(self,tag,attrs):
        if tag=='h1':self.h1+=1
        if tag=='article':
            self.language=dict(attrs).get('lang');self.text[self.language]=[]
    def handle_endtag(self,tag):
        if tag=='article':self.language=None
    def handle_data(self,data):
        if self.language:self.text[self.language].append(data)

def main():
    settings_version=json.loads((STORE/'console-settings.json').read_text())['version_name']
    entries=[]
    def add(path,kind,meta=None):
        entries.append({'file':str(path.relative_to(STORE)),'kind':kind,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),**(meta or {})})
    icon=STORE/'shared/icon-512.png';assert icon.stat().st_size<=1024*1024;add(icon,'icon',png(icon,512,512,6))
    for locale in ['ja-JP','en-US']:
        folder=STORE/locale
        for name,limit in [('title',30),('short-description',80),('full-description',4000),('release-notes',500)]:
            path=folder/f'{name}.txt';value=path.read_text().strip();assert 0<len(value)<=limit,(path,len(value));add(path,'store-copy',{'characters':len(value),'limit':limit})
        feature=folder/'feature-1024x500.png';add(feature,'feature',png(feature,1024,500,2))
        screenshots=sorted((folder/'screenshots').glob('*.png'));assert len(screenshots)==8
        alts=json.loads((folder/'screenshot-alt-text.json').read_text());assert len(alts)==8
        for shot,alt in zip(screenshots,alts):
            assert alt['file']=='screenshots/'+shot.name;assert 0<len(alt['alt'])<=140
            add(shot,'phone-screenshot',{**png(shot,1080,1920,2),'alt':alt['alt']})
        provenance=json.loads((folder/'provenance.json').read_text())
        assert provenance['version']==settings_version
        video=folder/'preview.mp4'
        probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(video)]))
        v=next(s for s in probe['streams'] if s['codec_type']=='video');a=next(s for s in probe['streams'] if s['codec_type']=='audio')
        assert (v['width'],v['height'],v['codec_name'],v['pix_fmt'],v['r_frame_rate'])==(1080,1920,'h264','yuv420p','30/1')
        assert a['codec_name']=='aac' and a['channels']==2 and a['sample_rate']=='48000'
        duration=float(probe['format']['duration']);assert 20<duration<45
        subprocess.run(['ffmpeg','-v','error','-i',str(video),'-f','null','-'],check=True)
        add(video,'preview-video',{'width':1080,'height':1920,'duration_seconds':duration,'video':'H.264, 30 fps output','audio':'AAC, 48 kHz, stereo'})
        strings=ROOT/f'app/src/main/res/{"values-ja" if locale=="ja-JP" else "values"}/strings.xml'
        policy=next(e.text for e in ET.parse(strings).getroot() if e.get('name')=='privacy_body').strip('"').replace('\\n','\n').replace("\\'","'")
        assert policy.strip()==(folder/'privacy-policy.txt').read_text().strip(),f'{locale}: app and public policy differ'
    settings=json.loads((STORE/'console-settings.json').read_text());assert settings['support_email']=='acesmash@gmail.com' and settings['developer_name']=='hatake716'
    assert settings['version_code']==5 and settings['version_name']=='1.0.4' and settings['console_uploaded'] is False
    assert settings['privacy_policy_url'] in (None,'https://hatake716.github.io/burockkuzushi-ni-taero/privacy/')
    assert all(v is None for v in settings['youtube_preview_urls'].values())
    policy=PolicyText();policy.feed((ROOT/'docs/privacy/index.html').read_text());assert policy.h1==1
    for language,locale in [('ja','ja-JP'),('en','en-US')]:
        normalize=lambda s:re.sub(r'\s+',' ',s).strip()
        assert normalize(' '.join(policy.text[language]))==normalize((STORE/locale/'privacy-policy.txt').read_text()),f'{locale}: public HTML policy differs'
    if settings['privacy_policy_url']:
        publication=json.loads((STORE/'publication.json').read_text())
        assert publication['privacy']['status']==200 and publication['privacy']['matches_local']
        assert publication['privacy']['sha256']==hashlib.sha256((ROOT/'docs/privacy/index.html').read_bytes()).hexdigest()
    for path in [STORE/'index.html',ROOT/'docs/privacy/index.html',ROOT/'docs/support/index.html']:
        parser=Links();parser.feed(path.read_text())
        for link in parser.links:
            if link=='asset-manifest.json':continue
            target=path.parent/link
            assert target.is_file() or (target.is_dir() and (target/'index.html').is_file()),(path,link)
    for path in sorted(STORE.rglob('*')):
        if path.is_file() and path.name!='asset-manifest.json' and str(path.relative_to(STORE)) not in {e['file'] for e in entries}:add(path,'supporting-file')
    result={'application_id':'io.github.hatake716.taero','version':'1.0.4','checked_on':'2026-09-14','screenshots':16,'videos':2,'entries':entries}
    (STORE/'asset-manifest.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    print(f'PASS: 16 screenshots, 2 videos, 3 graphics, 2 locale text limits, policy parity, links; {len(entries)} files hashed.')

if __name__=='__main__':main()
