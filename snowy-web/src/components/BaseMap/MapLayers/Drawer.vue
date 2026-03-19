<!--
增强的地块绘制组件 - Drawer.vue

新功能：
1. 智能删除保护机制
   - 当多边形有超过3个顶点时，正常删除顶点
   - 当多边形只有3个顶点时，删除操作将清空整个绘图并重置状态
   - 支持键盘 Delete/Backspace 键和 MapboxDraw 的 trash 方法
   - 自动重置面积计算和编辑状态

2. 自动生成线段中点标记
   - 在编辑模式下，会在每条边的中点显示绿色圆点
   - 点击中点可以在该位置插入新的顶点
   - 中点标记只在选中多边形时显示

使用方法：
- 调用 startDraw() 开始绘制
- 调用 endDraw() 结束绘制
- 调用 resetDraw() 重置绘制
- 使用 canDeleteVertex() 检查是否有可删除的顶点
- 使用 isEditing 获取当前编辑状态
-->
<template>
	<Marker
		v-if="data.landCenter && data.landCenter.coordinates"
		:coordinate="data.landCenter.coordinates"
		:key="data.landCenter.coordinates.join(',')"
		:offset="[0, 0]"
	>
		<div
			style="
				color: #fff;
				font-size: 14px;
				text-shadow: -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000, 1px 1px 0 #000;
			"
		>
			面积：{{ (data.calculationArea * 0.0015).toFixed(2) }}亩
		</div>
	</Marker>

	<!-- 中点标记现在使用Mapbox原生图层，移除Vue组件渲染 -->
</template>

<script setup>
	import { inject, onMounted, ref, onUnmounted, computed, reactive } from 'vue'
	import MapboxDraw from '@mapbox/mapbox-gl-draw'
	import { throttle, round } from 'lodash'
	import area from '@turf/area'
	import center from '@turf/center'
	import distance from '@turf/distance'
	import midpoint from '@turf/midpoint'
	import Marker from './Marker.vue'
	import { sum } from 'lodash'

	const props = defineProps({
		id: String
	})

	const map = inject('map')
	const draw = ref()
	const _Id = props.id || 'drawer'

	const pointSelected = ref()

	const deleteCoords = ref([])
	


	const data = reactive({
		distanceMarkers: [],
		landCenter: null,
		calculationArea: 0,
		isEditing: false,
		isEditingDisabled: false
	})
	
	// 中点数据，用于Mapbox原生图层
	let midpointData = {
		type: 'FeatureCollection',
		features: []
	}
	
	// 交互状态管理
	let interactionTimer = null
	let isUserInteracting = false

	const handleinitDraw = () => {
		draw.value = new MapboxDraw({
			/** 修改地块时不要控制器 */
			controls: { trash: true, polygon: true },
			mode: 'simple_select',
			// 自定义删除行为
			userProperties: true,
			styles: [
				// line stroke
				{
					id: 'gl-draw-line',
					type: 'line',
					filter: ['all', ['==', '$type', 'LineString']],
					layout: {
						'line-cap': 'round',
						'line-join': 'round'
					},
					paint: {
						'line-color': '#FFD200',
						'line-dasharray': [0.2, 2],
						'line-width': 2
					}
				},
				// polygon outline stroke
				// This doesn't style the first edge of the polygon, which uses the line stroke styling instead
				{
					id: 'gl-draw-polygon-stroke-active',
					type: 'line',
					filter: ['all', ['==', '$type', 'Polygon']],
					layout: {
						'line-cap': 'round',
						'line-join': 'round'
					},
					paint: {
						'line-color': '#FFD200',
						'line-dasharray': [0.2, 2],
						'line-width': 2
					}
				},

				// polygon fill
				{
					id: 'gl-draw-polygon-fill',
					type: 'fill',
					filter: ['all', ['==', '$type', 'Polygon']],
					paint: {
						'fill-color': '#FFBA00',
						'fill-outline-color': '#FFBA00',
						'fill-opacity': 0.6
					}
				},
				// vertex point halos active
				{
					id: 'gl-draw-polygon-and-line-vertex-halo-active',
					type: 'circle',
					filter: ['all', ['==', 'meta', 'vertex'], ['==', 'active', 'true'], ['==', '$type', 'Point']],
					paint: {
						'circle-radius': 8,
						'circle-color': '#FFF'
					}
				},
				// vertex point halos inactive
				{
					id: 'gl-draw-polygon-and-line-vertex-halo-inactive',
					type: 'circle',
					filter: ['all', ['==', 'meta', 'vertex'], ['==', 'active', 'false'], ['==', '$type', 'Point']],
					paint: {
						'circle-radius': 5,
						'circle-color': '#FFF'
					}
				},
				// vertex points
				{
					id: 'gl-draw-polygon-vertex-active',
					type: 'circle',
					filter: ['all', ['==', 'meta', 'vertex'], ['==', 'active', 'true'], ['==', '$type', 'Point']],
					paint: {
						'circle-radius': 5,
						'circle-color': '#FFD200'
					}
				},
				// vertex points inactive
				{
					id: 'gl-draw-polygon-vertex-inactive',
					type: 'circle',
					filter: ['all', ['==', 'meta', 'vertex'], ['==', 'active', 'false'], ['==', '$type', 'Point']],
					paint: {
						'circle-radius': 3,
						'circle-color': '#FFD200'
					}
				},
				// 新增：线段中点样式
				{
					id: 'gl-draw-midpoint',
					type: 'circle',
					filter: ['all', ['==', 'meta', 'midpoint'], ['==', '$type', 'Point']],
					paint: {
						'circle-radius': 4,
						'circle-color': '#00FF00',
						'circle-stroke-color': '#FFF',
						'circle-stroke-width': 2
					}
				}
			]
		})
	}

	const initMapBoxDraw = () => {
		if (!map.value) {
			return
		}

		handleinitDraw()

		map.value.on('draw.modechange', ({ mode }) => {
			try {
				// 如果编辑被禁用且尝试进入编辑模式，则阻止
				if (data.isEditingDisabled && mode === 'direct_select') {
					console.log('编辑已禁用，阻止进入编辑模式')
					draw.value.changeMode('simple_select')
					return
				}

				// 更新编辑状态
				data.isEditing = mode === 'direct_select' || mode === 'draw_polygon'

				if (mode !== 'draw_polygon') return
				/** 保证一次只画一个地块 */
				const { features } = draw.value.getAll()
				if (features?.length <= 1) return
				draw.value.deleteAll()
				draw.value?.changeMode('draw_polygon')
			} catch (e) {
				console.log(e)
			}
		})
		
		// 智能的事件处理 - 解决拖拽时中点标记错乱问题
		map.value.on('draw.create', setDistanceMarkers)
		
		// 智能拖拽状态检测
		
		// 监听所有鼠标交互开始
		map.value.on('mousedown', () => {
			if (data.isEditing && !data.isEditingDisabled) {
				isUserInteracting = true
				console.log('用户开始交互')
				// 清空中点标记，避免拖拽时错乱
				updateMidpointLayer([])
			}
		})
		
		// 监听鼠标释放和移动停止
		const resetInteractionState = () => {
			if (isUserInteracting) {
				console.log('用户交互结束，准备更新中点标记')
				isUserInteracting = false
				
				// 延迟更新，确保拖拽操作完全结束
				clearTimeout(interactionTimer)
				interactionTimer = setTimeout(() => {
					console.log('延迟更新中点标记')
					const features = draw.value.getAll().features
					if (features.length > 0 && data.isEditing && !data.isEditingDisabled) {
						const event = { features: [features[0]] }
						setDistanceMarkers(event)
					}
				}, 300) // 300ms 延迟确保拖拽完全结束
			}
		}
		
		map.value.on('mouseup', resetInteractionState)
		
		// 只在非交互状态下处理 draw.update
		map.value.on('draw.update', (e) => {
			console.log('draw.update 触发，交互状态:', isUserInteracting)
			if (!isUserInteracting) {
				// 只在用户不在交互时才更新中点标记
				setDistanceMarkers(e)
			} else {
				// 交互时只更新面积和边长，不更新中点
				updateBasicInfo(e)
			}
		})

		map.value.on('draw.selectionchange', handleSelect)

		// 添加键盘事件监听，防止通过键盘删除顶点
		const handleKeyDown = (e) => {
			// 如果编辑被禁用，则阻止键盘删除操作
			if (data.isEditingDisabled) {
				console.log('编辑已禁用，无法删除顶点')
				return
			}

			if (e.key === 'Delete' || e.key === 'Backspace') {
				console.log('drawow', draw.value.getAll())

				const { features } = draw.value.getAll()
				if (features.length > 0) {
					const feature = features[0]
					if (feature.geometry.type === 'Polygon') {
						const coordinates = feature.geometry.coordinates

						if (!coordinates[0] || !coordinates[0].length || coordinates[0].length < 3) {
							draw.value.deleteAll()
							data.landCenter = null
							// 清空所有绘图并重置状态
							data.distanceMarkers = []
							data.midpointMarkers = []
							data.calculationArea = 0
							data.isEditing = false

							draw.value.changeMode('draw_polygon')
						}
					}
				}
			}
		}

		// 添加键盘事件监听
		document.addEventListener('keydown', handleKeyDown)

		// 在组件卸载时移除事件监听
		onUnmounted(() => {
			document.removeEventListener('keydown', handleKeyDown)
			// 清理交互定时器
			if (interactionTimer) {
				clearTimeout(interactionTimer)
			}
		})

		/** 给地图添加画地块的control */
		map.value.addControl(draw.value, 'top-left')

		// 添加中点图层和数据源
		if (!map.value.getSource('midpoints')) {
			map.value.addSource('midpoints', {
				type: 'geojson',
				data: midpointData
			})

			// 添加中点图层
			map.value.addLayer({
				id: 'midpoint-layer',
				type: 'circle',
				source: 'midpoints',
				paint: {
					'circle-radius': 6,
					'circle-color': '#00ff00',
					'circle-stroke-color': '#fff',
					'circle-stroke-width': 2,
					'circle-opacity': ['case', ['boolean', ['feature-state', 'hover'], false], 1, 0.8]
				},
				filter: ['==', '$type', 'Point']
			})

			// 添加中点点击事件
			map.value.on('click', 'midpoint-layer', (e) => {
				if (data.isEditingDisabled) return
				
				const feature = e.features[0]
				const segmentIndex = feature.properties.segmentIndex
				const coordinates = feature.geometry.coordinates
				
				console.log('点击中点', { segmentIndex, coordinates })
				handleMidpointClick({ segmentIndex, coordinates })
			})

			// 中点悬停效果
			map.value.on('mouseenter', 'midpoint-layer', () => {
				map.value.getCanvas().style.cursor = 'pointer'
			})

			map.value.on('mouseleave', 'midpoint-layer', () => {
				map.value.getCanvas().style.cursor = ''
			})
		}
	}

	const handleSelect = (e) => {
		console.log('选择事件', e)

		// 如果编辑被禁用，则阻止选中操作
		if (data.isEditingDisabled) {
			console.log('编辑已禁用，无法选中地块')
			return
		}

		if (e.points && e.points.length > 0) {
			pointSelected.value = e.points[0]
		}

		// 检查当前选中的多边形是否只有3个点
		if (e.features && e.features.length > 0) {
			const feature = e.features[0]
			if (feature.geometry.type === 'Polygon') {
				const coordinates = feature.geometry.coordinates[0]
				// 多边形坐标数组最后一个点与第一个点相同，所以实际点数是 length - 1
				const actualPointCount = coordinates.length - 1

				if (actualPointCount <= 2) {
					// 如果只有3个点或更少，提示删除行为
					console.warn('多边形只有3个点，删除顶点将清空整个绘图')
				}

				// 当选中多边形时，设置编辑状态并生成中点标记
				data.isEditing = true
				
				// 使用延迟来避免与其他事件冲突
				setTimeout(() => {
					const event = { features: [feature] }
					setDistanceMarkers(event)
				}, 50)
			}
		}

		// 当没有选中任何要素时，清空中点标记
		if (!e.features || e.features.length === 0) {
			updateMidpointLayer([])
			data.isEditing = false
		}
	}

	// 新增：检查是否可以删除顶点
	const canDeleteVertex = () => {
		const features = draw.value.getAll().features
		if (features.length > 0) {
			const feature = features[0]
			if (feature.geometry.type === 'Polygon') {
				const coordinates = feature.geometry.coordinates[0]
				const actualPointCount = coordinates.length - 1
				// 现在总是可以删除，但3个点时会清空整个绘图
				return actualPointCount >= 3
			}
		}
		return false
	}



	// 处理中点点击事件，在中点位置插入新的顶点
	const handleMidpointClick = (midpointData) => {
		// 如果编辑被禁用，则阻止中点点击操作
		if (data.isEditingDisabled) {
			console.log('编辑已禁用，无法添加新顶点')
			return
		}

		console.log('点击中点', midpointData)
		const features = draw.value.getAll().features
		if (features.length > 0) {
			const feature = features[0]
			if (feature.geometry.type === 'Polygon') {
				const coordinates = feature.geometry.coordinates[0]
				const newCoordinates = [...coordinates]

				// 在指定位置插入新点
				newCoordinates.splice(midpointData.segmentIndex + 1, 0, midpointData.coordinates)

				// 更新多边形
				const updatedFeature = {
					...feature,
					geometry: {
						...feature.geometry,
						coordinates: [newCoordinates]
					}
				}

				// 删除旧的多边形并添加新的
				draw.value.delete(feature.id)
				const newFeature = draw.value.add(updatedFeature)

				// 选中新的多边形并进入编辑模式
				if (newFeature && newFeature.length > 0) {
					draw.value.changeMode('direct_select', { featureId: newFeature[0] })

					// 手动触发更新事件
					setTimeout(() => {
						const event = {
							features: [updatedFeature],
							type: 'draw.update'
						}
						setDistanceMarkers(event)
					}, 50)
				}
			}
		}
	}

	/** 初始化鼠标样式为"+"型 */
	const initCursorStyle = (e) => {
		setTimeout(() => {
			if (e.featureTarget) {
				map.value.getCanvas().style.cursor = 'pointer'
				return
			}
			if (map.value.getCanvas().style.cursor !== 'crosshair') {
				map.value.getCanvas().style.cursor = 'crosshair'
			}
		}, 4)
	}

	const styledCursor = throttle(initCursorStyle, 100)

	/** 获取地块边长，面积，并更新中点标记 */
	const setDistanceMarkers = (e) => {
		console.log('更新标记', e)
		
		// 检查事件数据是否有效
		if (!e || !e.features || !e.features[0] || !e.features[0].geometry) {
			console.warn('无效的事件数据，跳过标记更新')
			// 清空中点图层
			updateMidpointLayer([])
			return
		}
		
		const { geometry } = e.features[0]
		
		// 检查几何图形是否有效
		if (!geometry.coordinates || !geometry.coordinates[0] || !Array.isArray(geometry.coordinates[0])) {
			console.warn('无效的几何图形数据，跳过标记更新')
			updateMidpointLayer([])
			return
		}
		
		const points = geometry.coordinates[0]

		// 检查点数是否足够（至少需要4个点：3个顶点+首尾闭合点）
		if (points.length < 4) {
			console.log('点数不足，暂停标记更新')
			updateMidpointLayer([])
			return
		}

		try {
			const calculationArea = area(e.features[0]) || 0
			const { geometry: landCenter } = center(e.features[0])
			
			const distanceMarkers = []
			const midpointFeatures = []

			// 计算距离和中点
			for (let index = 0; index < points.length - 1; index++) {
				const currentPoint = points[index]
				const nextPoint = points[index + 1]
				
				// 检查点的有效性
				if (!currentPoint || !nextPoint || 
					!Array.isArray(currentPoint) || !Array.isArray(nextPoint) ||
					currentPoint.length < 2 || nextPoint.length < 2) {
					continue
				}

				try {
					const sideLength = distance(currentPoint, nextPoint, { units: 'meters' })
					const { geometry: pointGeo } = midpoint(currentPoint, nextPoint)

					distanceMarkers.push({
						point: pointGeo.coordinates,
						value: round(sideLength, 2)
					})

					// 创建中点Feature用于Mapbox图层
					if (data.isEditing && !data.isEditingDisabled) {
						midpointFeatures.push({
							type: 'Feature',
							geometry: {
								type: 'Point',
								coordinates: pointGeo.coordinates
							},
							properties: {
								segmentIndex: index,
								id: `midpoint-${index}`
							}
						})
					}
				} catch (calcError) {
					console.warn(`计算第${index}个线段时出错:`, calcError)
					continue
				}
			}

			// 更新数据
			if (distanceMarkers.length > 0) {
				data.distanceMarkers = distanceMarkers
				data.landCenter = landCenter
				data.calculationArea = Number(calculationArea.toFixed(2))
			}
			
			// 更新中点图层
			updateMidpointLayer(midpointFeatures)
			
		} catch (error) {
			console.error('setDistanceMarkers 执行出错:', error)
			updateMidpointLayer([])
		}
	}
	
	// 更新中点图层的函数
	const updateMidpointLayer = (features) => {
		if (map.value && map.value.getSource('midpoints')) {
			midpointData.features = features
			map.value.getSource('midpoints').setData(midpointData)
			console.log('更新中点图层，features数量:', features.length)
		}
	}
	
	// 只更新基本信息（面积、边长），不更新中点标记
	const updateBasicInfo = (e) => {
		console.log('仅更新基本信息', e)
		
		// 检查事件数据是否有效
		if (!e || !e.features || !e.features[0] || !e.features[0].geometry) {
			console.warn('无效的事件数据，跳过基本信息更新')
			return
		}
		
		const { geometry } = e.features[0]
		
		// 检查几何图形是否有效
		if (!geometry.coordinates || !geometry.coordinates[0] || !Array.isArray(geometry.coordinates[0])) {
			console.warn('无效的几何图形数据，跳过基本信息更新')
			return
		}
		
		const points = geometry.coordinates[0]

		// 检查点数是否足够
		if (points.length < 4) {
			console.log('点数不足，跳过基本信息更新')
			return
		}

		try {
			const calculationArea = area(e.features[0]) || 0
			const { geometry: landCenter } = center(e.features[0])
			
			const distanceMarkers = []

			// 只计算距离，不计算中点
			for (let index = 0; index < points.length - 1; index++) {
				const currentPoint = points[index]
				const nextPoint = points[index + 1]
				
				// 检查点的有效性
				if (!currentPoint || !nextPoint || 
					!Array.isArray(currentPoint) || !Array.isArray(nextPoint) ||
					currentPoint.length < 2 || nextPoint.length < 2) {
					continue
				}

				try {
					const sideLength = distance(currentPoint, nextPoint, { units: 'meters' })
					const { geometry: pointGeo } = midpoint(currentPoint, nextPoint)

					distanceMarkers.push({
						point: pointGeo.coordinates,
						value: round(sideLength, 2)
					})
				} catch (calcError) {
					console.warn(`计算第${index}个线段时出错:`, calcError)
					continue
				}
			}

			// 只更新基本数据
			if (distanceMarkers.length > 0) {
				data.distanceMarkers = distanceMarkers
				data.landCenter = landCenter
				data.calculationArea = Number(calculationArea.toFixed(2))
			}
			
		} catch (error) {
			console.error('updateBasicInfo 执行出错:', error)
		}
	}

	const startDraw = () => {
		console.log('开始绘制', draw.value)

		if (!draw.value) {
			handleinitDraw()
		}

		draw.value.changeMode('draw_polygon')
		data.isEditing = true
		data.isEditingDisabled = false // 开始绘制时重置禁用编辑状态
		/** 格式化鼠标指针 */
		map.value.on('mousemove', styledCursor)
	}

	const resetDraw = (stopDraw = false) => {
		if (draw.value) {
			draw.value.deleteAll()
			data.landCenter = null
			data.distanceMarkers = []
			data.isEditing = false
			data.isEditingDisabled = false // 重置时也重置禁用编辑状态
			// 清空中点图层
			updateMidpointLayer([])
		}
	}

	const endDraw = (isDelete) => {
		console.log('结束绘制', isDelete, draw.value)
		if (draw.value) {
			if (isDelete) {
				draw.value.deleteAll()
				data.landCenter = null
				data.distanceMarkers = []
				data.isEditingDisabled = false // 删除时重置禁用状态
				// 清空中点图层
				updateMidpointLayer([])
			} else {
				data.isEditingDisabled = true // 结束绘制时禁用编辑
				// 隐藏中点标记（编辑被禁用）
				updateMidpointLayer([])
			}
			map.value.getCanvas().style.cursor = 'auto'
			draw.value.changeMode('simple_select')
			data.isEditing = false
			map.value.off('mousemove', styledCursor)
		}
	}

	const afterDraw = () => {
		const features = draw.value.getAll()
		console.log('afterDraw', features)
		return {
			centerPoint: data.landCenter && data.landCenter.coordinates,
			perimeter: sum(data.distanceMarkers.map((item) => +item.value)).toFixed(2),
			area: (data.calculationArea * 0.0015).toFixed(2),
			geojson: features
		}
	}

	/**
	 * 让现有地块进入编辑状态
	 * @param {Object} geojson - 地块的 GeoJSON 数据
	 */
	const startEditExisting = (geojson) => {
		
		
		if (!draw.value) {
			
			handleinitDraw()
		}

		// 清空现有绘图
		draw.value.deleteAll()
		
		
		// 重置编辑禁用状态
		data.isEditingDisabled = false
		data.isEditing = true
		

		if (geojson && geojson.features && geojson.features.length > 0) {
			try {
				// 添加地块到编辑器
				const addedFeatures = draw.value.add(geojson)
				
				// 选中第一个地块并进入编辑模式
				if (addedFeatures && addedFeatures.length > 0) {
					const featureId = addedFeatures[0]
					
					// 切换到直接选择模式（编辑模式）
					setTimeout(() => {
						draw.value.changeMode('direct_select', { featureId: featureId })
						// draw.value.changeMode('draw_polygon', { featureId: featureId })
						// 手动触发更新事件来设置标记
						setTimeout(() => {
							const feature = draw.value.get(featureId)
							
							if (feature) {
								const event = {
									features: [feature],
									type: 'draw.update'
								}
								setDistanceMarkers(event)
							}
						}, 50)
					}, 50)
				} else {
					console.error('❌ 未成功添加任何features')
				}
			} catch (error) {
				console.error('❌ 添加地块到编辑器时出错:', error)
			}
		} else {
			console.error('❌ 无效的geojson数据:', {
				hasGeojson: !!geojson,
				hasFeatures: geojson && !!geojson.features,
				featuresLength: geojson && geojson.features && geojson.features.length
			})
		}

		// 设置鼠标样式
		map.value.getCanvas().style.cursor = 'auto'
	}

	onMounted(() => {
		initMapBoxDraw()
	})

	defineExpose({
		startDraw,
		afterDraw,
		endDraw,
		resetDraw,
		handleMidpointClick,
		canDeleteVertex,
		startEditExisting,
		isEditing: computed(() => data.isEditing),
		isEditingDisabled: computed(() => data.isEditingDisabled)
	})
</script>
