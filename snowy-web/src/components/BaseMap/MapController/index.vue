<template>
	<div :style="{ right: `${props.right}px` }" class="map-controller">
		<a-popover placement="leftTop">
			<template #content
				><div class="layerToggle">
					<div
						:class="`layerToggleItem img ${props.type === '卫星影像' ? 'active' : ''}`"
						@click="handleChangeType('卫星影像')"
					>
						<div class="layerToggleItemText">卫星影像</div>
					</div>
					<div
						:class="`layerToggleItem vec ${props.type === '电子底图' ? 'active' : ''}`"
						@click="handleChangeType('电子底图')"
					>
						<div class="layerToggleItemText">电子底图</div>
					</div>
				</div></template
			>

			<div class="map-controller-item layerChange"></div>
		</a-popover>
		<div class="map-controller-item zoomer">
			<div class="zoomIn" @click="handleZoomIn"></div>
			<div class="divider" />
			<div class="zoomOut" @click="handleZoomOut"></div>
		</div>
	</div>
</template>

<script setup>
	import { inject } from 'vue'

	const map = inject('map')

	const props = defineProps({
		right: Number,
		type: String
	})

	const emit = defineEmits(['changeType'])

	const handleChangeType = (type) => {
		emit('changeType', type)
	}

	// 处理地图放大
	const handleZoomIn = () => {
		map.value.zoomIn()
	}

	// 处理地图缩小
	const handleZoomOut = () => {
		map.value.zoomOut()
	}
</script>

<style lang="less" scoped>
	.map-controller {
		z-index: 30;
		position: absolute;
		bottom: 16px;
		transition: all 0.3s;
		.map-controller-item {
			background: #ffffff;
			border-radius: 8px;
		}
		.layerChange {
			width: 47px;
			height: 47px;
			display: flex;
			flex-direction: row;
			justify-content: center;
			align-items: center;

			cursor: pointer;

			&::before {
				content: '';
				width: 24px;
				height: 24px;
				background-size: 100% 100%;
				display: block;
				background-image: url('./imgs/layerChangeIcon.png');
			}
		}
		.zoomer {
			margin-top: 18px;
			.zoomIn,
			.zoomOut {
				cursor: pointer;
				width: 100%;
				height: 50px;
				display: flex;
				flex-direction: row;
				justify-content: center;
				align-items: center;

				&::before {
					content: '';
					width: 24px;
					height: 24px;
					background-size: 100% 100%;
					display: block;
				}
			}
			.zoomIn {
				&::before {
					background-image: url('./imgs/zoomInIcon.png');
				}
			}
			.zoomOut {
				&::before {
					background-image: url('./imgs/zoomOutIcon.png');
				}
			}
			.divider {
				margin: 0 12px;
				border-bottom: 1px dashed #aaaaaa;
			}
		}
	}
	.layerToggle {
		.layerToggleItem {
			cursor: pointer;
			width: 91px;
			height: 57px;

			border-radius: 8px;
			background-size: 100% 100%;
			margin: 4px 0;
			overflow: hidden;
			color: #ffffff;
			.layerToggleItemText {
				width: 100%;
				height: 100%;
				display: flex;
				flex-direction: row;
				border: 2px solid transparent;
				justify-content: center;
				align-items: center;
				background-color: rgba(0, 0, 0, 0.1);
				border-radius: 8px;
			}
		}

		.vec {
			.layerToggleItemText {
				background-color: rgba(0, 0, 0, 0.4);
			}
		}
		.active {
			.layerToggleItemText {
				border: 2px solid #ff8000;
			}
		}
		.img {
			background-image: url(./imgs/layer1.png);
		}
		.vec {
			background-image: url(./imgs/layer2.png);
		}
	}
</style>
