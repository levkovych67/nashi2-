export interface IdentityMeta {
  id: string
  title: string
  concept: string
  keywords: string[]
  voice: { tone: string; examples: string[] }
  fonts: {
    family: string
    role: 'display' | 'body'
    license: 'OFL-1.1' | 'commercial'
    source: string
    files: string[]
  }[]
  moodboardAlt: string
  moodboardCredits: string[]
}
