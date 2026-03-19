<script setup>
	import { inject, onMounted, ref, onUnmounted } from 'vue'

	const props = defineProps({
		id: String,
		url: String,
		sourceMinzoom: Number,
		sourceMaxzoom: Number,
		maskSourceId: String,
		before: String,
		stops: Array,
		show: Boolean,
		type: String
	})
	// emit
	const map = inject('map', null)

	const _Id = props.id || 'default-lerc'
	const _sourcename = `${_Id}-lerc-source`
	const _lercname = `${_Id}-lerc-layer`

	// 地图卸载
	onUnmounted(() => {
		const layer = map.value.getLayer(_lercname)
		const source = map.value.getSource(_sourcename)
		if (layer || source) {
			map.value.removeLayer(_lercname)
			map.value.removeSource(_sourcename)
		}
	})
	// lerc长势图层
	const lercLayer = () => {
		console.log('ssssssssss', props.maskSourceId, props.stops)
		map.value.addLayer(
			{
				id: _lercname,
				type: 'lercv2',
				source: _sourcename,
				paint: {
					'raster-opacity': 1,
					'raster-domain-max': props.stops[props.stops.length - 1][0],
					'raster-domain-min': props.stops[0][0],
					'raster-colorscale': {
						type: props.type || 'interval',
						stops: props.stops,
						min: props.stops[0][0],
						max: props.stops[props.stops.length - 1][0]
					},
					'lerc-include': [[props.stops[0][0], props.stops[props.stops.length - 1][0]]],
					'geojson-mask-source': props.maskSourceId ? props.maskSourceId : ''
				}
			},
			props.before
		)
	}
	const addSource = () => {
		map.value.addSource(_sourcename, {
			type: 'lerc_v2',
			tiles: [props.url],
			tileSize: 512,
			maxzoom: props.sourceMaxzoom ? props.sourceMaxzoom : 18,
			minzoom: props.sourceMinzoom ? props.sourceMinzoom : 0
		})
		lercLayer()
	}
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
	watch(
		() => props.url,
		(newValue, oldValue) => {
			if (newValue !== oldValue) {
				const source = map.value.getSource(_sourcename)
				const layer = map.value.getLayer(_lercname)
				if (layer || source) {
					map.value.removeLayer(_lercname)
					map.value.removeSource(_sourcename)
					init()
				} else {
					init()
				}
			}
		}
	)
	onMounted(() => {
		init()
	})
</script>
