<template>
	<div class="adapt-body">
		<div
			:class="[equalProportion ? 'adapt-border-equal' : 'adapt-border-equal-false', 'adapt-border']"
			:style="{ width: `${designWidth}px`, height: designHeight + 'px', transform: `scale(${scale})` }"
		>
			<slot></slot>
		</div>
	</div>
</template>
<script setup name="AdaptBody">
	const props = defineProps({
		/** 开启自适应，宽高有变形的情况 */
		adaptive: { type: Boolean, default: true },
		/** 等比例缩放 */
		equalProportion: { type: Boolean, default: false },
		/** 设计的宽度 */
		designWidth: { type: Number, default: 1920 },
		/** 设计高度 */
		designHeight: { type: Number, default: 1080 }
	})
	const scale = ref('1')
	onMounted(() => {
		onResize()
		window.addEventListener('resize', onResize)
	})
	/** 调整尺寸事件 */
	const onResize = () => {
		const { adaptive, designWidth, designHeight, equalProportion } = props
		const realWidth = window.document.body.clientWidth
		const realHeight = window.document.body.clientHeight
		const getDesignWidth = designWidth
		const getDesignHeight = designHeight
		if (adaptive) {
			scale.value = `${realWidth / getDesignWidth}, ${realHeight / getDesignHeight}`
		} else if (equalProportion) {
			const designSreenRatio = designWidth / designHeight
			const realScreenRatio = realWidth / realHeight
			if (realScreenRatio > designSreenRatio) {
				// 实际画面更宽
				scale.value = String(realHeight / designHeight)
			} else {
				// 实际画面更窄
				scale.value = String(realWidth / designWidth)
			}
		} else {
			scale.value = '1'
		}
	}
</script>
<style lang="less">
	.adapt-body {
		height: 100%;
		width: 100vw;
		overflow: hidden;
		position: relative;
		.adapt-border {
			position: absolute;
			top: 0;
		}
		.adapt-border-equal {
			left: 50%;
			transform-origin: top center;
			transform: translateX(-50%);
		}
		.adapt-border-equal-false {
			left: 0;
			transform-origin: top left;
		}
	}
</style>
