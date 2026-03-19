<template>
    <AdaptBody>
	<div class="stitch-emergency-page" :class="`level-${responseLevel.key}`">
		<div class="page-backdrop">
			<div class="page-backdrop__map" aria-hidden="true"></div>
			<div class="page-backdrop__gradient"></div>
			<div class="page-backdrop__mesh"></div>
		</div>

		<header class="page-header">
			<div class="header-status">
				<span class="header-status__tag">{{ responseLevel.banner }}</span>
				<span class="header-status__value">暴雨持续时间: {{ alertDuration }}</span>
			</div>

			<div class="header-title scan-line">
				<h1>应急指挥“一张图”</h1>
			</div>

			<div class="header-actions">
				<button class="action-button action-button--ghost" type="button" @click="logDrawerOpen = true">
					<HistoryOutlined />
					<span>查看日志</span>
				</button>
				<button class="action-button" type="button" @click="refreshDashboard">
					<SyncOutlined :spin="loading" />
					<span>刷新态势</span>
				</button>
				<div class="header-icons">
					<div class="header-user-shell" :title="userDisplayName">
						<div class="header-user">
							<a-avatar class="header-avatar" :src="userAvatar">
								{{ userInitial }}
							</a-avatar>
						</div>
						<div class="header-user-popover">
							<div class="emergency-user-menu__profile">
								<div class="emergency-user-menu__profile-main">
									<a-avatar class="header-avatar emergency-user-menu__avatar" :src="userAvatar">
										{{ userInitial }}
									</a-avatar>
									<div class="emergency-user-menu__text">
										<strong>{{ userDisplayName }}</strong>
										<span>{{ userMetaLine }}</span>
									</div>
								</div>
							</div>
							<div class="emergency-user-menu__divider"></div>
							<button class="emergency-user-menu__logout" type="button" @click="handleLogout">
								<ExportOutlined />
								<span>退出登录</span>
							</button>
						</div>
					</div>
				</div>
			</div>
		</header>

		<main class="page-main">
			<aside class="panel-column panel-column--left">
				<section class="panel-card glass-panel">
					<h3 class="panel-title">
						<span class="panel-title__bar"></span>
						当日实时天气
					</h3>
					<div class="weather-card">
						<div class="weather-card__hero">
							<div class="weather-card__icon">STORM</div>
							<div class="weather-card__temp">{{ weatherSnapshot.temperature }}</div>
							<div class="weather-card__label">{{ weatherSnapshot.label }}</div>
						</div>
						<div class="weather-card__stats">
							<div v-for="item in weatherSnapshot.stats" :key="item.label" class="weather-stat">
								<span>{{ item.label }}</span>
								<strong>{{ item.value }}</strong>
							</div>
						</div>
					</div>
				</section>

				<section class="panel-card glass-panel panel-card--fill">
					<div class="panel-head">
						<h3 class="panel-title">
							<span class="panel-title__bar panel-title__bar--danger"></span>
							实时预警列表
						</h3>
						<span class="panel-kicker">{{ warningList.length }} 条</span>
					</div>
					<div class="warning-list custom-scrollbar">
						<div v-for="item in warningList" :key="item.id" class="warning-item" :class="`warning-item--${item.level}`">
							<div class="warning-item__head">
								<span class="warning-item__level">{{ item.label }}</span>
								<span class="warning-item__time">{{ item.time }}</span>
							</div>
							<p>{{ item.text }}</p>
						</div>
						<div v-if="!warningList.length" class="empty-panel">暂无告警信号，系统正在持续监听。</div>
					</div>
				</section>
			</aside>

			<section class="center-stage">
				<div class="map-stage">
					<EmergencyMapStage
						:center-lng="scenarioState.centerLng"
						:center-lat="scenarioState.centerLat"
						:radius-meters="scenarioState.radiusMeters"
						:poi-list="poiList"
						:affected-poi-list="scenarioPoiList"
						:leak-point-list="leakPointList"
						:level-key="responseLevel.key"
						:color-map="poiColorMap"
					/>
				</div>

				<div class="legend-shell glass-panel">
					<div class="legend-block">
						<h4>设施图例</h4>
						<div class="legend-list">
							<div v-for="item in facilityLegend" :key="item.label" class="legend-item">
								<component :is="item.icon" :style="{ color: item.color }" />
								<span>{{ item.label }}</span>
							</div>
						</div>
					</div>
					<div class="legend-block legend-block--depth">
						<h4>积水深度图例</h4>
						<div class="legend-list">
							<div class="legend-item">
								<span class="legend-dot legend-dot--primary"></span>
								<span>严重淹没区 ({{ primaryFloodDepth }})</span>
							</div>
							<div class="legend-item">
								<span class="legend-dot legend-dot--secondary"></span>
								<span>次级积水点 ({{ secondaryFloodDepth }})</span>
							</div>
						</div>
					</div>
				</div>
			</section>

			<aside class="panel-column panel-column--right">
				<section class="panel-card glass-panel">
					<h3 class="panel-title">
						<span class="panel-title__bar panel-title__bar--cyan"></span>
						受灾设施统计
					</h3>
					<div class="facility-list">
						<div v-for="item in facilityStats" :key="item.label" class="facility-item">
							<div class="facility-item__meta">
								<component :is="item.icon" :style="{ color: item.color }" />
								<span>{{ item.label }}</span>
							</div>
							<strong>{{ formatCount(item.count) }}</strong>
						</div>
					</div>
				</section>

				<section class="panel-card glass-panel">
					<h3 class="panel-title">
						<span class="panel-title__bar panel-title__bar--amber"></span>
						救援物资配比
					</h3>
					<div class="resource-card">
						<div class="resource-donut">
							<svg viewBox="0 0 100 100">
								<circle class="resource-donut__track" cx="50" cy="50" r="40"></circle>
								<circle class="resource-donut__segment resource-donut__segment--amber" cx="50" cy="50" r="40"></circle>
								<circle class="resource-donut__segment resource-donut__segment--cyan" cx="50" cy="50" r="40"></circle>
								<circle class="resource-donut__segment resource-donut__segment--green" cx="50" cy="50" r="40"></circle>
							</svg>
							<div class="resource-donut__center">
								<span>总数</span>
								<strong>3700</strong>
							</div>
						</div>
						<div class="resource-list">
							<div v-for="item in resourceItems" :key="item.label" class="resource-item">
								<div class="resource-item__label">
									<span class="resource-item__dot" :style="{ background: item.color }"></span>
									<span>{{ item.label }}</span>
								</div>
								<strong :style="{ color: item.color }">{{ item.value }}</strong>
							</div>
						</div>
					</div>
				</section>

				<section class="panel-card glass-panel panel-card--fill">
					<h3 class="panel-title">
						<span class="panel-title__bar panel-title__bar--green"></span>
						7日气象预警趋势
					</h3>
					<div class="trend-card">
						<div class="trend-days">
							<div v-for="day in forecastDays" :key="day.label" class="trend-day">
								<span>{{ day.label }}</span>
								<component :is="day.icon" />
							</div>
						</div>
						<div class="trend-chart">
							<svg viewBox="0 0 300 100" preserveAspectRatio="none">
								<path d="M0,35 L60,25 L120,40 L180,30 L240,45 L300,35" class="trend-path trend-path--high"></path>
								<path d="M0,75 L60,65 L120,80 L180,70 L240,85 L300,75" class="trend-path trend-path--low"></path>
								<g class="trend-point-group trend-point-group--high">
									<circle v-for="point in highTempPoints" :key="`h-${point.x}`" :cx="point.x" :cy="point.y" r="3"></circle>
								</g>
								<g class="trend-point-group trend-point-group--low">
									<circle v-for="point in lowTempPoints" :key="`l-${point.x}`" :cx="point.x" :cy="point.y" r="3"></circle>
								</g>
								<g class="trend-text trend-text--high">
									<text v-for="point in highTempPoints" :key="`ht-${point.x}`" :x="point.x" :y="point.y - 10">{{ point.label }}</text>
								</g>
								<g class="trend-text trend-text--low">
									<text v-for="point in lowTempPoints" :key="`lt-${point.x}`" :x="point.x" :y="point.y + 16">{{ point.label }}</text>
								</g>
							</svg>
						</div>
						<!-- <div class="trend-metrics">
							<div v-for="item in forecastDays" :key="`${item.label}-metric`" class="trend-metric">
								<p>{{ item.humidity }}</p>
								<span>{{ item.wind }}</span>
							</div>
						</div> -->
					</div>
				</section>
			</aside>
		</main>

		<footer class="page-footer">
			<div class="assistant-brand">
				<div class="assistant-brand__icon">
					<RobotOutlined />
				</div>
				<div>
					<h4>AI 指挥助手</h4>
					<p>协议版本 4.0</p>
				</div>
			</div>

			<div class="assistant-message">
				<div class="assistant-message__bar"></div>
				<p>{{ aiSuggestion }}</p>
			</div>

			<div class="footer-actions">
				<div class="radius-editor">
					<span>受灾半径</span>
					<a-input-number v-model:value="radiusDraft" :min="200" :step="50" :controls="false" />
					<em>m</em>
				</div>
				<button class="footer-button footer-button--primary" type="button" @click="submitRadiusUpdate">
					{{ updating ? '更新中' : '更新半径' }}
				</button>
				<button class="footer-button footer-button--secondary" type="button" @click="acceptSuggestion">
					采纳建议
				</button>
			</div>
		</footer>

		<div class="status-strip">
			<div class="status-chip" :class="`status-chip--${streamMode}`" :title="streamHint">
				<NotificationOutlined />
				<span>{{ streamStatusText }}</span>
			</div>
			<div class="status-chip">
				<ClockCircleOutlined />
				<span>{{ screenTime }}</span>
			</div>
			<div class="status-chip">
				<AlertOutlined />
				<span>圈内设施 {{ scenarioPoiList.length }}</span>
			</div>
		</div>

		<a-drawer v-model:open="logDrawerOpen" title="演练操作日志" placement="right" :width="480">
			<div class="drawer-list">
				<div v-for="item in formattedLogs" :key="item.id" class="drawer-item">
					<div class="drawer-item__head">
						<strong>{{ item.title }}</strong>
						<span>{{ item.time }}</span>
					</div>
					<p>{{ item.detail }}</p>
					<div class="drawer-item__foot">{{ item.operator }}</div>
				</div>
				<div v-if="!formattedLogs.length" class="empty-panel">暂无演练日志。</div>
			</div>
		</a-drawer>
	</div>
</AdaptBody>
</template>

<script setup name="EmergencyHomeStitch">
	import dayjs from 'dayjs'
	import { message, Modal } from 'ant-design-vue'
    import AdaptBody from '@/components/AdaptBody/index.vue'
	import {
		AlertOutlined,
		AimOutlined,
		BankOutlined,
		CarOutlined,
		ClockCircleOutlined,
		ControlOutlined,
		ExclamationCircleOutlined,
		ExportOutlined,
		HistoryOutlined,
		InboxOutlined,
		MedicineBoxOutlined,
		NotificationOutlined,
		RobotOutlined,
		SafetyCertificateOutlined,
		SyncOutlined,
		TeamOutlined,
		ThunderboltOutlined
	} from '@ant-design/icons-vue'
	import { storeToRefs } from 'pinia'
	import { computed, createVNode, onBeforeUnmount, onMounted, ref } from 'vue'
	import sysConfig from '@/config'
	import loginApi from '@/api/auth/loginApi'
	import emergencyDrillApi from '@/api/biz/emergencyDrillApi'
	import router from '@/router'
	import { useGlobalStore } from '@/store'
	import tool from '@/utils/tool'
	import EmergencyMapStage from './components/EmergencyMapStage.vue'

	const DEFAULT_CENTER_LNG = 113.947321
	const DEFAULT_CENTER_LAT = 22.543211
	const DEFAULT_RADIUS_METERS = 1500
	const MAX_SIGNAL_COUNT = 8
	const POLLING_INTERVAL = 10000

	const weatherSnapshot = {
		temperature: '22°C',
		label: '大暴雨',
		stats: [
			{ label: '湿度', value: '95%' },
			{ label: '风速', value: '7级' },
			{ label: '气压', value: '998hPa' }
		]
	}

	const resourceItems = [
		{ label: '医疗包', value: '1850', color: '#ffb77d' },
		{ label: '帐篷', value: '960', color: '#00e4ff' },
		{ label: '救生衣', value: '890', color: '#00e471' }
	]

	const forecastDays = [
		{ label: '今天', icon: AlertOutlined, humidity: '95%', wind: '西南风 3-4级' },
		{ label: '明天', icon: ThunderboltOutlined, humidity: '92%', wind: '西南风 4-5级' },
		{ label: '周二', icon: NotificationOutlined, humidity: '85%', wind: '南风 2-3级' },
		{ label: '周三', icon: AimOutlined, humidity: '78%', wind: '东南风 3级' },
		{ label: '周四', icon: ThunderboltOutlined, humidity: '88%', wind: '东风 5-6级' },
		{ label: '周五', icon: AlertOutlined, humidity: '94%', wind: '东北风 4级' }
	]

	const highTempPoints = [
		{ x: 0, y: 35, label: '31°' },
		{ x: 60, y: 25, label: '33°' },
		{ x: 120, y: 40, label: '30°' },
		{ x: 180, y: 30, label: '32°' },
		{ x: 240, y: 45, label: '29°' },
		{ x: 300, y: 35, label: '31°' }
	]

	const lowTempPoints = [
		{ x: 0, y: 75, label: '24°' },
		{ x: 60, y: 65, label: '25°' },
		{ x: 120, y: 80, label: '22°' },
		{ x: 180, y: 70, label: '23°' },
		{ x: 240, y: 85, label: '21°' },
		{ x: 300, y: 75, label: '22°' }
	]

	const poiMetaMap = {
		医院: { icon: MedicineBoxOutlined, color: '#ff6b6b', glow: '0 0 12px rgba(255, 107, 107, 0.35)' },
		学校: { icon: BankOutlined, color: '#ffd700', glow: '0 0 12px rgba(255, 215, 0, 0.35)' },
		避险点: { icon: SafetyCertificateOutlined, color: '#00e471', glow: '0 0 12px rgba(0, 228, 113, 0.35)' },
		电力设施: { icon: ThunderboltOutlined, color: '#00a3ff', glow: '0 0 12px rgba(0, 163, 255, 0.35)' },
		消防设施: { icon: AlertOutlined, color: '#ff7a8d', glow: '0 0 12px rgba(255, 122, 141, 0.35)' },
		物资仓储: { icon: InboxOutlined, color: '#ffb77d', glow: '0 0 12px rgba(255, 183, 125, 0.35)' },
		社区服务: { icon: TeamOutlined, color: '#8ee7ff', glow: '0 0 12px rgba(142, 231, 255, 0.35)' },
		交通枢纽: { icon: CarOutlined, color: '#93a5ff', glow: '0 0 12px rgba(147, 165, 255, 0.35)' },
		排涝设施: { icon: ControlOutlined, color: '#3ee7ff', glow: '0 0 12px rgba(62, 231, 255, 0.35)' },
		应急指挥: { icon: AimOutlined, color: '#24f0cf', glow: '0 0 12px rgba(36, 240, 207, 0.35)' }
	}

	const globalStore = useGlobalStore()
	const { userInfo } = storeToRefs(globalStore)
	const loading = ref(false)
	const updating = ref(false)
	const logDrawerOpen = ref(false)
	const streamMode = ref('connecting')
	const streamHint = ref('正在连接实时推送')
	const now = ref(dayjs())
	const poiList = ref([])
	const logList = ref([])
	const signalFeed = ref([])
	const radiusDraft = ref(DEFAULT_RADIUS_METERS)
	const scenarioState = ref(createScenarioState())

	let clockTimer = null
	let pollingTimer = null
	let streamInstance = null

	const userDisplayName = computed(() => userInfo.value?.name || userInfo.value?.userName || '应急值守员')
	const userAvatar = computed(() => userInfo.value?.avatar || '')
	const userInitial = computed(() => userDisplayName.value.slice(0, 1) || '守')
	const userMetaLine = computed(() => {
		const metaParts = [userInfo.value?.orgName, userInfo.value?.positionName].filter(Boolean)
		return metaParts.join(' · ') || '当前值守人员'
	})
	const screenTime = computed(() => now.value.format('YYYY.MM.DD HH:mm:ss'))
	const leakPointList = computed(() => (Array.isArray(scenarioState.value.leakPointList) ? scenarioState.value.leakPointList : []))
	const latestSignal = computed(() => signalFeed.value[0] || scenarioState.value.latestSignal || null)
	const streamStatusText = computed(() => {
		if (streamMode.value === 'connected') {
			return 'SSE 已连接'
		}
		if (streamMode.value === 'fallback') {
			return '轮询兜底中'
		}
		return '连接中'
	})
	const alertDuration = computed(() => {
		const oldestLog = logList.value[logList.value.length - 1]
		if (!oldestLog?.createTime) {
			return '4h 25m'
		}
		const totalMinutes = Math.max(dayjs().diff(dayjs(oldestLog.createTime), 'minute'), 1)
		const hours = Math.floor(totalMinutes / 60)
		const minutes = totalMinutes % 60
		return `${hours}h ${minutes}m`
	})

	const scenarioPoiList = computed(() =>
		queryLocalPoiList(scenarioState.value.centerLng, scenarioState.value.centerLat, scenarioState.value.radiusMeters)
	)
	const responseLevel = computed(() => {
		const radiusMeters = Number(scenarioState.value.radiusMeters || 0)
		const affectedCount = scenarioPoiList.value.length
		if (radiusMeters >= 2400 || affectedCount >= 22) {
			return { key: 'critical', banner: '暴雨红色预警激活', label: '红色预警' }
		}
		if (radiusMeters >= 1700 || affectedCount >= 14) {
			return { key: 'warning', banner: '暴雨橙色预警激活', label: '橙色预警' }
		}
		return { key: 'monitor', banner: '暴雨蓝色监测激活', label: '蓝色监测' }
	})
	const poiColorMap = computed(() =>
		Object.keys(poiMetaMap).reduce((result, key) => {
			result[key] = poiMetaMap[key].color
			return result
		}, {})
	)

	const facilityLegend = computed(() => [
		{ label: '医疗机构', icon: MedicineBoxOutlined, color: poiMetaMap.医院.color },
		{ label: '教育机构', icon: BankOutlined, color: poiMetaMap.学校.color },
		{ label: '避险设施', icon: SafetyCertificateOutlined, color: poiMetaMap.避险点.color }
	])

	const facilityStats = computed(() => [
		{ label: '受灾学校', count: countPoiByTypes(['学校']), icon: BankOutlined, color: '#ffd700' },
		{ label: '受灾医院', count: countPoiByTypes(['医院']), icon: MedicineBoxOutlined, color: '#ff6b6b' },
		{ label: '受损变电站', count: countPoiByTypes(['电力设施']), icon: ThunderboltOutlined, color: '#00a3ff' }
	])

	const formattedLogs = computed(() =>
		logList.value.map((item) => ({
			id: item.id,
			title: item.operationName || '系统事件',
			time: formatDateTime(item.createTime),
			detail: item.detail || '暂无详细描述',
			operator: item.operatorName || '系统'
		}))
	)

	const warningList = computed(() => {
		const entries = []
		if (latestSignal.value) {
			entries.push({
				id: latestSignal.value.signalId || 'latest-signal',
				...buildWarningMeta(latestSignal.value.signalName, latestSignal.value.message),
				time: formatTime(latestSignal.value.signalTime),
				text: latestSignal.value.message || latestSignal.value.signalName || '灾情信号更新'
			})
		}
		logList.value.slice(0, 5).forEach((item) => {
			const text = item.detail || item.operationName || '系统事件'
			entries.push({
				id: item.id,
				...buildWarningMeta(item.operationName, text),
				time: formatTime(item.createTime),
				text
			})
		})
		return entries.slice(0, 6)
	})

	const aiSuggestion = computed(() => {
		if (!latestSignal.value) {
			return `AI 建议：态势数据已接入，建议先核对 ${scenarioPoiList.value.length} 个圈内设施并确认主受灾半径。`
		}
		if (responseLevel.value.key === 'critical') {
			return `AI 建议：检测到多个关键设施处于淹没风险中。建议优先疏散医院与学校，并建立 3 条水上救援通道。`
		}
		if (latestSignal.value.signalType === 'NEW_LEAK_POINT') {
			return `AI 建议：${latestSignal.value.leakPoint?.name || '新漏水点'} 已出现，请在 500 米范围内部署巡检队与排涝车。`
		}
		return `AI 建议：险情仍在演进，建议继续扩大监测圈并优先保护 ${topFacilityLabel.value}。`
	})

	const topFacilityLabel = computed(
		() => facilityStats.value.slice().sort((left, right) => right.count - left.count)[0]?.label || '关键设施'
	)
	const primaryFloodDepth = computed(() => `${Math.max(1.8, Math.min(4.8, Number(scenarioState.value.radiusMeters || 0) / 470)).toFixed(1)}m`)
	const secondaryFloodDepth = computed(() => `${Math.max(0.9, Math.min(2.4, Number(scenarioState.value.radiusMeters || 0) / 930)).toFixed(1)}m`)

	function createScenarioState() {
		return {
			centerLng: DEFAULT_CENTER_LNG,
			centerLat: DEFAULT_CENTER_LAT,
			radiusMeters: DEFAULT_RADIUS_METERS,
			totalPoiCount: 0,
			poiCountInRange: 0,
			signalCounter: 0,
			latestSignal: null,
			leakPointList: []
		}
	}

	function calcDistanceMeters(centerLng, centerLat, targetLng, targetLat) {
		const earthRadius = 6371000
		const latDistance = ((targetLat - centerLat) * Math.PI) / 180
		const lngDistance = ((targetLng - centerLng) * Math.PI) / 180
		const a =
			Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
			Math.cos((centerLat * Math.PI) / 180) *
				Math.cos((targetLat * Math.PI) / 180) *
				Math.sin(lngDistance / 2) *
				Math.sin(lngDistance / 2)
		const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
		return earthRadius * c
	}

	function queryLocalPoiList(centerLng, centerLat, radiusMeters) {
		if (!Number.isFinite(centerLng) || !Number.isFinite(centerLat) || !Number.isFinite(radiusMeters)) {
			return []
		}
		return poiList.value
			.map((poi) => ({
				...poi,
				distanceMeters: Number(calcDistanceMeters(centerLng, centerLat, poi.lng, poi.lat).toFixed(2))
			}))
			.filter((poi) => poi.distanceMeters <= Number(radiusMeters || 0))
			.sort((left, right) => left.distanceMeters - right.distanceMeters)
	}

	function countPoiByTypes(typeList) {
		return scenarioPoiList.value.filter((item) => typeList.includes(item.type)).length
	}

	function buildWarningMeta(title, text) {
		const content = `${title || ''} ${text || ''}`
		if (/加重|严重|扩大|漏水/.test(content)) {
			return { level: 'red', label: '红色预警' }
		}
		if (/人工|调整|更新|排查/.test(content)) {
			return { level: 'orange', label: '橙色预警' }
		}
		return { level: 'cyan', label: '蓝色通报' }
	}

	function appendSignal(signal) {
		if (!signal || !signal.signalId) {
			return
		}
		if (signalFeed.value.some((item) => item.signalId === signal.signalId)) {
			return
		}
		signalFeed.value = [signal, ...signalFeed.value].slice(0, MAX_SIGNAL_COUNT)
	}

	function applyScenarioState(state, appendLatest = true) {
		if (!state) {
			return
		}
		scenarioState.value = {
			...createScenarioState(),
			...scenarioState.value,
			...state,
			leakPointList: Array.isArray(state.leakPointList) ? state.leakPointList : leakPointList.value
		}
		radiusDraft.value = Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS)
		if (appendLatest && scenarioState.value.latestSignal) {
			appendSignal(scenarioState.value.latestSignal)
		}
	}

	function parseStreamPayload(event) {
		try {
			return typeof event.data === 'string' ? JSON.parse(event.data) : event.data
		} catch (error) {
			return null
		}
	}

	function formatTime(value) {
		return value ? dayjs(value).format('HH:mm:ss') : '--'
	}

	function formatDateTime(value) {
		return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '--'
	}

	function formatCount(value) {
		return `${Number(value || 0).toString().padStart(2, '0')}`
	}

	function clearLoginState() {
		tool.data.remove('TOKEN')
		tool.data.remove('USER_INFO')
		tool.data.remove('MENU')
		tool.data.remove('PERMISSIONS')
		globalStore.setUserInfo({})
	}

	function handleLogout() {
		Modal.confirm({
			title: '提示',
			content: '确认退出当前用户？',
			icon: createVNode(ExclamationCircleOutlined),
			maskClosable: false,
			okText: '确定',
			cancelText: '取消',
			async onOk() {
				const token = tool.data.get('TOKEN')
				const closeMessage = message.loading('退出中...', 0)
				try {
					await loginApi.logout({ token })
					clearLoginState()
					await router.replace({ path: '/login' })
				} catch (error) {
					tool.data.clear()
					globalStore.setUserInfo({})
					await router.replace({ path: '/login' })
					location.reload()
				} finally {
					closeMessage()
				}
			}
		})
	}

	async function loadLogList(silent = false) {
		try {
			const data = await emergencyDrillApi.getLogList({ limit: 12 })
			logList.value = Array.isArray(data) ? data : []
		} catch (error) {
			if (!silent) {
				message.error('加载演练日志失败')
			}
		}
	}

	async function loadState(silent = false) {
		try {
			const data = await emergencyDrillApi.getDisasterState()
			applyScenarioState(data)
		} catch (error) {
			if (!silent) {
				message.error('加载灾情态势失败')
			}
		}
	}

	async function refreshDashboard() {
		loading.value = true
		try {
			const [stateData, logData] = await Promise.all([
				emergencyDrillApi.getDisasterState(),
				emergencyDrillApi.getLogList({ limit: 12 })
			])
			applyScenarioState(stateData)
			logList.value = Array.isArray(logData) ? logData : []
		} finally {
			loading.value = false
		}
	}

	async function bootstrap() {
		loading.value = true
		try {
			const [poiData, stateData, logData] = await Promise.all([
				emergencyDrillApi.getMockPoiList(),
				emergencyDrillApi.getDisasterState(),
				emergencyDrillApi.getLogList({ limit: 12 })
			])
			poiList.value = Array.isArray(poiData) ? poiData : []
			logList.value = Array.isArray(logData) ? logData : []
			applyScenarioState(stateData)
		} catch (error) {
			message.error('初始化应急大屏失败')
		} finally {
			loading.value = false
		}
	}

	async function submitRadiusUpdate(remark = '指挥席手动更新受灾半径') {
		if (!Number.isFinite(Number(radiusDraft.value)) || Number(radiusDraft.value) <= 0) {
			message.warning('请输入有效的受灾半径')
			return
		}
		updating.value = true
		try {
			const stateData = await emergencyDrillApi.updateRadius({
				radiusMeters: Number(radiusDraft.value),
				remark
			})
			applyScenarioState(stateData)
			if (stateData?.latestSignal) {
				appendSignal(stateData.latestSignal)
			}
			await loadLogList(true)
			message.success('受灾半径已更新')
		} finally {
			updating.value = false
		}
	}

	async function acceptSuggestion() {
		const increment = responseLevel.value.key === 'critical' ? 220 : 120
		radiusDraft.value = Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS) + increment
		await submitRadiusUpdate(`采纳AI建议，扩大监测圈 ${increment} 米`)
	}

	function startClock() {
		stopClock()
		clockTimer = window.setInterval(() => {
			now.value = dayjs()
		}, 1000)
	}

	function stopClock() {
		if (clockTimer) {
			window.clearInterval(clockTimer)
			clockTimer = null
		}
	}

	function stopPolling() {
		if (pollingTimer) {
			window.clearInterval(pollingTimer)
			pollingTimer = null
		}
	}

	function startPolling(reason = '实时流不可用，已切换为 10 秒轮询') {
		stopPolling()
		streamMode.value = 'fallback'
		streamHint.value = reason
		pollingTimer = window.setInterval(async () => {
			await loadState(true)
			await loadLogList(true)
		}, POLLING_INTERVAL)
	}

	function stopStream() {
		if (streamInstance) {
			streamInstance.close()
			streamInstance = null
		}
	}

	function connectStream() {
		const token = tool.data.get('TOKEN')
		if (!window.EventSource || !token) {
			startPolling('浏览器或登录态不支持 SSE，已切换轮询')
			return
		}
		stopStream()
		stopPolling()
		streamMode.value = 'connecting'
		streamHint.value = '正在连接实时推送'
		const streamUrl = `${sysConfig.API_URL}/biz/emergency/drill/disaster/stream?${sysConfig.TOKEN_NAME}=${encodeURIComponent(token)}`
		streamInstance = new EventSource(streamUrl)
		streamInstance.onopen = () => {
			streamMode.value = 'connected'
			streamHint.value = 'SSE 推送稳定在线'
		}
		streamInstance.addEventListener('snapshot', (event) => {
			const data = parseStreamPayload(event)
			if (data) {
				applyScenarioState(data)
			}
		})
		streamInstance.addEventListener('signal', async (event) => {
			const data = parseStreamPayload(event)
			if (!data) {
				return
			}
			appendSignal(data)
			const mergedLeakPointList = data.leakPoint
				? [data.leakPoint, ...leakPointList.value.filter((item) => item.id !== data.leakPoint.id)].slice(0, 20)
				: leakPointList.value
			scenarioState.value = {
				...scenarioState.value,
				centerLng: Number(data.centerLng || scenarioState.value.centerLng),
				centerLat: Number(data.centerLat || scenarioState.value.centerLat),
				radiusMeters: Number(data.radiusMeters || scenarioState.value.radiusMeters),
				poiCountInRange: Number(data.affectedPoiCount || scenarioState.value.poiCountInRange),
				signalCounter: Number(data.signalNo || scenarioState.value.signalCounter),
				latestSignal: data,
				leakPointList: mergedLeakPointList
			}
			radiusDraft.value = Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS)
			await loadLogList(true)
		})
		streamInstance.onerror = () => {
			stopStream()
			startPolling()
		}
	}

	onMounted(async () => {
		startClock()
		await bootstrap()
		connectStream()
	})

	onBeforeUnmount(() => {
		stopClock()
		stopPolling()
		stopStream()
	})
</script>

<style scoped lang="less">
	@import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&family=Space+Grotesk:wght@400;500;700&display=swap');

	.stitch-emergency-page {
		--page-bg: #0a192f;
		--panel-bg: rgba(17, 32, 54, 0.62);
		--panel-border: rgba(255, 255, 255, 0.07);
		--text-main: #d6e3ff;
		--text-muted: rgba(197, 198, 205, 0.78);
		--accent: #00a3ff;
		--accent-soft: #00e4ff;
		--accent-green: #00e471;
		--accent-amber: #ffb77d;
		position: relative;
		margin: -10px;
		width: 1920px;
		min-width: 1920px;
		max-width: 1920px;
		height: 1080px;
		min-height: 1080px;
		max-height: 1080px;
		padding: 0;
		overflow: hidden;
		background: #081629;
		color: var(--text-main);
		font-family: 'Inter', 'Microsoft YaHei', sans-serif;
	}

	.page-backdrop {
		position: absolute;
		inset: 0;
		overflow: hidden;
	}

	.page-backdrop__map {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		opacity: 0.55;
		background:
			radial-gradient(circle at 50% 40%, rgba(0, 142, 255, 0.18), transparent 28%),
			radial-gradient(circle at 24% 30%, rgba(0, 228, 255, 0.1), transparent 18%),
			radial-gradient(circle at 75% 28%, rgba(0, 228, 113, 0.08), transparent 18%);
		filter: contrast(1.15);
	}

	.page-backdrop__gradient {
		position: absolute;
		inset: 0;
		background:
			radial-gradient(circle at 15% 18%, rgba(0, 163, 255, 0.12), transparent 22%),
			radial-gradient(circle at 82% 20%, rgba(0, 228, 113, 0.12), transparent 20%),
			linear-gradient(180deg, rgba(10, 25, 47, 0.88) 0%, rgba(10, 25, 47, 0.22) 45%, rgba(10, 25, 47, 0.9) 100%);
	}

	.page-backdrop__mesh {
		position: absolute;
		inset: 0;
		background-image:
			linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
			linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
		background-size: 84px 84px;
		mask-image: radial-gradient(circle at center, rgba(0, 0, 0, 0.8), transparent 78%);
		opacity: 0.2;
	}

	.page-header,
	.page-footer,
	.status-strip,
	.page-main {
		position: relative;
		z-index: 1;
	}

	.page-header {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		display: flex;
		align-items: center;
		gap: 24px;
		height: 84px;
		padding: 0 32px;
        z-index: 999;
		background: rgba(10, 25, 47, 0.82);
		backdrop-filter: blur(16px);
		border-bottom: 1px solid rgba(255, 255, 255, 0.05);
	}

	.header-status {
		display: flex;
		flex: 1 1 0;
		flex-direction: column;
		gap: 4px;
		min-width: 220px;
	}

	.header-status__tag {
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.28em;
		color: var(--accent);
		text-transform: uppercase;
	}

	.header-status__value {
		font-family: 'Space Grotesk', 'Microsoft YaHei', sans-serif;
		font-size: 18px;
		font-weight: 700;
		color: #ffffff;
	}

	.header-title {
		position: relative;
		flex: 0 0 auto;
		margin: 0px auto;
        margin-top: 10px;
		text-align: center;
	}

	.header-title h1 {
		margin: 0;
		font-family: 'Space Grotesk', 'Microsoft YaHei', sans-serif;
		font-size: 42px;
		font-weight: 700;
		letter-spacing: 0.2em;
		color: var(--accent);
		text-shadow: 0 0 10px rgba(0, 163, 255, 0.8), 0 0 24px rgba(0, 163, 255, 0.28);
	}

	.header-title p {
		margin: 6px 0 0;
		font-size: 11px;
		letter-spacing: 0.22em;
		text-transform: uppercase;
		color: rgba(214, 227, 255, 0.72);
	}

	.header-actions {
		display: flex;
		align-items: center;
		flex: 1 1 0;
		gap: 16px;
		min-width: 360px;
		justify-content: flex-end;
	}

	.action-button {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 10px 16px;
		border: 1px solid rgba(0, 163, 255, 0.38);
		border-radius: 4px;
		background: rgba(0, 163, 255, 0.14);
		color: var(--accent);
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.12em;
		cursor: pointer;
		transition: 0.2s ease;
	}

	.action-button:hover {
		background: rgba(0, 163, 255, 0.22);
	}

	.action-button--ghost {
		background: rgba(255, 255, 255, 0.04);
		border-color: rgba(255, 255, 255, 0.1);
		color: var(--text-main);
	}

	.header-icons {
		display: flex;
		align-items: center;
		gap: 12px;
		padding-left: 16px;
		border-left: 1px solid rgba(255, 255, 255, 0.08);
	}

	.header-user-shell {
		position: relative;
		display: inline-flex;
		align-items: center;
		padding-bottom: 10px;
		margin-bottom: -10px;
	}

	.header-user-shell:hover .header-user-popover {
		opacity: 1;
		visibility: visible;
		transform: translateY(0);
		pointer-events: auto;
	}

	.header-user {
		position: relative;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 46px;
		height: 46px;
		padding: 4px;
		border-radius: 16px;
		border: 1px solid rgba(0, 163, 255, 0.18);
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.08), rgba(255, 255, 255, 0.02));
		box-shadow: 0 10px 24px rgba(0, 0, 0, 0.22), inset 0 1px 0 rgba(255, 255, 255, 0.06);
		cursor: pointer;
		transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
	}

	.header-user::after {
		content: '';
		position: absolute;
		right: 6px;
		bottom: 6px;
		width: 8px;
		height: 8px;
		border-radius: 999px;
		border: 1px solid rgba(8, 22, 41, 0.92);
		background: #00e471;
		box-shadow: 0 0 12px rgba(0, 228, 113, 0.7);
	}

	.header-user:hover {
		transform: translateY(-1px);
		border-color: rgba(0, 228, 255, 0.48);
		box-shadow: 0 14px 28px rgba(0, 0, 0, 0.28), 0 0 18px rgba(0, 163, 255, 0.22);
	}

	.header-user-popover {
		position: absolute;
		top: calc(100% + 2px);
		right: 0;
		z-index: 30;
		opacity: 0;
		visibility: hidden;
		min-width: 232px;
		padding: 10px;
		border-radius: 18px;
		border: 1px solid rgba(0, 163, 255, 0.22);
		background: linear-gradient(180deg, rgba(10, 25, 47, 0.98), rgba(8, 22, 41, 0.94));
		box-shadow: 0 22px 48px rgba(0, 0, 0, 0.3), inset 0 1px 0 rgba(255, 255, 255, 0.05);
		transform: translateY(8px);
		pointer-events: none;
		transition: opacity 0.18s ease, transform 0.18s ease, visibility 0.18s ease;
	}

	.header-user-popover::before {
		content: '';
		position: absolute;
		top: -5px;
		right: 18px;
		width: 10px;
		height: 10px;
		transform: rotate(45deg);
		border-top: 1px solid rgba(0, 163, 255, 0.22);
		border-left: 1px solid rgba(0, 163, 255, 0.22);
		background: rgba(10, 25, 47, 0.98);
	}

	.header-avatar {
		width: 36px;
		height: 36px;
		border-radius: 12px;
		border: 1px solid rgba(0, 163, 255, 0.24);
		background: radial-gradient(circle at 30% 20%, rgba(0, 228, 255, 0.7), rgba(0, 163, 255, 0.18) 55%, rgba(8, 22, 41, 0.98));
		color: #eff8ff;
		font-family: 'Space Grotesk', 'Microsoft YaHei', sans-serif;
		font-size: 14px;
		font-weight: 700;
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
	}

	:deep(.header-avatar img) {
		object-fit: cover;
	}

	.emergency-user-menu__profile {
		padding: 6px 8px 10px;
	}

	.emergency-user-menu__profile-main {
		display: flex;
		align-items: center;
		gap: 12px;
	}

	.emergency-user-menu__avatar {
		width: 44px;
		height: 44px;
		border-radius: 14px;
	}

	.emergency-user-menu__text {
		min-width: 0;
	}

	.emergency-user-menu__text strong {
		display: block;
		font-size: 15px;
		font-weight: 700;
		color: #ffffff;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.emergency-user-menu__text span {
		display: block;
		margin-top: 4px;
		font-size: 12px;
		letter-spacing: 0.04em;
		color: rgba(214, 227, 255, 0.7);
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}

	.emergency-user-menu__divider {
		height: 1px;
		margin: 6px 0;
		background: rgba(255, 255, 255, 0.08);
	}

	.emergency-user-menu__logout {
		display: flex;
		align-items: center;
		gap: 10px;
		width: 100%;
		padding: 10px 12px;
		border: none;
		border-radius: 12px;
		background: transparent;
		color: var(--text-main);
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.08em;
		cursor: pointer;
		transition: background 0.2s ease, color 0.2s ease;
	}

	.emergency-user-menu__logout:hover {
		background: rgba(0, 163, 255, 0.14);
		color: #ffffff;
	}

	.emergency-user-menu__logout .anticon {
		font-size: 14px;
	}

	.page-main {
		display: grid;
		grid-template-columns: 3fr 6fr 3fr;
		gap: 24px;
		height: 100%;
		padding: 104px 24px 132px;
	}

	.panel-column {
		display: flex;
		flex-direction: column;
		gap: 16px;
		min-height: 0;
	}

	.panel-card {
		display: flex;
		flex-direction: column;
		padding: 18px;
		border: 1px solid var(--panel-border);
		border-radius: 8px;
		box-shadow: 0 20px 36px rgba(0, 0, 0, 0.24);
	}

	.panel-card--fill {
		flex: 1;
		min-height: 0;
	}

	.glass-panel {
		background: var(--panel-bg);
		backdrop-filter: blur(14px);
	}

	.panel-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}

	.panel-title {
		display: flex;
		align-items: center;
		gap: 10px;
		margin: 0 0 14px;
		font-size: 12px;
		font-weight: 700;
		letter-spacing: 0.18em;
		color: var(--accent);
		text-transform: uppercase;
	}

	.panel-title__bar {
		width: 4px;
		height: 14px;
		border-radius: 999px;
		background: var(--accent);
		box-shadow: 0 0 12px rgba(0, 163, 255, 0.8);
	}

	.panel-title__bar--danger {
		background: #ef4444;
		box-shadow: 0 0 12px rgba(239, 68, 68, 0.65);
	}

	.panel-title__bar--cyan {
		background: var(--accent-soft);
		box-shadow: 0 0 12px rgba(0, 228, 255, 0.65);
	}

	.panel-title__bar--amber {
		background: var(--accent-amber);
		box-shadow: 0 0 12px rgba(255, 183, 125, 0.65);
	}

	.panel-title__bar--green {
		background: var(--accent-green);
		box-shadow: 0 0 12px rgba(0, 228, 113, 0.65);
	}

	.panel-kicker {
		font-size: 11px;
		color: rgba(214, 227, 255, 0.7);
	}

	.weather-card {
		display: grid;
		grid-template-columns: 1.1fr 1fr;
		gap: 16px;
	}

	.weather-card__hero {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		padding: 16px 12px;
		border-radius: 8px;
		background: rgba(0, 163, 255, 0.08);
		border: 1px solid rgba(0, 163, 255, 0.12);
	}

	.weather-card__icon {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 18px;
		font-weight: 700;
		letter-spacing: 0.18em;
		color: var(--accent);
	}

	.weather-card__temp {
		margin-top: 10px;
		font-family: 'Space Grotesk', 'Microsoft YaHei', sans-serif;
		font-size: 30px;
		font-weight: 700;
		color: #ffffff;
	}

	.weather-card__label {
		margin-top: 6px;
		font-size: 11px;
		color: rgba(0, 163, 255, 0.7);
	}

	.weather-card__stats {
		display: flex;
		flex-direction: column;
		gap: 8px;
		justify-content: center;
	}

	.weather-stat {
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 12px;
		color: var(--text-muted);
	}

	.weather-stat strong {
		color: #ffffff;
		font-weight: 600;
	}

	.warning-list {
		flex: 1;
		min-height: 0;
		padding-right: 4px;
		overflow: auto;
	}

	.warning-item {
		padding: 14px 14px 14px 16px;
		margin-bottom: 12px;
		border-left: 4px solid transparent;
		background: rgba(255, 255, 255, 0.04);
	}

	.warning-item--red {
		background: rgba(239, 68, 68, 0.12);
		border-left-color: #ef4444;
	}

	.warning-item--orange {
		background: rgba(249, 115, 22, 0.12);
		border-left-color: #f97316;
	}

	.warning-item--cyan {
		background: rgba(0, 163, 255, 0.12);
		border-left-color: var(--accent);
	}

	.warning-item__head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		margin-bottom: 8px;
	}

	.warning-item__level {
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.08em;
	}

	.warning-item__time {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 11px;
		color: rgba(214, 227, 255, 0.5);
	}

	.warning-item p {
		margin: 0;
		font-size: 13px;
		line-height: 1.6;
		color: rgba(214, 227, 255, 0.92);
	}

	.center-stage {
		position: relative;
		display: flex;
		flex-direction: column;
		justify-content: flex-end;
		min-height: 0;
	}

	.map-stage {
		position: relative;
		flex: 1;
		min-height: 0;
		border-radius: 10px;
		overflow: hidden;
	}

	.map-stage__halo,
	.map-stage__overlay {
		position: absolute;
		inset: 0;
	}

	.map-stage__halo {
		background:
			radial-gradient(circle at 50% 50%, rgba(0, 163, 255, 0.16), transparent 30%),
			radial-gradient(circle at 42% 60%, rgba(0, 228, 255, 0.08), transparent 16%);
	}

	.map-stage__overlay {
		background: linear-gradient(180deg, rgba(10, 25, 47, 0.04) 0%, rgba(10, 25, 47, 0.18) 100%);
	}

	.flood-zone {
		position: absolute;
		transform: translate(-50%, -50%);
		pointer-events: none;
	}

	.flood-zone svg {
		width: 100%;
		height: 100%;
		overflow: visible;
		filter: drop-shadow(0 0 15px rgba(59, 130, 246, 0.4));
		animation: flood-pulse 3.2s ease-in-out infinite;
	}

	.flood-zone--primary path:first-child {
		fill: rgba(30, 58, 138, 0.45);
		stroke: rgba(59, 130, 246, 0.86);
		stroke-width: 2;
	}

	.flood-zone--primary path:last-child {
		fill: rgba(37, 99, 235, 0.28);
		stroke: rgba(96, 165, 250, 0.56);
		stroke-width: 1.2;
	}

	.flood-zone--secondary svg {
		filter: drop-shadow(0 0 10px rgba(56, 189, 248, 0.38));
	}

	.flood-zone--secondary path {
		fill: rgba(14, 165, 233, 0.15);
		stroke: rgba(56, 189, 248, 0.42);
		stroke-width: 1.5;
	}

	.flood-zone--secondary circle {
		fill: rgba(56, 189, 248, 0.12);
		transform-origin: center;
		animation: ping 2.2s ease-in-out infinite;
	}

	.flood-zone__label {
		position: absolute;
		left: 50%;
		bottom: -4px;
		transform: translate(-50%, 100%);
		display: flex;
		flex-direction: column;
		align-items: center;
		padding: 8px 12px;
		border-radius: 4px;
		border: 1px solid rgba(59, 130, 246, 0.55);
		background: rgba(10, 25, 47, 0.88);
		backdrop-filter: blur(8px);
		font-size: 11px;
		line-height: 1.4;
		color: #60a5fa;
		white-space: nowrap;
	}

	.flood-zone__label strong {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 12px;
		color: #d6e3ff;
	}

	.flood-zone__label--secondary {
		color: #67e8f9;
		border-color: rgba(56, 189, 248, 0.5);
	}

	.map-marker {
		position: absolute;
		transform: translate(-50%, -50%);
		transition: transform 0.25s ease;
	}

	.map-marker:hover {
		transform: translate(-50%, -58%);
	}

	.map-marker__box {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 36px;
		height: 36px;
		border: 1px solid;
		border-radius: 6px;
		background: rgba(13, 28, 50, 0.88);
		backdrop-filter: blur(10px);
		font-size: 18px;
	}

	.leak-marker {
		position: absolute;
		display: inline-flex;
		align-items: center;
		gap: 6px;
		transform: translate(-50%, -50%);
		padding: 6px 10px;
		border-radius: 999px;
		background: rgba(147, 0, 10, 0.24);
		border: 1px solid rgba(255, 180, 171, 0.38);
		color: #ffb4ab;
		font-size: 11px;
	}

	.legend-shell {
		display: flex;
		align-items: stretch;
		gap: 24px;
		margin-top: 16px;
		padding: 16px 18px;
		border: 1px solid rgba(255, 255, 255, 0.06);
		border-radius: 8px;
		background: rgba(1, 14, 36, 0.78);
		align-self: flex-start;
        position: absolute;
        z-index: 999;
	}

	.legend-block {
		padding-right: 24px;
		border-right: 1px solid rgba(255, 255, 255, 0.08);
	}

	.legend-block--depth {
		padding-right: 0;
		border-right: 0;
	}

	.legend-block h4 {
		margin: 0 0 12px;
		font-size: 10px;
		font-weight: 700;
		letter-spacing: 0.18em;
		text-transform: uppercase;
		color: var(--accent);
	}

	.legend-list {
		display: grid;
		gap: 10px;
	}

	.legend-item {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 12px;
		color: rgba(214, 227, 255, 0.82);
	}

	.legend-dot {
		width: 12px;
		height: 12px;
		border-radius: 50%;
	}

	.legend-dot--primary {
		background: rgba(37, 99, 235, 0.62);
		border: 1px solid rgba(96, 165, 250, 0.9);
		box-shadow: 0 0 8px rgba(37, 99, 235, 0.5);
	}

	.legend-dot--secondary {
		background: rgba(56, 189, 248, 0.32);
		border: 1px solid rgba(103, 232, 249, 0.85);
		box-shadow: 0 0 8px rgba(56, 189, 248, 0.4);
	}

	.facility-list {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}

	.facility-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 14px 16px;
		border-radius: 8px;
		background: rgba(26, 35, 51, 0.56);
		border: 1px solid rgba(255, 255, 255, 0.05);
	}

	.facility-item__meta {
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 13px;
		color: rgba(214, 227, 255, 0.8);
	}

	.facility-item strong {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 24px;
		color: #ffffff;
	}

	.resource-card {
		display: flex;
		align-items: center;
		gap: 18px;
	}

	.resource-donut {
		position: relative;
		width: 112px;
		height: 112px;
		flex-shrink: 0;
	}

	.resource-donut svg {
		width: 100%;
		height: 100%;
		transform: rotate(-90deg);
	}

	.resource-donut__track,
	.resource-donut__segment {
		fill: transparent;
		stroke-width: 10;
	}

	.resource-donut__track {
		stroke: rgba(255, 255, 255, 0.08);
	}

	.resource-donut__segment {
		stroke-linecap: round;
		stroke-dasharray: 251.2;
	}

	.resource-donut__segment--amber {
		stroke: #ffb77d;
		stroke-dasharray: 178 251.2;
	}

	.resource-donut__segment--cyan {
		stroke: #00e4ff;
		stroke-dasharray: 40 251.2;
		stroke-dashoffset: -178;
	}

	.resource-donut__segment--green {
		stroke: #00e471;
		stroke-dasharray: 33.2 251.2;
		stroke-dashoffset: -218;
	}

	.resource-donut__center {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
	}

	.resource-donut__center span {
		font-size: 10px;
		color: rgba(214, 227, 255, 0.5);
		text-transform: uppercase;
	}

	.resource-donut__center strong {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 18px;
		color: #ffffff;
	}

	.resource-list {
		flex: 1;
		display: grid;
		gap: 10px;
	}

	.resource-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		font-size: 12px;
	}

	.resource-item__label {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		color: rgba(214, 227, 255, 0.82);
	}

	.resource-item__dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
	}

	.resource-item strong {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 13px;
	}

	.trend-card {
		display: flex;
		flex-direction: column;
		flex: 1;
		min-height: 0;
		padding: 14px;
		border-radius: 10px;
		background: rgba(1, 14, 36, 0.32);
		border: 1px solid rgba(255, 255, 255, 0.04);
	}

	.trend-days,
	.trend-metrics {
		display: grid;
		grid-template-columns: repeat(6, minmax(0, 1fr));
		gap: 8px;
		text-align: center;
	}

	.trend-day {
		display: flex;
		flex-direction: column;
		gap: 6px;
		align-items: center;
		font-size: 11px;
		color: rgba(214, 227, 255, 0.58);
	}

	.trend-day :deep(svg) {
		font-size: 18px;
		color: var(--accent);
	}

	.trend-chart {
		flex: 1;
		min-height: 180px;
		margin: 12px 0 16px;
	}

	.trend-chart svg {
		width: 100%;
		height: 100%;
		overflow: visible;
	}

	.trend-path {
		fill: none;
		stroke-width: 2.5;
	}

	.trend-path--high {
		stroke: #ffb4ab;
		filter: drop-shadow(0 0 5px #ffb4ab);
	}

	.trend-path--low {
		stroke: var(--accent);
		filter: drop-shadow(0 0 5px var(--accent));
	}

	.trend-point-group--high {
		fill: #ffb4ab;
	}

	.trend-point-group--low {
		fill: var(--accent);
	}

	.trend-text {
		font-size: 8px;
		text-anchor: middle;
	}

	.trend-text--high {
		fill: #ffb4ab;
	}

	.trend-text--low {
		fill: var(--accent);
	}

	.trend-metric {
		font-size: 10px;
		color: rgba(214, 227, 255, 0.56);
		line-height: 1.4;
	}

	.trend-metric p {
		margin: 0 0 4px;
		color: rgba(0, 163, 255, 0.72);
	}

	.trend-metric span {
		display: block;
	}

	.page-footer {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		display: flex;
		align-items: center;
		gap: 22px;
		height: 108px;
		padding: 0 28px;
		background: rgba(4, 19, 41, 0.95);
		backdrop-filter: blur(22px);
		border-top: 1px solid rgba(255, 255, 255, 0.1);
		box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.5);
	}

	.assistant-brand {
		display: flex;
		align-items: center;
		gap: 14px;
		flex-shrink: 0;
	}

	.assistant-brand__icon {
		display: flex;
		align-items: center;
		justify-content: center;
		width: 48px;
		height: 48px;
		border-radius: 8px;
		border: 1px solid rgba(0, 163, 255, 0.4);
		background: rgba(0, 163, 255, 0.2);
		box-shadow: 0 0 16px rgba(0, 163, 255, 0.28);
		font-size: 28px;
		color: var(--accent);
	}

	.assistant-brand h4 {
		margin: 0 0 4px;
		font-family: 'Space Grotesk', 'Microsoft YaHei', sans-serif;
		font-size: 15px;
		letter-spacing: 0.12em;
		color: var(--accent);
	}

	.assistant-brand p {
		margin: 0;
		font-size: 10px;
		color: rgba(214, 227, 255, 0.42);
		text-transform: uppercase;
	}

	.assistant-message {
		position: relative;
		flex: 1;
		min-width: 0;
		padding: 18px 18px 18px 22px;
		border-radius: 8px;
		background: rgba(1, 14, 36, 0.56);
		border: 1px solid rgba(0, 163, 255, 0.2);
		overflow: hidden;
	}

	.assistant-message__bar {
		position: absolute;
		left: 0;
		top: 0;
		bottom: 0;
		width: 4px;
		background: var(--accent);
		box-shadow: 0 0 15px var(--accent);
	}

	.assistant-message p {
		margin: 0;
		font-size: 15px;
		line-height: 1.6;
		color: var(--accent);
	}

	.footer-actions {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-shrink: 0;
	}

	.radius-editor {
		display: inline-flex;
		align-items: center;
		gap: 10px;
		padding: 0 12px;
		height: 42px;
		border-radius: 8px;
		background: rgba(255, 255, 255, 0.05);
		border: 1px solid rgba(255, 255, 255, 0.1);
		font-size: 12px;
		color: rgba(214, 227, 255, 0.74);
	}

	.radius-editor em {
		font-style: normal;
		color: rgba(214, 227, 255, 0.5);
	}

	.radius-editor :deep(.ant-input-number) {
		width: 96px;
		background: transparent;
		border: 0;
		box-shadow: none;
	}

	.radius-editor :deep(.ant-input-number-input) {
		height: 40px;
		padding: 0;
		font-family: 'Space Grotesk', sans-serif;
		font-size: 16px;
		font-weight: 700;
		color: #ffffff;
		text-align: right;
		background: transparent;
	}

	.footer-button {
		height: 42px;
		padding: 0 18px;
		border-radius: 8px;
		font-size: 13px;
		font-weight: 700;
		cursor: pointer;
		transition: 0.2s ease;
	}

	.footer-button--primary {
		border: 0;
		background: var(--accent);
		color: #ffffff;
	}

	.footer-button--primary:hover {
		filter: brightness(1.08);
	}

	.footer-button--secondary {
		border: 1px solid rgba(255, 255, 255, 0.12);
		background: rgba(255, 255, 255, 0.04);
		color: rgba(214, 227, 255, 0.86);
	}

	.footer-button--secondary:hover {
		background: rgba(255, 255, 255, 0.08);
	}

	.status-strip {
		position: absolute;
		right: 28px;
		bottom: 124px;
		display: flex;
		gap: 10px;
	}

	.status-chip {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 9px 12px;
		border-radius: 999px;
		background: rgba(10, 25, 47, 0.72);
		border: 1px solid rgba(255, 255, 255, 0.08);
		backdrop-filter: blur(10px);
		font-size: 12px;
		color: rgba(214, 227, 255, 0.82);
	}

	.status-chip--connected {
		color: #63ff93;
	}

	.status-chip--fallback {
		color: #ffb77d;
	}

	.status-chip--connecting {
		color: #8ee7ff;
	}

	.drawer-list {
		display: grid;
		gap: 12px;
	}

	.drawer-item {
		padding: 14px 16px;
		border-radius: 8px;
		background: #f5f7fb;
	}

	.drawer-item__head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		margin-bottom: 8px;
	}

	.drawer-item__head strong {
		font-size: 14px;
		color: #182230;
	}

	.drawer-item__head span,
	.drawer-item__foot {
		font-size: 12px;
		color: #6b7280;
	}

	.drawer-item p {
		margin: 0 0 8px;
		font-size: 13px;
		line-height: 1.6;
		color: #364152;
	}

	.empty-panel {
		display: grid;
		place-items: center;
		min-height: 140px;
		font-size: 13px;
		color: rgba(214, 227, 255, 0.56);
	}

	.custom-scrollbar::-webkit-scrollbar {
		width: 4px;
	}

	.custom-scrollbar::-webkit-scrollbar-track {
		background: rgba(255, 255, 255, 0.04);
	}

	.custom-scrollbar::-webkit-scrollbar-thumb {
		background: rgba(0, 163, 255, 0.28);
		border-radius: 999px;
	}

	.scan-line {
		position: relative;
		overflow: hidden;
	}

	.scan-line::after {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
		height: 100%;
		background: linear-gradient(90deg, transparent, rgba(0, 163, 255, 0.18), transparent);
		transform: translateX(-100%);
		animation: scan 4.2s linear infinite;
		pointer-events: none;
	}

	.level-critical .header-status__tag,
	.level-critical .panel-title,
	.level-critical .assistant-message p {
		color: #ff9b7a;
	}

	.level-critical .panel-title__bar,
	.level-critical .assistant-message__bar {
		background: #ff8a65;
		box-shadow: 0 0 12px rgba(255, 138, 101, 0.75);
	}

	@keyframes scan {
		0% {
			transform: translateX(-100%);
		}
		20% {
			transform: translateX(100%);
		}
		100% {
			transform: translateX(100%);
		}
	}

	@keyframes flood-pulse {
		0%,
		100% {
			opacity: 0.42;
		}
		50% {
			opacity: 0.75;
		}
	}

	@keyframes ping {
		0% {
			transform: scale(0.7);
			opacity: 0.7;
		}
		100% {
			transform: scale(1.3);
			opacity: 0;
		}
	}
</style>
