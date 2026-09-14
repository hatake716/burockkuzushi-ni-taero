#!/usr/bin/env python3
"""Build a scoped Play handoff ZIP, excluding private signing data and raw takes."""
from pathlib import Path
import hashlib,json,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'artifacts'
metadata=json.loads((ROOT/'app/build/outputs/apk/release/output-metadata.json').read_text())
assert metadata['elements'][0]['versionName']=='1.0.3', 'This packager targets 1.0.3; do not mix a newer build/mapping into the archived kit'
subprocess.run(['python3',str(ROOT/'tools/check_store_assets.py')],check=True)
files={}
for folder in ['docs/store','docs/privacy','docs/support']:
    for p in (ROOT/folder).rglob('*'):
        if p.is_file():files[str(p.relative_to(ROOT))]=p.read_bytes()
for name in ['PLAY_STORE.md','PLAY_DECLARATIONS.md','PLAY_RELEASE.md','AUDIO.md']:
    files[f'docs/{name}']=(ROOT/'docs'/name).read_bytes()
for name in ['taero-1.0.3-play.aab','taero-1.0.3-release.apk','upload-certificate.pem']:
    files['release/'+name]=(OUT/'play-1.0.3/release'/name).read_bytes()
files['release/mapping.txt']=(ROOT/'app/build/outputs/mapping/release/mapping.txt').read_bytes()
for name in ['build.txt','ui-ja.txt','ui-en.txt','language.txt','capture-ja.txt','capture-en.txt','apk-signature.txt','aab-signature.txt','apk-badging.txt','bundle-validation.txt','release-smoke.txt','assets-check.txt']:
    files['verification/'+name]=(OUT/'play-1.0.3'/name).read_bytes()
for locale in ['ja','en']:
    name=f'release-privacy-{locale}.png';files['verification/'+name]=(OUT/'play-1.0.3/review'/name).read_bytes()
files['README-FIRST.txt']='''ブロック崩しに耐えろ！ / Survive Breakout! — Google Play materials 1.0.3
Developer: hatake716 / Support: acesmash@gmail.com

最初に docs/PLAY_STORE.md を開いてください。
画像・動画・文章のプレビュー：docs/store/index.html
Google Playアップロード用：release/taero-1.0.3-play.aab
直接インストール用：release/taero-1.0.3-release.apk
申告資料：docs/PLAY_DECLARATIONS.md
検証記録・署名の説明：docs/PLAY_RELEASE.md
公開用プライバシーポリシー：docs/privacy/index.html
公開用サポートページ：docs/support/index.html

日本語・英語それぞれ8枚の実画面、1本の紹介動画、ストア文章があります。
公開プライバシーポリシー：https://hatake716.github.io/burockkuzushi-ni-taero/privacy/
YouTube URLは未発行です。Play Console送信・審査申請・公開は未実施です。
秘密鍵・パスワード・開発用APK・生の収録データはこのZIPに含みません。
既存の開発版アプリに配布版APKを上書きできない場合は署名が異なるためです。
既存データを消して回避しないでください。詳細は検証記録を参照してください。

Integrity: run python3 verify_integrity.py in this extracted directory.
'''.encode()
files['verify_integrity.py']=b'''from pathlib import Path
import hashlib
root=Path(__file__).resolve().parent
lines=(root/'SHA256SUMS').read_text().splitlines()
for line in lines:
    expected,name=line.split('  ',1)
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==expected,name
print(f'PASS: {len(lines)} files match SHA-256 checksums')
'''
for name,data in files.items():
    assert not any(s in name for s in ['.jks','.keystore','keystore.properties','store-password','raw.mp4','app-debug'])
    assert b'-----BEGIN PRIVATE KEY-----' not in data and b'-----BEGIN RSA PRIVATE KEY-----' not in data
    if name.endswith(('.txt','.md','.json','.html','.py')):assert b'storePassword=' not in data and b'keyPassword=' not in data
files['SHA256SUMS']=''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n' for name,data in sorted(files.items())).encode()
path=OUT/'SurviveBreakout-GooglePlay-1.0.3.zip'
with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    for name,data in sorted(files.items()):
        info=zipfile.ZipInfo(name,(2026,9,13,12,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None
    for line in z.read('SHA256SUMS').decode().splitlines():
        digest,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==digest
digest=hashlib.sha256(path.read_bytes()).hexdigest()
path.with_suffix('.zip.sha256').write_text(f'{digest}  {path.name}\n')
print(json.dumps({'file':str(path),'files':len(files),'bytes':path.stat().st_size,'sha256':digest},indent=2))
