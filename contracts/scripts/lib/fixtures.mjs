import { REGIONS, regionOf } from './regions.mjs';
import { fixtureId, pinFor, hash } from './pin.mjs';
import { slugify } from './translit.mjs';
import { cleanBiography, firstSentence, summarize, taglineFrom } from './text.mjs';
import { mapLinks, safeUrl } from './links.mjs';
import { clipsFor, fragmentFor } from './audio.mjs';
import { pickDetailSamples } from './select.mjs';

const textLicense = 'https://creativecommons.org/licenses/by-sa/4.0/deed.uk';
const labels = new Map(REGIONS.map(row => [row.code,row.name]));
const collator = new Intl.Collator('uk');
const nameOrder = (a,b) => collator.compare(a.name,b.name) || a.id.localeCompare(b.id);
const alphabetic = (a,b) => a < b ? -1 : a > b ? 1 : 0;
const fixedDate = '2026-11-08T18:35:57Z'; // V1 snapshot timestamp; generated fixtures are reproducible.
const asDate = date => date != null && /^\d{4}(-\d{2}){0,2}$/.test(String(date)) ? String(date) : null;

function kindOf(record) {
  return record.ensemble || record.categories?.includes('BAND') ? 'BAND' : 'PERSON';
}
function isMusic(record) {
  return record.categories?.some(category => category === 'MUSICIAN' || category === 'BAND')
    && record.locationBasis === (kindOf(record) === 'BAND' ? 'FORMATION' : 'BIRTH');
}
function usablePlace(place) {
  return place?.qid && place.labelUk && regionOf(place) && Number.isFinite(place.lat)
    && Number.isFinite(place.lon) && Math.abs(place.lat) <= 90 && Math.abs(place.lon) <= 180;
}
function mapImages(record, submission) {
  if (submission) {
    return [{url:record.imageUrl,thumbUrl:record.imageUrl,alt:record.name,credit:null}];
  }
  const url = safeUrl(record.imageUrl);
  const credit = record.imageCredit;
  if (!url || !credit?.author || !credit.license || !safeUrl(credit.filePageUrl)) return [];
  return [{
    url, thumbUrl:url, alt:record.name,
    credit:{author:credit.author,license:credit.license,licenseUrl:safeUrl(credit.licenseUrl),sourceUrl:credit.filePageUrl}
  }];
}
function profile({record,submission,id}, place, slug) {
  const name = record.name;
  const bio = cleanBiography(record.biography);
  const links = mapLinks(record.socialLinks);
  const wikiUrl = links.find(link => link.platform === 'WIKIPEDIA')?.url
    ?? 'https://uk.wikipedia.org/wiki/' + encodeURIComponent((record.wikiTitle || name).replaceAll(' ','_'));
  const textSource = submission ? null : {
    type:'WIKIPEDIA',title:record.wikiTitle || name,url:wikiUrl,license:'CC BY-SA 4.0',licenseUrl:textLicense
  };
  const hasAudio = submission ? record.demoAudio : links.some(link => link.group === 'MUSIC' || link.group === 'VIDEO');
  const clips = hasAudio ? clipsFor(id) : [];
  const works = (record.works ?? []).filter(work => work.title).map(work => ({
    title:work.title,year:work.year ?? null,type:'OTHER',url:safeUrl(work.articleUrl)
  }));
  const bytes = hash(id);
  const precision = submission ? 'CITY' : 'SETTLEMENT';
  const pin = pinFor(id,place,precision);
  return {
    id,slug,name,kind:kindOf(record),
    tagline:taglineFrom(firstSentence(bio)),summary:summarize(bio,300),description:summarize(bio,2000),textSource,
    images:mapImages(record,submission),
    origin:{
      placeId:place.placeId,placeName:place.name,region:place.region,regionName:labels.get(place.region),
      basis:submission ? 'ORIGIN' : record.locationBasis,precision,lat:pin.lat,lon:pin.lon
    },
    dates:{birth:asDate(record.birthDate),death:asDate(record.deathDate),activeFrom:asDate(record.foundationYear),activeTo:asDate(record.disbandYear)},
    genres:(record.genres ?? []).slice(0,5),
    materials:{clips:clips.map(({url,...clip}) => clip),works,videos:[]},
    links,youtubeChannelUrl:links.find(link => link.platform === 'YOUTUBE')?.url ?? null,
    likesCount:bytes[3]*37 + bytes[4],hasAudio,updatedAt:fixedDate
  };
}
function entry(artist) {
  const {id,slug,name,kind,hasAudio} = artist;
  const thumb = artist.images[0]?.thumbUrl;
  return {id,slug,name,kind,...(thumb && {thumbUrl:thumb}),hasAudio};
}
export function buildFixtures(records, regionsMeta, extras = []) {
  const meta = new Map(regionsMeta.map(region => [region.code,region]));
  if (meta.size !== REGIONS.length || REGIONS.some(region => !meta.has(region.code))) {
    throw new Error('Required geographic metadata for all 27 regions is missing.');
  }
  const candidates = [
    ...records.filter(isMusic).map(record => ({record,submission:false})),
    ...extras.map(record => ({record,submission:true}))
  ].filter(({record}) => record.name && record.qid && usablePlace(record.place))
    .map(item => ({...item,id:fixtureId(item.record.qid)}))
    .sort((a,b)=>alphabetic(a.id,b.id));
  const seenIds = new Set();
  const placeMap = new Map();
  for (const {record,id} of candidates) {
    if (seenIds.has(id)) throw new Error('Duplicate V1 entity: ' + record.qid);
    seenIds.add(id);
    const place = record.place;
    const stat = placeMap.get(place.qid) ?? {
      placeId:place.qid,name:place.labelUk,region:regionOf(place),type:'TOWN',lat:place.lat,lon:place.lon,count:0
    };
    stat.count++;
    if (stat.count >= 5) stat.type = 'CITY';
    placeMap.set(place.qid,stat);
  }
  const taken = new Set(['index']); // reserved list fixture filename
  const artists = candidates.map(candidate => profile(candidate,placeMap.get(candidate.record.place.qid),slugify(candidate.record.name,taken)));
  const pinItems = artists.map(artist => {
    const {lat,lon,region,placeId} = artist.origin;
    return {...entry(artist),lat,lon,region,placeId};
  });
  const pins = {version:hash(JSON.stringify(pinItems)).toString('hex').slice(0,8),pins:pinItems};
  const places = [...placeMap.values()].sort((a,b) => b.count-a.count || collator.compare(a.name,b.name) || alphabetic(a.placeId,b.placeId));
  const counts = new Map();
  for (const place of places) counts.set(place.region,(counts.get(place.region) || 0) + place.count);
  const regions = REGIONS.map(({code,name}) => {
    const {center,bbox} = meta.get(code);
    return {code,name,center,bbox,count:counts.get(code) ?? 0};
  });
  const filters = [{key:'region',label:'Область',type:'MULTI_SELECT',options:regions.map(
    ({code,name,count}) => ({value:code,label:name,count})
  )}];
  const items = [...artists].sort(nameOrder).slice(0,20).map(artist => ({
    ...entry(artist),placeName:artist.origin.placeName,region:artist.origin.region,likesCount:artist.likesCount
  }));
  const index = {items,page:0,size:20,totalItems:artists.length,totalPages:Math.ceil(artists.length / 20)};
  const details = Object.fromEntries(pickDetailSamples(artists,items).map(artist => [artist.slug,artist]));
  const fragments = Object.fromEntries(artists.filter(artist => artist.hasAudio).map(
    artist => [artist.id,fragmentFor(clipsFor(artist.id)[0])]
  ));
  const placeSearch = places.map(({count,...place}) => ({...place,regionName:labels.get(place.region)}));
  return {pins,places,regions,filters,index,details,fragments,placeSearch};
}
