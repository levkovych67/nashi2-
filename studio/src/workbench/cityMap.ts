import maplibregl from 'maplibre-gl'
import type { AtlasData } from './data'

export function createCityMap(div: HTMLDivElement, data: AtlasData) {
  const colors = getComputedStyle(div)
  const css = (key: string) => colors.getPropertyValue(key).trim()
  return new maplibregl.Map({
    container: div,
    attributionControl: false,
    maplibreLogo: false,
    style: {
      version: 8,
      sources: {
        regions: { type: 'geojson', data: data.shapes },
        city: {
          type: 'vector',
          tiles: [location.origin + '/lviv/{z}/{x}/{y}.pbf'],
          minzoom: 11,
          maxzoom: 11,
          bounds: [23.90626, 49.72449, 24.2578, 49.95121],
        },
      },
      layers: [
        {
          id: 'base',
          type: 'background',
          paint: { 'background-color': css('--water') },
        },
        {
          id: 'land',
          type: 'fill',
          source: 'regions',
          paint: { 'fill-color': css('--land') },
        },
        {
          id: 'parks',
          type: 'fill',
          source: 'city',
          'source-layer': 'landcover',
          paint: { 'fill-color': css('--point'), 'fill-opacity': 0.17 },
        },
        {
          id: 'water',
          type: 'fill',
          source: 'city',
          'source-layer': 'water',
          paint: { 'fill-color': css('--water') },
        },
        {
          id: 'streets',
          type: 'line',
          source: 'city',
          'source-layer': 'transportation',
          paint: {
            'line-color': css('--line'),
            'line-width': ['interpolate', ['linear'], ['zoom'], 11, 1.3, 15, 6],
          },
        },
        {
          id: 'road-center',
          type: 'line',
          source: 'city',
          'source-layer': 'transportation',
          paint: {
            'line-color': css('--white'),
            'line-width': ['interpolate', ['linear'], ['zoom'], 11, 0.7, 15, 4],
          },
        },
        {
          id: 'border',
          type: 'line',
          source: 'regions',
          paint: { 'line-color': css('--line'), 'line-width': 1.5 },
        },
      ],
    },
    center: [24.03, 49.84],
    zoom: 11.5,
    minZoom: 11,
    maxZoom: 15,
    maxBounds: [
      [23.907, 49.725],
      [24.257, 49.95],
    ],
    interactive: true,
    canvasContextAttributes: { antialias: false },
  })
}
