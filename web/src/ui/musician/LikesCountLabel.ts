const numbers = new Intl.NumberFormat('uk-UA')

/** The plural copy with the number grouped for reading ("5 313 лайків"). */
export const formatLikes = (label: (count: number) => string, count: number): string =>
  label(count).replace(String(count), numbers.format(count))
