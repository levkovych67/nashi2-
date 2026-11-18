export interface Variant {
  id: string
  name: string
  description: string
  plus: string
  minus: string
}
export function option(
  id: string,
  name: string,
  description: string,
  plus: string,
  minus: string,
): Variant {
  return { id, name, description, plus, minus }
}
