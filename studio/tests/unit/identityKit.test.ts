import {it,expect} from 'vitest'
import {readFileSync,existsSync,readdirSync} from 'node:fs'
import {join} from 'node:path'
import {createRequire} from 'node:module'
import {REQUIRED_TOKEN_PATHS,checkContrast,flattenTokens,missingTokens,tokensToCss,contrastRatio} from '../../src/tokens/tokens'
import {REQUIRED_COPY_KEYS} from '../../src/proto/copy/required'
import uk from '../../src/proto/copy/copy.uk.json'
import {t} from '../../src/proto/copy/t'
const fonts=createRequire(import.meta.url)('fontkit') as {openSync:(path:string)=>{hasGlyphForCodePoint:(code:number)=>boolean}}
for(const id of ['v1-aero','ornament','nichna-karta','atlas'])it('айдентика '+id+' готова для передачі',()=>{
 const root=join(process.cwd(),'src/identities',id)
 const tree=flattenTokens(JSON.parse(readFileSync(join(root,'tokens.json'),'utf8')))
 expect(REQUIRED_TOKEN_PATHS).toHaveLength(76)
 expect(missingTokens(tree)).toEqual([])
 expect(checkContrast(tree)).toEqual([])
 expect(tokensToCss(':root',tree).match(/--n-[\w-]+:/g)).toHaveLength(76)
 for(const key of ['logo','logo-mark','favicon'])expect(existsSync(join(root,'assets',key+'.svg'))).toBe(true)
 const dir=join(root,'assets/fonts')
 for(const file of readdirSync(dir).filter(f=>f.endsWith('.woff2'))){
   const font=fonts.openSync(join(dir,file))
   expect(existsSync(join(dir,'LICENSE-'+file.replace('.woff2','.txt')))).toBe(true)
   for(const code of [0x491,0x454,0x456,0x457,0x2bc,0x20b4])expect(font.hasGlyphForCodePoint(code)).toBe(true)
 }
})
it('передає українські тексти й усі категорії множини',()=>{
 for(const key of REQUIRED_COPY_KEYS)expect(uk).toHaveProperty([key])
 for(const [count,text] of [[1,'1 лайк'],[3,'3 лайки'],[11,'11 лайків'],[21,'21 лайк']] as const)expect(t('musician.likesCount',{count})).toBe(text)
 expect(contrastRatio('#000000','#ffffff')).toBe(21)
 expect(()=>flattenTokens({color:{$type:'color',a:{$value:'{color.b}'},b:{$value:'{color.a}'}}})).toThrow(/Circular alias/)
})
