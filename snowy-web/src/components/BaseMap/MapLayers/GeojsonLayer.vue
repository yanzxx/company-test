<template>
	<div class="geojsonLayer" />
</template>
<script setup>
	const props = defineProps({
		id: String,
		mapType: String,
		before: String,
		paint: Object,
		layout: Object,
		sourceId: String,
		filter: Array
	})

	const map = inject('map')
	const _Id = props.id || 'default-geojson'
	const _layername = `${_Id}-layer`

	// 监听 layout 变化
	watch(
		() => props.layout,
		(newLayout) => {
			if (map.value && map.value.getLayer(_layername)) {
				// 更新图层的 layout 属性
				for (const key in newLayout) {
					map.value.setLayoutProperty(_layername, key, newLayout[key])
				}
			}
		},
		{ deep: true }
	)

	// 监听 paint 变化
	watch(
		() => props.paint,
		(newPaint) => {
			if (map.value && map.value.getLayer(_layername)) {
				// 更新图层的 paint 属性
				for (const key in newPaint) {
					map.value.setPaintProperty(_layername, key, newPaint[key])
				}
			}
		},
		{ deep: true }
	)

	// 监听 filter 变化
	watch(
		() => props.filter,
		(newFilter) => {
			if (map.value && map.value.getLayer(_layername)) {
				map.value.setFilter(_layername, newFilter)
			}
		},
		{ deep: true }
	)

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
			type: props.mapType || 'fill',
			source: `${props.sourceId}-geojson-source`,
			paint: props.paint || {
				'fill-opacity': 0,
				'fill-color': '#005acd'
			},
			layout: props.layout || {}
		}

		if (props.filter) {
			params.filter = props.filter
		}

		// 填充图层
		map.value.addLayer(params, props.before)
	}
</script>
