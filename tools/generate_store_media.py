#!/usr/bin/env python3
"""Render store media from StoreCaptureTest recordings; requires NumPy and FFmpeg.

No game state is changed. Screenshots are only re-encoded as RGB PNG. Video
adds a caption band around the entire captured screen. Audio is a new mix of
the game's original assets, timed approximately from the capture event log.
"""
from pathlib import Path
import argparse, json, subprocess, wave, hashlib
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
STORE = ROOT / 'docs/store'
RAW = ROOT / 'app/src/main/res/raw'
RATE = 48000

def run(args):
    subprocess.run(args, check=True)

def samples(path):
    result = subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','f32le','-ac','2','-ar',str(RATE),'-'],check=True,stdout=subprocess.PIPE)
    return np.frombuffer(result.stdout,dtype='<f4').reshape(-1,2)

def timestamp(t):
    ms=round(t*1000)
    return f'{ms//3600000:02}:{ms//60000%60:02}:{ms//1000%60:02},{ms%1000:03}'

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('language', choices=['ja','en']); args=parser.parse_args()
    lang=args.language; locale={'ja':'ja-JP','en':'en-US'}[lang]
    capture=ROOT/f'artifacts/play-1.0.3/capture/{lang}'
    out=STORE/locale; (out/'screenshots').mkdir(parents=True,exist_ok=True)
    scratch=ROOT/f'artifacts/play-1.0.3/video/{lang}';scratch.mkdir(parents=True,exist_ok=True)
    log=json.loads((capture/'screens/capture.json').read_text())
    assert len(log['screenshots'])==8
    for shot in log['screenshots']:
        run(['ffmpeg','-v','error','-y','-i',str(capture/'screens'/shot['file']),'-frames:v','1','-pix_fmt','rgb24',str(out/'screenshots'/shot['file'])])
    probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(capture/'raw.mp4')]))
    start=3.9; duration=float(probe['format']['duration'])-start-.15
    # The raw recording and observation clocks approximately share their origin.
    # There can still be capture/compositor latency; this is not sample accurate.
    recorder_delay=0.0
    music=samples(RAW/'festival.ogg'); count=round(duration*RATE)
    mixed=np.tile(music,(int(np.ceil(count/len(music))),1))[:count].copy()*.60
    sounds={key:samples(RAW/f'{key}.wav') for key in ['burst','restore','slot','cheat']}
    previous={'destroyed':0,'restored':0,'slots':0,'locked':False}
    for event in log['observations']:
        t=event['videoSeconds']-recorder_delay-start
        cues=[]
        for field,key in [('destroyed','burst'),('restored','restore'),('slots','slot')]:
            if event[field]>previous[field]: cues.append((key,min(event[field]-previous[field],3)))
        if event['locked'] and not previous['locked']: cues.append(('cheat',1))
        previous=event
        for key,amount in cues:
            pos=round(t*RATE)
            if pos<0 or pos>=count: continue
            sound=sounds[key]; end=min(count,pos+len(sound))
            mixed[pos:end]+=sound[:end-pos]*(.30 if key=='burst' else .45)*min(1.4,amount**.3)
    fade=min(RATE,len(mixed));mixed[:fade]*=np.linspace(0,1,fade)[:,None];mixed[-fade:]*=np.linspace(1,0,fade)[:,None]
    peak=float(np.abs(mixed).max()); mixed/=max(1,peak/0.94)
    with wave.open(str(scratch/'mix.wav'),'wb') as wav:
        wav.setnchannels(2);wav.setsampwidth(2);wav.setframerate(RATE);wav.writeframes((mixed*32767).astype('<i2').tobytes())
    if lang=='ja':
        captions=[(0,4,'壊すのはCPU。守るのは、あなた。'),(4,9,'空いたブロックを、指１本で再生'),(9,17.0,'７秒ごとのスロットで、攻撃が変わる'),(17.0,20.7,'２本指は、３秒間の再生禁止！'),(20.7,26,'効果が重なる。緊張が加速する。'),(26,duration,'スコアは、生存秒数の２乗。')]
    else:
        captions=[(0,4,'The CPU breaks them. You rebuild.'),(4,9,'Restore empty blocks with one finger.'),(9,17.0,'CPU effects stack every 7 seconds.'),(17.0,20.7,'Two fingers? Rebuilding locks for 3s.'),(20.7,duration,'Your score = survival seconds squared.')]
    srt='\n\n'.join(f'{i}\n{timestamp(a)} --> {timestamp(b)}\n{text}' for i,(a,b,text) in enumerate(captions,1))+'\n'
    (out/'preview.srt').write_text(srt)
    font=subprocess.check_output(['fc-match','-f','%{file}','Noto Sans CJK JP']).decode()
    chain="[0:v]fps=30,scale=1000:1778,pad=1080:1920:40:0:color=0x0b1126,setsar=1,drawbox=x=100:y=1804:w=880:h=3:color=0xb8ff52:t=fill"
    for i,(a,b,caption) in enumerate(captions):
        textfile=scratch/f'caption-{i}.txt';textfile.write_text(caption)
        chain+=f",drawtext=fontfile='{font}':textfile='{textfile}':fontsize={34 if lang=='en' else 38}:fontcolor=0xf3f7ff:borderw=1:bordercolor=0xf3f7ff:x=(w-tw)/2:y=1840:enable='between(t,{a},{b})'"
    chain+='[v];[1:a]loudnorm=I=-14:TP=-1:LRA=9,aresample=48000[a]'
    run(['ffmpeg','-v','warning','-y','-ss',str(start),'-i',str(capture/'raw.mp4'),'-i',str(scratch/'mix.wav'),'-filter_complex',chain,'-map','[v]','-map','[a]','-t',str(duration),'-c:v','libx264','-preset','medium','-crf','19','-pix_fmt','yuv420p','-c:a','aac','-b:a','192k','-movflags','+faststart',str(out/'preview.mp4')])
    provenance={'version':'1.0.3','language':locale,'capture':'Android API 35 emulator, 1080 × 1920, ordinary touch input, unmodified game state',
        'screenshots':'Full screen, no overlays or retouching; lossless RGBA to RGB encoding only. Snapshot log reads state after capture; that observation can differ from the presented frame.',
        'video':{'source_sha256':hashlib.sha256((capture/'raw.mp4').read_bytes()).hexdigest(),'trim_start_seconds':start,'duration_seconds':duration,'screen':'Entire captured screen scaled to 1000 × 1778 inside a 1080 × 1920 caption layout; no speed changes or spliced gameplay.',
            'audio':'New mix of original bundled music and sound effects; event-log timings are approximate (capture/compositor latency is not corrected). Not device audio capture. Random in-game burst pitch and paddle sounds are not reproduced exactly.'},
        'screenshots_capture_log':log['screenshots']}
    (out/'provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
    print(f'{locale}: 8 screenshots and {duration:.2f}s video')

if __name__=='__main__': main()
