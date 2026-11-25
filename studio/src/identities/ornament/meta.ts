import type { IdentityMeta } from '../types'

export default {
  id: 'ornament',
  title: 'Сучасний орнамент',
  concept:
    'Ромб у знаку задає ритм позначкам. Червоний акцент і широкий Unbounded підтримують геометрію орнаменту. Сітка збирає повторювані форми у впізнаваний візерунок.',
  keywords: ['Ромб', 'повтор', 'тканина'],
  voice: {
    tone: 'Короткі фрази з дієсловом на початку',
    examples: ['Знайдіть свій гурт.'],
  },
  moodboardAlt: 'Модульний двоколірний ромбічний орнамент',
  moodboardCredits: ['Авторська графічна композиція дизайн-студії «Наші».'],
  fonts: [
    {
      family: 'Unbounded',
      role: 'display',
      license: 'OFL-1.1',
      source: 'https://github.com/google/fonts/tree/main/ofl/unbounded',
      files: ['unbounded.woff2'],
    },
    {
      family: 'Source Sans 3',
      role: 'body',
      license: 'OFL-1.1',
      source: 'https://github.com/google/fonts/tree/main/ofl/sourcesans3',
      files: ['source-sans-3.woff2'],
    },
  ],
} satisfies IdentityMeta
