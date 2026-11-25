import {readFileSync,writeFileSync,mkdirSync} from 'node:fs'
import {join} from 'node:path'
import {flattenTokens,missingTokens,checkContrast,tokensToCss} from '../src/tokens/tokens'
const root='src/identities'
const styles=[]
for(const id of ['atlas','ornament','nichna-karta','v1-aero']){
  const flat=flattenTokens(JSON.parse(readFileSync(join(root,id,'tokens.json'),'utf8')))
  const missing=missingTokens(flat)
  if(missing.length)throw new Error(id+' — бракує '+missing.join(', '))
  const bad=checkContrast(flat)
  if(bad.length)throw new Error(id+' — недостатній контраст '+JSON.stringify(bad))
  styles.push(tokensToCss('[data-identity="'+id+'"],[data-direction="'+id+'"]',flat))
}
mkdirSync(join(root,'generated'),{recursive:true})
writeFileSync(join(root,'generated/identities.css'),styles.join('\n')+'\n')
