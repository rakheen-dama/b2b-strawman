# Emulates PostgreSQL jsonb::text output (object keys ordered by byte length then bytes, ", " / ": " separators,
# JSON string escaping) for a JSON file and prints md5 + length — comparable with obs-sql.sql [f4] md5(content::text).
# usage: python3 -I jsonb-text-md5.py <file.json>...   (added in the 648F CodeRabbit round 2)
import json, hashlib, sys
def s(x):
    out=['"']
    for ch in x:
        o=ord(ch)
        if ch=='"': out.append('\\"')
        elif ch=='\\': out.append('\\\\')
        elif ch=='\n': out.append('\\n')
        elif ch=='\t': out.append('\\t')
        elif ch=='\r': out.append('\\r')
        elif ch=='\b': out.append('\\b')
        elif ch=='\f': out.append('\\f')
        elif o<0x20: out.append('\\u%04x'%o)
        else: out.append(ch)
    out.append('"'); return ''.join(out)
def j(v):
    if isinstance(v,dict):
        ks=sorted(v.keys(), key=lambda k:(len(k.encode()), k.encode()))
        return '{'+', '.join(s(k)+': '+j(v[k]) for k in ks)+'}'
    if isinstance(v,list): return '['+', '.join(j(x) for x in v)+']'
    if isinstance(v,str): return s(v)
    if v is True: return 'true'
    if v is False: return 'false'
    if v is None: return 'null'
    return json.dumps(v)
for f in sys.argv[1:]:
    t=j(json.load(open(f,encoding='utf-8')))
    print(f.split('/')[-1], hashlib.md5(t.encode()).hexdigest(), len(t))
