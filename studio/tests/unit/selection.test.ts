import { describe, expect, it } from 'vitest'
import { DECISIONS, known } from '../../src/decisions/catalog'
import {
  decodeSelection,
  encodeSelection,
  decisionsFile,
} from '../../src/selection/selection'

describe('студія вибору', () => {
  it('містить 17 окремих рішень', () => {
    expect(new Set(DECISIONS.map((d) => d.id)).size).toBe(17)
  })
  it('зберігає вибір без перетворення його на погодження', () => {
    const draft = {
      'map.pin': {
        variant: 'DROP_ICON',
        params: { radius: 24, withLabel: true },
      },
    }
    expect(decodeSelection(encodeSelection(draft))).toEqual({
      draft,
      rejected: [],
    })
    expect(decisionsFile(draft).decisions['map.pin'].status).toBe('PROPOSED')
  })
  it('відкидає пошкоджені, надмірні та невідомі варіанти', () => {
    expect(decodeSelection('%%%').rejected).toEqual(['sel'])
    expect(decodeSelection('A'.repeat(4001)).rejected).toEqual(['sel:too-long'])
    expect(
      decodeSelection(
        btoa(
          JSON.stringify({
            v: 1,
            s: {
              'like.button': { variant: 'BAD' },
              identity: { variant: 'atlas' },
            },
          }),
        ),
      ).draft,
    ).toEqual({ identity: { variant: 'atlas' } })
    expect(known('map.origin-notice', 'PIN_CARD_LABEL')).toBe(false)
  })
})
