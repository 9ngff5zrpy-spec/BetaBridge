# SPDX-License-Identifier: GPL-3.0-or-later
#!/usr/bin/env python3
"""Convert locally installed Beta assets to a scoped Bedrock resource pack.

Requires Pillow. Source images come from the user's original Beta client JAR;
sounds are accepted only when their SHA-1 agrees with the pinned Beta asset index.
No download, world edits, behavior pack, or gameplay claims are made here.
"""
import argparse
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import re
import shutil
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CLIENT = None
RESOURCES = None
DEST = ROOT / 'resourcepack/build'
VERSION = [0, 2, 6]
parser = argparse.ArgumentParser()
parser.add_argument('--client',type=Path,required=True)
parser.add_argument('--resources',type=Path,required=True,help='Classic resource directory or Java assets/objects directory')
parser.add_argument('--reference-dir',type=Path,default=ROOT/'.cache/references')
parser.add_argument('--output-dir',type=Path,default=ROOT/'build/resourcepack')
parser.add_argument('--profile',choices=['b1.6.6'],default='b1.6.6')
parser.add_argument('--boat-renderer',choices=['native','custom'],default='native')
args = parser.parse_args()
if args.profile=="b1.6.6":
    CLIENT=args.client.resolve()
    DEST=args.output_dir.resolve()
    VERSION=[0,3,7]
REFS=args.reference_dir.resolve()
RESOURCES=args.resources.resolve()
expected=json.loads((ROOT/'dependencies.lock.json').read_text())['minecraftClient']['sha256']
if hashlib.sha256(CLIENT.read_bytes()).hexdigest()!=expected:raise ValueError('Expected the pinned original Beta 1.6.6 client JAR')
PACK_DIR=DEST.parent

def reference(name):
    return json.loads(re.sub(r'^\s*//.*$', '', (REFS / name).read_text(), flags=re.M))

def paths(value):
    if isinstance(value, str): yield value
    elif isinstance(value, list):
        for entry in value: yield from paths(entry)
    elif isinstance(value, dict):
        if 'path' in value: yield value['path']
        elif 'textures' in value: yield from paths(value['textures'])

terrain_ref = reference('bedrock-terrain-texture.json')
item_ref = reference('bedrock-item-texture.json')
sound_ref = reference('bedrock-sound-definitions.json')['sound_definitions']
known_paths = set(paths(list(terrain_ref['texture_data'].values()))) | set(paths(list(item_ref['texture_data'].values())))
entity_paths = {x['path'].removeprefix('resource_pack/') for x in json.loads((REFS/'bedrock-samples-tree.json').read_text())['tree']}
if DEST.exists(): raise ValueError('Output directory already exists; choose a new --output-dir')
DEST.mkdir(parents=True)
source = zipfile.ZipFile(CLIENT)
terrain = Image.open(io.BytesIO(source.read('terrain.png'))).convert('RGBA')
items = Image.open(io.BytesIO(source.read('gui/items.png'))).convert('RGBA')
report = {'version': VERSION, 'clientJarSha256': hashlib.sha256(CLIENT.read_bytes()).hexdigest(),
          'assetIndexSha256': hashlib.sha256((ROOT/'data/legacy-audio-index.json').read_bytes()).hexdigest(),
          'officialReferences': {
              name: hashlib.sha256((REFS/name).read_bytes()).hexdigest()
              for name in ['bedrock-terrain-texture.json','bedrock-item-texture.json','bedrock-sound-definitions.json']},
          'textures': [], 'sounds': [], 'soundEvents': [], 'limitations': [
              'Initial conversion: not every Beta texture, geometry, animation, UI or sound trigger is replaced.',
              'Client lighting, biome tint and rendering remain Bedrock; no recreation of the Beta renderer.',
              f'Fog approximates Beta {"short (64 blocks)" if args.profile=="b1.6.6" else "normal (128 blocks)"} render distance; exponential fluid fog, biome/sky blending and local brightness adaptation differ.',
              'Fluids, fire and portal animations, bed geometry, armor on entities and humanoid UV conversion remain pending.',
              'Resource pack has no behavior module; movement, damage and recipes are controlled separately.']}

def tile(atlas, index, path):
    assert path in known_paths, f'Path absent from official Bedrock atlas: {path}'
    output = DEST / (path + '.png')
    output.parent.mkdir(parents=True, exist_ok=True)
    x, y = index % 16 * 16, index // 16 * 16
    atlas.crop((x,y,x+16,y+16)).save(output)
    report['textures'].append({'path': path, 'source': 'terrain.png' if atlas is terrain else 'gui/items.png', 'tile': index})

blocks = {
'grass_top':0,'stone':1,'dirt':2,'grass_side':3,'planks_oak':4,'stone_slab_side':5,'stone_slab_top':6,
'brick':7,'tnt_side':8,'tnt_top':9,'tnt_bottom':10,'web':11,'flower_rose':12,'flower_dandelion':13,'sapling_oak':15,
'cobblestone':16,'bedrock':17,'sand':18,'gravel':19,'log_oak':20,'log_oak_top':21,'iron_block':22,'gold_block':23,
'diamond_block':24,'mushroom_red':28,'mushroom_brown':29,'gold_ore':32,'iron_ore':33,'coal_ore':34,'bookshelf':35,
'cobblestone_mossy':36,'obsidian':37,'crafting_table_top':43,'furnace_front_off':44,'furnace_side':45,
'sponge':48,'glass':49,'diamond_ore':50,'redstone_ore':51,'leaves_oak':52,'leaves_oak_opaque':53,
'crafting_table_side':59,'crafting_table_front':60,'furnace_front_on':61,'furnace_top':62,
'mob_spawner':65,'snow':66,'ice':67,'grass_side_snowed':68,'cactus_top':69,'cactus_side':70,'cactus_bottom':71,
'clay':72,'reeds':73,'jukebox_top':75,'jukebox_side':74,'torch_on':80,'door_wood_upper':81,'door_iron_upper':82,
'ladder':83,'farmland_wet':86,'farmland_dry':87,'lever':96,'door_wood_lower':97,'door_iron_lower':98,
'redstone_torch_on':99,'pumpkin_top':102,'netherrack':103,'soul_sand':104,'glowstone':105,
'rail_normal_turned':112,'redstone_torch_off':115,'log_spruce':116,'log_birch':117,'pumpkin_side':118,
'pumpkin_face_off':119,'pumpkin_face_on':120,'cake_top':121,'cake_side':122,'cake_inner':123,'noteblock':74,
'rail_normal':128,'repeater_off':131,'leaves_spruce':132,'leaves_spruce_opaque':133,'leaves_birch':52,'leaves_birch_opaque':53,
'cake_bottom':124,'repeater_on':147,'lapis_ore':160,'lapis_block':144,'redstone_dust_cross':164,
'redstone_dust_line':165,'sandstone_top':176,'sandstone_normal':192,'sandstone_bottom':208,
}
if args.profile=='b1.6.6':
    chest_textures={}
    for index in (25,26,27,41,42,57,58):
        path=f'textures/blocks/betabridge_chest_{index}'
        out=DEST/(path+'.png');out.parent.mkdir(parents=True,exist_ok=True)
        terrain.crop((index%16*16,index//16*16,index%16*16+16,index//16*16+16)).save(out)
        report['textures'].append({'path':path,'source':'terrain.png','tile':index})
        chest_textures[f'betabridge_chest_{index}']={'textures':path}
    texture_file=DEST/'textures/terrain_texture.json'
    texture_file.write_text(json.dumps({'resource_pack_name':'betabridge_beta_chests',
        'texture_name':'atlas.terrain','texture_data':chest_textures},indent=2)+'\n')
    # Preserve the entire pinned official HUD's bindings and input controls.
    # Only remove the seven-pixel XP gap in the two centred Classic layouts;
    # Pocket/top-corner layouts and the hotbar itself are unchanged.
    hud_source=REFS/'chest-hud-official/hud_screen.json'
    raw=hud_source.read_text()
    raw=re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*|/\*[\s\S]*?\*/',lambda m:m.group(1) or '',raw)
    hud=json.loads(re.sub(r',\s*([}\]])',r'\1',raw))
    moved=[]
    for panel in ('centered_gui_elements_at_bottom_middle','centered_gui_elements_at_bottom_middle_touch'):
        for control in hud[panel]['controls']:
            for name,value in control.items():
                if name.split('@')[0] in ('heart_rend','armor_rend','bubbles_rend_0','bubbles_rend_1'):
                    old=value['offset'].copy();value['offset'][1]+=7
                    moved.append({'panel':panel,'control':name,'before':old,'after':value['offset'].copy()})
    assert len(moved)==8
    ui=DEST/'ui';ui.mkdir(exist_ok=True)
    (ui/'hud_screen.json').write_text(json.dumps(hud,ensure_ascii=False,indent=2)+'\n')
    report['classicChests']={'source':'original e.a and terrain.png','cubePixels':16,
        'singleAndDouble':True,'textureTiles':[25,26,27,41,42,57,58],'manualInventoryAndVisualCheck':'pending'}
    report['hudSpacing']={'sourceSha256':hashlib.sha256(hud_source.read_bytes()).hexdigest(),
        'downwardPixels':7,'changes':moved,'phoneCheckPending':True}
if args.profile=='b1.6.6':
    # Original Beta 1.6.6 texture indexes, checked against its block classes.
    blocks.update({'rail_golden':163,'rail_golden_powered':179,'rail_detector':195,
                   'trapdoor':84,'tallgrass':39,'deadbush':55,'fern':56,
                   'sapling_spruce':63,'sapling_birch':79})
# Some modern identifiers use a different filename; resolve only against Mojang's reference.
aliases = {'furnace_front_off':'furnace_front_off','mob_spawner':'mob_spawner','flower_rose':'flower_rose',
           'rail_normal_turned':'rail_normal_turned','rail_normal':'rail_normal'}
for name, index in blocks.items():
    path = 'textures/blocks/' + aliases.get(name,name)
    if path in known_paths: tile(terrain,index,path)
    else: report.setdefault('unmappedBlockPaths',[]).append(path)

# Bedrock's grass-side TGA alpha is a tint mask, not transparency. The opaque
# Java PNG tinted all the dirt green and applied a second tint to the green rim.
# Preserve the Beta dirt RGB; tint only the grass, using its green luminance.
grass_side = terrain.crop((48, 0, 64, 16)).copy()
for y in range(16):
    for x in range(16):
        r, g, b, a = grass_side.getpixel((x, y))
        is_grass = g > r and g > b
        grass_side.putpixel((x, y), (g, g, g, 255) if is_grass else (r, g, b, 0))
(DEST/'textures/blocks/grass_side.png').unlink()
grass_side.save(DEST/'textures/blocks/grass_side.tga')
report['grassSideTint'] = {'format':'TGA', 'alpha':'255 grass tint mask / 0 original dirt RGB',
                          'reference':'Mojang bedrock-samples grass_side.tga', 'sourceTile':3}

# Beta ModelBoat: five box parts, 64x32 atlas, no paddles. The game entity,
# riding links and movement remain under Geyser / the original Beta server.
boat_texture = 'textures/entity/boat/boat_oak.png'
out = DEST/boat_texture; out.parent.mkdir(parents=True, exist_ok=True)
out.write_bytes(source.read('item/boat.png'))
report['textures'].append({'path':boat_texture, 'source':'item/boat.png'})
bones = [{'name':'betabridge_hull', 'pivot':[0,0,0], 'rotation':[0,90,0]}]
parts = [
    ('bottom', [0,3,0], [-12,-5,-3], [24,16,4], [-90,0,0], [0,8]),
    ('left', [11,3,0], [1,4,-1], [20,6,2], [0,90,0], [0,0]),
    ('right', [-11,3,0], [-21,4,-1], [20,6,2], [0,-90,0], [0,0]),
    ('front', [0,3,-9], [-10,4,-10], [20,6,2], [0,180,0], [0,0]),
    ('back', [0,3,9], [-10,4,8], [20,6,2], [0,0,0], [0,0]),
]
if args.profile == 'b1.6.6':
    # Original ex/po/fc: convert the render matrix scale(-1,-1,1),
    # including rotation points and box origins. Do not mix local/world origins.
    bones = [{'name':'boat', 'pivot':[0,0,0], 'rotation':[0,90,0]}]
    parts = [
        ('bottom',[0,-4,0],[-12,-12,-3],[24,16,4],[-90,0,0],[0,8]),
        ('left',[11,-4,0],[1,-3,-1],[20,6,2],[0,90,0],[0,0]),
        ('right',[-11,-4,0],[-21,-3,-1],[20,6,2],[0,-90,0],[0,0]),
        ('front',[0,-4,-9],[-10,-3,-10],[20,6,2],[0,180,0],[0,0]),
        ('back',[0,-4,9],[-10,-3,8],[20,6,2],[0,0,0],[0,0]),
    ]
for name, pivot, origin, size, rotation, uv in parts:
    bones.append({'name':name,'parent':('boat' if args.profile=='b1.6.6' else 'betabridge_hull'),'pivot':pivot,'rotation':rotation,
                  'cubes':[{'origin':origin,'size':size,'uv':uv}]})
boat_identifier='geometry.boat'
if args.profile=='b1.6.6':
    # Bake the five orthogonal Beta parts for a registered custom Bedrock actor.
    # ViaLegacy floors center-.3 to the next 1/32: -10/32. The appearance adapter
    # removes Geyser's native boat offset, so restore 5 pixels from feet to centre.
    boat_identifier='geometry.boat' if args.boat_renderer=='native' else 'geometry.betabridge_boat'
    def face(uv,size,rotation=0):
        result={'uv':uv,'uv_size':size}
        if rotation:result['uv_rotation']=rotation
        return result
    cap_uv={'north':face([2,2],[20,6]),'south':face([24,2],[20,6]),
            'west':face([0,2],[2,6]),'east':face([22,2],[2,6]),
            'up':face([2,0],[20,2]),'down':face([22,0],[20,2])}
    side_uv={'west':face([2,2],[20,6]),'east':face([24,2],[20,6]),
             'north':face([0,2],[2,6]),'south':face([22,2],[2,6]),
             'up':face([2,0],[20,2],90),'down':face([22,0],[20,2],90)}
    floor_uv={'up':face([32,12],[24,16],90),'down':face([4,12],[24,16],90),
              'north':face([28,12],[4,16],90),'south':face([0,12],[4,16],90),
              'west':face([4,8],[24,4]),'east':face([28,8],[24,4])}
    bones=[{'name':'betabridge_hull','pivot':[0,0,0],'cubes':[
        {'origin':origin,'size':size,'uv':uv} for origin,size,uv in [
            ([-8,-8,-12],[16,4,24],floor_uv),
            ([-10,-4,-12],[20,6,2],cap_uv),([-10,-4,10],[20,6,2],cap_uv),
            ([-10,-4,-10],[2,6,20],side_uv),([8,-4,-10],[2,6,20],side_uv)]]}]
    if args.boat_renderer=='custom':
        for cube in bones[0]['cubes']:
            cube['origin'][1]+=6 # native origin -.375 -> generic actor feet
    else:
        cubes=bones[0]['cubes']
        bones=[{'name':name,'pivot':[0,0,0],'cubes':[cube]}
               for name,cube in zip(('bottom','front','back','left','right'),cubes)]
        # Retain native animation targets without drawing modern paddles.
        bones.extend({'name':name,'pivot':[0,0,0]} for name in ('paddle_left','paddle_right','water_patch'))
boat = {'format_version':'1.12.0','minecraft:geometry':[{
    'description':{'identifier':boat_identifier,'texture_width':64,'texture_height':32,
                   'visible_bounds_width':3,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},
    'bones':bones}]}
if args.profile=='b1.6.6' and args.boat_renderer=='custom':
    boat['format_version']='1.21.0'
    entity={'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':'betabridge:boat','min_engine_version':'1.21.0',
        'materials':{'default':'entity'},'textures':{'default':boat_texture.removesuffix('.png')},
        'geometry':{'default':boat_identifier},'render_controllers':['controller.render.betabridge_boat']}}}
    entity_path=DEST/'entity/betabridge_boat.entity.json';entity_path.parent.mkdir(parents=True,exist_ok=True)
    entity_path.write_text(json.dumps(entity,indent=2)+'\n')
    controller={'format_version':'1.8.0','render_controllers':{'controller.render.betabridge_boat':{
        'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']}}}
    controller_path=DEST/'render_controllers/betabridge_boat.render_controllers.json';controller_path.parent.mkdir(parents=True,exist_ok=True)
    controller_path.write_text(json.dumps(controller,indent=2)+'\n')
elif args.profile=='b1.6.6':
    # Native boat has a built-in renderer: it requires geometry.boat. Preserve
    # Mojang's native client-entity contract; no generic/custom actor/controller.
    # Native boat still uses the legacy geometry contract. Avoid per-face
    # uv_rotation / modern cube schemas by baking the UV transforms into a
    # compact box-UV atlas and emitting the legacy 1.8 geometry document.
    fallback_cubes=[copy.deepcopy(b['cubes'][0]) for b in bones[:5]]
    for cube in fallback_cubes:cube['origin'][1]+=6
    fallback_geometry={'format_version':'1.21.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.betabridge_boat','texture_width':64,'texture_height':32,
            'visible_bounds_width':3,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},
        'bones':[{'name':'betabridge_hull','pivot':[0,0,0],'cubes':fallback_cubes}]}]}
    fallback_entity={'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':'betabridge:boat','min_engine_version':'1.21.0','materials':{'default':'entity'},
        'textures':{'default':'textures/entity/boat/beta_source'},'geometry':{'default':'geometry.betabridge_boat'},
        'render_controllers':['controller.render.betabridge_boat']}}}
    for path,data in [('models/entity/betabridge_boat_fallback.geo.json',fallback_geometry),
            ('entity/betabridge_boat.entity.json',fallback_entity),
            ('render_controllers/betabridge_boat.render_controllers.json',{'format_version':'1.8.0','render_controllers':{
                'controller.render.betabridge_boat':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']}}})]:
        target=DEST/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data,indent=2)+'\n')
    original_atlas=Image.open(io.BytesIO(source.read('item/boat.png'))).convert('RGBA')
    native_atlas=Image.new('RGBA',(128,128))
    for bone,base in zip(bones[:5],([0,0],[0,32],[0,32],[0,48],[0,48])):
        cube=bone['cubes'][0];dx,dy,dz=cube['size'];u,v=base
        destinations={'west':(u,v+dz,dz,dy),'north':(u+dz,v+dz,dx,dy),
            'east':(u+dz+dx,v+dz,dz,dy),'south':(u+2*dz+dx,v+dz,dx,dy),
            'up':(u+dz,v,dx,dz),'down':(u+dz+dx,v,dx,dz)}
        for side,(tx,ty,tw,th) in destinations.items():
            face=cube['uv'][side];sx,sy=face['uv'];sw,sh=face['uv_size']
            pixels=original_atlas.crop((sx,sy,sx+sw,sy+sh))
            if face.get('uv_rotation'):pixels=pixels.rotate(-face['uv_rotation'],expand=True)
            assert pixels.size==(tw,th),(bone['name'],side,pixels.size,(tw,th))
            native_atlas.paste(pixels,(tx,ty))
        cube['uv']=base
    native_atlas.save(DEST/boat_texture)
    (DEST/'textures/entity/boat/beta_source.png').write_bytes(source.read('item/boat.png'))
    (DEST/'textures/entity/boat/boat_oak.texture_set.json').write_text(json.dumps({
        'format_version':'1.16.100','minecraft:texture_set':{'color':'boat_oak'}},indent=2)+'\n')
    boat={'format_version':'1.8.0','geometry.boat':{'texturewidth':128,'textureheight':128,
        'visible_bounds_width':3,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0],'bones':bones}}
    entity=json.loads((REFS/'boat-official/boat.entity.json').read_text())
    assert entity['minecraft:client_entity']['description']['identifier']=='minecraft:boat'
    assert entity['minecraft:client_entity']['description']['geometry']['default']=='geometry.boat'
    for key in entity['minecraft:client_entity']['description']['textures']:
        entity['minecraft:client_entity']['description']['textures'][key]=boat_texture.removesuffix('.png')
    entity_path=DEST/'entity/boat.entity.json';entity_path.parent.mkdir(parents=True,exist_ok=True)
    entity_path.write_text(json.dumps(entity,indent=2)+'\n')
out=DEST/'models/entity/betabridge_boat.geo.json';out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(json.dumps(boat,indent=2)+'\n')
report['boat']={'source':('Beta 1.6.6 ex/po/fc matrices and original item/boat.png' if args.profile=='b1.6.6' else 'Beta 1.4_01 ModelBoat (dy)'),
                'geometry':boat_identifier,'paddles':False,'visualVerification':'pending on Bedrock'}
if args.profile=='b1.6.6':
    native=args.boat_renderer=='native'
    report['boat'].update({'renderer':('native minecraft:boat / built-in geometry.boat' if native else 'registered betabridge:boat actor, opaque entity material'),'bakedOrthogonalHull':True,
        'legacyCenterToModernFeet':10/32,'geyserOffset':.375 if native else 0,'modelOriginOffsetPixels':-1 if native else 5,
        'collisionWidth':1.6 if native else 1.5,'collisionHeight':.6,'nativeBuoyancy':native})
# Original fence visual dimensions (ct.l) with the full 16x24x16 collision
# registered separately by BetaFenceBlock. Modern Bedrock supports height 24.
if args.profile == 'b1.6.6':
    geometries=[]
    for mask in range(16):
        cubes=[{'origin':[-2,0,-2],'size':[4,16,4]}]
        east,north,south,west=[bool(mask & bit) for bit in (1,2,4,8)]
        along_x=east or west or not (north or south)
        if along_x:
            # Bedrock model X is reflected relative to block/world X. A 180°
            # turn corrected X but also reversed Z, breaking the corners.
            low=-8 if east else -1; high=8 if west else 1
            for y in (6,12): cubes.append({'origin':[low,y,-1],'size':[high-low,3,2]})
        if north or south:
            low=-8 if north else -1; high=8 if south else 1
            for y in (6,12): cubes.append({'origin':[-1,y,low],'size':[2,3,high-low]})
        for cube in cubes:
            x,y,z=cube['size']
            cube['uv']={face:{'uv':[0,0],'uv_size':size} for face,size in
                [('north',[x,y]),('south',[x,y]),('west',[z,y]),('east',[z,y]),('up',[x,z]),('down',[x,z])]}
        geometries.append({'description':{'identifier':'geometry.betabridge_fence_'+str(mask),
            'texture_width':16,'texture_height':16,'visible_bounds_width':2,'visible_bounds_height':2,'visible_bounds_offset':[0,.5,0]},
            'bones':[{'name':'fence','pivot':[0,0,0],'cubes':cubes}]})
    (DEST/'models/entity/betabridge_fence.geo.json').write_text(json.dumps({'format_version':'1.12.0','minecraft:geometry':geometries},indent=2)+'\n')
    report['fence']={'visualSource':'Beta 1.6.6 ct.l','collisionSource':'original gk.d',
        'collisionPixels':{'origin':[-8,0,-8],'size':[16,24,16]},'variants':16,'modelToBlockAxes':[-1,1,1],'phoneCheckPending':True}
for stage in range(8): tile(terrain,88+stage,f'textures/blocks/wheat_stage_{stage}')
catalog=json.loads((ROOT/'data/catalog-assets.json').read_text())
wool_tiles = [variant[0] for variant in next(b for b in catalog['blocks'] if b['id']==35)['texturesByMetadata']]
colors = ['white','orange','magenta','light_blue','yellow','lime','pink','gray','silver','cyan','purple','blue','brown','green','red','black']
for color,index in zip(colors,wool_tiles):
    tile(terrain,index,'textures/blocks/wool_colored_'+color)
for stage in range(10):
    path=f'textures/environment/destroy_stage_{stage}'
    # Damage overlay textures are outside terrain_texture.json but present in official tree.
    if path+'.png' in entity_paths:
        output=DEST/(path+'.png'); output.parent.mkdir(parents=True,exist_ok=True)
        terrain.crop((stage*16,240,stage*16+16,256)).save(output)
        report['textures'].append({'path':path,'source':'terrain.png','tile':240+stage})

item_names={259:'flint_and_steel',260:'apple',261:'bow_standby',262:'arrow',263:'coal',264:'diamond',265:'iron_ingot',
266:'gold_ingot',280:'stick',281:'bowl',282:'mushroom_stew',287:'string',288:'feather',289:'gunpowder',295:'seeds_wheat',
296:'wheat',297:'bread',318:'flint',319:'porkchop_raw',320:'porkchop_cooked',321:'painting',322:'apple_golden',
323:'sign',324:'door_wood',325:'bucket_empty',326:'bucket_water',327:'bucket_lava',328:'minecart_normal',329:'saddle',
330:'door_iron',331:'redstone_dust',332:'snowball',333:'boat_oak',334:'leather',335:'bucket_milk',336:'brick',337:'clay_ball',
338:'reeds',339:'paper',340:'book_normal',341:'slimeball',342:'minecart_chest',343:'minecart_furnace',344:'egg',
345:'compass_item',346:'fishing_rod_uncast',347:'clock_item',348:'glowstone_dust',349:'fish_raw',350:'fish_cooked',
352:'bone',353:'sugar',354:'cake',355:'bed_red',356:'repeater',357:'cookie',2256:'record_13',2257:'record_cat'}
if args.profile=='b1.6.6': item_names[358]='map_filled'
for mat,ids in [('iron',[267,256,257,258,292]),('wood',[268,269,270,271,290]),('stone',[272,273,274,275,291]),
                ('diamond',[276,277,278,279,293]),('gold',[283,284,285,286,294])]:
    for tool,item_id in zip(['sword','shovel','pickaxe','axe','hoe'],ids): item_names[item_id]=f'{mat}_{tool}'
for start,material in [(298,'leather'),(302,'chainmail'),(306,'iron'),(310,'diamond'),(314,'gold')]:
    for offset,part in enumerate(['helmet','chestplate','leggings','boots']): item_names[start+offset]=f'{material}_{part}'
catalog=json.loads((ROOT/'data/catalog-assets.json').read_text())
for item in catalog['items']:
    if item['id'] not in item_names: continue
    path='textures/items/'+item_names[item['id']]
    if path in known_paths: tile(items,item['icon'],path)
    else: report.setdefault('unmappedItemPaths',[]).append(path)
dye_tiles=[78,94,110,126,142,158,174,190,79,95,111,127,143,159,175,191]
for color,index in zip(['black','red','green','brown','blue','purple','cyan','silver','gray','pink','lime','yellow','light_blue','magenta','orange','white'],dye_tiles):
    tile(items,index,'textures/items/dye_powder_'+color)

compatible_entities={'mob/creeper.png':'creeper/creeper','mob/ghast.png':'ghast/ghast','mob/ghast_fire.png':'ghast/ghast_shooting',
                     'mob/slime.png':'slime/slime','mob/squid.png':'squid','mob/wolf.png':'wolf/wolf','mob/wolf_angry.png':'wolf/wolf_angry'}
for old,new in compatible_entities.items():
    path='textures/entity/'+new+'.png'
    assert path in entity_paths, path
    output=DEST/path;output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(source.read(old))
    report['textures'].append({'path':path,'source':old})

index=json.loads((ROOT/'data/legacy-audio-index.json').read_text())
asset_entries=dict(index['objects']);asset_entries.update(index['custom'])
copied={}
def sound_assets(group):
    result=[]
    matcher=re.compile(re.escape(group)+r'\d*\.ogg$')
    for name,meta in sorted(asset_entries.items()):
        if not matcher.fullmatch(name): continue
        candidates=[RESOURCES/name,RESOURCES/'objects'/meta['hash'][:2]/meta['hash'],RESOURCES/meta['hash'][:2]/meta['hash']]
        location=next((p for p in candidates if p.is_file()),None)
        if location is None:raise ValueError('Missing original classic resource: '+name+' (SHA-1 '+meta['hash']+')')
        data=location.read_bytes()
        if hashlib.sha1(data).hexdigest()!=meta['hash']: raise ValueError(f'Beta asset hash mismatch: {name}')
        assert data.startswith(b'OggS'),name
        path='sounds/betabridge/'+name.removesuffix('.ogg').replace(' ','_')
        if path not in copied:
            out=DEST/(path+'.ogg');out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(data)
            copied[path]=name;report['sounds'].append({'path':path,'source':name,'sha1':meta['hash']})
        result.append(path)
    if not result: raise ValueError(f'Missing indexed Beta sound group {group}')
    return result

definitions={}
def event(modern,group,stream=False):
    if modern not in sound_ref: raise ValueError('Unknown official sound event '+modern)
    assets=sound_assets(group)
    original=sound_ref[modern]
    definitions[modern]={'category':original['category'],'sounds':[{'name':s,'stream':True,'volume':0.2} if stream else s for s in assets]}
    report['soundEvents'].append({'event':modern,'sourceGroup':group})

events={'game.player.hurt':'newsound/random/hurt','random.hurt':'newsound/random/hurt',
        'random.door_open':'newsound/random/door_open','random.door_close':'newsound/random/door_close',
        'random.bow':'newsound/random/bow','random.click':'newsound/random/click','random.pop':'newsound/random/pop',
        'random.explode':'newsound/random/explode','random.fizz':'newsound/random/fizz','random.fuse':'newsound/random/fuse',
        'random.splash':'newsound/random/splash','random.glass':'newsound/random/glass','ambient.cave':'newsound/ambient/cave/cave',
        'liquid.water':'newsound/liquid/water','liquid.lava':'newsound/liquid/lava','liquid.lavapop':'newsound/liquid/lavapop',
        'fire.fire':'newsound/fire/fire','fire.ignite':'newsound/fire/ignite','portal.portal':'newsound/portal/portal',
        'portal.travel':'newsound/portal/travel','portal.trigger':'newsound/portal/trigger'}
for mob in ['chicken','cow','pig','sheep','skeleton','zombie','spider','creeper']:
    events[f'mob.{mob}.say']=f'newsound/mob/{mob}'
for mob in ['chicken','cow','skeleton','zombie']:
    events[f'mob.{mob}.hurt']=f'newsound/mob/{mob}hurt'
for mob in ['pig','zombie','spider','creeper']:
    events[f'mob.{mob}.death']=f'newsound/mob/{mob}death'
events['mob.skeleton.death']='newsound/mob/skeletonhurt'
events['mob.chicken.plop']='newsound/mob/chickenplop'
events['mob.slime.big']='newsound/mob/slime';events['mob.slime.small']='newsound/mob/slime';events['mob.slime.attack']='newsound/mob/slimeattack'
for s in ['bark','death','growl','hurt','panting','shake','whine']: events['mob.wolf.'+s]='newsound/mob/wolf/'+s
for s in ['charge','death','fireball','moan','scream']: events['mob.ghast.'+s]='newsound/mob/ghast/'+s
for s in ['zpig','zpigangry','zpigdeath','zpighurt']: events['mob.zombiepig.'+s]='newsound/mob/zombiepig/'+s
for material in ['grass','gravel','stone','wood','sand','snow','cloth']:
    events['step.'+material]='newsound/step/'+material
    # Beta uses its legacy sound/step bank when breaking the original four materials.
    events['dig.'+material]=('sound/step/' if material in ['grass','gravel','stone','wood'] else 'newsound/step/')+material
for s in ['bass','bassattack','bd','hat','harp','pling','snare']:events['note.'+s]='newsound/note/'+s
for modern,group in events.items(): event(modern,group)
# Player damage and player attacks are distinct Bedrock events. Beta has the
# vocal hurt sound, but none of the modern strong/critical/no-damage punches.
event('game.player.die','newsound/random/hurt')
classic_hurt=sound_assets('newsound/random/hurt')
for name in ['game.player.hurt','game.player.die','random.hurt']:
    definitions[name]['sounds']=[{'name':asset,'load_on_low_memory':True} for asset in classic_hurt]
attack_events=sorted(name for name in sound_ref if name.startswith('game.player.attack.'))
for name in attack_events:
    definitions[name]={'category':sound_ref[name]['category'],'sounds':[]}
# Also replace the original file paths, covering built-in/preloaded player sound
# references without depending solely on the definition override being selected.
hurt_bytes=(DEST/(classic_hurt[0]+'.ogg')).read_bytes()
hurt_aliases=set()
for name in ['game.player.hurt','game.player.die']:
    for entry in sound_ref[name]['sounds']:
        path=entry if isinstance(entry,str) else entry['name']
        assert path.startswith('sounds/damage/hit') and '..' not in path
        output=DEST/(path+'.ogg');output.parent.mkdir(parents=True,exist_ok=True)
        output.write_bytes(hurt_bytes);hurt_aliases.add(path)
report['playerDamage']={'source':'newsound/random/hurt.ogg',
    'sha1':hashlib.sha1(hurt_bytes).hexdigest(),'vanillaPathOverrides':sorted(hurt_aliases),
    'mutedModernAttackEvents':attack_events,'inGameAudioCheckPending':True,
    'routingReference':'https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/sounds.json'}
assert len(hurt_aliases)==3 and len(attack_events)>=3
assert all((DEST/(path+'.ogg')).read_bytes()==hurt_bytes for path in hurt_aliases)
music=[]
for group in ['music/calm','newmusic/hal','newmusic/nuance','newmusic/piano']: music+=sound_assets(group)
music_events=sorted(name for name in sound_ref if name.startswith('music.'))
for modern in music_events:
    definitions[modern]={'category':'music','sounds':[{'name':s,'stream':True,'volume':0.2} for s in music]}
    report['soundEvents'].append({'event':modern,'sourceGroup':'classic-calm-hal-nuance-piano'})
for disc in ['13','cat']:
    asset=sound_assets('streaming/'+disc)[0]
    # Beta's metadata observer owns playback. Native jukebox prediction cannot
    # start a second stream that is unrelated to our explicit stop event.
    definitions['record.'+disc]={'category':'record','sounds':[]}
    for channel in range(16):
        definitions[f'betabridge.jukebox.{disc}.{channel}']={'category':'record',
            'sounds':[{'name':asset,'stream':True,'load_on_low_memory':True}]}
for modern in sorted(name for name in sound_ref if name.startswith('record.') and name not in ['record.13','record.cat']):
    definitions[modern]={'category':'record','sounds':[]}
report['musicPolicy']={'artist':'C418','scope':'Beta 1.4 original background playlist and discs 13/cat only',
                      'backgroundTracks':len(music),'overriddenMusicEvents':music_events,
                      'modernDiscEventsMuted':sorted(name for name in definitions if name.startswith('record.') and name not in ['record.13','record.cat'] and not definitions[name]['sounds'])}
report['musicPolicy']['jukebox']={'channels':16,'soundEvents':32,'nativePredictionMuted':['record.13','record.cat'],
    'playback':'Server-confirmed named events; stop by the exact event name per jukebox',
    'newAudioCopies':0,'manualStopCheckPending':True}
report['musicPolicy']['backgroundScheduler']='Native Bedrock biome music scheduler retained; user confirmed playback'
assert len(music)==12
assert all(definitions[name]['sounds'] and all(sound['name'] in music for sound in definitions[name]['sounds']) for name in music_events)
(DEST/'sounds').mkdir(exist_ok=True)
(DEST/'sounds/sound_definitions.json').write_text(json.dumps({'format_version':'1.14.0','sound_definitions':definitions},indent=2)+'\n')

# These constants were checked in the locally installed original Beta client:
# mw.a(int): GL_LINEAR, start = renderDistance * 0.25, end = renderDistance;
# mw.a(float,int): renderDistance = 256 >> option (normal is 128 blocks).
# sy.a(float,float): the unblended Overworld fog base is (192/255,216/255,1),
# modulated by the celestial angle. mw.h(float) also blends sky/biome color and
# local brightness, which this resource pack cannot reproduce exactly.
# Fluid GL_EXP (density 0.1 water / 2.0 lava) becomes a linear approximation.
fog_dir=DEST/'fogs'
fog_dir.mkdir(parents=True,exist_ok=True)
def rgb_hex(rgb):
    return '#'+''.join(f'{max(0,min(255,round(c*255))):02X}' for c in rgb)

def fog_color_at(ticks):
    angle=(ticks%24000)/24000-0.25
    if angle<0: angle+=1
    if angle>1: angle-=1
    original=angle
    angle=1-(math.cos(angle*math.pi)+1)/2
    angle=original+(angle-original)/3
    brightness=max(0,min(1,math.cos(angle*math.pi*2)*2+0.5))
    return rgb_hex(((192/255)*(brightness*0.94+0.06),
                    (216/255)*(brightness*0.94+0.06),brightness*0.91+0.09))

def distance(start,end,color):
    return {'fog_start':start,'fog_end':end,'fog_color':color,'render_distance_type':'fixed'}

def write_fog(identifier,air):
    setting={'format_version':'1.16.100','minecraft:fog_settings':{
        'description':{'identifier':identifier},'distance':{
            'air':air,
            'water':distance(0,30,'#050533'),
            'lava':distance(0,1.5,'#991A00')}}}
    name=identifier.split(':',1)[1]+'.json'
    (fog_dir/name).write_text(json.dumps(setting,indent=2)+'\n')
    # Validate actual files, including the same namespace/phase contract used by
    # BetaAtmosphere. This deliberately does not replace global biome defaults.
    restored=json.loads((fog_dir/name).read_text())['minecraft:fog_settings']
    assert restored['description']['identifier']==identifier
    for entry in restored['distance'].values():
        assert 0<=entry['fog_start']<entry['fog_end']
        assert re.fullmatch(r'#[0-9A-F]{6}',entry['fog_color'])

fog_end = 64 if args.profile == 'b1.6.6' else 128
for phase in range(48):
    write_fog(f'betabridge:beta_overworld_{phase:02d}',distance(fog_end/4,fog_end,fog_color_at(phase*500+250)))
write_fog('betabridge:beta_nether',distance(0,fog_end,'#330808'))
assert fog_color_at(6000)=='#C0D8FF'
assert fog_color_at(18000)=='#0C0D17'
report['fog']={'definitions':49,'normalRenderDistanceBlocks':fog_end,'airStartFraction':0.25,
               'dayNightPhases':48,'phaseTicks':500,'activation':'Per-session Geyser camera fog stack; no global biome replacement',
               'evidence':{'clientClasses':['mw.a(int)','mw.h(float)','sy.a(long,float)','sy.a(float,float)','sf.a(float,float)'],
                           'bedrockSpecification':'https://learn.microsoft.com/en-us/minecraft/creator/documents/foginresourcepacks'},
               'limitations':['Linear approximation of exponential water/lava fog',
                              'Overworld base color and time modulation; no biome/sky color blending or local brightness adaptation',
                              'Lighting, shadows, clouds, sky and renderer remain Bedrock; visual comparison on the iPhone is pending']}

# Preserve each official client biome's other components while giving water a
# consistent saturated blue. This is an artistic approximation, not a claim to
# reproduce Beta's procedural animated texture or its renderer.
water_blue='#334CFF'
biome_lock=json.loads((ROOT/'data/bedrock-biomes.lock.json').read_text())
biome_dir=DEST/'biomes';biome_dir.mkdir(exist_ok=True)
# Representative original Beta colormap colors grouped by ViaLegacy's actual
# biome mapping. Categories merged upstream retain a weighted equivalent tint.
# This preserves regional variation; it cannot reproduce continuous per-column
# climate tinting after ViaLegacy has collapsed the categories.
beta_tints = {}
if args.profile == 'b1.6.6':
    palette = json.loads((ROOT/'data/biome-colors.json').read_text())
    groups = {'desert':['DESERT'], 'forest':['FOREST','SEASONAL_FOREST'],
              'plains':['PLAINS'], 'jungle':['RAINFOREST'],
              'savanna':['SAVANNA','SHRUBLAND'], 'swampland':['SWAMPLAND'],
              'taiga':['TAIGA'], 'ice_plains':['TUNDRA']}
    for target, categories in groups.items():
        values=[palette['biomes'][key] for key in categories]
        total=sum(v['samples'] for v in values)
        beta_tints[target]={kind:'#'+''.join(f'{round(sum(int(v[kind][1+i:3+i],16)*v["samples"] for v in values)/total):02X}' for i in (0,2,4)) for kind in ('grass','foliage')}
    colormaps=DEST/'textures/colormap';colormaps.mkdir(parents=True,exist_ok=True)
    for old,new in [('grasscolor','grass'),('foliagecolor','foliage')]:
        (colormaps/(new+'.png')).write_bytes(source.read('misc/'+old+'.png'))
    report['betaBiomeTints']={'colors':beta_tints,'source':'original misc/grasscolor.png and misc/foliagecolor.png; ViaLegacy climate sampler',
        'sampleSeed':palette['seed'],'bounds':palette['bounds'],'limitation':'Representative per-biome colors, not continuous Beta per-column climate colors; upstream merges seasonal forest/forest and savanna/shrubland.'}

for entry in biome_lock['files']:
    filename=Path(entry['path']).name
    raw=(REFS/'bedrock-biomes'/filename).read_bytes()
    assert hashlib.sha256(raw).hexdigest()==entry['sha256']
    data=json.loads(raw)
    original=json.loads(raw)
    components=data['minecraft:client_biome']['components']
    components.setdefault('minecraft:water_appearance',{})['surface_color']=water_blue
    tint=beta_tints.get(filename.removesuffix('.client_biome.json'))
    if tint:
        components['minecraft:grass_appearance']={'color':tint['grass']}
        components['minecraft:foliage_appearance']={'color':tint['foliage']}
    previous=original['minecraft:client_biome']['components']
    changed={'minecraft:water_appearance'} | ({'minecraft:grass_appearance','minecraft:foliage_appearance'} if tint else set())
    assert {k:v for k,v in components.items() if k not in changed}=={
        k:v for k,v in previous.items() if k not in changed}
    assert {k:v for k,v in components['minecraft:water_appearance'].items() if k!='surface_color'}=={
        k:v for k,v in previous.get('minecraft:water_appearance',{}).items() if k!='surface_color'}
    (biome_dir/filename).write_text(json.dumps(data,indent=2)+'\n')
# Legacy custom-content fallback, preserving the official fog and transparency
# fields. Modern clients use the individual definitions above.
legacy_biomes=json.loads(re.sub(r'/\*.*?\*/','',(REFS/'bedrock-biomes_client.json').read_text(),flags=re.S))
for name,biome in legacy_biomes['biomes'].items():
    biome['water_surface_color']=water_blue
(DEST/'biomes_client.json').write_text(json.dumps(legacy_biomes,indent=2)+'\n')
report['water']={'surfaceColor':water_blue,'clientBiomeFiles':len(biome_lock['files']),
    'sourceLockSha256':hashlib.sha256((ROOT/'data/bedrock-biomes.lock.json').read_bytes()).hexdigest(),
    'legacyFallback':True,'otherBiomeComponentsPreservedExceptBetaGrassFoliage':True,'manualPhoneCheckPending':True,
    'limitations':['Blue color approximation; original Beta fluid animation and rendering are not reproduced',
                   'Vibrant Visuals and other client rendering modes require visual comparison'],
    'specification':'https://learn.microsoft.com/en-us/minecraft/creator/reference/content/clientbiomesreference/examples/components/minecraftclientbiomes_water_appearance'}
assert len(list(biome_dir.glob('*.json')))==len(biome_lock['files'])>0

manifest={'format_version':2,'header':{'name':'BetaBridge · '+args.profile,'description':'Texturas y sonidos clásicos convertidos desde Beta. Conversión inicial para el laboratorio local.',
         'uuid':'93317e0c-2a88-4433-95e8-4d931cfb1814','version':VERSION,'min_engine_version':[1,26,50]},
         'modules':[{'type':'resources','uuid':'2a2de18b-83cc-4633-84aa-60ecc28c0dc0','version':VERSION}]}
(DEST/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
terrain.crop((0,0,16,16)).resize((128,128),Image.Resampling.NEAREST).save(DEST/'pack_icon.png')
pack=PACK_DIR/('BetaBridge-'+args.profile+'.mcpack')
with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as archive:
    for file in sorted(DEST.rglob('*')):
        if file.is_file():
            info=zipfile.ZipInfo(str(file.relative_to(DEST)),date_time=(2026,10,17,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            archive.writestr(info,file.read_bytes())
with zipfile.ZipFile(pack) as archive:
    assert archive.testzip() is None
    assert json.loads(archive.read('manifest.json'))==manifest
report['packSha256']=hashlib.sha256(pack.read_bytes()).hexdigest()
report['packBytes']=pack.stat().st_size
report['physicalOggFiles']=len(list((DEST/'sounds').rglob('*.ogg')))
report['soundDefinitionCount']=len(definitions)
(PACK_DIR/'coverage.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(f"PASS: {len(report['textures'])} texture files, {len(copied)} verified source OGGs, {len(definitions)} sound events; {pack.stat().st_size/1048576:.1f} MiB")
print('Unmapped initial texture paths:',report.get('unmappedBlockPaths',[]),report.get('unmappedItemPaths',[]))
