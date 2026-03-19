<template>
	<div />
</template>
<script setup>
	import { inject, onMounted, ref, onUnmounted } from 'vue'
	import tool from '@/utils/tool'
	import sysConfig from '@/config/index'
	const props = defineProps({
		eventQueue: Array
	})

	const map = inject('map')

	const emits = defineEmits(['baseClick'])

	const init = () => {
		map.value.on('click', clickCallback)
	}

	const clickCallback = (e) => {
		let trigger = false

		const features = map.value.queryRenderedFeatures(e.point)
		console.log('xxxxxxxx', features)
		for (let i = 0; i < features.length; i++) {
			const featureTrigger = props.eventQueue?.find((item) => item.layerName === features[i].layer.id)
			if (featureTrigger?.callback) {
				try {
					featureTrigger.callback(features[i].properties)
					trigger = true
				} catch (e) {
					console.log(e)
				}
				break
			}
		}

		if (!trigger) {
			emits('baseClick')
		}
	}

	onMounted(() => {
		const loaded = map.value?._loaded
		if (loaded) {
			init()
		} else {
			map.value?.on('load', () => {
				init()
			})
		}
	})

	watch(
		() => map?.value,
		(newValue) => {
			if (map?.value) {
				init()
			}
		},
		{ immediate: true }
	)

	onUnmounted(() => {
		map.value.off('click', clickCallback)
	})
</script>
