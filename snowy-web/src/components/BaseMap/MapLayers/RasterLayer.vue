<template>
	<div class="baseLayer" />
</template>

<script setup>
	import { inject, onMounted, ref, onUnmounted, watch } from 'vue'

	const props = defineProps({
		id: String,
		before: Array,
		paint: Object,
		url: {
			type: String
		}
	})

	// emit
	const map = inject('map')
	const _Id = props.id || 'base'
	const _layerId = `${_Id}-raster-map-layer`
	const _sourceId = `${_Id}-raster-source`
	const flag = ref(true)
	onUnmounted(() => {
		uninstall()
	})

	// 移除挂载
	const uninstall = () => {
		if (map.value && map.value.getLayer(_layerId)) {
			map.value.removeLayer(_layerId)
			map.value.removeSource(_sourceId)
		}
	}
	watch(
		() => props.url,
		() => {
			if (flag.value) return
			uninstall()
			init()
		}
	)

	// 初始化地图
	const init = () => {
		const loaded = map.value._loaded
		if (loaded) {
			addSource()
			addLayer()
		} else {
			map.value.on('load', () => {
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

		map.value.addSource(_sourceId, {
			type: 'raster',
			tileSize: 256,
			tiles: [props.url]
		})
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
			props.before
		)
	}
</script>
