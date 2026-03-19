<template>
	<div class="mapMarker" :id="props.markerId" ref="markerRef">
		<slot></slot>
	</div>
</template>

<script setup>
	import mapboxgl from 'gago-mapbox-gl-cgcs2000'
	import { ref, watch, onUnmounted } from 'vue'

	const props = defineProps({
		coordinate: Array,
		markerId: String,
		offset: Array
	})

	const markerRef = ref()
	const map = inject('map')
	const marker = ref(null)

	onMounted(() => {
		initMarker()
	})

	watch(
		() => props.coordinate,
		(newVal) => {
			if (marker.value && newVal) {
				marker.value.setLngLat(newVal)
			}
		},
		{ deep: true }
	)

	const initMarker = () => {
		const loaded = map.value._loaded
		if (loaded) {
			addMarker()
		} else {
			map.value.on('load', () => {
				addMarker()
			})
		}
	}

	const addMarker = () => {
		marker.value = new mapboxgl.Marker(markerRef.value, { offset: props.offset, scale: props.scale })
			.setLngLat(props.coordinate)
			.addTo(map.value)
	}

	onUnmounted(() => {
		if (marker.value) {
			marker.value.remove()
			marker.value = null
		}
	})
</script>

<style lang="less">
	.mapMarker {
		position: absolute;
		top: 0;
		left: 0;
	}
</style>
