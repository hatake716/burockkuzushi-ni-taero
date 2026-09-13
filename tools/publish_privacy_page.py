#!/usr/bin/env python3
"""Publish only the privacy page to this repository's gh-pages branch.

Uses an isolated index and preserves other existing gh-pages files.
Run only when website publication is authorized. Verify the public URL after
the GitHub Pages deployment finishes; pushing alone is not publication proof.
"""
from pathlib import Path
import json,os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1];REPO='hatake716/burockkuzushi-ni-taero'
def command(args,**kw):
    return subprocess.run(args,cwd=ROOT,check=True,stdout=subprocess.PIPE,**kw).stdout.decode().strip()

site=subprocess.run(['gh','api',f'repos/{REPO}/pages'],cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
if site.returncode:
    assert b'404' in site.stderr,site.stderr.decode()
else:
    config=json.loads(site.stdout)
    assert config['build_type']=='legacy' and config['source']=={'branch':'gh-pages','path':'/'},'Existing Pages source differs; inspect it before changing publication settings.'
existing=command(['git','ls-remote','--heads','origin','refs/heads/gh-pages'])
parent=None
if existing:
    command(['git','fetch','origin','gh-pages']);parent=command(['git','rev-parse','FETCH_HEAD'])
redirect='''<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta http-equiv="refresh" content="0;url=privacy/"><title>Privacy Policy | Survive Breakout!</title><a href="privacy/">プライバシーポリシー / Privacy Policy</a></html>\n'''.encode()
files={'.nojekyll':b'','index.html':redirect,'privacy/index.html':(ROOT/'docs/privacy/index.html').read_bytes()}
with tempfile.TemporaryDirectory(prefix='taero-pages-index-') as tmp:
    env=dict(os.environ,GIT_INDEX_FILE=str(Path(tmp)/'index'))
    command(['git','read-tree',parent] if parent else ['git','read-tree','--empty'],env=env)
    for name,data in files.items():
        blob=command(['git','hash-object','-w','--stdin'],input=data)
        command(['git','update-index','--add','--cacheinfo',f'100644,{blob},{name}'],env=env)
    tree=command(['git','write-tree'],env=env)
    if parent and tree==command(['git','rev-parse',parent+'^{tree}']):commit=parent
    else:
        args=['git','commit-tree',tree]
        if parent:args+=['-p',parent]
        commit=command(args,input=b'Publish bilingual Survive Breakout privacy policy\n')
command(['git','-c','credential.https://github.com.helper=','-c','credential.https://github.com.helper=!/run/current-system/sw/bin/gh auth git-credential','push','origin',f'{commit}:refs/heads/gh-pages'])
if site.returncode:
    # Creating gh-pages can enable Pages automatically before the API POST.
    created=subprocess.run(['gh','api','--method','POST',f'repos/{REPO}/pages','--input','-'],cwd=ROOT,input=json.dumps({'build_type':'legacy','source':{'branch':'gh-pages','path':'/'}}).encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if created.returncode:
        assert b'409' in created.stderr,created.stderr.decode()
        config=json.loads(command(['gh','api',f'repos/{REPO}/pages']))
    else:config=json.loads(created.stdout)
assert config['build_type']=='legacy' and config['source']=={'branch':'gh-pages','path':'/'}
print(json.dumps({'branch':'gh-pages','commit':commit,'html_url':config['html_url'],'status':'Await deployment and verify public HTTPS content'},indent=2))
