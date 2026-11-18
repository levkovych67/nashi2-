import { describe, expect, it } from 'vitest'
import { DECISIONS } from '../../src/decisions/catalog'
import { variants } from '../../src/decisions/variants'

describe('кожне рішення доступне для порівняння',()=>{
  for(const decision of DECISIONS) {
    it(decision.id,()=>{
      const options = variants[decision.id] ?? []
      expect(options.length).toBeGreaterThanOrEqual(2)
      expect(options.map(v=>v.id).sort()).toEqual([...decision.techVariantIds].sort())
      for(const option of options) {
        expect(option.name.trim()).not.toEqual('')
        expect(option.plus.trim()).not.toEqual('')
        expect(option.minus.trim()).not.toEqual('')
      }
    })
  }
})
