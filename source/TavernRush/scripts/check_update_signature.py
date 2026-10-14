#!/usr/bin/env python3
"""Refuse a falsely labelled in-place Android update. Does not install or delete anything."""
import argparse, os, re, subprocess, sys
from pathlib import Path

def metadata(path, tools):
    signature=subprocess.run([str(tools/'apksigner'),'verify','--print-certs',str(path)],capture_output=True,text=True)
    if signature.returncode: raise ValueError('APK не прошёл проверку подписи')
    certs=sorted(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)',signature.stdout))
    if not certs: raise ValueError('Отпечаток сертификата не найден')
    info=subprocess.run([str(tools/'aapt'),'dump','badging',str(path)],capture_output=True,text=True,check=True).stdout
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)'",info)
    if not package: raise ValueError('Пакет/версия не распознаны')
    return package.group(1),int(package.group(2)),[c.lower() for c in certs]

def compare(old,new):
    if old[0]!=new[0]:return 2,'ОТКАЗ: другой пакет; это не обновление с сохранением данных.'
    if old[2]!=new[2]:return 3,'ОТКАЗ: другой сертификат. Не удаляйте установленную игру ради этой сборки.'
    if new[1]<=old[1]:return 4,'ОТКАЗ: versionCode не увеличен.'
    return 0,'OK: пакет и сертификат совпадают, versionCode увеличен. Физическая установка не проверена.'

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('baseline',type=Path);ap.add_argument('candidate',type=Path);ap.add_argument('--build-tools',type=Path)
    a=ap.parse_args();sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    tools=a.build_tools or (Path(sdk)/'build-tools'/'34.0.0' if sdk else None)
    if not tools:ap.error('Укажите ANDROID_HOME или --build-tools')
    try:code,message=compare(metadata(a.baseline,tools),metadata(a.candidate,tools))
    except (OSError,ValueError,subprocess.CalledProcessError) as e:print('ОТКАЗ: '+str(e),file=sys.stderr);return 5
    print(message);return code
if __name__=='__main__':sys.exit(main())
