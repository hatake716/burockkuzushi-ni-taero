#!/usr/bin/env python3
"""Sign existing release outputs with the project's private upload key (kept outside Git)."""
from pathlib import Path
import subprocess, shutil
ROOT=Path(__file__).resolve().parents[1]
PRIVATE=Path.home()/'.local/share/taero-signing'
OUT=ROOT/'artifacts/play-1.0.3/release';OUT.mkdir(parents=True,exist_ok=True)
key=PRIVATE/'taero-upload.jks';password=PRIVATE/'store-password'
assert key.is_file() and password.is_file(), 'Upload key must exist in the private signing directory'
aab=OUT/'taero-1.0.3-play.aab';apk=OUT/'taero-1.0.3-release.apk'
shutil.copy2(ROOT/'app/build/outputs/bundle/release/app-release.aab',aab)
subprocess.run(['jarsigner','-keystore',str(key),'-storepass:file',str(password),'-keypass:file',str(password),'-sigalg','SHA256withRSA','-digestalg','SHA-256',str(aab),'taero-upload'],check=True)
subprocess.run(['java','-jar',str(Path.home()/'Android/Sdk/build-tools/36.0.0/lib/apksigner.jar'),'sign','--ks',str(key),'--ks-key-alias','taero-upload','--ks-pass','file:'+str(password),'--out',str(apk),str(ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk')],check=True)
shutil.copy2(PRIVATE/'upload-certificate.pem',OUT/'upload-certificate.pem')
print('Signed release APK and Play AAB:',OUT)
