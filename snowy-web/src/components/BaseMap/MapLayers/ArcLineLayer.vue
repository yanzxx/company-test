<template>
	<div class="geojsonLayer" />
</template>
<script setup>
import { inject, watch, onMounted, onUnmounted, ref } from 'vue'
import { lineString } from '@turf/helpers'
import bezierSpline from '@turf/bezier-spline'

/**
 * ArcLineLayer 组件属性
 * @typedef {Object} ArcLineLayerProps
 * @property {string} id 图层ID
 * @property {string} mapType 图层类型（默认line）
 * @property {string} before 插入到哪个图层之前
 * @property {Object} paint paint属性
 * @property {Object} layout layout属性
 * @property {string} sourceId 数据源ID
 * @property {Array} filter 过滤条件
 * @property {Array<number>} start 起点坐标 [lng, lat]
 * @property {Array<number>} end 终点坐标 [lng, lat]
 * @property {number} curvature 弧线弯曲度，正数越大越弯
 * @property {number} resolution 曲线分辨率，越大越丝滑
 * @property {number} sharpness 曲线锐利度，越小越圆润
 * @property {Function} onClick 点击事件回调，参数为(e, feature)
 */

const props = defineProps({
  id: String,
  mapType: {
    type: String,
    default: 'line'
  },
  before: String,
  paint: Object,
  layout: Object,
  sourceId: String,
  filter: Array,
  start: {
    type: Array,
    required: true
  },
  end: {
    type: Array,
    required: true
  },
  curvature: {
    type: Number,
    default: 0.8// 默认弯曲度
  },
  /**
   * 曲线分辨率，越大越丝滑
   */
  resolution: {
    type: Number,
    default: 1000
  },
  /**
   * 曲线锐利度，越小越圆润
   */
  sharpness: {
    type: Number,
    default: 0.8
  },
  /**
   * 点击事件回调，参数为(e, feature)
   */
  onClick: {
    type: Function
  }
})

const map = inject('map')
const _Id = props.id || 'default-arc-line'
const _layername = `${_Id}-layer`
const _sourceName = `${props.sourceId || _Id}-arc-line-source`

/**
 * 线性插值颜色（支持三段）
 * @param {string[]} colors 颜色数组
 * @param {number} t 归一化进度（0-1）
 * @returns {string} 16进制颜色
 */
function lerpColor(colors, t) {
  // 只支持三段
  if (colors.length !== 3) return colors[0];
  let c1, c2, localT;
  if (t < 0.5) {
    c1 = colors[0];
    c2 = colors[1];
    localT = t / 0.5;
  } else {
    c1 = colors[1];
    c2 = colors[2];
    localT = (t - 0.5) / 0.5;
  }
  // 16进制转rgb
  const hexToRgb = hex => hex.length === 7 ? [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16)) : [0,0,0];
  const rgbToHex = rgb => '#' + rgb.map(x => x.toString(16).padStart(2, '0')).join('');
  const rgb1 = hexToRgb(c1);
  const rgb2 = hexToRgb(c2);
  const rgb = rgb1.map((v,i) => Math.round(v + (rgb2[i] - v) * localT));
  return rgbToHex(rgb);
}

/**
 * 生成两点之间的贝塞尔弧线GeoJSON（多段分段，每段带渐变色）
 * @param {Array<number>} start 起点坐标 [lng, lat]
 * @param {Array<number>} end 终点坐标 [lng, lat]
 * @param {number} curvature 弯曲度，正数越大越弯
 * @param {number} [resolution=500] 曲线分辨率，越大越丝滑
 * @param {number} [sharpness=0.8] 曲线锐利度，越小越圆润
 * @param {number|null} [highlight=null] 高亮线段的id，点击时设置
 * @returns {Object} GeoJSON FeatureCollection
 */
function createArcLineGeoJSON(start, end, curvature = 0.2, resolution = 500, sharpness = 0.8, highlight = null) {
  // 计算中点
  const mid = [
    (start[0] + end[0]) / 2,
    (start[1] + end[1]) / 2
  ]
  // 计算法向量方向（经纬度差交换并取反）
  const dx = end[0] - start[0]
  const dy = end[1] - start[1]
  const norm = Math.sqrt(dx * dx + dy * dy) || 1
  // 法向量
  const nx = -dy / norm
  const ny = dx / norm
  // 控制点：中点基础上，法向量方向偏移
  const control = [
    mid[0] + nx * curvature,
    mid[1] + ny * curvature
  ]
  // 构造三点的LineString
  const line = lineString([start, control, end])
  // 用bezierSpline生成平滑曲线
  const curved = bezierSpline(line, { resolution, sharpness })
  const coords = curved.geometry.coordinates
  // 颜色分三段
  const colorArr = ['#20A8F0', '#A0E775', '#F49125']
  /**
   * 将曲线拆分为多段，每段一个Feature，带color属性
   */
  const features = []
  for (let i = 0; i < coords.length - 1; i++) {
    const t = i / (coords.length - 2) // 归一化进度
    const color = lerpColor(colorArr, t)
    features.push({
      type: 'Feature',
      geometry: {
        type: 'LineString',
        coordinates: [coords[i], coords[i + 1]]
      },
      properties: {
        color,
        id: props.id + '-layer', // 每段唯一id
       // highlight: highlight === i // 是否高亮
      }
    })
  }
  return {
    type: 'FeatureCollection',
    features
  }
}

// 高亮线段id，点击后设置
/**
 * 当前高亮的线段id
 * @type {import('vue').Ref<number|null>}
 */
const highlightId = ref(null)

// 内部保存事件处理函数，便于解绑
let _onLayerClick = null

// 监听 layout、paint、filter 变化，动态更新图层属性
watch(
  () => props.layout,
  (newLayout) => {
    if (map.value && map.value.getLayer(_layername)) {
      for (const key in newLayout) {
        map.value.setLayoutProperty(_layername, key, newLayout[key])
      }
    }
  },
  { deep: true }
)

watch(
  () => props.paint,
  (newPaint) => {
    if (map.value && map.value.getLayer(_layername)) {
      for (const key in newPaint) {
        map.value.setPaintProperty(_layername, key, newPaint[key])
      }
    }
  },
  { deep: true }
)

watch(
  () => props.filter,
  (newFilter) => {
    if (map.value && map.value.getLayer(_layername)) {
      map.value.setFilter(_layername, newFilter)
    }
  },
  { deep: true }
)

// 监听起点终点变化、参数变化或高亮id变化，自动更新source数据
watch(
  [() => props.start, () => props.end, () => props.curvature, () => props.resolution, () => props.sharpness, highlightId],
  ([newStart, newEnd, newCurvature, newResolution, newSharpness, newHighlightId]) => {
    if (map.value && map.value.getSource(_sourceName)) {
      const geojson = createArcLineGeoJSON(newStart, newEnd, newCurvature, newResolution, newSharpness, newHighlightId)
      map.value.getSource(_sourceName).setData(geojson)
    }
  },
  { deep: true }
)

onUnmounted(() => {
  // 卸载时移除图层和数据源
  if (map.value) {
    if (map.value.getLayer(_layername)) {
      map.value.removeLayer(_layername)
    }
    if (map.value.getSource(_sourceName)) {
      map.value.removeSource(_sourceName)
    }
    // 卸载时解绑点击事件
    if (_onLayerClick) {
      map.value.off('click', _layername, _onLayerClick)
      _onLayerClick = null
    }
  }
})

onMounted(() => {
  init()
})

/**
 * 初始化地图，添加source和layer
 */
const init = () => {
  const loaded = map.value._loaded
  if (loaded) {
    addSourceAndLayer()
  } else {
    map.value.on('load', () => {
      addSourceAndLayer()
    })
  }
}

/**
 * 添加GeoJSON数据源和弧线图层
 */
const addSourceAndLayer = () => {
  // 先移除已存在的source和layer，避免重复
  if (map.value.getLayer(_layername)) {
    map.value.removeLayer(_layername)
  }
  if (map.value.getSource(_sourceName)) {
    map.value.removeSource(_sourceName)
  }
  // 生成弧线GeoJSON
  const geojson = createArcLineGeoJSON(props.start, props.end, props.curvature, props.resolution, props.sharpness)
  // 添加GeoJSON数据源
  map.value.addSource(_sourceName, {
    type: 'geojson',
    data: geojson
  })
  // 添加弧线图层
  const params = {
    id: _layername,
    type: 'line',
    source: _sourceName,
    paint: props.paint || {
      'line-width': 6,
      // 使用属性驱动的颜色
      'line-color': ['get', 'color'],
      // 只有高亮的线段透明度为1，其余为0.6
      'line-opacity': 1
    },
    layout: props.layout || {}
  }
  if (props.filter) {
    params.filter = props.filter
  }
  map.value.addLayer(params, props.before)

  // 先解绑，防止重复绑定
  if (_onLayerClick) {
      map.value.off('click', _layername, _onLayerClick)
    }
    /**
     * 图层点击事件处理
     * @param {Object} e 事件对象
     */
    _onLayerClick = function(e) {
      const feature = e.features && e.features[0]
      console.log('feature', feature)
      if (feature && feature.properties) {
        // 设置高亮id，自动刷新数据
        highlightId.value = feature.properties.id
      }
      console.log('highlightId', highlightId.value)
      const layers = map.value.getStyle().layers
      layers.forEach(layer => {
        if (layer.id.indexOf('arc-line-layer') > -1) {
          console.log('layer.id', layer.id)
          if (layer.id === highlightId.value) {
            console.log('layer', layer)
            // 高亮当前图层
            map.value.setPaintProperty(layer.id, 'line-opacity', 1)
          } else {
            // 其他图层设为半透明
            map.value.setPaintProperty(layer.id, 'line-opacity', 0.4)
          }
        }
      })
      // 保留外部回调
      if (props.onClick) {
        props.onClick(e, feature)
      }
    }
    map.value.on('click', _layername, _onLayerClick)
}
</script>
