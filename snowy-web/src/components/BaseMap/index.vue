<template>
	<div class="map  no-scale" :id="props.containerId ? props.containerId : 'map'"><slot v-if="mapLoaded"></slot></div>
</template>

<script setup>
	import mapboxgl from 'gago-mapbox-gl-cgcs2000'
	import sysConfig from '@/config/index'
	const map = ref(null)

	console.log(map.value, 'mappppppp')

	const emit = defineEmits(['getMap'])
	const mapLoaded = ref(false)
	const props = defineProps({
		positionType: {
			// 定位类型是通过zoom定位还是fit定位
			type: String, // 值有zoom和fit
			default: () => {
				return 'fit'
			}
		},
		zoom: {
			type: Number,
			default: () => {
				return 10
			}
		},
		maxZoom: {
			type: Number,
			default: () => {
				return 22
			}
		},
		minZoom: {
			type: Number,
			default: () => {
				return 3
			}
		},
		bbox: {
			//  四至值
			type: Array,
			default: () => {
				return sysConfig.MAPBBOX
			}
		},
		padding: {
			type: Array,
			default: () => {
				return [0, 0, 0, 0]
			}
		},
		projectionVal: {
			// 投影坐标系的值eg EPSG:4326 EPSG:3857 EPSG:4490
			type: String,
			default: () => {
				return 'EPSG:4490'
			}
		},
		center: {
			type: Array,
			default: () => {
				return undefined
			}
		},
		containerId: {
			type: String,
			default: () => {
				return 'map'
			}
		},
		addWindow: {
			type: Boolean,
			default: false
		}
	})
	// 重新启用AdaptBody事件监听（但保持简单）
	const handleAdaptBodyResize = () => {
		// 简单调用resize，不做复杂操作
		setTimeout(() => {
			resizeMap()
		}, 50)
	}
	
	onMounted(() => {
		init()
		window.addEventListener('adaptbody-resize', handleAdaptBodyResize)
	})
	
	onUnmounted(() => {
		// 移除resize事件监听
		window.removeEventListener('adaptbody-resize', handleAdaptBodyResize)
		
		// 清理地图实例
		if (map.value) {
			map.value.remove()
			map.value = null
		}
		if (props.addWindow) {
			window._map = null
		}
	})
	watch(
		() => props.bbox,
		(newValue, oldValue) => {
			if (newValue !== oldValue) {
				map.value.fitBounds(newValue, {
					padding: { top: props.padding[0], bottom: props.padding[1], left: props.padding[2], right: props.padding[3] },
					duration: 500 // 设置动画持续时间为500毫秒，比默认值更快
				})
			}
		}
	)
	watch(
		() => props.center,
		(newValue, oldValue) => {
			console.log(newValue)
			if (newValue !== oldValue && newValue) {
				map.value &&
					map.value.jumpTo({
						center: newValue,
						zoom: props.zoom
					})
			}
		}
	)
	const init = () => {
		console.log(map.value, 'mappppppp')
		
		const defaultOptions = {
			container: props.containerId ? props.containerId : 'map',
			preserveDrawingBuffer: true,
			style: {
				version: 8,
				layers: [],
				sources: {},
				glyphs: '/static/fonts/glyphs/{fontstack}/{range}.pbf'
			},
			bounds: props.bbox,
			// 倾斜角
			pitch: 0,
			maxZoom: 20,
			zoom: props.zoom
		}

		map.value = new mapboxgl.Map({
			...defaultOptions
		})
		
		if (props.addWindow) {
			window._map = map.value
			console.log(window._map, 'mappppppp')
		}
		window._map = map.value
		if (props.center) {
			map.value.jumpTo({
				center: props.center
			})
			provide('map', map)
			map.value.on('load', () => {
				mapLoaded.value = true
			})
			return
		}
		if (props.bbox) {
			map.value.fitBounds(props.bbox, {
				padding: { top: props.padding[0], bottom: props.padding[1], left: props.padding[2], right: props.padding[3] },
				duration: 500 // 设置动画持续时间为500毫秒，比默认值更快
			})
		}
		
		provide('map', map)
		map.value.on('load', () => {
			mapLoaded.value = true
		})
		map.value.resize()
	}
	
	/**
	 * 手动调用地图resize方法
	 * 用于窗口尺寸变化后重新计算地图大小
	 */
	const resizeMap = () => {
		if (map.value) {
			map.value.resize()
		}
	}
	
	// 临时移除容器尺寸监听，防止循环
	// const observeContainerResize = () => {
	// 	...
	// }
	
	/**
	 * 获取地图实例
	 */
	const getMapInstance = () => {
		return map.value
	}
	
	// 暴露方法给外部使用
	defineExpose({
		/** 调整地图大小 */
		resizeMap,
		/** 强制更新地图尺寸（延迟执行） */
		forceResize: () => {
			setTimeout(() => {
				resizeMap()
			}, 100)
			setTimeout(() => {
				resizeMap()
			}, 300)
		},
		/** 获取地图实例 */
		getMapInstance,
		/** 地图是否已加载 */
		isMapLoaded: () => mapLoaded.value
	})
</script>
<style>
	.map {
		height: 100%;
		width: 100%;
		position: relative !important;
	}
	
	
	.mapboxgl-marker {
		display: inline-block;
	}
	
	.mapboxgl-canvas-container {
		position: relative !important;
	}
	
	.mapboxgl-control-container {
		display: none;
	}
</style>
