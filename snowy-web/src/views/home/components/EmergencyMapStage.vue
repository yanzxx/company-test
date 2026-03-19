<template>
	<div class="emergency-map-stage">
		<BaseMap
			container-id="emergency-home-map"
			:bbox="mapBbox"
			:padding="[72, 72, 72, 72]"
			:min-zoom="8"
			:max-zoom="17"
			:zoom="11"
		>
			<RasterLayer id="emergency-dark-base" :url="darkBaseUrl" :paint="baseRasterPaint" />
			<RasterLayer id="emergency-dark-label" :url="darkLabelUrl" :paint="labelRasterPaint" />

			<Source id="emergency-flood-zones" :geojson="floodZoneGeojson" />
			<GeojsonLayer
				id="emergency-primary-flood-fill"
				source-id="emergency-flood-zones"
				map-type="fill"
				:filter="['==', ['get', 'zone'], 'primary']"
				:paint="primaryFloodFillPaint"
			/>
			<GeojsonLayer
				id="emergency-primary-flood-line"
				source-id="emergency-flood-zones"
				map-type="line"
				:filter="['==', ['get', 'zone'], 'primary']"
				:paint="primaryFloodLinePaint"
			/>
			<GeojsonLayer
				id="emergency-secondary-flood-fill"
				source-id="emergency-flood-zones"
				map-type="fill"
				:filter="['==', ['get', 'zone'], 'secondary']"
				:paint="secondaryFloodFillPaint"
			/>
			<GeojsonLayer
				id="emergency-secondary-flood-line"
				source-id="emergency-flood-zones"
				map-type="line"
				:filter="['==', ['get', 'zone'], 'secondary']"
				:paint="secondaryFloodLinePaint"
			/>

			<Source id="emergency-center-point" :geojson="centerPointGeojson" />
			<GeojsonLayer id="emergency-center-glow" source-id="emergency-center-point" map-type="circle" :paint="centerGlowPaint" />
			<GeojsonLayer id="emergency-center-core" source-id="emergency-center-point" map-type="circle" :paint="centerCorePaint" />

			<Source id="emergency-poi-points" :geojson="poiGeojson" />
			<GeojsonLayer
				id="emergency-poi-glow"
				source-id="emergency-poi-points"
				map-type="circle"
				:filter="['==', ['get', 'isAffected'], true]"
				:paint="poiGlowPaint"
			/>
			<GeojsonLayer id="emergency-poi-core" source-id="emergency-poi-points" map-type="circle" :paint="poiCirclePaint" />

			<Source id="emergency-poi-labels" :geojson="poiLabelGeojson" />
			<SymbolLayer id="emergency-poi-name" source-id="emergency-poi-labels" :layout="poiLabelLayout" :paint="poiLabelPaint" />

			<Source id="emergency-leak-points" :geojson="leakGeojson" />
			<GeojsonLayer id="emergency-leak-glow" source-id="emergency-leak-points" map-type="circle" :paint="leakGlowPaint" />
			<GeojsonLayer id="emergency-leak-core" source-id="emergency-leak-points" map-type="circle" :paint="leakCorePaint" />

			<Marker marker-id="emergency-center-marker" :coordinate="centerCoordinate" :offset="[0, 0]">
				<div class="center-marker">
					<div class="center-marker__core"></div>
					<div class="center-marker__label">灾情中心</div>
				</div>
			</Marker>

			<Marker v-for="item in displayLeakMarkers" :key="item.id" :marker-id="item.id" :coordinate="[item.lng, item.lat]" :offset="[0, -18]">
				<div class="leak-marker">
					<EnvironmentOutlined />
					<span>{{ item.name }}</span>
				</div>
			</Marker>
		</BaseMap>

		<div class="map-stage__halo"></div>
		<div class="map-stage__scanline"></div>

		<div class="map-stage__hud">
			<span class="map-stage__eyebrow">China City Grid</span>
			<strong>{{ levelLabel }}</strong>
			<p>暗色底图 · 后端态势驱动 · 实时灾情半径</p>
		</div>

		<div class="map-stage__stats">
			<div class="map-stage__stat">
				<span>受灾半径</span>
				<strong>{{ radiusText }}</strong>
			</div>
			<div class="map-stage__stat">
				<span>圈内设施</span>
				<strong>{{ affectedPoiList.length }}</strong>
			</div>
			<div class="map-stage__stat">
				<span>漏水点</span>
				<strong>{{ displayLeakMarkers.length }}</strong>
			</div>
		</div>
	</div>
</template>

<script setup>
	import { computed } from 'vue'
	import { EnvironmentOutlined } from '@ant-design/icons-vue'
	import BaseMap from '@/components/BaseMap/index.vue'
	import RasterLayer from '@/components/BaseMap/MapLayers/RasterLayer.vue'
	import GeojsonLayer from '@/components/BaseMap/MapLayers/GeojsonLayer.vue'
	import Marker from '@/components/BaseMap/MapLayers/Marker.vue'
	import SymbolLayer from '@/components/BaseMap/MapLayers/SymbolLayer.vue'
	import Source from '@/components/BaseMap/MapSources/Source.vue'

	const DEFAULT_CENTER = [113.947321, 22.543211]
	const TDT_TOKEN = '94a9ef43494c03ab32a38cba10671c05'
	const EMPTY_COLLECTION = {
		type: 'FeatureCollection',
		features: []
	}
	const FALLBACK_COLOR = '#8ee7ff'

	const props = defineProps({
		centerLng: {
			type: Number,
			default: 113.947321
		},
		centerLat: {
			type: Number,
			default: 22.543211
		},
		radiusMeters: {
			type: Number,
			default: 1500
		},
		poiList: {
			type: Array,
			default: () => []
		},
		affectedPoiList: {
			type: Array,
			default: () => []
		},
		leakPointList: {
			type: Array,
			default: () => []
		},
		levelKey: {
			type: String,
			default: 'monitor'
		},
		colorMap: {
			type: Object,
			default: () => ({})
		}
	})

	const darkBaseUrl = `https://t0.tianditu.gov.cn/vec_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=vec&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TDT_TOKEN}`
	const darkLabelUrl = `https://t0.tianditu.gov.cn/cva_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=cva&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${TDT_TOKEN}`

	const safeCenterLng = computed(() => (Number.isFinite(Number(props.centerLng)) ? Number(props.centerLng) : DEFAULT_CENTER[0]))
	const safeCenterLat = computed(() => (Number.isFinite(Number(props.centerLat)) ? Number(props.centerLat) : DEFAULT_CENTER[1]))
	const safeRadiusMeters = computed(() => Math.max(Number(props.radiusMeters || 0), 200))
	const centerCoordinate = computed(() => [safeCenterLng.value, safeCenterLat.value])
	const affectedPoiIdSet = computed(() => new Set(props.affectedPoiList.map((item) => item.id)))
	const displayLeakMarkers = computed(() =>
		props.leakPointList.filter((item) => isValidCoordinate(item.lng, item.lat)).slice(0, 6)
	)

	const levelLabel = computed(() => {
		if (props.levelKey === 'critical') {
			return '红色应急态势'
		}
		if (props.levelKey === 'warning') {
			return '橙色预警监测'
		}
		return '蓝色监测态势'
	})
	const radiusText = computed(() => `${Math.round(safeRadiusMeters.value)}m`)

	const levelPalette = computed(() => {
		if (props.levelKey === 'critical') {
			return {
				glow: 'rgba(255, 106, 136, 0.22)',
				fill: 'rgba(255, 82, 119, 0.18)',
				line: '#ff7a96'
			}
		}
		if (props.levelKey === 'warning') {
			return {
				glow: 'rgba(255, 183, 77, 0.2)',
				fill: 'rgba(255, 173, 66, 0.18)',
				line: '#ffb95f'
			}
		}
		return {
			glow: 'rgba(0, 180, 255, 0.18)',
			fill: 'rgba(0, 133, 255, 0.14)',
			line: '#4db8ff'
		}
	})

	const baseRasterPaint = {
		'raster-opacity': 0.94,
		'raster-saturation': -1,
		'raster-contrast': 0.5,
		'raster-brightness-min': 0.03,
		'raster-brightness-max': 0.36
	}
	const labelRasterPaint = {
		'raster-opacity': 0.7,
		'raster-saturation': -0.35,
		'raster-contrast': 0.2,
		'raster-brightness-min': 0.2,
		'raster-brightness-max': 0.62
	}

	const primaryFloodFillPaint = computed(() => ({
		'fill-color': levelPalette.value.fill,
		'fill-opacity': 1
	}))
	const primaryFloodLinePaint = computed(() => ({
		'line-color': levelPalette.value.line,
		'line-width': 2.2,
		'line-opacity': 0.92
	}))
	const secondaryFloodFillPaint = {
		'fill-color': 'rgba(24, 207, 255, 0.14)',
		'fill-opacity': 1
	}
	const secondaryFloodLinePaint = {
		'line-color': '#4ce8ff',
		'line-width': 1.6,
		'line-opacity': 0.86
	}
	const centerGlowPaint = {
		'circle-color': '#28d7ff',
		'circle-radius': 12,
		'circle-blur': 0.75,
		'circle-opacity': 0.46
	}
	const centerCorePaint = {
		'circle-color': '#e8fbff',
		'circle-radius': 4,
		'circle-stroke-color': '#28d7ff',
		'circle-stroke-width': 2
	}
	const poiGlowPaint = {
		'circle-color': ['coalesce', ['get', 'color'], FALLBACK_COLOR],
		'circle-radius': ['interpolate', ['linear'], ['zoom'], 9, 7, 14, 15],
		'circle-opacity': 0.18,
		'circle-blur': 0.8
	}
	const poiCirclePaint = {
		'circle-color': ['case', ['boolean', ['get', 'isAffected'], false], ['coalesce', ['get', 'color'], FALLBACK_COLOR], '#4f607d'],
		'circle-radius': ['case', ['boolean', ['get', 'isAffected'], false], ['interpolate', ['linear'], ['zoom'], 9, 4.5, 14, 8.5], ['interpolate', ['linear'], ['zoom'], 9, 2, 14, 4.5]],
		'circle-opacity': ['case', ['boolean', ['get', 'isAffected'], false], 0.95, 0.35],
		'circle-stroke-color': ['case', ['boolean', ['get', 'isAffected'], false], '#eefcff', 'rgba(173, 197, 255, 0.16)'],
		'circle-stroke-width': ['case', ['boolean', ['get', 'isAffected'], false], 1.6, 0.8]
	}
	const poiLabelLayout = {
		'text-field': ['get', 'name'],
		'text-size': ['interpolate', ['linear'], ['zoom'], 9, 10, 14, 13],
		'text-offset': [0, 1.25],
		'text-anchor': 'top',
		'text-allow-overlap': false,
		'text-ignore-placement': false
	}
	const poiLabelPaint = {
		'text-color': '#e8f7ff',
		'text-halo-color': 'rgba(4, 16, 32, 0.92)',
		'text-halo-width': 1.2
	}
	const leakGlowPaint = {
		'circle-color': '#ff879b',
		'circle-radius': ['interpolate', ['linear'], ['zoom'], 9, 10, 14, 18],
		'circle-opacity': 0.2,
		'circle-blur': 0.85
	}
	const leakCorePaint = {
		'circle-color': '#ffd2d8',
		'circle-radius': ['interpolate', ['linear'], ['zoom'], 9, 3.5, 14, 6],
		'circle-stroke-color': '#ff6f89',
		'circle-stroke-width': 2
	}

	const mapBbox = computed(() => {
		const pointList = collectBoundsPoints(safeCenterLng.value, safeCenterLat.value, safeRadiusMeters.value, props.poiList, props.leakPointList)
		const lngList = pointList.map((item) => item[0])
		const latList = pointList.map((item) => item[1])
		const minLng = Math.min(...lngList)
		const maxLng = Math.max(...lngList)
		const minLat = Math.min(...latList)
		const maxLat = Math.max(...latList)
		const lngPadding = Math.max((maxLng - minLng) * 0.14, 0.012)
		const latPadding = Math.max((maxLat - minLat) * 0.14, 0.01)
		return [
			[minLng - lngPadding, minLat - latPadding],
			[maxLng + lngPadding, maxLat + latPadding]
		]
	})

	const floodZoneGeojson = computed(() => {
		const featureList = []
		const primaryZone = createCircleFeature(safeCenterLng.value, safeCenterLat.value, safeRadiusMeters.value, { zone: 'primary' })
		if (primaryZone) {
			featureList.push(primaryZone)
		}
		const firstLeakPoint = displayLeakMarkers.value[0]
		if (firstLeakPoint) {
			const secondaryZone = createCircleFeature(firstLeakPoint.lng, firstLeakPoint.lat, Math.max(safeRadiusMeters.value * 0.45, 280), {
				zone: 'secondary'
			})
			if (secondaryZone) {
				featureList.push(secondaryZone)
			}
		}
		return createFeatureCollection(featureList)
	})

	const centerPointGeojson = computed(() =>
		createFeatureCollection([
			{
				type: 'Feature',
				properties: {
					name: '灾情中心'
				},
				geometry: {
					type: 'Point',
					coordinates: centerCoordinate.value
				}
			}
		])
	)

	const poiGeojson = computed(() =>
		createFeatureCollection(
			props.poiList
				.filter((item) => isValidCoordinate(item.lng, item.lat))
				.map((item) => ({
					type: 'Feature',
					properties: {
						id: item.id,
						name: item.name,
						type: item.type,
						color: props.colorMap[item.type] || FALLBACK_COLOR,
						isAffected: affectedPoiIdSet.value.has(item.id)
					},
					geometry: {
						type: 'Point',
						coordinates: [Number(item.lng), Number(item.lat)]
					}
				}))
		)
	)

	const poiLabelGeojson = computed(() =>
		createFeatureCollection(
			props.affectedPoiList
				.filter((item) => isValidCoordinate(item.lng, item.lat))
				.slice(0, 12)
				.map((item) => ({
					type: 'Feature',
					properties: {
						id: item.id,
						name: item.name
					},
					geometry: {
						type: 'Point',
						coordinates: [Number(item.lng), Number(item.lat)]
					}
				}))
		)
	)

	const leakGeojson = computed(() =>
		createFeatureCollection(
			displayLeakMarkers.value.map((item) => ({
				type: 'Feature',
				properties: {
					id: item.id,
					name: item.name
				},
				geometry: {
					type: 'Point',
					coordinates: [Number(item.lng), Number(item.lat)]
				}
			}))
		)
	)

	function createFeatureCollection(features) {
		return {
			type: 'FeatureCollection',
			features: Array.isArray(features) ? features : EMPTY_COLLECTION.features
		}
	}

	function createCircleFeature(centerLng, centerLat, radiusMeters, properties = {}, segments = 72) {
		if (!isValidCoordinate(centerLng, centerLat) || !Number.isFinite(Number(radiusMeters)) || Number(radiusMeters) <= 0) {
			return null
		}
		const coordinates = []
		for (let index = 0; index <= segments; index += 1) {
			const angle = (Math.PI * 2 * index) / segments
			const eastMeters = Math.cos(angle) * Number(radiusMeters)
			const northMeters = Math.sin(angle) * Number(radiusMeters)
			coordinates.push([offsetLng(centerLng, centerLat, eastMeters), offsetLat(centerLat, northMeters)])
		}
		return {
			type: 'Feature',
			properties,
			geometry: {
				type: 'Polygon',
				coordinates: [coordinates]
			}
		}
	}

	function collectBoundsPoints(centerLng, centerLat, radiusMeters, poiList, leakPointList) {
		const pointList = [
			[centerLng, centerLat],
			[offsetLng(centerLng, centerLat, radiusMeters), centerLat],
			[offsetLng(centerLng, centerLat, -radiusMeters), centerLat],
			[centerLng, offsetLat(centerLat, radiusMeters)],
			[centerLng, offsetLat(centerLat, -radiusMeters)]
		]
		;[...(Array.isArray(poiList) ? poiList : []), ...(Array.isArray(leakPointList) ? leakPointList : [])].forEach((item) => {
			if (isValidCoordinate(item.lng, item.lat)) {
				pointList.push([Number(item.lng), Number(item.lat)])
			}
		})
		return pointList
	}

	function offsetLng(centerLng, centerLat, eastMeters) {
		return Number(centerLng) + Number(eastMeters) / (111320 * Math.max(Math.cos((Number(centerLat) * Math.PI) / 180), 0.2))
	}

	function offsetLat(centerLat, northMeters) {
		return Number(centerLat) + Number(northMeters) / 110540
	}

	function isValidCoordinate(lng, lat) {
		return Number.isFinite(Number(lng)) && Number.isFinite(Number(lat))
	}
</script>

<style scoped lang="less">
	.emergency-map-stage {
		position: relative;
		height: 100%;
		border-radius: 10px;
		overflow: hidden;
		background:
			radial-gradient(circle at 50% 45%, rgba(0, 184, 255, 0.1), transparent 34%),
			linear-gradient(180deg, rgba(7, 18, 36, 0.94), rgba(4, 12, 26, 0.98));
		border: 1px solid rgba(98, 178, 255, 0.12);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.03);
	}

	.map-stage__halo,
	.map-stage__scanline,
	.map-stage__hud,
	.map-stage__stats {
		position: absolute;
		pointer-events: none;
	}

	.map-stage__halo {
		inset: 0;
		z-index: 2;
		background:
			radial-gradient(circle at 50% 50%, rgba(0, 184, 255, 0.16), transparent 34%),
			linear-gradient(180deg, rgba(7, 18, 36, 0.08), rgba(7, 18, 36, 0.24));
	}

	.map-stage__scanline {
		left: 0;
		right: 0;
		top: 0;
		height: 42%;
		z-index: 2;
		background: linear-gradient(180deg, rgba(122, 218, 255, 0.08), transparent 58%);
	}

	.map-stage__hud {
		top: 18px;
		left: 18px;
		z-index: 4;
		display: flex;
		flex-direction: column;
		gap: 6px;
		max-width: 320px;
		padding: 14px 16px;
		border-radius: 12px;
		background: rgba(4, 14, 28, 0.72);
		border: 1px solid rgba(89, 177, 255, 0.2);
		backdrop-filter: blur(10px);
	}

	.map-stage__eyebrow {
		font-size: 10px;
		letter-spacing: 0.24em;
		text-transform: uppercase;
		color: rgba(122, 218, 255, 0.78);
	}

	.map-stage__hud strong {
		font-size: 20px;
		font-weight: 700;
		color: #f3fbff;
	}

	.map-stage__hud p {
		margin: 0;
		font-size: 12px;
		line-height: 1.6;
		color: rgba(214, 227, 255, 0.72);
	}

	.map-stage__stats {
		right: 18px;
		top: 18px;
		z-index: 4;
		display: grid;
		gap: 10px;
		width: 148px;
	}

	.map-stage__stat {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 12px 14px;
		border-radius: 12px;
		background: rgba(4, 14, 28, 0.72);
		border: 1px solid rgba(89, 177, 255, 0.16);
		backdrop-filter: blur(10px);
	}

	.map-stage__stat span {
		font-size: 11px;
		color: rgba(214, 227, 255, 0.64);
	}

	.map-stage__stat strong {
		font-size: 18px;
		font-weight: 700;
		color: #ffffff;
	}

	.center-marker {
		position: relative;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 8px;
	}

	.center-marker__core {
		width: 16px;
		height: 16px;
		border-radius: 50%;
		border: 2px solid rgba(40, 215, 255, 0.9);
		background: rgba(232, 251, 255, 0.94);
		box-shadow: 0 0 0 10px rgba(40, 215, 255, 0.12), 0 0 22px rgba(40, 215, 255, 0.35);
	}

	.center-marker__label {
		padding: 6px 12px;
		border-radius: 999px;
		background: rgba(6, 20, 39, 0.88);
		border: 1px solid rgba(40, 215, 255, 0.3);
		color: #dff8ff;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.08em;
		white-space: nowrap;
	}

	.leak-marker {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 7px 12px;
		border-radius: 999px;
		background: rgba(111, 12, 29, 0.84);
		border: 1px solid rgba(255, 137, 155, 0.42);
		color: #ffd0d8;
		font-size: 11px;
		font-weight: 600;
		white-space: nowrap;
		box-shadow: 0 8px 18px rgba(0, 0, 0, 0.28), 0 0 18px rgba(255, 111, 137, 0.18);
	}

	.leak-marker .anticon {
		font-size: 12px;
	}

	:deep(.map) {
		z-index: 1;
	}

	:deep(.mapboxgl-canvas-container),
	:deep(.mapboxgl-canvas) {
		height: 100%;
	}

	:deep(.mapboxgl-marker) {
		z-index: 4;
	}
</style>
