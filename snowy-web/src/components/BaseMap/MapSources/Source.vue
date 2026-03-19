<template>
	<div class="geojsonSource" />
</template>
<script setup>
	const props = defineProps({
		id: String,
		geojson: String,
		clearLayer: Array
	})

	const map = inject('map')
	const _Id = props.id || 'default-geojson'
	const _sourceName = `${_Id}-geojson-source`

	onUnmounted(() => {
		// if (props.clearLayer) {
		// 	props.clearLayer.map((item) => {
		// 		if (map.value.getLayer(`${item}-layer`)) {
		// 			map.value.removeLayer(`${item}-layer`)
		// 		}
		// 	})
		// }
		// // 移除挂载
		// if (map.value && map.value.getSource(_sourceName)) {
		// 	map.value.removeSource(_sourceName)
		// }
	})

	onMounted(() => {
		init()
	})

	watch(
		() => props.geojson,
		() => {
			const source = map.value.getSource(_sourceName)
			source.setData(JSON.parse(JSON.stringify(props.geojson)))
		}
	)

	// 初始化地图
	const init = () => {
		const loaded = map.value._loaded
		if (loaded) {
			addSource()
		} else {
			map.value.on('load', () => {
				addSource()
			})
		}
	}

	const addSource = () => {
		map.value.addSource(_sourceName, {
			type: 'geojson',
			data: props.geojson
		})
	}
</script>
