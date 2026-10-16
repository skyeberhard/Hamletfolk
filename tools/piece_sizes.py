"""Prints the size (x, y, z) and the number of beds of the vanilla village pieces the template catalog uses, read from the server jar.
Usage: python tools/piece_sizes.py [path to paper-26.2.jar]. The numbers in docs/BUILDING_REQUIREMENTS.md come from this.
"""
import gzip, io, struct, sys, zipfile

JAR = sys.argv[1] if len(sys.argv) > 1 else 'paper/run/versions/26.2/paper-26.2.jar'

class R:
    def __init__(self, b):
        self.b = b
        self.i = 0
    def take(self, n):
        v = self.b[self.i:self.i + n]
        self.i += n
        return v
    def byte(self):  return struct.unpack('>b', self.take(1))[0]
    def short(self): return struct.unpack('>h', self.take(2))[0]
    def int(self):   return struct.unpack('>i', self.take(4))[0]
    def long(self):  return struct.unpack('>q', self.take(8))[0]
    def float(self): return struct.unpack('>f', self.take(4))[0]
    def double(self): return struct.unpack('>d', self.take(8))[0]
    def string(self):
        n = struct.unpack('>H', self.take(2))[0]
        return self.take(n).decode('utf-8', 'replace')

def payload(r, t):
    if t == 1: return r.byte()
    if t == 2: return r.short()
    if t == 3: return r.int()
    if t == 4: return r.long()
    if t == 5: return r.float()
    if t == 6: return r.double()
    if t == 7:
        n = r.int(); return list(r.take(n))
    if t == 8: return r.string()
    if t == 9:
        it = r.byte(); n = r.int()
        return [payload(r, it) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            tt = r.byte()
            if tt == 0: break
            name = r.string()
            d[name] = payload(r, tt)
        return d
    if t == 11:
        n = r.int(); return [r.int() for _ in range(n)]
    if t == 12:
        n = r.int(); return [r.long() for _ in range(n)]
    raise ValueError(t)

def load(z, path):
    data = gzip.decompress(z.read(path))
    r = R(data)
    t = r.byte(); r.string()
    return payload(r, t)

def info(z, biome, folder, piece):
    path = f'data/minecraft/structure/village/{biome}/{folder}/{piece}.nbt'
    try:
        d = load(z, path)
    except KeyError:
        return None
    palette = d['palette']
    beds = 0
    chests = 0
    for b in d['blocks']:
        st = palette[b['state']]
        name = st['Name']
        if name.endswith('_bed') and st.get('Properties', {}).get('part') == 'head':
            beds += 1
    sx, sy, sz = d['size']
    return sx, sy, sz, beds

z = zipfile.ZipFile(JAR)
pieces = []
for biome in ['plains', 'desert', 'savanna', 'snowy', 'taiga']:
    pieces += [(biome, 'houses', f'{biome}_small_house_1'), (biome, 'houses', f'{biome}_medium_house_1')]
    pieces += [(biome, 'houses', f'{biome}_big_house_1')] if biome == 'plains' else []
    farm = {'plains': 'plains_small_farm_1', 'desert': 'desert_farm_1', 'savanna': 'savanna_small_farm', 'snowy': 'snowy_farm_1', 'taiga': 'taiga_small_farm_1'}[biome]
    pieces += [(biome, 'houses', farm)]
    if biome != 'snowy':
        pieces += [(biome, 'houses', f'{biome}_large_farm_1')]
    pieces += [(biome, 'houses', f'{biome}_tool_smith_1')]
    centres = {'desert': ['desert_meeting_point_2', 'desert_meeting_point_3'], 'savanna': ['savanna_meeting_point_4', 'savanna_meeting_point_2'],
               'snowy': ['snowy_meeting_point_3', 'snowy_meeting_point_2'], 'taiga': ['taiga_meeting_point_2'],
               'plains': ['plains_fountain_01', 'plains_meeting_point_3']}[biome]
    pieces += [(biome, 'town_centers', c) for c in centres]
print('biome|piece|x|y|z|beds')
for biome, folder, piece in pieces:
    r = info(z, biome, folder, piece)
    print(f'{biome}|{piece}|' + ('missing' if r is None else '|'.join(str(v) for v in r)))
# the other house variants (small 1..8) to see the range of beds
print('--- every small house variant')
for biome in ['plains', 'desert', 'savanna', 'snowy', 'taiga']:
    row = []
    for n in range(1, 10):
        r = info(z, biome, 'houses', f'{biome}_small_house_{n}')
        if r:
            row.append(f'{n}:{r[0]}x{r[2]}/{r[3]}b')
    print(biome, ' '.join(row))
