#!/usr/bin/env python3
"""Original deterministic score + synthesized instruments. Requires numpy and ffmpeg.

No samples, external MIDI, or existing melodies. Main progression: F-G-Am-Am
(bVI-bVII-i in A minor), with an original 32-bar melody at 180 BPM.
"""
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np

RATE = 44100
BPM = 180
BEAT = 60 / BPM
ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
ROOT.mkdir(parents=True, exist_ok=True)
rng = np.random.default_rng(716)

def tone(note, duration, kind):
    t = np.arange(int(RATE * duration)) / RATE
    f = 440 * 2 ** ((note - 69) / 12)
    attack = np.minimum(t / .005, 1)
    release = np.minimum((duration - t) / .04, 1).clip(0, 1)
    if kind == "piano":
        v = sum((.66 ** (h - 1)) * np.sin(2 * np.pi * f * h * (1 + h*h*.00002) * t)
                * np.exp(-t * (2.5 + h * 1.9)) for h in range(1, 7))
        return v * attack * release * .44
    if kind == "bass":
        v = np.sin(2*np.pi*f*t) + .22*np.sin(4*np.pi*f*t) + .08*np.sin(6*np.pi*f*t)
        return v * attack * release * np.exp(-t*3) * .7
    # Band-limited bright square/saw hybrid, with 6 Hz vibrato.
    phase = 2*np.pi*f*t + .012*np.sin(2*np.pi*6*t)
    v = sum(np.sin(h*phase)/(h**1.3) for h in range(1, 12) if h*f < RATE/2)
    return v * attack * release * (.75 + .25*np.exp(-t*12)) * .45

def drum(kind):
    duration = {"kick": .30, "snare": .20, "hat": .06, "crash": .8}[kind]
    t = np.arange(int(RATE * duration)) / RATE
    noise = rng.uniform(-1, 1, len(t))
    if kind == "kick":
        phase = 2*np.pi*(47*t + 115*.024*(1-np.exp(-t/.024)))
        return np.sin(phase)*np.exp(-t*15)*.95 + noise*np.exp(-t*180)*.13
    if kind == "snare":
        return (noise*.55+np.sin(2*np.pi*185*t)*.35)*np.exp(-t*23)
    bright = noise - np.roll(noise, 1)*.8
    return bright*np.exp(-t*(70 if kind == "hat" else 6))*(.23 if kind == "hat" else .35)

def write_wav(path, data):
    pcm = (np.clip(data, -1, 1)*32767).astype("<i2")
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1 if pcm.ndim == 1 else 2)
        f.setsampwidth(2); f.setframerate(RATE); f.writeframes(pcm.tobytes())

bars = 32
length = round(bars * 4 * BEAT * RATE)
mix = np.zeros((length, 2), dtype=np.float64)

def add(sound, beat, gain=1, pan=0):
    start = round(beat*BEAT*RATE)
    # Wrap note tails around the exact loop boundary, avoiding a silent seam.
    indices = (start + np.arange(len(sound))) % length
    np.add.at(mix[:, 0], indices, sound*gain*np.sqrt((1-pan)/2))
    np.add.at(mix[:, 1], indices, sound*gain*np.sqrt((1+pan)/2))

chords = [(53,57,60), (55,59,62), (57,60,64), (57,60,64)]
melody = [
    [81, 79, 77, 76, 77, 81, 84, 83],
    [83, 81, 79, 78, 79, 83, 86, 83],
    [84, 83, 81, 76, 79, 81, 88, 86],
    [84, 81, 79, 76, 81, 83, 84, 88],
    [89, 88, 84, 81, 84, 86, 89, 88],
    [86, 83, 79, 83, 86, 88, 86, 83],
    [88, 86, 84, 83, 81, 79, 76, 79],
    [81, 84, 88, 84, 83, 79, 81, 81],
]
for bar in range(bars):
    chord = chords[bar % 4]
    section = bar // 8
    for beat in range(4):
        add(drum("kick"), bar*4+beat, .70)
        if beat in (1, 3): add(drum("snare"), bar*4+beat, .68, .10)
        for half in (0, .5): add(drum("hat"), bar*4+beat+half, .5, -.32)
        add(tone(chord[0]-24, BEAT*.78, "bass"), bar*4+beat, .53)
        add(tone(chord[0]-12, BEAT*.30, "bass"), bar*4+beat+.75, .27)
        for note in chord: add(tone(note, BEAT*.7, "piano"), bar*4+beat+.5, .19, -.2)
    # Fast piano arpeggios move around the stereo field.
    for k in range(16):
        note = chord[[0,1,2,1][k%4]] + 12 + (12 if k%8 == 7 else 0)
        add(tone(note, BEAT*.8, "piano"), bar*4+k/4, .14, .45 if k%2 else -.45)
    line = melody[bar % 8]
    for k, note in enumerate(line):
        if section == 2: note -= 12
        dur = BEAT * (.82 if k == 7 else .44)
        instrument = "piano" if section == 2 else "lead"
        add(tone(note, dur, instrument), bar*4+k/2, .38 if section != 2 else .7, .08)
        # Dotted delay adds festival spaciousness without burying the notes.
        add(tone(note, dur, instrument), bar*4+k/2+.75, .065, -.45)
    if bar % 4 == 0: add(drum("crash"), bar*4, .4, .25)
    if bar % 8 == 7:
        for k in range(6): add(drum("snare"), bar*4+2.5+k*.25, .23+k*.035, .2)

mix = np.tanh(mix * 1.1)
mix *= .89 / np.max(np.abs(mix))
with tempfile.TemporaryDirectory() as tmp:
    wav = Path(tmp)/"music.wav"
    write_wav(wav, mix)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav),
                    "-c:a", "libvorbis", "-q:a", "5", str(ROOT/"festival.ogg")], check=True)

# Every effect is synthesized from oscillators/noise, including firework-like bursts.
t = np.arange(int(RATE*.24))/RATE
phase = 2*np.pi*(1350*t - 1800*t*t)
hit = (np.sin(phase)*.6+rng.uniform(-1,1,len(t))*.3)*np.exp(-t*22)*np.minimum(t/.001,1)
write_wav(ROOT/"burst.wav", hit*.64)
restore = tone(88,.19,"piano")*.7
restore[:len(tone(93,.12,"lead"))] += tone(93,.12,"lead")*.23
write_wav(ROOT/"restore.wav", restore)
notice = np.zeros(int(RATE*.5))
for i, note in enumerate([69,76,81,88]):
    s = tone(note,.20,"lead")*.38
    j = int(i*.085*RATE); notice[j:j+len(s)] += s
write_wav(ROOT/"slot.wav", notice)
t = np.arange(int(RATE*.34))/RATE
write_wav(ROOT/"cheat.wav", np.sin(2*np.pi*150*t)*np.sin(2*np.pi*11*t)*np.exp(-t*5)*.5)
write_wav(ROOT/"bounce.wav", tone(64,.065,"piano")*.26)
print(f"Generated original 180 BPM / {bars} bars / {length/RATE:.6f} s loop and 5 effects in {ROOT}")
