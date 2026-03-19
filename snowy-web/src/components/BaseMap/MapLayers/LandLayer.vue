
<template>
	<div></div>
</template>
<script setup>
	import { inject, ref, onUnmounted, nextTick, watch } from 'vue'
	import tool from '@/utils/tool'
	import sysConfig from '@/config/index'

	const token = tool.data.get('TOKEN')
	const map = inject('map', null)
	// const farmlandId = 

	const props = defineProps({
		id: String,
		url: String,
		type: String,
		paint: Object,
		sourceLayer: String,
		filter: Array,
		canClick: Boolean,
		showName: Array,
		selectedId: String,
		farmId: String,
		isEdit: Boolean
	})

	const _Id = props.id || 'land-polygon'
	const _layername = `${_Id}-pbf-layer`
	const _symbolLayerName = `${_Id}-pbf-symbol-layer`
	const _lineLayerName = `${_Id}-pbf-line-layer`
	const _sourcename = `${_Id}-pbf-source`
	const _symbolSourcename = `${_Id}-pbf-symbol-source`

	const _hightlightlayername = `${_Id}-pbf-hightlight-layer`

	const pbfUrl = `${sysConfig.API_URL}/al/land/vectorTile/polygon/{z}/{x}/{y}.pbf`
	const symoblPbfUrl = `${sysConfig.API_URL}/al/land/vectorTile/centerPoint/{z}/{x}/{y}.pbf`

	onUnmounted(() => {
		uninstall()
	})

	// 地图卸载
	const uninstall = () => {
		const source = map.value.getSource(_sourcename)
		const symbolSource = map.value.getSource(_symbolSourcename)
		const layer = map.value.getLayer(_layername)
		const symbolLayer = map.value.getLayer(_symbolLayerName)
		const linelayer = map.value.getLayer(_lineLayerName)

		const hightlightLayer = map.value.getLayer(`${_hightlightlayername}-line`)

		if (hightlightLayer) {
			// map.value.removeLayer(`${_hightlightlayername}-fill`)
			map.value.removeLayer(`${_hightlightlayername}-line`)
		}

		if (symbolLayer) {
			map.value.removeLayer(_symbolLayerName)
		}

		if (linelayer) {
			map.value.removeLayer(_lineLayerName)
		}

		if (layer) {
			map.value.removeLayer(_layername)
		}

		if (source) {
			map.value.removeSource(_sourcename)
		}

		if (symbolSource) {
			map.value.removeSource(_symbolSourcename)
		}
	}

	// pbf图层
	const pbfLayer = () => {
		const options =  {
			id: _layername,
			type: props.type,
			source: _sourcename,
			'source-layer': props.sourceLayer,
			paint: { 'fill-color': '#006bbc', 'fill-opacity': 0.3 },
		}

		const lineOptions = {
			id: _lineLayerName,
			type: 'line',
			source: _sourcename,
			'source-layer': props.sourceLayer,
			paint: { 'line-color': '#58a1b3', 'line-width': 2 }
		}

		if (props.filter) {
			options.filter = props.filter
			lineOptions.filter = props.filter
		}

		const baseLayer = map.value.getLayer("base-raster-map-layer")
		if (baseLayer) {
			map.value.addLayer({
			...options,
		}, "base-raster-map-layer")
		} else {
			map.value.addLayer(options)
		}

		
		
		map.value.addLayer(lineOptions)

		if (props.selectedId) {
			addHighLightLayer()
		}
	}

	const addSymbolLayer = (name) => {
		// 如果name是数组，处理多个字段的展示
		let textField = ''
		let layerId = _symbolLayerName

		if (Array.isArray(name)) {
			// 构建文本字段，用|分隔，area字段加上亩单位
			const textParts = name.map((field) => {
				return field === 'area' ? `{${field}}亩` : `{${field}}`
			})
			textField = textParts.join('|')
		} else {
			// 兼容原有的单个字段处理
			textField = name === 'area' ? `{${name}}亩` : `{${name}}`
		}

		map.value.addLayer({
			id: layerId,
			type: 'symbol',
			source: _symbolSourcename,
			'source-layer': props.sourceLayer,
			minzoom: 10,
			layout: {
				'text-field': textField,
				'text-size': 14
			},
			paint: {
				'text-color': '#fff',
				'text-halo-color': '#000',
				'text-halo-width': 1
			}
		})
	}

	const addHighLightLayer = () => {
		// map.value.addLayer({
		// 	id: `${_hightlightlayername}-fill`,
		// 	type: 'fill',
		// 	source: _sourcename,
		// 	'source-layer': props.sourceLayer,
		// 	paint: {
		// 		'fill-color': '#fff',
		// 		'fill-opacity': 0.000004
		// 	},
		// 	filter: ['==', ['get', 'id'], props.selectedId]
		// })

		map.value.addLayer({
			id: `${_hightlightlayername}-line`,
			type: 'line',
			source: _sourcename,
			'source-layer': props.sourceLayer,
			paint: {
				'line-color': '#fadb14',
				'line-width': 1,
				'line-opacity': 1
			},
			filter: ['==', ['get', 'id'], props.selectedId]
		})
	}

	const addSource = () => {
		map.value.addSource(_sourcename, {
			type: 'vector',
			tiles: [pbfUrl + `?token=${token}&farmlandId=${props.farmId}`]
		})
		map.value.addSource(_symbolSourcename, {
			type: 'vector',
			tiles: [symoblPbfUrl + `?token=${token}&farmlandId=${props.farmId}`]
		})
		pbfLayer()
		addSymbolLayer(props.showName)
	}

	const init = () => {
		setTimeout(() => {
			const loaded = map.value._loaded
			if (loaded) {
				addSource()
			} else {
				map.value.on('load', () => {
					addSource()
				})
			}
		}, 4)
	}
	watch(
		() => map?.value,
		(newValue) => {
			if (map?.value) {
				init()
			}
		},
		{ immediate: true }
	)
	watch(
		() => [props.filter],
		(newValue, oldValue) => {
			if (newValue !== oldValue && newValue !== null) {
				uninstall()
				init()
			}
		}
	)

	// 监听showName变化
	watch(
		() => props.showName,
		(newShowName) => {
			const showNames = ['name', 'area', 'crop_type']
			console.log('newShowName', newShowName)
			map.value.removeLayer(_symbolLayerName)

			addSymbolLayer(newShowName)
		}
	)

	watch(
		() => props.selectedId,
		(newValue) => {
			if (newValue === '') {
				map.value.removeLayer(`${_hightlightlayername}-line`)
				return
			}
			if (map.value.getLayer(`${_hightlightlayername}-line`)) {
				// map.value.setFilter(`${_hightlightlayername}-fill`, ['==', ['get', 'id'], newValue])
				map.value.setFilter(`${_hightlightlayername}-line`, ['==', ['get', 'id'], newValue])
			} else {
				addHighLightLayer()
			}
		}
	)
	watch(
		() => props.farmId,
		(newValue, oldValue) => {
			if (newValue !== oldValue) {
				uninstall()
				init()
			}
		}
	)
</script>
