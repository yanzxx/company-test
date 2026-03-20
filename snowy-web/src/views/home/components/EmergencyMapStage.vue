<template>
	<div class="emergency-map-stage">
		<BaseMap
			container-id="emergency-home-map"
			:bbox="mapBbox"
			:padding="[48, 48, 48, 48]"
			:min-zoom="8"
			:max-zoom="17"
			:zoom="12"
			:lock-camera-after-init="true"
		>
			<RasterLayer id="emergency-dark-base" :url="darkBaseUrl" :paint="baseRasterPaint" />
			<RasterLayer id="emergency-dark-label" :url="darkLabelUrl" :paint="labelRasterPaint" />

			<Source id="emergency-flood-zones" :geojson="floodZoneGeojson" />
			<Source id="emergency-flood-wave-bands" :geojson="floodWaveGeojson" />
			<GeojsonLayer id="emergency-flood-wave-fill" source-id="emergency-flood-wave-bands" map-type="fill" :paint="floodWaveFillPaint" />
			<GeojsonLayer id="emergency-flood-wave-line" source-id="emergency-flood-wave-bands" map-type="line" :paint="floodWaveLinePaint" />
			<GeojsonLayer id="emergency-flood-fill" source-id="emergency-flood-zones" map-type="fill" :paint="floodFillPaint" />
			<GeojsonLayer id="emergency-flood-ripple-soft" source-id="emergency-flood-zones" map-type="line" :paint="floodRippleSoftPaint" />
			<GeojsonLayer id="emergency-flood-ripple" source-id="emergency-flood-zones" map-type="line" :paint="floodRipplePaint" />
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

			<Marker
				v-for="item in displayLeakMarkers"
				:key="`leak-${item.id}`"
				:marker-id="`leak-${item.id}`"
				:coordinate="[item.lng, item.lat]"
				:offset="[0, -30]"
			>
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
	import { computed, onMounted, onUnmounted, ref } from 'vue'
	import { AimOutlined, EnvironmentOutlined } from '@ant-design/icons-vue'
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
	const FLOOD_ANIMATION_INTERVAL = 180
	const FLOOD_DEPTH_COLOR_STOPS = [
		{ depth: 0.3, fill: '#34d399', line: '#86efac', ripple: '#dcfce7' },
		{ depth: 0.8, fill: '#10b981', line: '#6ee7b7', ripple: '#a7f3d0' },
		{ depth: 1.2, fill: '#22d3ee', line: '#67e8f9', ripple: '#a5f3fc' },
		{ depth: 1.5, fill: '#0ea5e9', line: '#38bdf8', ripple: '#7dd3fc' },
		{ depth: 2.1, fill: '#2563eb', line: '#93c5fd', ripple: '#dbeafe' }
	]
	const floodFillColorExpression = buildDepthColorExpression('fill')
	const floodLineColorExpression = buildDepthColorExpression('line')
	const floodRippleColorExpression = buildDepthColorExpression('ripple')
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
		signalCounter: {
			type: Number,
			default: 0
		},
		latestSignal: {
			type: Object,
			default: () => null
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
	const floodAnimationTick = ref(0)
	const affectedPoiIdSet = computed(() => new Set(props.affectedPoiList.map((item) => item.id)))
	const displayLeakMarkers = computed(() =>
		props.leakPointList
			.filter((item) => isValidCoordinate(item.lng, item.lat))
			.slice(0, 6)
			.map((item, index) => ({
				...item,
				id: item.id || `temp-leak-${index}`,
				name: item.name || `漏水点-${index + 1}`,
				lng: Number(item.lng),
				lat: Number(item.lat)
			}))
	)
	const latestSignalType = computed(() => props.latestSignal?.signalType || '')
	const floodAnimationState = computed(() => {
		const phase = floodAnimationTick.value * 0.42
		return {
			fillPulse: (Math.sin(phase) + 1) / 2,
			edgePulse: (Math.sin(phase + Math.PI / 3) + 1) / 2,
			ripplePrimary: (Math.sin(phase) + 1) / 2,
			rippleSecondary: (Math.sin(phase + Math.PI * 0.72) + 1) / 2
		}
	})
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
	const floodScenarioFactors = computed(() => {
		const signalIndex = Math.max(Number(props.signalCounter || 0), 0)
		const leakCount = displayLeakMarkers.value.length
		const affectedCount = props.affectedPoiList.length
		const signalWave = (Math.sin(signalIndex * 0.78 + leakCount * 0.33) + 1) / 2
		const clusterWave = (Math.cos(signalIndex * 0.46 + affectedCount * 0.18) + 1) / 2

		let rangeBoost = 1 + (signalWave - 0.5) * 0.18 + Math.min(leakCount, 6) * 0.022 + Math.min(affectedCount, 24) * 0.006
		let depthBoost = 1 + (clusterWave - 0.5) * 0.24 + Math.min(leakCount, 6) * 0.04 + Math.min(affectedCount, 24) * 0.008

		if (latestSignalType.value === 'RISK_AGGRAVATED') {
			rangeBoost += 0.09
			depthBoost += 0.12
		} else if (latestSignalType.value === 'NEW_LEAK_POINT') {
			rangeBoost += 0.03
			depthBoost += 0.16
		} else if (latestSignalType.value === 'MANUAL_RADIUS_UPDATE') {
			rangeBoost += 0.05
			depthBoost += 0.06
		}

		return {
			signalWave,
			clusterWave,
			primaryRadiusScale: clamp(rangeBoost + 0.04, 0.94, 1.32),
			secondaryRadiusScale: clamp(rangeBoost - 0.06, 0.88, 1.22),
			primaryDepthScale: clamp(depthBoost + 0.06, 0.92, 1.32),
			secondaryDepthScale: clamp(depthBoost - 0.08, 0.85, 1.18),
			leakRadiusScale: clamp(0.82 + leakCount * 0.07 + signalWave * 0.1, 0.82, 1.18),
			leakDepthScale: clamp(
				0.96 + leakCount * 0.08 + clusterWave * 0.12 + (latestSignalType.value === 'NEW_LEAK_POINT' ? 0.18 : 0),
				0.95,
				1.42
			)
		}
	})
	const floodPocketDescriptors = computed(() => {
		const scenarioFactors = floodScenarioFactors.value
		const primarySeedList = resolveFloodSeedList({
			primaryFirst: priorityInfrastructureList.value.filter((item) => item.isAffected),
			fallback: props.affectedPoiList,
			limit: 3
		})
		const secondarySeedList = resolveFloodSeedList({
			fallback: props.affectedPoiList.filter((item) => !primarySeedList.some((seed) => seed.id === item.id)),
			limit: 4
		})
		const descriptorList = []

		primarySeedList.forEach((item, index) => {
			const depthMeters = clamp(
				(1.72 - index * 0.26 + scenarioFactors.signalWave * 0.22 + (index === 0 ? scenarioFactors.clusterWave * 0.24 : 0)) *
					scenarioFactors.primaryDepthScale,
				0.95,
				2.1
			)
			descriptorList.push({
				centerLng: Number(item.lng),
				centerLat: Number(item.lat),
				radiusX: clamp((safeRadiusMeters.value * (0.16 - index * 0.014)) * scenarioFactors.primaryRadiusScale, 180, 390),
				radiusY: clamp((safeRadiusMeters.value * (0.108 - index * 0.012)) * (scenarioFactors.primaryRadiusScale - 0.04), 120, 260),
				rotation: 18 + index * 34 + scenarioFactors.signalWave * 16,
				zone: 'primary',
				seedId: item.id,
				depthMeters,
				depthLevel: resolveFloodDepthLevel(depthMeters),
				phaseOffset: index * 0.85,
				pulseScale: 0.11
			})
		})

		secondarySeedList.forEach((item, index) => {
			const depthMeters = clamp(
				(0.78 - index * 0.12 + scenarioFactors.clusterWave * 0.14 + (index === 0 ? scenarioFactors.signalWave * 0.08 : 0)) *
					scenarioFactors.secondaryDepthScale,
				0.35,
				1.28
			)
			descriptorList.push({
				centerLng: Number(item.lng),
				centerLat: Number(item.lat),
				radiusX: clamp((safeRadiusMeters.value * (0.1 - index * 0.01)) * scenarioFactors.secondaryRadiusScale, 105, 250),
				radiusY: clamp((safeRadiusMeters.value * (0.075 - index * 0.008)) * (scenarioFactors.secondaryRadiusScale - 0.03), 78, 180),
				rotation: 42 + index * 29 + scenarioFactors.clusterWave * 12,
				zone: 'secondary',
				seedId: item.id,
				depthMeters,
				depthLevel: resolveFloodDepthLevel(depthMeters),
				phaseOffset: 1.4 + index * 0.68,
				pulseScale: 0.08
			})
		})

		displayLeakMarkers.value.slice(0, 2).forEach((item, index) => {
			const depthMeters = clamp(
				(1.02 + displayLeakMarkers.value.length * 0.08 - index * 0.1 + scenarioFactors.signalWave * 0.24) *
					scenarioFactors.leakDepthScale,
				0.85,
				2.1
			)
			descriptorList.push({
				centerLng: Number(item.lng),
				centerLat: Number(item.lat),
				radiusX: clamp((104 + displayLeakMarkers.value.length * 18 - index * 12) * scenarioFactors.leakRadiusScale, 92, 240),
				radiusY: clamp((76 + displayLeakMarkers.value.length * 12 - index * 10) * (scenarioFactors.leakRadiusScale - 0.04), 70, 190),
				rotation: 12 + index * 41 + scenarioFactors.signalWave * 24,
				zone: 'leak',
				seedId: item.id,
				depthMeters,
				depthLevel: resolveFloodDepthLevel(depthMeters),
				phaseOffset: 2.1 + index * 0.72,
				pulseScale: 0.06
			})
		})

		if (!descriptorList.length) {
			descriptorList.push({
				centerLng: safeCenterLng.value,
				centerLat: safeCenterLat.value,
				radiusX: clamp(safeRadiusMeters.value * 0.16, 180, 320),
				radiusY: clamp(safeRadiusMeters.value * 0.1, 120, 220),
				rotation: 20,
				zone: 'primary',
				seedId: 'fallback-center',
				depthMeters: 1.2,
				depthLevel: resolveFloodDepthLevel(1.2),
				phaseOffset: 0,
				pulseScale: 0.1
			})
		}

		return descriptorList
	})
	const centerCoordinate = computed(() =>
		calculateDisasterCenterCoordinate(floodPocketDescriptors.value, safeRadiusMeters.value, [safeCenterLng.value, safeCenterLat.value])
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

	const floodFillPaint = computed(() => {
		const { fillPulse } = floodAnimationState.value
		return {
			'fill-color': floodFillColorExpression,
			'fill-opacity': ['interpolate', ['linear'], ['get', 'depthMeters'], 0.3, 0.16 + fillPulse * 0.03, 0.8, 0.2 + fillPulse * 0.04, 1.5, 0.26 + fillPulse * 0.05, 2.1, 0.32 + fillPulse * 0.06]
		}
	})
	const floodWaveFillPaint = computed(() => {
		const { ripplePrimary, rippleSecondary } = floodAnimationState.value
		return {
			'fill-color': floodRippleColorExpression,
			'fill-opacity': ['case', ['==', ['get', 'bandIndex'], 1], 0.04 + ripplePrimary * 0.07, 0.025 + rippleSecondary * 0.05]
		}
	})
	const floodWaveLinePaint = computed(() => {
		const { ripplePrimary, rippleSecondary } = floodAnimationState.value
		return {
			'line-color': floodRippleColorExpression,
			'line-width': ['case', ['==', ['get', 'bandIndex'], 1], 1.6 + ripplePrimary * 2.6, 1 + rippleSecondary * 2.1],
			'line-opacity': ['case', ['==', ['get', 'bandIndex'], 1], 0.2 + (1 - ripplePrimary) * 0.24, 0.14 + (1 - rippleSecondary) * 0.2],
			'line-blur': 0.7
		}
	})
	const floodLinePaint = computed(() => {
		const { edgePulse } = floodAnimationState.value
		return {
			'line-color': floodLineColorExpression,
			'line-width': 2 + edgePulse * 0.85,
			'line-opacity': 0.82 + edgePulse * 0.14
		}
	})
	const floodRipplePaint = computed(() => {
		const { ripplePrimary } = floodAnimationState.value
		return {
			'line-color': floodRippleColorExpression,
			'line-width': 2.6 + ripplePrimary * 8.8,
			'line-opacity': 0.03 + (1 - ripplePrimary) * 0.18,
			'line-blur': 0.4 + ripplePrimary * 1.3
		}
	})
	const floodRippleSoftPaint = computed(() => {
		const { rippleSecondary } = floodAnimationState.value
		return {
			'line-color': floodRippleColorExpression,
			'line-width': 5.2 + rippleSecondary * 13.5,
			'line-opacity': 0.015 + (1 - rippleSecondary) * 0.11,
			'line-blur': 1.1 + rippleSecondary * 2.1
		}
	})
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

	let floodAnimationTimer = null

	onMounted(() => {
		floodAnimationTimer = window.setInterval(() => {
			floodAnimationTick.value = (floodAnimationTick.value + 1) % 10000
		}, FLOOD_ANIMATION_INTERVAL)
	})

	onUnmounted(() => {
		if (floodAnimationTimer) {
			window.clearInterval(floodAnimationTimer)
			floodAnimationTimer = null
		}
	})
	const mapBbox = computed(() => {
		const focusRadiusMeters = clamp(safeRadiusMeters.value * 0.68, 320, 980)
		const focusPoiList = props.affectedPoiList.length ? props.affectedPoiList : priorityInfrastructureList.value
		const pointList = collectBoundsPoints(centerCoordinate.value[0], centerCoordinate.value[1], focusRadiusMeters, focusPoiList)
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
		const animationPhase = floodAnimationTick.value * 0.36
		return createFeatureCollection(
			floodPocketDescriptors.value
				.map((item) =>
					createFloodPocketFeature(
						item.centerLng,
						item.centerLat,
						item.radiusX,
						item.radiusY,
						item.rotation,
						{
							zone: item.zone,
							seedId: item.seedId,
							depthMeters: item.depthMeters,
							depthLevel: item.depthLevel
						},
						48,
						animationPhase + item.phaseOffset,
						item.pulseScale
					)
				)
				.filter(Boolean)
		)
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
	const floodWaveGeojson = computed(() => {
		const wavePhase = floodAnimationTick.value * 0.3
		const featureList = []
		;(Array.isArray(floodZoneGeojson.value?.features) ? floodZoneGeojson.value.features : []).forEach((feature, index) => {
			const nearBand = createFloodWaveBandFeature(feature, 1.1 + Math.sin(wavePhase + index * 0.4) * 0.05, {
				...(feature.properties || {}),
				bandIndex: 1
			})
			const farBand = createFloodWaveBandFeature(feature, 1.24 + Math.sin(wavePhase + 1.2 + index * 0.33) * 0.08, {
				...(feature.properties || {}),
				bandIndex: 2
			})
			if (nearBand) {
				featureList.push(nearBand)
			}
			if (farBand) {
				featureList.push(farBand)
			}
		})
		return createFeatureCollection(featureList)
	})

	function createFeatureCollection(features) {
		return {
			type: 'FeatureCollection',
			features: Array.isArray(features) ? features : EMPTY_COLLECTION.features
		}
	}

	function calculateDisasterCenterCoordinate(descriptorList, radiusMeters, fallbackCenter) {
		const fallbackCoordinate = Array.isArray(fallbackCenter) && fallbackCenter.length >= 2 ? fallbackCenter : DEFAULT_CENTER
		const validDescriptorList = (Array.isArray(descriptorList) ? descriptorList : []).filter((item) =>
			isValidCoordinate(item?.centerLng, item?.centerLat)
		)
		if (!validDescriptorList.length) {
			return fallbackCoordinate
		}
		if (validDescriptorList.length === 1) {
			return [Number(validDescriptorList[0].centerLng), Number(validDescriptorList[0].centerLat)]
		}

		const concentrationDistance = clamp(Number(radiusMeters) * 0.28, 180, 420)
		const weightedDescriptorList = validDescriptorList.map((item, index) => {
			const depthWeight = Math.pow(Math.max(Number(item.depthMeters || 0), 0.35), 2.45)
			const zoneWeight = item.zone === 'primary' ? 1.34 : item.zone === 'leak' ? 1.18 : 0.9
			const concentrationWeight = validDescriptorList.reduce((total, otherItem, otherIndex) => {
				if (index === otherIndex) {
					return total
				}
				const distanceMeters = calculateDistanceMeters(
					Number(item.centerLng),
					Number(item.centerLat),
					Number(otherItem.centerLng),
					Number(otherItem.centerLat)
				)
				const closeness = Math.max(0, 1 - distanceMeters / concentrationDistance)
				return total + closeness * Math.max(Number(otherItem.depthMeters || 0), 0.35)
			}, 0)
			return {
				...item,
				weight: depthWeight * zoneWeight * (1 + concentrationWeight * 0.72)
			}
		})

		const totalWeight = weightedDescriptorList.reduce((total, item) => total + item.weight, 0)
		if (!Number.isFinite(totalWeight) || totalWeight <= 0) {
			return fallbackCoordinate
		}

		const centerLng =
			weightedDescriptorList.reduce((total, item) => total + Number(item.centerLng) * item.weight, 0) / totalWeight
		const centerLat =
			weightedDescriptorList.reduce((total, item) => total + Number(item.centerLat) * item.weight, 0) / totalWeight
		return [centerLng, centerLat]
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

	function createFloodPocketFeature(centerLng, centerLat, radiusXMeters, radiusYMeters, rotationDeg, properties = {}, segments = 48, phase = 0, pulseScale = 0) {
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
		const scale = 1 + Math.sin(Number(phase)) * Number(pulseScale || 0)
		for (let index = 0; index <= segments; index += 1) {
			const angle = (Math.PI * 2 * index) / segments
			const waveFactor =
				1 +
				0.14 * Math.sin(angle * 3 + rotation + Number(phase)) +
				0.08 * Math.cos(angle * 2 - rotation - Number(phase) * 0.7) +
				0.04 * Math.sin(angle * 6 + Number(phase) * 1.8)
			const ellipseEast = Math.cos(angle) * Number(radiusXMeters) * waveFactor * scale
			const ellipseNorth = Math.sin(angle) * Number(radiusYMeters) * waveFactor * scale
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

	function buildDepthColorExpression(colorKey) {
		const expression = ['interpolate', ['linear'], ['get', 'depthMeters']]
		FLOOD_DEPTH_COLOR_STOPS.forEach((item) => {
			expression.push(item.depth, item[colorKey])
		})
		return expression
	}

	function createFloodWaveBandFeature(feature, scaleFactor, properties = {}) {
		const ring = feature?.geometry?.type === 'Polygon' ? feature.geometry.coordinates?.[0] : null
		if (!Array.isArray(ring) || ring.length < 4 || Number(scaleFactor) <= 1) {
			return null
		}
		const center = calculatePolygonCenter(ring)
		const outerRing = ring.map((item) => scaleCoordinateFromCenter(item, center, scaleFactor))
		const innerRing = ring.slice().reverse()
		return {
			type: 'Feature',
			properties,
			geometry: {
				type: 'Polygon',
				coordinates: [outerRing, innerRing]
			}
		}
	}

	function calculatePolygonCenter(ring) {
		const validRing = ring.slice(0, -1)
		if (!validRing.length) {
			return [0, 0]
		}
		const lngTotal = validRing.reduce((total, item) => total + Number(item[0] || 0), 0)
		const latTotal = validRing.reduce((total, item) => total + Number(item[1] || 0), 0)
		return [lngTotal / validRing.length, latTotal / validRing.length]
	}

	function scaleCoordinateFromCenter(coordinate, center, scaleFactor) {
		const lng = Number(center[0]) + (Number(coordinate[0]) - Number(center[0])) * Number(scaleFactor)
		const lat = Number(center[1]) + (Number(coordinate[1]) - Number(center[1])) * Number(scaleFactor)
		return [lng, lat]
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

	.leak-marker {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		padding: 8px 12px;
		border-radius: 999px;
		background: rgba(86, 10, 22, 0.96);
		border: 1px solid rgba(255, 154, 170, 0.72);
		color: #fff3f5;
		font-size: 12px;
		font-weight: 700;
		line-height: 1;
		white-space: nowrap;
		box-shadow: 0 10px 22px rgba(0, 0, 0, 0.32), 0 0 18px rgba(255, 111, 137, 0.28);
		z-index: 12;
		transform: translateY(-6px);
	}

	.leak-marker::before {
		content: '';
		position: absolute;
		left: 50%;
		bottom: -11px;
		width: 2px;
		height: 10px;
		background: linear-gradient(180deg, rgba(255, 165, 176, 0.88), rgba(255, 165, 176, 0));
		transform: translateX(-50%);
	}

	.leak-marker::after {
		content: '';
		position: absolute;
		left: 50%;
		bottom: -16px;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: #ff6f89;
		border: 2px solid rgba(255, 241, 242, 0.95);
		box-shadow: 0 0 0 6px rgba(255, 111, 137, 0.16);
		transform: translateX(-50%);
	}

	.leak-marker .anticon {
		font-size: 13px;
		color: #ffb3c0;
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
