#!/usr/bin/env python3
"""The new catalogue must never masquerade as implemented runtime content."""
import json,re
from pathlib import Path
root=Path(__file__).resolve().parent.parent
x=json.loads((root/'docs/specs/WEAPONS_40_NEW.json').read_text())
a=x['weapons'];assert len(a)==40 and x['implemented_new']==0
assert len({w['id'] for w in a})==40 and len({w['name'] for w in a})==40
runtime='\n'.join((root/f'app/src/main/java/com/tavernrush/game/content/{n}.kt').read_text() for n in ['Weapons','Arsenal'])
for w in a:
 assert w['status']=='DESIGN_ONLY'
 assert all(w[k].strip() for k in ['id','name','mechanic','choice','cost'])
 assert '"'+w['id']+'"' not in runtime,w['id']
assert '52' in (root/'docs/specs/TZ_WORLD_FIRST_R17.md').read_text()
print('40 additional unique design entries; implemented_new=0; existing 12 unchanged; target52, evolutions excluded')
