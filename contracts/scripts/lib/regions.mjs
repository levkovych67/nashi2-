const rows = [
  ['CRIMEA', 'Автономна Республіка Крим', '43'],
  ['VINNYTSIA', 'Вінницька область', '05'],
  ['VOLYN', 'Волинська область', '07'],
  ['DNIPROPETROVSK', 'Дніпропетровська область', '12'],
  ['DONETSK', 'Донецька область', '14'],
  ['ZHYTOMYR', 'Житомирська область', '18'],
  ['ZAKARPATTIA', 'Закарпатська область', '21'],
  ['ZAPORIZHZHIA', 'Запорізька область', '23'],
  ['IVANO_FRANKIVSK', 'Івано-Франківська область', '26'],
  ['KYIV', 'Київська область', '32'],
  ['KIROVOHRAD', 'Кіровоградська область', '35'],
  ['LUHANSK', 'Луганська область', '09'],
  ['LVIV', 'Львівська область', '46'],
  ['MYKOLAIV', 'Миколаївська область', '48'],
  ['ODESA', 'Одеська область', '51'],
  ['POLTAVA', 'Полтавська область', '53'],
  ['RIVNE', 'Рівненська область', '56'],
  ['SUMY', 'Сумська область', '59'],
  ['TERNOPIL', 'Тернопільська область', '61'],
  ['KHARKIV', 'Харківська область', '63'],
  ['KHERSON', 'Херсонська область', '65'],
  ['KHMELNYTSKYI', 'Хмельницька область', '68'],
  ['CHERKASY', 'Черкаська область', '71'],
  ['CHERNIVTSI', 'Чернівецька область', '77'],
  ['CHERNIHIV', 'Чернігівська область', '74'],
  ['KYIV_CITY', 'Київ', '30'],
  ['SEVASTOPOL_CITY', 'Севастополь', '40']
];
export const REGIONS = rows.map(([code, name, suffix]) => ({ code, name, iso: 'UA-' + suffix }));
const codes = new Set(REGIONS.map(row => row.code));
export function regionOf(place) {
  if (place?.qid === 'Q1899') return 'KYIV_CITY';
  if (place?.qid === 'Q7525') return 'SEVASTOPOL_CITY';
  return codes.has(place?.region) ? place.region : null;
}
