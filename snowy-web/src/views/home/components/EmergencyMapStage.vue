<template>
	<div class="emergency-map-stage">
		<BaseMap
			container-id="emergency-home-map"
			:bbox="mapBbox"
			:padding="[48, 48, 48, 48]"
			:min-zoom="8"
			:max-zoom="17"
			:zoom="12"
		>
			<RasterLayer id="emergency-dark-base" :url="darkBaseUrl" :paint="baseRasterPaint" />
			<RasterLayer id="emergency-dark-label" :url="darkLabelUrl" :paint="labelRasterPaint" />

			<Source id="emergency-flood-zones" :geojson="floodZoneGeojson" />
			<GeojsonLayer id="emergency-flood-fill" source-id="emergency-flood-zones" map-type="fill" :paint="floodFillPaint" />
			<GeojsonLayer id="emergency-flood-line" source-id="emergency-flood-zones" map-type="line" :paint="floodLinePaint" />

			<Source id="emergency-center-point" :geojson="centerPointGeojson" />
			<GeojsonLayer id="emergency-center-glow" source-id="emergency-center-point" map-type="circle" :paint="centerGlowPaint" />
			<GeojsonLayer id="emergency-center-core" source-id="emergency-center-point" map-type="circle" :paint="centerCorePaint" />

			<Marker marker-id="emergency-center-marker" :coordinate="centerCoordinate" :offset="[0, 0]">
				<div class="center-marker">
					<div class="center-marker__core"></div>
					<div class="center-marker__label">灾情中心</div>
				</div>
			</Marker>

			<Marker
				v-for="item in priorityInfrastructureList"
				:key="`facility-${item.id}`"
				:marker-id="`facility-${item.id}`"
				:coordinate="[item.lng, item.lat]"
				:offset="[0, -16]"
			>
				<div class="facility-marker" :class="{ 'facility-marker--affected': item.isAffected }" :style="{ '--facility-color': item.color }">
					<div class="facility-marker__tag">
						<component :is="item.icon" class="facility-marker__icon" />
						<span>{{ item.shortLabel }}</span>
					</div>
					<div class="facility-marker__body">
						<strong>{{ item.name }}</strong>
						<span>{{ item.type }} · {{ item.isAffected ? '圈内重点设施' : '重点设施' }}</span>
					</div>
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
		</div>
	</div>
</template>

<script setup>
	import { computed } from 'vue'
	import { AimOutlined } from '@ant-design/icons-vue'
	import BaseMap from '@/components/BaseMap/index.vue'
	import RasterLayer from '@/components/BaseMap/MapLayers/RasterLayer.vue'
	import GeojsonLayer from '@/components/BaseMap/MapLayers/GeojsonLayer.vue'
	import Marker from '@/components/BaseMap/MapLayers/Marker.vue'
	import Source from '@/components/BaseMap/MapSources/Source.vue'

	const DEFAULT_CENTER = [113.947321, 22.543211]
	const TDT_TOKEN = '94a9ef43494c03ab32a38cba10671c05'
	const EMPTY_COLLECTION = {
		type: 'FeatureCollection',
		features: []
	}
	const FALLBACK_COLOR = '#8ee7ff'
	const FLOOD_DEPTH_STYLE_MAP = {
		heavy: {
			fill: 'rgba(37, 99, 235, 0.28)',
			line: '#60a5fa'
		},
		medium: {
			fill: 'rgba(56, 189, 248, 0.24)',
			line: '#67e8f9'
		},
		light: {
			fill: 'rgba(16, 185, 129, 0.22)',
			line: '#6ee7b7'
		}
	}
	const PRIORITY_FACILITY_TYPES = ['应急指挥', '排涝设施', '医院', '学校', '消防设施', '电力设施', '交通枢纽', '物资仓储', '避险点']
	const FACILITY_SHORT_LABEL_MAP = {
		应急指挥: '指挥',
		排涝设施: '排涝',
		医院: '医院',
		学校: '学校',
		消防设施: '消防',
		电力设施: '电力',
		交通枢纽: '交通',
		物资仓储: '物资',
		避险点: '避险'
	}

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
		},
		iconMap: {
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
	const priorityInfrastructureList = computed(() =>
		PRIORITY_FACILITY_TYPES.flatMap((type) => {
			const matchedList = props.poiList
				.filter((item) => item.type === type && isValidCoordinate(item.lng, item.lat))
				.map((item) => ({
					...item,
					isAffected: affectedPoiIdSet.value.has(item.id),
					distanceToCenter: calculateDistanceMeters(safeCenterLng.value, safeCenterLat.value, Number(item.lng), Number(item.lat)),
					color: props.colorMap[item.type] || FALLBACK_COLOR,
					icon: props.iconMap[item.type] || AimOutlined,
					shortLabel: FACILITY_SHORT_LABEL_MAP[item.type] || item.type
				}))
				.sort((left, right) => {
					if (left.isAffected !== right.isAffected) {
						return left.isAffected ? -1 : 1
					}
					return left.distanceToCenter - right.distanceToCenter
				})
			return matchedList.length ? [matchedList[0]] : []
		})
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

	const floodFillPaint = {
		'fill-color': [
			'match',
			['get', 'depthLevel'],
			'heavy',
			FLOOD_DEPTH_STYLE_MAP.heavy.fill,
			'medium',
			FLOOD_DEPTH_STYLE_MAP.medium.fill,
			FLOOD_DEPTH_STYLE_MAP.light.fill
		],
		'fill-opacity': 1
	}
	const floodLinePaint = {
		'line-color': [
			'match',
			['get', 'depthLevel'],
			'heavy',
			FLOOD_DEPTH_STYLE_MAP.heavy.line,
			'medium',
			FLOOD_DEPTH_STYLE_MAP.medium.line,
			FLOOD_DEPTH_STYLE_MAP.light.line
		],
		'line-width': 2.2,
		'line-opacity': 0.92
	}
	const centerGlowPaint = {
		'circle-color': '#ef4444',
		'circle-radius': 16,
		'circle-blur': 0.82,
		'circle-opacity': 0.58
	}
	const centerCorePaint = {
		'circle-color': '#fff1f2',
		'circle-radius': 5,
		'circle-stroke-color': '#ef4444',
		'circle-stroke-width': 2.6
	}
	const mapBbox = computed(() => {
		const focusRadiusMeters = clamp(safeRadiusMeters.value * 0.55, 280, 900)
		const focusPoiList = props.affectedPoiList.length ? props.affectedPoiList : priorityInfrastructureList.value
		const pointList = [
			...collectBoundsPoints(safeCenterLng.value, safeCenterLat.value, focusRadiusMeters, focusPoiList),
			...collectGeojsonCoordinatePoints(floodZoneGeojson.value)
		]
		const lngList = pointList.map((item) => item[0])
		const latList = pointList.map((item) => item[1])
		const minLng = Math.min(...lngList)
		const maxLng = Math.max(...lngList)
		const minLat = Math.min(...latList)
		const maxLat = Math.max(...latList)
		const lngPadding = Math.max((maxLng - minLng) * 0.1, 0.006)
		const latPadding = Math.max((maxLat - minLat) * 0.1, 0.005)
		return [
			[minLng - lngPadding, minLat - latPadding],
			[maxLng + lngPadding, maxLat + latPadding]
		]
	})

	const floodZoneGeojson = computed(() => {
		const featureList = []
		const primarySeedList = resolveFloodSeedList({
			primaryFirst: priorityInfrastructureList.value.filter((item) => item.isAffected),
			fallback: props.affectedPoiList,
			limit: 3
		})
		const secondarySeedList = resolveFloodSeedList({
			fallback: props.affectedPoiList.filter((item) => !primarySeedList.some((seed) => seed.id === item.id)),
			limit: 4
		})

		primarySeedList.forEach((item, index) => {
			const radiusX = clamp(safeRadiusMeters.value * 0.17 - index * 24, 180, 360)
			const radiusY = clamp(safeRadiusMeters.value * 0.11 - index * 18, 120, 240)
			const rotation = 18 + index * 34
			const depthMeters = clamp(1.85 - index * 0.32, 0.95, 2.1)
			const zone = createFloodPocketFeature(item.lng, item.lat, radiusX, radiusY, rotation, {
				zone: 'primary',
				seedId: item.id,
				depthMeters,
				depthLevel: resolveFloodDepthLevel(depthMeters)
			})
			if (zone) {
				featureList.push(zone)
			}
		})

		secondarySeedList.forEach((item, index) => {
			const radiusX = clamp(safeRadiusMeters.value * 0.11 - index * 12, 110, 220)
			const radiusY = clamp(safeRadiusMeters.value * 0.08 - index * 10, 80, 170)
			const rotation = 42 + index * 29
			const depthMeters = clamp(1.05 - index * 0.18, 0.35, 1.1)
			const zone = createFloodPocketFeature(item.lng, item.lat, radiusX, radiusY, rotation, {
				zone: 'secondary',
				seedId: item.id,
				depthMeters,
				depthLevel: resolveFloodDepthLevel(depthMeters)
			})
			if (zone) {
				featureList.push(zone)
			}
		})

		if (!featureList.length) {
			const fallbackZone = createFloodPocketFeature(
				safeCenterLng.value,
				safeCenterLat.value,
				clamp(safeRadiusMeters.value * 0.16, 180, 320),
				clamp(safeRadiusMeters.value * 0.1, 120, 220),
				20,
				{ zone: 'primary', seedId: 'fallback-center', depthMeters: 1.2, depthLevel: resolveFloodDepthLevel(1.2) }
			)
			if (fallbackZone) {
				featureList.push(fallbackZone)
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

	function createFeatureCollection(features) {
		return {
			type: 'FeatureCollection',
			features: Array.isArray(features) ? features : EMPTY_COLLECTION.features
		}
	}

	function resolveFloodDepthLevel(depthMeters) {
		if (Number(depthMeters) > 1.5) {
			return 'heavy'
		}
		if (Number(depthMeters) > 0.8) {
			return 'medium'
		}
		return 'light'
	}

	function resolveFloodSeedList({ primaryFirst = [], fallback = [], limit = 3 }) {
		const resultList = []
		const addedIdSet = new Set()
		;[...(Array.isArray(primaryFirst) ? primaryFirst : []), ...(Array.isArray(fallback) ? fallback : [])].forEach((item) => {
			if (!item || !isValidCoordinate(item.lng, item.lat)) {
				return
			}
			const key = item.id || `${item.lng}-${item.lat}`
			if (addedIdSet.has(key) || resultList.length >= limit) {
				return
			}
			addedIdSet.add(key)
			resultList.push(item)
		})
		return resultList
	}

	function createFloodPocketFeature(centerLng, centerLat, radiusXMeters, radiusYMeters, rotationDeg, properties = {}, segments = 48) {
		if (
			!isValidCoordinate(centerLng, centerLat) ||
			!Number.isFinite(Number(radiusXMeters)) ||
			!Number.isFinite(Number(radiusYMeters)) ||
			Number(radiusXMeters) <= 0 ||
			Number(radiusYMeters) <= 0
		) {
			return null
		}
		const coordinates = []
		const rotation = (Number(rotationDeg) * Math.PI) / 180
		for (let index = 0; index <= segments; index += 1) {
			const angle = (Math.PI * 2 * index) / segments
			const waveFactor = 1 + 0.12 * Math.sin(angle * 3 + rotation) + 0.06 * Math.cos(angle * 2 - rotation)
			const ellipseEast = Math.cos(angle) * Number(radiusXMeters) * waveFactor
			const ellipseNorth = Math.sin(angle) * Number(radiusYMeters) * waveFactor
			const eastMeters = ellipseEast * Math.cos(rotation) - ellipseNorth * Math.sin(rotation)
			const northMeters = ellipseEast * Math.sin(rotation) + ellipseNorth * Math.cos(rotation)
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

	function clamp(value, min, max) {
		return Math.min(Math.max(Number(value), min), max)
	}

	function collectBoundsPoints(centerLng, centerLat, radiusMeters, poiList) {
		const pointList = [
			[centerLng, centerLat],
			[offsetLng(centerLng, centerLat, radiusMeters), centerLat],
			[offsetLng(centerLng, centerLat, -radiusMeters), centerLat],
			[centerLng, offsetLat(centerLat, radiusMeters)],
			[centerLng, offsetLat(centerLat, -radiusMeters)]
		]
		;[...(Array.isArray(poiList) ? poiList : [])].forEach((item) => {
			if (isValidCoordinate(item.lng, item.lat)) {
				pointList.push([Number(item.lng), Number(item.lat)])
			}
		})
		return pointList
	}

	function collectGeojsonCoordinatePoints(geojson) {
		const pointList = []
		;(Array.isArray(geojson?.features) ? geojson.features : []).forEach((feature) => {
			collectNestedCoordinatePoints(feature?.geometry?.coordinates, pointList)
		})
		return pointList
	}

	function collectNestedCoordinatePoints(coordinates, pointList) {
		if (!Array.isArray(coordinates)) {
			return
		}
		if (coordinates.length >= 2 && isValidCoordinate(coordinates[0], coordinates[1])) {
			pointList.push([Number(coordinates[0]), Number(coordinates[1])])
			return
		}
		coordinates.forEach((item) => {
			collectNestedCoordinatePoints(item, pointList)
		})
	}

	function offsetLng(centerLng, centerLat, eastMeters) {
		return Number(centerLng) + Number(eastMeters) / (111320 * Math.max(Math.cos((Number(centerLat) * Math.PI) / 180), 0.2))
	}

	function offsetLat(centerLat, northMeters) {
		return Number(centerLat) + Number(northMeters) / 110540
	}

	function calculateDistanceMeters(centerLng, centerLat, targetLng, targetLat) {
		const earthRadius = 6371000
		const latDistance = ((targetLat - centerLat) * Math.PI) / 180
		const lngDistance = ((targetLng - centerLng) * Math.PI) / 180
		const a =
			Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
			Math.cos((centerLat * Math.PI) / 180) *
				Math.cos((targetLat * Math.PI) / 180) *
				Math.sin(lngDistance / 2) *
				Math.sin(lngDistance / 2)
		const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
		return earthRadius * c
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

	.center-marker::before {
		content: '';
		position: absolute;
		top: -12px;
		left: 50%;
		width: 44px;
		height: 44px;
		border-radius: 50%;
		border: 2px solid rgba(248, 113, 113, 0.55);
		background: rgba(239, 68, 68, 0.14);
		transform: translateX(-50%);
		animation: centerMarkerPulse 1.8s ease-out infinite;
	}

	.center-marker__core {
		position: relative;
		width: 18px;
		height: 18px;
		border-radius: 50%;
		border: 2px solid rgba(254, 202, 202, 0.95);
		background: radial-gradient(circle, rgba(255, 244, 244, 0.98) 0%, rgba(248, 113, 113, 0.92) 55%, rgba(220, 38, 38, 0.96) 100%);
		box-shadow: 0 0 0 10px rgba(239, 68, 68, 0.16), 0 0 26px rgba(239, 68, 68, 0.48);
		animation: centerMarkerFlash 1.2s ease-in-out infinite;
	}

	.center-marker__label {
		padding: 6px 12px;
		border-radius: 999px;
		background: rgba(68, 10, 18, 0.92);
		border: 1px solid rgba(248, 113, 113, 0.68);
		color: #fff5f5;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.08em;
		white-space: nowrap;
		box-shadow: 0 0 16px rgba(239, 68, 68, 0.26);
		animation: centerMarkerLabelBlink 1.2s steps(2, end) infinite;
	}

	@keyframes centerMarkerPulse {
		0% {
			opacity: 0.9;
			transform: translateX(-50%) scale(0.72);
		}
		100% {
			opacity: 0;
			transform: translateX(-50%) scale(1.55);
		}
	}

	@keyframes centerMarkerFlash {
		0%,
		100% {
			transform: scale(1);
			box-shadow: 0 0 0 10px rgba(239, 68, 68, 0.16), 0 0 26px rgba(239, 68, 68, 0.48);
		}
		50% {
			transform: scale(1.18);
			box-shadow: 0 0 0 16px rgba(239, 68, 68, 0.08), 0 0 34px rgba(248, 113, 113, 0.72);
		}
	}

	@keyframes centerMarkerLabelBlink {
		0%,
		100% {
			opacity: 1;
		}
		50% {
			opacity: 0.74;
		}
	}

	.facility-marker {
		position: relative;
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 132px;
		padding: 8px 12px 8px 8px;
		border-radius: 14px;
		background: rgba(5, 17, 32, 0.88);
		border: 1px solid var(--facility-color);
		box-shadow: 0 10px 24px rgba(0, 0, 0, 0.28);
	}

	.facility-marker::after {
		content: '';
		position: absolute;
		left: 22px;
		bottom: -8px;
		width: 10px;
		height: 10px;
		border-right: 1px solid var(--facility-color);
		border-bottom: 1px solid var(--facility-color);
		background: rgba(5, 17, 32, 0.88);
		transform: rotate(45deg);
	}

	.facility-marker--affected {
		background: rgba(12, 24, 44, 0.94);
		box-shadow: 0 12px 28px rgba(0, 0, 0, 0.32);
	}

	.facility-marker__tag {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		flex-direction: column;
		gap: 2px;
		min-width: 38px;
		height: 38px;
		padding: 0 8px;
		border-radius: 10px;
		background: rgba(255, 255, 255, 0.06);
		color: var(--facility-color);
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.04em;
		line-height: 1;
	}

	.facility-marker__icon {
		font-size: 15px;
	}

	.facility-marker__body {
		display: flex;
		flex-direction: column;
		gap: 3px;
		min-width: 0;
	}

	.facility-marker__body strong {
		max-width: 154px;
		color: #f5fbff;
		font-size: 12px;
		font-weight: 700;
		line-height: 1.2;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.facility-marker__body span {
		color: rgba(214, 227, 255, 0.68);
		font-size: 10px;
		line-height: 1.2;
		white-space: nowrap;
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
