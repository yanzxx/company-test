<template></template>
<script setup>
	import { inject, ref, onUnmounted, nextTick } from 'vue'
	import tool from '@/utils/tool'

	const map = inject('map', null)
	// token
	const token = tool.data.get('TOKEN')

	const props = defineProps({
		id: String,
		url: String,
		type: String,
		paint: Object,
		sourceLayer: String,
		filter: Array,
		canClick: Boolean,
		clickLayerId: String
	})

	const _Id = props.id || 'default'
	const _layername = `${_Id}-pbf-layer`
	const _sourcename = `${_Id}-pbf-source`

	onUnmounted(() => {
		uninstall()
	})

	// 地图卸载
	const uninstall = () => {
		const source = map.value.getSource(_sourcename)
		const layer = map.value.getLayer(_layername)

		if (layer) {
			map.value.removeLayer(_layername)
		}

		if (source) {
			map.value.removeSource(_sourcename)
		}
	}

	// pbf图层
	const pbfLayer = () => {
		const options = {
			id: _layername,
			type: props.type,
			source: _sourcename,
			'source-layer': props.sourceLayer,
			paint: { ...props.paint }
		}

		if (props.filter) {
			options.filter = props.filter
		}

		map.value.addLayer(options)
	}

	const addSource = () => {
		map.value.addSource(_sourcename, {
			type: 'vector',
			tiles: [props.url]
		})
		pbfLayer()
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
		() => [props.url, props.filter],
		(newValue, oldValue) => {
			if (newValue !== oldValue) {
				uninstall()
				init()
			}
		}
	)
</script>
