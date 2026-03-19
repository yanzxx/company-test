<template>
	<div class="geojsonLayer" />
</template>
<script setup>
	const props = defineProps({
		id: String,
		mapType: String,
		before: String,
		paint: Object,
		features: String,
		sourceId: String,
		layout: Object,
		filter: String
	})

	const map = inject('map')
	const _Id = props.id || 'default-symbol'
	const _layername = `${_Id}-symbol-layer`
	const _sourcename = `${props.sourceId}-geojson-source`

	onUnmounted(() => {
		// 移除挂载
		if (map.value && map.value.getLayer(_layername)) {
			map.value.removeLayer(_layername)
		}
	})

	onMounted(() => {
		init()
	})

	// 初始化地图
	const init = () => {
		const loaded = map.value._loaded
		if (loaded) {
			addLayer()
		} else {
			map.value.on('load', () => {
				addLayer()
			})
		}
	}

	const addLayer = () => {
		const params = {
			id: _layername,
			type: 'symbol',
			source: _sourcename,
			layout: props.layout || {
				'text-field': '{name}',
				'text-max-width': 6
			},
			paint: props.paint || {
				'text-color': '#fff',
				'text-halo-color': '#000',
				'text-halo-width': 1
			}
		}

		if (props.filter) {
			params.filter = props.filter
		}

		map.value.addLayer(params, props.before)
	}
</script>
