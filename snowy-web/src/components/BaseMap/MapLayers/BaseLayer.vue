<template>
	<div class="baseLayer" />
	<MapController :right="props.right || 480" :type="type" @changeType="changeType" />
</template>

<script setup>
	import { inject, onMounted, ref, onUnmounted, watch } from 'vue'
	import MapController from '@/components/BaseMap/MapController/index.vue'

	const tk = '94a9ef43494c03ab32a38cba10671c05'
	// 天地图卫星影像
	const wgs2000Source = [0, 1, 2, 3, 4, 5, 6, 7].map(
		(item, index) =>
			`https://t${index}.tianditu.gov.cn/img_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=img&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tk}`
	)
	// 天地图电子底图
	const wgs2000Source_vec = [0, 1, 2, 3, 4, 5, 6, 7].map(
		(item, index) =>
			`https://t${index}.tianditu.gov.cn/vec_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=vec&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tk}`
	)

	// 天地图卫星影像注记
	const wgs2000Cva = [0, 1, 2, 3, 4, 5, 6, 7].map(
		(item, index) =>
			`https://t${index}.tianditu.gov.cn/cia_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=cia&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tk}`
	)

	// 天地图电子底图注记
	const wgs2000Cva_vec = [0, 1, 2, 3, 4, 5, 6, 7].map(
		(item, index) =>
			`https://t${index}.tianditu.gov.cn/cva_c/wmts?Service=WMTS&Request=GetTile&Version=1.0.0&layer=cva&style=default&tilematrixset=c&Format=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=${tk}`
	)

	const props = defineProps({
		id: String,
		before: Array,
		paint: Object,
		addCva: Boolean,
		right: Number,
		defaultType: {
			type: String,
			default: '电子底图'
		}
	})

	const type = ref(props.defaultType)

	// emit
	const map = inject('map')
	const _Id = props.id || 'base'
	const _layerId = `${_Id}-source-map-layer`
	const _sourceId = `${_Id}-layer-source`
	const flag = ref(true)
	onUnmounted(() => {
		uninstall()
		if (map.value.getLayer('emptyPositionLayer')) {
			map.value.removeLayer('emptyPositionLayer')
		}
	})

	// 移除挂载
	const uninstall = () => {
		if (map.value && map.value.getLayer(_layerId)) {
			map.value.removeLayer(_layerId)
			map.value.removeSource(_sourceId)
		}

		if (map.value && map.value.getLayer('cva_' + _layerId)) {
			map.value.removeLayer('cva_' + _layerId)
			map.value.removeSource('cva_' + _sourceId)
		}
	}

	// 切换图层类型
	const changeType = (_type) => {
		type.value = _type
		uninstall()

		addSource()
		addLayer()
	}

	// 增加一个空图层用于定位baselayer永远处于最底层
	const addEmptyLayer = () => {
		map.value.addLayer({
			id: 'emptyPositionLayer',
			type: 'fill',
			source: {
				type: 'geojson',
				data: {
					type: 'FeatureCollection',
					features: []
				}
			}
		})
	}

	// 初始化地图
	const init = () => {
		console.log(map, 'xxxxx')
		const loaded = map.value._loaded
		if (loaded) {
			addEmptyLayer()
			addSource()
			addLayer()
		} else {
			map.value.on('load', () => {
				addEmptyLayer()
				addSource()
				addLayer()
			})
		}
		flag.value = false
	}

	onMounted(() => {
		init()
	})

	// 添加source
	const addSource = () => {
		if (!map.value) return

		const tiles = type.value === '卫星影像' ? wgs2000Source : wgs2000Source_vec
		const cvaTiles = type.value === '卫星影像' ? wgs2000Cva : wgs2000Cva_vec

		map.value.addSource(_sourceId, {
			type: 'raster',
			tileSize: 256,
			maxzoom: 17,
			tiles: tiles
		})

		if (props.addCva) {
			map.value.addSource('cva_' + _sourceId, {
				type: 'raster',
				tileSize: 256,
				maxzoom: 17,
				tiles: cvaTiles
			})
		}
	}

	const addLayer = () => {
		if (!map.value) return

		// 底图
		map.value.addLayer(
			{
				id: _layerId,
				type: 'raster',
				source: _sourceId,
				minzoom: 0,
				maxzoom: 22,
				paint: props.paint || {}
			},
			props.before || 'emptyPositionLayer'
		)

		if (props.addCva) {
			map.value.addLayer(
				{
					id: 'cva_' + _layerId,
					type: 'raster',
					source: 'cva_' + _sourceId,
					minzoom: 0,
					maxzoom: 22,
					paint: props.paint || {}
				},
				props.before || 'emptyPositionLayer'
			)
		}
	}
</script>
