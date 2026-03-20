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
				<button class="action-button action-button--ghost" type="button" @click="handleOpenLogDrawer()">
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
							<div class="weather-card__icon" aria-hidden="true">
								<svg viewBox="0 0 80 80" class="weather-card__icon-svg">
									<defs>
										<linearGradient id="stormCloudGradient" x1="0%" x2="100%" y1="0%" y2="100%">
											<stop offset="0%" stop-color="#dff4ff" />
											<stop offset="100%" stop-color="#71d8ff" />
										</linearGradient>
										<linearGradient id="stormRainGradient" x1="0%" x2="0%" y1="0%" y2="100%">
											<stop offset="0%" stop-color="#9cecff" />
											<stop offset="100%" stop-color="#1fb6ff" />
										</linearGradient>
										<linearGradient id="stormBoltGradient" x1="0%" x2="100%" y1="0%" y2="100%">
											<stop offset="0%" stop-color="#ffe58f" />
											<stop offset="100%" stop-color="#ffb703" />
										</linearGradient>
									</defs>
									<path
										d="M24 50c-8.4 0-15-6-15-13.8C9 29 14.7 24 21.8 23.2C24.1 16 30.5 11 38.1 11c8.6 0 15.7 5.8 17.6 13.8
										c7.5.1 13.3 5.7 13.3 12.9C69 45 62.8 50 55.2 50H24Z"
										fill="url(#stormCloudGradient)"
										opacity="0.95"
									/>
									<path d="M30 58l-6 10" stroke="url(#stormRainGradient)" stroke-linecap="round" stroke-width="4" />
									<path d="M42 58l-6 12" stroke="url(#stormRainGradient)" stroke-linecap="round" stroke-width="4" />
									<path d="M54 58l-6 10" stroke="url(#stormRainGradient)" stroke-linecap="round" stroke-width="4" />
									<path
										d="M43 39H34l6-10-1 8h8L37 55l6-16Z"
										fill="url(#stormBoltGradient)"
										stroke="rgba(255,229,143,0.45)"
										stroke-width="1.2"
										stroke-linejoin="round"
									/>
								</svg>
							</div>
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
						:affected-poi-list="displayScenarioPoiList"
						:leak-point-list="leakPointList"
						:rescue-route-list="adoptedRescueRouteList"
						:signal-counter="scenarioState.signalCounter"
						:latest-signal="latestSignal"
						:level-key="responseLevel.key"
						:color-map="poiColorMap"
						:icon-map="poiIconMap"
					/>
				</div>

				<div class="legend-shell glass-panel">
					<div class="legend-block">
						<h4>设施图例</h4>
						<div class="legend-list legend-list--facility">
							<div v-for="item in facilityLegend" :key="item.label" class="legend-item">
								<component :is="item.icon" :style="{ color: item.color }" />
								<span>{{ item.label }}</span>
							</div>
						</div>
					</div>
					<div class="legend-block legend-block--depth">
						<h4>积水深度图例</h4>
						<div class="legend-list">
							<div v-for="item in floodDepthLegend" :key="item.label" class="legend-item">
								<span class="legend-dot" :style="item.dotStyle"></span>
								<span>{{ item.label }}</span>
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

				<!-- <section class="panel-card glass-panel">
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
				</section> -->

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
				<div class="assistant-message__meta">
					<span>{{ aiSuggestionRiskSummary }}</span>
					<span>{{ aiSuggestionGeneratedTime }}</span>
				</div>
				<p>{{ aiSuggestionLoading ? 'AI 正在推演最新救援方案...' : aiSuggestion }}</p>
			</div>

			<div class="footer-actions">
				<div class="radius-editor">
					<span>受灾半径</span>
					<a-input-number v-model:value="radiusDraft" :min="200" :step="50" :controls="false" />
					<em>m</em>
				</div>
				<button class="footer-button footer-button--primary" type="button" @click="submitRadiusUpdate()">
					{{ updating ? '更新中' : '更新半径' }}
				</button>
				<button class="footer-button footer-button--secondary" type="button" @click="handleOpenPromptDrawer()">
					<CodeOutlined />
					<span>Prompt 辅助</span>
				</button>
				<button class="footer-button footer-button--secondary" type="button" @click="acceptSuggestion()">
					采纳建议
				</button>
			</div>
		</footer>

		

		<a-drawer
			v-model:visible="logDrawerOpen"
			title="演练操作日志"
			placement="right"
			:width="520"
			wrapClassName="emergency-log-drawer"
			:mask-style="{ background: 'rgba(2, 8, 23, 0.58)', backdropFilter: 'blur(4px)' }"
			:content-wrapper-style="{ boxShadow: '-18px 0 42px rgba(0, 0, 0, 0.34)' }"
			:drawer-style="{
				background: 'radial-gradient(circle at top left, rgba(0, 228, 255, 0.1), transparent 28%), linear-gradient(180deg, rgba(8, 18, 34, 0.98), rgba(4, 12, 26, 0.98))',
				borderLeft: '1px solid rgba(122, 218, 255, 0.14)',
				color: 'var(--text-main)'
			}"
			:header-style="{ padding: '18px 20px 16px', background: 'transparent', borderBottom: '1px solid rgba(122, 218, 255, 0.12)' }"
			:body-style="{ padding: '18px 20px 20px', background: 'transparent' }"
		>
			<div class="drawer-toolbar">
				<div class="drawer-toolbar__summary">
					<strong>{{ formattedLogs.length }}</strong>
					<span>条记录</span>
				</div>
				<button class="drawer-toolbar__refresh" type="button" @click="refreshLogPanel()">
					<SyncOutlined :spin="logLoading" />
					<span>刷新日志</span>
				</button>
			</div>
			<div class="drawer-filters">
				<a-input v-model:value="logKeyword" allow-clear placeholder="搜索操作名称 / 描述 / 操作人" />
				<div class="drawer-filter-tabs">
					<button
						v-for="item in logFilterOptions"
						:key="item.key"
						class="drawer-filter-tab"
						:class="{ 'drawer-filter-tab--active': activeLogFilter === item.key }"
						type="button"
						@click="activeLogFilter = item.key"
					>
						<span>{{ item.label }}</span>
						<em>{{ item.count }}</em>
					</button>
				</div>
			</div>
			<div class="drawer-list">
				<div v-for="item in filteredLogs" :key="item.id" class="drawer-item">
					<div class="drawer-item__head">
						<div class="drawer-item__title">
							<span class="drawer-item__tag" :class="`drawer-item__tag--${item.group}`">{{ item.groupLabel }}</span>
							<strong>{{ item.title }}</strong>
						</div>
						<span>{{ item.time }}</span>
					</div>
					<p>{{ item.detail }}</p>
					<div class="drawer-item__foot">
						<span>{{ item.operator }}</span>
						<span>{{ item.typeLabel }}</span>
					</div>
				</div>
				<div v-if="!filteredLogs.length" class="empty-panel">暂无匹配的演练日志。</div>
			</div>
		</a-drawer>

		<a-drawer
			v-model:visible="promptDrawerOpen"
			title="AI Prompt 辅助"
			placement="left"
			:width="620"
			wrapClassName="ai-prompt-drawer"
			:mask-style="{ background: 'rgba(2, 8, 23, 0.54)', backdropFilter: 'blur(4px)' }"
			:content-wrapper-style="{ boxShadow: '18px 0 42px rgba(0, 0, 0, 0.34)' }"
			:drawer-style="{
				background: 'radial-gradient(circle at top right, rgba(0, 228, 255, 0.08), transparent 24%), linear-gradient(180deg, rgba(8, 18, 34, 0.98), rgba(4, 12, 26, 0.98))',
				borderRight: '1px solid rgba(122, 218, 255, 0.14)',
				color: 'var(--text-main)'
			}"
			:header-style="{ padding: '18px 20px 16px', background: 'transparent', borderBottom: '1px solid rgba(122, 218, 255, 0.12)' }"
			:body-style="{ padding: '18px 20px 20px', background: 'transparent' }"
		>
			<div class="prompt-panel">
				<div class="prompt-panel__meta">
					<div>
						<strong>{{ aiSuggestionProvider }}</strong>
						<p>{{ aiSuggestionMode }}</p>
					</div>
					<button class="drawer-toolbar__refresh" type="button" @click="loadAiSuggestion()">
						<SyncOutlined :spin="aiSuggestionLoading" />
						<span>重新生成</span>
					</button>
				</div>
				<div class="prompt-card">
					<h4>当前救援建议</h4>
					<p>{{ aiSuggestion }}</p>
				</div>
				<div class="prompt-card">
					<h4>行动要点</h4>
					<div class="prompt-action-list">
						<div v-for="item in aiSuggestionActions" :key="item" class="prompt-action-item">{{ item }}</div>
					</div>
				</div>
				<div class="prompt-card">
					<h4>AI 规划救援路线</h4>
					<div class="prompt-route-list">
						<div
							v-for="route in aiSuggestionRoutes"
							:key="route.routeId"
							class="prompt-route-item"
							:style="{ '--route-color': route.lineColor, borderColor: route.lineColor }"
						>
							<div class="prompt-route-item__head">
								<strong>{{ route.routeName }}</strong>
								<span>{{ route.estimatedMinutes }} 分钟</span>
							</div>
							<p>{{ route.description }}</p>
							<div class="prompt-route-item__path">{{ route.startName }} → {{ route.endName }}</div>
						</div>
					</div>
				</div>
				<div class="prompt-card">
					<h4>GeoJSON 示例结构</h4>
					<pre class="prompt-template">{{ aiGeoJsonExample }}</pre>
				</div>
				<div class="prompt-card">
					<h4>空间计算代码示例</h4>
					<pre class="prompt-template">{{ aiSpatialCodeExample }}</pre>
				</div>
				<div class="prompt-card">
					<h4>{{ aiPromptTitle }}</h4>
					<pre class="prompt-template">{{ aiPromptTemplate }}</pre>
				</div>
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
		CodeOutlined,
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
	const MAX_SIGNAL_COUNT = 50
	const LOG_FETCH_LIMIT = 50
	const POLLING_INTERVAL = 10000
	const LOG_FILTER_ITEMS = [
		{ key: 'all', label: '全部' },
		{ key: 'manual', label: '调度操作' },
		{ key: 'signal', label: '灾情推演' },
		{ key: 'query', label: '查询记录' },
		{ key: 'system', label: '系统事件' }
	]
	const WARNING_FEED_EXCLUDED_OPERATION_TYPES = ['DASHBOARD_REFRESH', 'AI_SUGGESTION_ACCEPT']

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
	const promptDrawerOpen = ref(false)
	const logLoading = ref(false)
	const aiSuggestionLoading = ref(false)
	const logKeyword = ref('')
	const activeLogFilter = ref('all')
	const aiSuggestionState = ref(null)
	const adoptedRescueRouteList = ref([])
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
		(Array.isArray(scenarioState.value.affectedPoiList) ? scenarioState.value.affectedPoiList : []).slice()
	)
	const displayScenarioPoiList = computed(() =>
		scenarioPoiList.value
			.filter((item) => item && isValidCoordinate(item.lng, item.lat))
			.map((item) => ({
				...item,
				lng: Number(item.lng),
				lat: Number(item.lat)
			}))
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
	const poiIconMap = computed(() =>
		Object.keys(poiMetaMap).reduce((result, key) => {
			result[key] = poiMetaMap[key].icon
			return result
		}, {})
	)

	const totalFacilityStats = computed(() =>
		(Array.isArray(scenarioState.value.totalFacilityStats) ? scenarioState.value.totalFacilityStats : []).map((item) =>
			decorateFacilityStat(item)
		)
	)
	const facilityLegend = computed(() =>
		totalFacilityStats.value.map((item) => ({
			label: item.label,
			icon: item.icon,
			color: item.color
		}))
	)
	const floodDepthLegend = computed(() => [
		{
			label: '轻度积水区 (<= 0.8m)',
			dotStyle: {
				background: 'rgba(16, 185, 129, 0.22)',
				border: '1px solid #6ee7b7',
				boxShadow: '0 0 8px rgba(16, 185, 129, 0.38)'
			}
		},
		{
			label: '中度积水区 (0.8m - 1.5m)',
			dotStyle: {
				background: 'rgba(56, 189, 248, 0.24)',
				border: '1px solid #67e8f9',
				boxShadow: '0 0 8px rgba(56, 189, 248, 0.45)'
			}
		},
		{
			label: '重度积水区 (> 1.5m)',
			dotStyle: {
				background: 'rgba(37, 99, 235, 0.28)',
				border: '1px solid #60a5fa',
				boxShadow: '0 0 8px rgba(37, 99, 235, 0.5)'
			}
		}
	])

	const affectedFacilityStats = computed(() =>
		(Array.isArray(scenarioState.value.affectedFacilityStats) ? scenarioState.value.affectedFacilityStats : []).map((item) =>
			decorateFacilityStat(item)
		)
	)
	const facilityStats = computed(() => affectedFacilityStats.value)

	const formattedLogs = computed(() =>
		logList.value.map((item) => {
			const categoryMeta = resolveLogCategoryMeta(item.operationType, item.operationName, item.detail)
			return {
				id: item.id,
				title: item.operationName || '系统事件',
				time: formatDateTime(item.createTime),
				detail: item.detail || '暂无详细描述',
				operator: item.operatorName || '系统',
				group: categoryMeta.group,
				groupLabel: categoryMeta.groupLabel,
				typeLabel: categoryMeta.typeLabel,
				searchText: `${item.operationName || ''} ${item.detail || ''} ${item.operatorName || ''} ${categoryMeta.groupLabel} ${categoryMeta.typeLabel}`.toLowerCase()
			}
		})
	)
	const filteredLogs = computed(() => {
		const keyword = logKeyword.value.trim().toLowerCase()
		return formattedLogs.value.filter((item) => {
			const matchesFilter = activeLogFilter.value === 'all' ? true : item.group === activeLogFilter.value
			const matchesKeyword = keyword ? item.searchText.includes(keyword) : true
			return matchesFilter && matchesKeyword
		})
	})
	const logFilterOptions = computed(() => {
		const countMap = formattedLogs.value.reduce((result, item) => {
			result[item.group] = Number(result[item.group] || 0) + 1
			return result
		}, {})
		return LOG_FILTER_ITEMS.map((item) => ({
			...item,
			count: item.key === 'all' ? formattedLogs.value.length : Number(countMap[item.key] || 0)
		}))
	})

	const warningList = computed(() => {
		const entries = []
		const seenKeys = new Set()
		const pushEntry = (entry) => {
			if (!entry) {
				return
			}
			const dedupeKey = `${entry.time}|${entry.text}|${entry.label}`
			if (seenKeys.has(dedupeKey)) {
				return
			}
			seenKeys.add(dedupeKey)
			entries.push(entry)
		}
		signalFeed.value.forEach((item, index) => {
			pushEntry(createWarningEntryFromSignal(item, `signal-${index}`))
		})
		if (!signalFeed.value.length && latestSignal.value) {
			pushEntry(createWarningEntryFromSignal(latestSignal.value, 'latest-signal'))
		}
		logList.value.filter((item) => isWarningFeedLog(item)).forEach((item, index) => {
			pushEntry(createWarningEntryFromLog(item, `log-${index}`))
		})
		return entries.slice(0, 10)
	})

	const aiSuggestionFallback = computed(() => {
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
	const aiSuggestion = computed(() => aiSuggestionState.value?.suggestion || aiSuggestionFallback.value)
	const aiSuggestionRiskSummary = computed(() => aiSuggestionState.value?.riskSummary || `圈内设施 ${displayScenarioPoiList.value.length} 个`)
	const aiSuggestionProvider = computed(() => aiSuggestionState.value?.providerName || '规则推演引擎')
	const aiSuggestionMode = computed(() => aiSuggestionState.value?.modeLabel || 'Fallback Strategy')
	const aiSuggestionActions = computed(() => {
		const actionList = Array.isArray(aiSuggestionState.value?.actionList) ? aiSuggestionState.value.actionList : []
		if (actionList.length) {
			return actionList
		}
		return [
			`优先保护 ${topFacilityLabel.value}，保持圈内设施滚动复核。`,
			`围绕当前半径 ${Math.round(Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS))} 米范围持续巡检。`
		]
	})
	const aiPromptTitle = computed(() => aiSuggestionState.value?.promptTitle || 'GeoJSON / 空间计算 Prompt 辅助')
	const aiPromptTemplate = computed(() => aiSuggestionState.value?.promptTemplate || buildPromptTemplateFallback())
	const aiGeoJsonExample = computed(() => aiSuggestionState.value?.geoJsonExample || buildGeoJsonExampleFallback())
	const aiSpatialCodeExample = computed(() => aiSuggestionState.value?.spatialCodeExample || buildSpatialCodeExampleFallback())
	const aiSuggestionRoutes = computed(() => {
		const backendRouteList = normalizeRescueRouteList(aiSuggestionState.value?.routeList)
		return backendRouteList.length ? backendRouteList : buildRescueRouteFallback()
	})
	const aiSuggestionGeneratedTime = computed(() =>
		aiSuggestionState.value?.generatedTime ? `生成于 ${formatTime(aiSuggestionState.value.generatedTime)}` : '本地回退建议'
	)

	const topFacilityLabel = computed(() => affectedFacilityStats.value[0]?.label || totalFacilityStats.value[0]?.label || '关键设施')

	function createScenarioState() {
		return {
			centerLng: DEFAULT_CENTER_LNG,
			centerLat: DEFAULT_CENTER_LAT,
			radiusMeters: DEFAULT_RADIUS_METERS,
			totalPoiCount: 0,
			poiCountInRange: 0,
			affectedPoiList: [],
			totalFacilityStats: [],
			affectedFacilityStats: [],
			signalCounter: 0,
			latestSignal: null,
			leakPointList: []
		}
	}

	function decorateFacilityStat(item) {
		const type = item?.type || '设施'
		return {
			type,
			label: type,
			count: Number(item?.count || 0),
			icon: poiMetaMap[type]?.icon || AimOutlined,
			color: poiMetaMap[type]?.color || '#8ee7ff'
		}
	}

	function buildWarningMeta(title, text, eventType = '') {
		const content = `${title || ''} ${text || ''}`
		if (/RISK_AGGRAVATED/.test(eventType) || /加重|严重|扩大|失控|重度/.test(content)) {
			return { level: 'red', label: '红色预警' }
		}
		if (/NEW_LEAK_POINT|RADIUS_UPDATE/.test(eventType) || /漏水|人工|调整|更新|排查|巡检|处置/.test(content)) {
			return { level: 'orange', label: '橙色预警' }
		}
		return { level: 'yellow', label: '黄色预警' }
	}

	function resolveLogCategoryMeta(operationType = '', title = '', detail = '') {
		const content = `${operationType || ''} ${title || ''} ${detail || ''}`
		if (/DASHBOARD_REFRESH|RADIUS_UPDATE|MANUAL_RADIUS_UPDATE|AI_SUGGESTION_ACCEPT/.test(content)) {
			return { group: 'manual', groupLabel: '调度操作', typeLabel: '人工处置' }
		}
		if (/SYSTEM_SIGNAL|NEW_LEAK_POINT|RISK_AGGRAVATED/.test(content) || /漏水点|险情加重|灾情/.test(content)) {
			return { group: 'signal', groupLabel: '灾情推演', typeLabel: '态势推送' }
		}
		if (/POI_QUERY|QUERY/.test(content) || /查询/.test(content)) {
			return { group: 'query', groupLabel: '查询记录', typeLabel: '检索操作' }
		}
		return { group: 'system', groupLabel: '系统事件', typeLabel: '系统日志' }
	}

	function isWarningFeedLog(logItem) {
		if (!logItem?.operationType) {
			return true
		}
		return !WARNING_FEED_EXCLUDED_OPERATION_TYPES.includes(logItem.operationType)
	}

	function createWarningEntryFromSignal(signal, fallbackId) {
		if (!signal) {
			return null
		}
		const text = signal.message || signal.signalName || '灾情信号更新'
		return {
			id: signal.signalId || fallbackId,
			...buildWarningMeta(signal.signalName, text, signal.signalType),
			time: formatTime(signal.signalTime),
			text
		}
	}

	async function recordOperationLog(payload, options = {}) {
		const { refreshLogs = false } = options
		try {
			await emergencyDrillApi.recordLog(payload)
			if (refreshLogs || logDrawerOpen.value) {
				await loadLogList(true)
			}
		} catch (error) {
			// 日志记录失败不阻塞主流程
		}
	}

	function handleOpenLogDrawer() {
		logDrawerOpen.value = true
		logKeyword.value = ''
		activeLogFilter.value = 'all'
		refreshLogPanel(false)
	}

	async function handleOpenPromptDrawer() {
		promptDrawerOpen.value = true
		if (!aiSuggestionState.value) {
			await loadAiSuggestion(false)
		}
	}

	function createWarningEntryFromLog(logItem, fallbackId) {
		if (!logItem) {
			return null
		}
		const text = logItem.detail || logItem.operationName || '系统事件'
		return {
			id: logItem.id || fallbackId,
			...buildWarningMeta(logItem.operationName, text, logItem.operationType),
			time: formatTime(logItem.createTime),
			text
		}
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
			affectedPoiList: Array.isArray(state.affectedPoiList) ? state.affectedPoiList : scenarioPoiList.value,
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
		return `${Number(value || 0)}`
	}

	function buildPromptTemplateFallback() {
		return [
			'你是城市内涝应急 GIS 助手，请基于以下态势输出救援调度建议、GeoJSON 示例、救援路线和空间计算代码。',
			`- 灾情中心: (${Number(scenarioState.value.centerLng || DEFAULT_CENTER_LNG).toFixed(6)}, ${Number(scenarioState.value.centerLat || DEFAULT_CENTER_LAT).toFixed(6)})`,
			`- 受灾半径: ${Math.round(Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS))} 米`,
			`- 圈内设施数: ${displayScenarioPoiList.value.length} 个`,
			`- 主要受灾设施: ${topFacilityLabel.value}`,
			`- 当前建议路线数: ${buildRescueRouteFallback().length} 条`,
			'- 输出字段: plan, routeList, geojson, spatialCode, riskNotes'
		].join('\n')
	}

	function buildGeoJsonExampleFallback() {
		const centerLng = Number(scenarioState.value.centerLng || DEFAULT_CENTER_LNG)
		const centerLat = Number(scenarioState.value.centerLat || DEFAULT_CENTER_LAT)
		const radiusMeters = Math.round(Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS))
		const circleCoordinates = Array.from({ length: 25 }, (_, index) => {
			const angle = (Math.PI * 2 * index) / 24
			const eastMeters = Math.cos(angle) * radiusMeters
			const northMeters = Math.sin(angle) * radiusMeters
			return [
				Number((centerLng + eastMeters / (111320 * Math.cos((centerLat * Math.PI) / 180))).toFixed(6)),
				Number((centerLat + northMeters / 110540).toFixed(6))
			]
		})
		const featureCollection = {
			type: 'FeatureCollection',
			features: [
				{
					type: 'Feature',
					properties: {
						kind: 'disaster-center',
						radiusMeters,
						affectedPoiCount: displayScenarioPoiList.value.length
					},
					geometry: {
						type: 'Point',
						coordinates: [Number(centerLng.toFixed(6)), Number(centerLat.toFixed(6))]
					}
				},
				{
					type: 'Feature',
					properties: {
						kind: 'flood-range',
						radiusMeters,
						warningLevel: responseLevel.value.label
					},
					geometry: {
						type: 'Polygon',
						coordinates: [circleCoordinates]
					}
				},
				...buildRescueRouteFallback().map((route) => ({
					type: 'Feature',
					properties: {
						kind: 'rescue-route',
						routeId: route.routeId,
						routeName: route.routeName,
						routeType: route.routeType,
						lineColor: route.lineColor
					},
					geometry: {
						type: 'LineString',
						coordinates: route.pointList.map((point) => [Number(point.lng.toFixed(6)), Number(point.lat.toFixed(6))])
					}
				})),
				...displayScenarioPoiList.value.slice(0, 6).map((item) => ({
					type: 'Feature',
					properties: {
						id: item.id,
						name: item.name,
						type: item.type,
						distanceMeters: Number(item.distanceMeters || 0)
					},
					geometry: {
						type: 'Point',
						coordinates: [Number(Number(item.lng).toFixed(6)), Number(Number(item.lat).toFixed(6))]
					}
				})),
				...leakPointList.value.slice(0, 2).map((item) => ({
					type: 'Feature',
					properties: {
						kind: 'leak-point',
						name: item.name
					},
					geometry: {
						type: 'Point',
						coordinates: [Number(Number(item.lng).toFixed(6)), Number(Number(item.lat).toFixed(6))]
					}
				}))
			]
		}
		return JSON.stringify(featureCollection, null, 2)
	}

	function buildSpatialCodeExampleFallback() {
		return [
			`const center = [${Number(scenarioState.value.centerLng || DEFAULT_CENTER_LNG).toFixed(6)}, ${Number(scenarioState.value.centerLat || DEFAULT_CENTER_LAT).toFixed(6)}]`,
			`const radiusMeters = ${Math.round(Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS))}`,
			'',
			'function haversineMeters([lng1, lat1], [lng2, lat2]) {',
			'  const toRad = (value) => (value * Math.PI) / 180',
			'  const dLat = toRad(lat2 - lat1)',
			'  const dLng = toRad(lng2 - lng1)',
			'  const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2',
			'  return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))',
			'}',
			'',
			'export function filterAffectedPoi(poiList) {',
			'  return poiList',
			'    .map((poi) => ({ ...poi, distanceMeters: haversineMeters(center, [poi.lng, poi.lat]) }))',
			'    .filter((poi) => poi.distanceMeters <= radiusMeters)',
			'    .sort((left, right) => left.distanceMeters - right.distanceMeters)',
			'}',
			'',
			'export function spreadClusteredPoi(poiList, minGapMeters = 60) {',
			'  const meterToLng = (meters, lat) => meters / (111320 * Math.cos((lat * Math.PI) / 180))',
			'  const meterToLat = (meters) => meters / 110540',
			'  return poiList.map((poi, index) => {',
			'    const angle = ((index % 12) / 12) * Math.PI * 2',
			'    const ring = Math.floor(index / 12) + 1',
			'    const offsetMeters = ring * minGapMeters',
			'    return {',
			'      ...poi,',
			'      renderLng: poi.lng + meterToLng(Math.cos(angle) * offsetMeters, poi.lat),',
			'      renderLat: poi.lat + meterToLat(Math.sin(angle) * offsetMeters)',
			'    }',
			'  })',
			'}',
			'',
			'export function buildRescueRoute(start, end, bendMeters = 90) {',
			'  const meterToLng = (meters, lat) => meters / (111320 * Math.cos((lat * Math.PI) / 180))',
			'  const meterToLat = (meters) => meters / 110540',
			'  const dx = end[0] - start[0]',
			'  const dy = end[1] - start[1]',
			'  const norm = Math.sqrt(dx * dx + dy * dy) || 1',
			'  const nx = (-dy / norm) * bendMeters',
			'  const ny = (dx / norm) * bendMeters',
			'  const midLng = (start[0] + end[0]) / 2',
			'  const midLat = (start[1] + end[1]) / 2',
			'  return [',
			'    start,',
			'    [midLng + meterToLng(nx, midLat), midLat + meterToLat(ny)],',
			'    end',
			'  ]',
			'}'
		].join('\n')
	}

	function normalizeRescueRouteList(routeList) {
		return (Array.isArray(routeList) ? routeList : [])
			.map((route, index) => {
				const pointList = (Array.isArray(route?.pointList) ? route.pointList : [])
					.filter((item) => isValidCoordinate(item?.lng, item?.lat))
					.map((item, pointIndex) => ({
						pointName: item.pointName || `节点${pointIndex + 1}`,
						pointRole: item.pointRole || (pointIndex === 0 ? 'start' : pointIndex === (route.pointList?.length || 0) - 1 ? 'end' : 'via'),
						lng: Number(item.lng),
						lat: Number(item.lat)
					}))
				if (pointList.length < 2) {
					return null
				}
				return {
					routeId: route.routeId || `ai-route-${index + 1}`,
					routeName: route.routeName || `救援路线${index + 1}`,
					routeType: route.routeType || 'dispatch',
					description: route.description || 'AI 生成的救援调度路线',
					priorityLevel: Number(route.priorityLevel || index + 1),
					lineColor: route.lineColor || '#38bdf8',
					estimatedMinutes: Math.max(Number(route.estimatedMinutes || 0), 1),
					pointList,
					startName: pointList[0].pointName,
					endName: pointList[pointList.length - 1].pointName
				}
			})
			.filter(Boolean)
	}

	function buildRescueRouteFallback() {
		const disasterCenter = resolveFallbackDisasterCenter()
		const routeList = []
		const drainageOrigin = findNearestPoiByTypes(poiList.value, disasterCenter.lng, disasterCenter.lat, ['排涝设施', '消防设施', '应急指挥'])
		const nearestLeak = findNearestLeakPointFallback(disasterCenter.lng, disasterCenter.lat)
		if (drainageOrigin) {
			routeList.push(
				buildFallbackRescueRoute({
					routeId: 'fallback-drainage-route',
					routeName: '排涝抢险路线',
					routeType: 'drainage',
					lineColor: '#38bdf8',
					priorityLevel: 1,
					description: '排涝抢险组优先进入漏水点周边，建立近场排水通道。',
					startPoi: drainageOrigin,
					endPoint: nearestLeak
						? { pointName: nearestLeak.name, lng: Number(nearestLeak.lng), lat: Number(nearestLeak.lat) }
						: { pointName: '灾情中心', lng: disasterCenter.lng, lat: disasterCenter.lat },
					disasterCenter
				})
			)
		}
		const medicalTarget = findPriorityAffectedPoi(disasterCenter.lng, disasterCenter.lat, ['医院', '学校', '社区服务', '交通枢纽', '物资仓储'])
		const medicalOrigin = findNearestPoiByTypes(
			poiList.value,
			medicalTarget?.lng ?? disasterCenter.lng,
			medicalTarget?.lat ?? disasterCenter.lat,
			['医院', '应急指挥', '消防设施'],
			medicalTarget?.id
		)
		if (medicalOrigin) {
			routeList.push(
				buildFallbackRescueRoute({
					routeId: 'fallback-medical-route',
					routeName: '医疗救援路线',
					routeType: 'medical',
					lineColor: '#fb7185',
					priorityLevel: 2,
					description: '医疗救援组前往重点受灾设施，建立现场救治与转运节点。',
					startPoi: medicalOrigin,
					endPoint: medicalTarget
						? { pointName: medicalTarget.name, lng: Number(medicalTarget.lng), lat: Number(medicalTarget.lat) }
						: { pointName: '灾情中心', lng: disasterCenter.lng, lat: disasterCenter.lat },
					disasterCenter
				})
			)
		}
		const evacuationOrigin = findPriorityAffectedPoi(disasterCenter.lng, disasterCenter.lat, ['学校', '社区服务', '医院', '交通枢纽'])
		if (evacuationOrigin) {
			const evacuationTarget = findNearestPoiByTypes(
				poiList.value,
				evacuationOrigin.lng,
				evacuationOrigin.lat,
				['避险点', '应急指挥'],
				evacuationOrigin.id
			)
			if (evacuationTarget) {
				routeList.push(
					buildFallbackRescueRoute({
						routeId: 'fallback-evacuation-route',
						routeName: '人员转运路线',
						routeType: 'evacuation',
						lineColor: '#22c55e',
						priorityLevel: 3,
						description: '疏散转运组沿最近避险通道组织人员分批转运。',
						startPoi: evacuationOrigin,
						endPoint: { pointName: evacuationTarget.name, lng: Number(evacuationTarget.lng), lat: Number(evacuationTarget.lat) },
						disasterCenter
					})
				)
			}
		}
		return routeList.filter(Boolean).slice(0, 3)
	}

	function resolveFallbackDisasterCenter() {
		const weightedPoints = []
		leakPointList.value.forEach((item) => {
			if (isValidCoordinate(item?.lng, item?.lat)) {
				weightedPoints.push({ lng: Number(item.lng), lat: Number(item.lat), weight: 1.65 })
			}
		})
		displayScenarioPoiList.value.forEach((item) => {
			if (isValidCoordinate(item?.lng, item?.lat)) {
				weightedPoints.push({ lng: Number(item.lng), lat: Number(item.lat), weight: resolveAffectedPoiWeight(item.type) })
			}
		})
		if (!weightedPoints.length) {
			return {
				lng: Number(scenarioState.value.centerLng || DEFAULT_CENTER_LNG),
				lat: Number(scenarioState.value.centerLat || DEFAULT_CENTER_LAT)
			}
		}
		const totalWeight = weightedPoints.reduce((total, item) => total + item.weight, 0)
		return {
			lng: weightedPoints.reduce((total, item) => total + item.lng * item.weight, 0) / totalWeight,
			lat: weightedPoints.reduce((total, item) => total + item.lat * item.weight, 0) / totalWeight
		}
	}

	function resolveAffectedPoiWeight(type) {
		if (['医院', '学校', '交通枢纽'].includes(type)) {
			return 1.35
		}
		if (['排涝设施', '消防设施', '应急指挥'].includes(type)) {
			return 1.18
		}
		return 1
	}

	function findNearestPoiByTypes(sourceList, referenceLng, referenceLat, typeList = [], excludeId = '') {
		return (Array.isArray(sourceList) ? sourceList : [])
			.filter((item) => item && typeList.includes(item.type) && isValidCoordinate(item.lng, item.lat))
			.filter((item) => !excludeId || item.id !== excludeId)
			.slice()
			.sort(
				(left, right) =>
					calculateDistanceMeters(left.lng, left.lat, referenceLng, referenceLat) -
					calculateDistanceMeters(right.lng, right.lat, referenceLng, referenceLat)
			)[0] || null
	}

	function findPriorityAffectedPoi(referenceLng, referenceLat, typeList = []) {
		for (const type of typeList) {
			const matched = displayScenarioPoiList.value
				.filter((item) => item?.type === type && isValidCoordinate(item.lng, item.lat))
				.slice()
				.sort(
					(left, right) =>
						calculateDistanceMeters(left.lng, left.lat, referenceLng, referenceLat) -
						calculateDistanceMeters(right.lng, right.lat, referenceLng, referenceLat)
				)[0]
			if (matched) {
				return matched
			}
		}
		return (
			displayScenarioPoiList.value
				.filter((item) => isValidCoordinate(item.lng, item.lat))
				.slice()
				.sort(
					(left, right) =>
						calculateDistanceMeters(left.lng, left.lat, referenceLng, referenceLat) -
						calculateDistanceMeters(right.lng, right.lat, referenceLng, referenceLat)
				)[0] || null
		)
	}

	function findNearestLeakPointFallback(referenceLng, referenceLat) {
		return (
			leakPointList.value
				.filter((item) => isValidCoordinate(item?.lng, item?.lat))
				.slice()
				.sort(
					(left, right) =>
						calculateDistanceMeters(left.lng, left.lat, referenceLng, referenceLat) -
						calculateDistanceMeters(right.lng, right.lat, referenceLng, referenceLat)
				)[0] || null
		)
	}

	function buildFallbackRescueRoute({ routeId, routeName, routeType, lineColor, priorityLevel, description, startPoi, endPoint, disasterCenter }) {
		if (!startPoi || !endPoint || !isValidCoordinate(startPoi.lng, startPoi.lat) || !isValidCoordinate(endPoint.lng, endPoint.lat)) {
			return null
		}
		const pointList = buildFallbackRoutePoints(startPoi, endPoint, disasterCenter, priorityLevel)
		if (pointList.length < 2) {
			return null
		}
		return {
			routeId,
			routeName,
			routeType,
			lineColor,
			priorityLevel,
			description,
			estimatedMinutes: estimateFallbackRouteMinutes(pointList, routeType),
			pointList,
			startName: pointList[0].pointName,
			endName: pointList[pointList.length - 1].pointName
		}
	}

	function buildFallbackRoutePoints(startPoi, endPoint, disasterCenter, priorityLevel) {
		const pointList = [
			{
				pointName: startPoi.name || '起点',
				pointRole: 'start',
				lng: Number(startPoi.lng),
				lat: Number(startPoi.lat)
			}
		]
		const distanceMeters = calculateDistanceMeters(startPoi.lng, startPoi.lat, endPoint.lng, endPoint.lat)
		if (distanceMeters >= 180) {
			const viaPoint = buildFallbackRouteViaPoint(startPoi, endPoint, disasterCenter, priorityLevel)
			if (viaPoint) {
				pointList.push(viaPoint)
			}
		}
		pointList.push({
			pointName: endPoint.pointName || endPoint.name || '终点',
			pointRole: 'end',
			lng: Number(endPoint.lng),
			lat: Number(endPoint.lat)
		})
		return pointList
	}

	function buildFallbackRouteViaPoint(startPoi, endPoint, disasterCenter, priorityLevel) {
		const baseLng = (Number(startPoi.lng) + Number(endPoint.lng) + Number(disasterCenter.lng)) / 3
		const baseLat = (Number(startPoi.lat) + Number(endPoint.lat) + Number(disasterCenter.lat)) / 3
		const eastMeters =
			(Number(endPoint.lng) - Number(startPoi.lng)) * 111320 * Math.max(Math.cos((baseLat * Math.PI) / 180), 0.2)
		const northMeters = (Number(endPoint.lat) - Number(startPoi.lat)) * 110540
		const length = Math.sqrt(eastMeters * eastMeters + northMeters * northMeters)
		if (length < 1) {
			return null
		}
		const bendMeters = Math.max(55, Math.min(length * 0.18, 150))
		const direction = priorityLevel % 2 === 0 ? -1 : 1
		return {
			pointName: '中继调度点',
			pointRole: 'via',
			lng: offsetLngByMeters(baseLng, baseLat, (-northMeters / length) * bendMeters * direction),
			lat: offsetLatByMeters(baseLat, (eastMeters / length) * bendMeters * direction)
		}
	}

	function estimateFallbackRouteMinutes(pointList, routeType) {
		const totalDistanceMeters = pointList.slice(0, -1).reduce((total, item, index) => {
			const nextPoint = pointList[index + 1]
			return total + calculateDistanceMeters(item.lng, item.lat, nextPoint.lng, nextPoint.lat)
		}, 0)
		const speedMetersPerMinute = routeType === 'medical' ? 560 : routeType === 'evacuation' ? 420 : 460
		return Math.max(Math.ceil(totalDistanceMeters / speedMetersPerMinute) + 2, 3)
	}

	function offsetLngByMeters(lng, lat, eastMeters) {
		return Number(lng) + Number(eastMeters) / (111320 * Math.max(Math.cos((Number(lat) * Math.PI) / 180), 0.2))
	}

	function offsetLatByMeters(lat, northMeters) {
		return Number(lat) + Number(northMeters) / 110540
	}

	function calculateDistanceMeters(fromLng, fromLat, toLng, toLat) {
		const earthRadius = 6371000
		const dLat = ((Number(toLat) - Number(fromLat)) * Math.PI) / 180
		const dLng = ((Number(toLng) - Number(fromLng)) * Math.PI) / 180
		const a =
			Math.sin(dLat / 2) * Math.sin(dLat / 2) +
			Math.cos((Number(fromLat) * Math.PI) / 180) *
				Math.cos((Number(toLat) * Math.PI) / 180) *
				Math.sin(dLng / 2) *
				Math.sin(dLng / 2)
		return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
	}

	function isValidCoordinate(lng, lat) {
		return Number.isFinite(Number(lng)) && Number.isFinite(Number(lat))
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
			const data = await emergencyDrillApi.getLogList({ limit: LOG_FETCH_LIMIT })
			logList.value = Array.isArray(data) ? data : []
		} catch (error) {
			if (!silent) {
				message.error('加载演练日志失败')
			}
		}
	}

	async function loadAiSuggestion(showError = false) {
		aiSuggestionLoading.value = true
		try {
			const data = await emergencyDrillApi.getAiSuggestion()
			aiSuggestionState.value = data || null
		} catch (error) {
			if (showError) {
				message.error('加载 AI 救援建议失败')
			}
		} finally {
			aiSuggestionLoading.value = false
		}
	}

	async function refreshLogPanel(showMessage = true) {
		logLoading.value = true
		try {
			await loadLogList(true)
			if (showMessage) {
				message.success('操作日志已刷新')
			}
		} finally {
			logLoading.value = false
		}
	}

	async function loadState(silent = false) {
		try {
			const data = await emergencyDrillApi.getDisasterState()
			applyScenarioState(data)
			await loadAiSuggestion(false)
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
				emergencyDrillApi.getLogList({ limit: LOG_FETCH_LIMIT })
			])
			applyScenarioState(stateData)
			logList.value = Array.isArray(logData) ? logData : []
			await loadAiSuggestion(false)
			await recordOperationLog(
				{
					operationType: 'DASHBOARD_REFRESH',
					operationName: '刷新应急态势',
					detail: `手动刷新首页态势，当前受灾半径 ${Math.round(Number(stateData?.radiusMeters || scenarioState.value.radiusMeters || 0))} 米，圈内设施 ${Array.isArray(stateData?.affectedPoiList) ? stateData.affectedPoiList.length : scenarioPoiList.value.length} 个。`
				},
				{ refreshLogs: true }
			)
			message.success('态势数据已刷新')
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
				emergencyDrillApi.getLogList({ limit: LOG_FETCH_LIMIT })
			])
			poiList.value = Array.isArray(poiData) ? poiData : []
			logList.value = Array.isArray(logData) ? logData : []
			applyScenarioState(stateData)
			await loadAiSuggestion(false)
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
		const finalRemark = typeof remark === 'string' && remark.trim() ? remark : '指挥席手动更新受灾半径'
		updating.value = true
		try {
			const stateData = await emergencyDrillApi.updateRadius({
				radiusMeters: Number(radiusDraft.value),
				remark: finalRemark
			})
			applyScenarioState(stateData)
			if (stateData?.latestSignal) {
				appendSignal(stateData.latestSignal)
			}
			await loadAiSuggestion(false)
			await loadLogList(true)
			message.success('受灾半径已更新')
		} finally {
			updating.value = false
		}
	}

	async function acceptSuggestion() {
		const increment = responseLevel.value.key === 'critical' ? 220 : 120
		radiusDraft.value = Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS) + increment
		adoptedRescueRouteList.value = aiSuggestionRoutes.value.map((route) => ({
			...route,
			pointList: route.pointList.map((point) => ({ ...point }))
		}))
		const routeSummary = adoptedRescueRouteList.value.map((item) => item.routeName).join('、')
		await recordOperationLog(
			{
				operationType: 'AI_SUGGESTION_ACCEPT',
				operationName: '采纳AI建议',
				detail: `采纳AI建议，已将待更新受灾半径预设为 ${Math.round(Number(radiusDraft.value))} 米，并下发 ${adoptedRescueRouteList.value.length} 条救援路线：${routeSummary || '暂无'}。`
			},
			{ refreshLogs: true }
		)
		message.success(
			adoptedRescueRouteList.value.length
				? `已采纳 AI 建议，地图已绘制 ${adoptedRescueRouteList.value.length} 条救援路线`
				: `已采纳 AI 建议，受灾半径待更新为 ${Math.round(Number(radiusDraft.value))} 米`
		)
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
				loadAiSuggestion(false)
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
				affectedPoiList: Array.isArray(data.affectedPoiList) ? data.affectedPoiList : scenarioPoiList.value,
				signalCounter: Number(data.signalNo || scenarioState.value.signalCounter),
				latestSignal: data,
				leakPointList: mergedLeakPointList
			}
			radiusDraft.value = Number(scenarioState.value.radiusMeters || DEFAULT_RADIUS_METERS)
			await loadState(true)
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
		display: flex;
		align-items: center;
		justify-content: center;
		width: 72px;
		height: 72px;
		border-radius: 20px;
		background:
			radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.2), transparent 40%),
			linear-gradient(180deg, rgba(0, 163, 255, 0.18), rgba(0, 163, 255, 0.06));
		box-shadow: inset 0 0 0 1px rgba(159, 232, 255, 0.16), 0 12px 26px rgba(0, 110, 190, 0.18);
	}

	.weather-card__icon-svg {
		width: 58px;
		height: 58px;
		filter: drop-shadow(0 0 10px rgba(78, 206, 255, 0.24));
		animation: weatherStormFloat 3.4s ease-in-out infinite;
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

	@keyframes weatherStormFloat {
		0%,
		100% {
			transform: translateY(0);
		}
		50% {
			transform: translateY(-3px);
		}
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

	.warning-item--yellow {
		background: rgba(250, 204, 21, 0.12);
		border-left-color: #facc15;
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

	.legend-list--facility {
		grid-template-columns: repeat(2, minmax(0, 1fr));
		column-gap: 18px;
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
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: 12px;
	}

	.facility-item {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 10px 16px;
		border-radius: 8px;
		background: rgba(26, 35, 51, 0.56);
		border: 1px solid rgba(255, 255, 255, 0.05);
	}

	.facility-item__meta {
		display: flex;
		align-items: center;
		gap: 12px;
		min-width: 0;
		font-size: 13px;
		color: rgba(214, 227, 255, 0.8);
	}

	.facility-item__meta span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.facility-item strong {
		font-family: 'Space Grotesk', sans-serif;
		flex-shrink: 0;
		font-size: 22px;
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

	.assistant-message__meta {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 16px;
		margin-bottom: 8px;
		font-size: 11px;
		color: rgba(214, 227, 255, 0.48);
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
		display: inline-flex;
		align-items: center;
		gap: 8px;
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

	.drawer-filters :deep(.ant-input-affix-wrapper),
	.drawer-filters :deep(.ant-input) {
		background: rgba(8, 20, 38, 0.9);
		border-color: rgba(122, 218, 255, 0.16);
		color: #e6f4ff;
		box-shadow: none;
	}

	.drawer-filters :deep(.ant-input-affix-wrapper:hover),
	.drawer-filters :deep(.ant-input-affix-wrapper-focused),
	.drawer-filters :deep(.ant-input:hover),
	.drawer-filters :deep(.ant-input:focus) {
		border-color: rgba(0, 228, 255, 0.34);
	}

	.drawer-filters :deep(.ant-input::placeholder) {
		color: rgba(214, 227, 255, 0.36);
	}

	.drawer-filters :deep(.ant-input-clear-icon),
	.drawer-filters :deep(.ant-input-prefix) {
		color: rgba(142, 231, 255, 0.66);
	}

	.drawer-toolbar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		margin-bottom: 14px;
	}

	.drawer-toolbar__summary {
		display: flex;
		align-items: baseline;
		gap: 8px;
	}

	.drawer-toolbar__summary strong {
		font-family: 'Space Grotesk', sans-serif;
		font-size: 22px;
		color: #f3fbff;
	}

	.drawer-toolbar__summary span {
		font-size: 12px;
		color: rgba(214, 227, 255, 0.56);
	}

	.drawer-toolbar__refresh {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 8px 12px;
		border: 1px solid rgba(122, 218, 255, 0.16);
		border-radius: 999px;
		background: rgba(0, 163, 255, 0.12);
		color: #9fe8ff;
		font-size: 12px;
		font-weight: 600;
		cursor: pointer;
		transition: background 0.2s ease, color 0.2s ease;
	}

	.drawer-toolbar__refresh:hover {
		background: rgba(0, 163, 255, 0.18);
		color: #ffffff;
	}

	.drawer-filters {
		display: grid;
		gap: 12px;
		margin-bottom: 16px;
	}

	.drawer-filter-tabs {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}

	.drawer-filter-tab {
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 6px 10px;
		border: 1px solid rgba(122, 218, 255, 0.14);
		border-radius: 999px;
		background: rgba(8, 20, 38, 0.88);
		color: rgba(214, 227, 255, 0.72);
		font-size: 12px;
		cursor: pointer;
		transition: all 0.2s ease;
	}

	.drawer-filter-tab:hover {
		border-color: rgba(0, 228, 255, 0.22);
		color: #f3fbff;
		transform: translateY(-1px);
	}

	.drawer-filter-tab em {
		font-style: normal;
		font-family: 'Space Grotesk', sans-serif;
		font-size: 11px;
		color: rgba(214, 227, 255, 0.4);
	}

	.drawer-filter-tab--active {
		border-color: rgba(0, 228, 255, 0.28);
		background: rgba(0, 163, 255, 0.14);
		color: #f3fbff;
		box-shadow: 0 10px 22px rgba(0, 163, 255, 0.12);
	}

	.drawer-filter-tab--active em {
		color: #9fe8ff;
	}

	.drawer-list {
		display: grid;
		gap: 12px;
	}

	.drawer-item {
		position: relative;
		overflow: hidden;
		padding: 14px 16px;
		border-radius: 12px;
		background:
			linear-gradient(180deg, rgba(12, 28, 52, 0.92), rgba(7, 18, 34, 0.86)),
			rgba(8, 20, 38, 0.82);
		border: 1px solid rgba(122, 218, 255, 0.12);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02), 0 14px 28px rgba(0, 0, 0, 0.16);
		transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
	}

	.drawer-item::before {
		content: '';
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
		height: 1px;
		background: linear-gradient(90deg, rgba(0, 228, 255, 0), rgba(0, 228, 255, 0.42), rgba(0, 228, 255, 0));
		opacity: 0.82;
	}

	.drawer-item:hover {
		transform: translateY(-2px);
		border-color: rgba(0, 228, 255, 0.22);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.03), 0 18px 34px rgba(0, 0, 0, 0.22);
	}

	.drawer-item__head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 12px;
		margin-bottom: 10px;
	}

	.drawer-item__title {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 8px;
	}

	.drawer-item__head strong {
		font-size: 14px;
		color: #f3fbff;
	}

	.drawer-item__tag {
		display: inline-flex;
		align-items: center;
		padding: 2px 8px;
		border-radius: 999px;
		font-size: 11px;
		font-weight: 700;
		letter-spacing: 0.04em;
	}

	.drawer-item__tag--manual {
		background: rgba(14, 165, 233, 0.16);
		color: #7dd3fc;
	}

	.drawer-item__tag--signal {
		background: rgba(249, 115, 22, 0.16);
		color: #fdba74;
	}

	.drawer-item__tag--query {
		background: rgba(168, 85, 247, 0.16);
		color: #d8b4fe;
	}

	.drawer-item__tag--system {
		background: rgba(100, 116, 139, 0.16);
		color: #cbd5e1;
	}

	.drawer-item__head span,
	.drawer-item__foot {
		font-size: 12px;
		color: rgba(214, 227, 255, 0.5);
	}

	.drawer-item p {
		margin: 0 0 10px;
		font-size: 13px;
		line-height: 1.6;
		color: rgba(230, 244, 255, 0.82);
	}

	.drawer-item__foot {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}

	.empty-panel {
		display: grid;
		place-items: center;
		min-height: 140px;
		border: 1px dashed rgba(122, 218, 255, 0.18);
		border-radius: 12px;
		background: rgba(8, 20, 38, 0.5);
		font-size: 13px;
		color: rgba(214, 227, 255, 0.56);
	}

	.prompt-panel {
		display: grid;
		gap: 16px;
	}

	.prompt-panel__meta {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
	}

	.prompt-panel__meta strong {
		display: block;
		font-size: 15px;
		color: #f3fbff;
	}

	.prompt-panel__meta p {
		margin: 4px 0 0;
		font-size: 12px;
		color: rgba(214, 227, 255, 0.56);
	}

	.prompt-card {
		padding: 16px 18px;
		border-radius: 14px;
		background: rgba(8, 20, 38, 0.82);
		border: 1px solid rgba(122, 218, 255, 0.12);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02);
	}

	.prompt-card h4 {
		margin: 0 0 12px;
		font-size: 13px;
		font-weight: 700;
		letter-spacing: 0.04em;
		color: #f3fbff;
	}

	.prompt-card p {
		margin: 0;
		font-size: 13px;
		line-height: 1.7;
		color: rgba(230, 244, 255, 0.84);
	}

	.prompt-action-list {
		display: grid;
		gap: 10px;
	}

	.prompt-action-item {
		position: relative;
		padding-left: 14px;
		font-size: 13px;
		line-height: 1.6;
		color: rgba(230, 244, 255, 0.84);
	}

	.prompt-action-item::before {
		content: '';
		position: absolute;
		left: 0;
		top: 8px;
		width: 6px;
		height: 6px;
		border-radius: 999px;
		background: #00e4ff;
		box-shadow: 0 0 10px rgba(0, 228, 255, 0.58);
	}

	.prompt-route-list {
		display: grid;
		gap: 12px;
	}

	.prompt-route-item {
		padding: 12px 14px;
		border-radius: 12px;
		background: linear-gradient(180deg, rgba(12, 28, 52, 0.94), rgba(6, 16, 30, 0.88));
		border: 1px solid rgba(122, 218, 255, 0.18);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.02);
	}

	.prompt-route-item__head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		margin-bottom: 8px;
	}

	.prompt-route-item__head strong {
		font-size: 13px;
		color: #f3fbff;
	}

	.prompt-route-item__head span {
		font-size: 12px;
		color: var(--route-color);
		font-weight: 700;
	}

	.prompt-route-item p {
		margin: 0;
		font-size: 12px;
		line-height: 1.6;
		color: rgba(230, 244, 255, 0.76);
	}

	.prompt-route-item__path {
		margin-top: 8px;
		padding-top: 8px;
		border-top: 1px dashed rgba(122, 218, 255, 0.12);
		font-size: 12px;
		font-weight: 700;
		color: #c9f4ff;
	}

	.prompt-template {
		margin: 0;
		padding: 14px 16px;
		border-radius: 12px;
		background: rgba(3, 12, 26, 0.84);
		border: 1px solid rgba(122, 218, 255, 0.12);
		font-family: 'SFMono-Regular', 'Consolas', 'Liberation Mono', monospace;
		font-size: 12px;
		line-height: 1.7;
		white-space: pre-wrap;
		word-break: break-word;
		color: #bdefff;
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

<style lang="less">
	.emergency-log-drawer {
		.ant-drawer-mask {
			background: rgba(2, 8, 23, 0.58);
			backdrop-filter: blur(4px);
		}

		.ant-drawer-content-wrapper {
			box-shadow: -18px 0 42px rgba(0, 0, 0, 0.34);
		}

		.ant-drawer-content {
			position: relative;
			background:
				radial-gradient(circle at top left, rgba(0, 228, 255, 0.1), transparent 28%),
				linear-gradient(180deg, rgba(8, 18, 34, 0.98), rgba(4, 12, 26, 0.98));
			border-left: 1px solid rgba(122, 218, 255, 0.14);
			color: #d6e3ff;
		}

		.ant-drawer-content::before {
			content: '';
			position: absolute;
			inset: 0;
			background:
				linear-gradient(180deg, rgba(255, 255, 255, 0.05), transparent 18%),
				radial-gradient(circle at top right, rgba(0, 163, 255, 0.08), transparent 26%);
			pointer-events: none;
		}

		.ant-drawer-header {
			position: relative;
			z-index: 1;
			padding: 18px 20px 16px;
			background: transparent;
			border-bottom: 1px solid rgba(122, 218, 255, 0.12);
		}

		.ant-drawer-header-title {
			align-items: center;
		}

		.ant-drawer-title {
			display: flex;
			align-items: center;
			gap: 10px;
			font-size: 15px;
			font-weight: 700;
			letter-spacing: 0.08em;
			color: #f3fbff;
		}

		.ant-drawer-title::before {
			content: '';
			width: 7px;
			height: 7px;
			border-radius: 999px;
			background: #00e4ff;
			box-shadow: 0 0 12px rgba(0, 228, 255, 0.72);
			flex: 0 0 auto;
		}

		.ant-drawer-close {
			display: inline-flex;
			align-items: center;
			justify-content: center;
			width: 32px;
			height: 32px;
			margin-inline-end: 0;
			border: 1px solid rgba(122, 218, 255, 0.16);
			border-radius: 999px;
			background: rgba(9, 23, 42, 0.72);
			color: rgba(214, 227, 255, 0.72);
			transition: all 0.2s ease;
		}

		.ant-drawer-close:hover {
			border-color: rgba(0, 228, 255, 0.3);
			background: rgba(0, 163, 255, 0.14);
			color: #ffffff;
		}

		.ant-drawer-body {
			position: relative;
			z-index: 1;
			padding: 18px 20px 20px;
			background: transparent;
			scrollbar-width: thin;
			scrollbar-color: rgba(0, 163, 255, 0.28) rgba(255, 255, 255, 0.04);
		}

		.ant-drawer-body::-webkit-scrollbar {
			width: 6px;
		}

		.ant-drawer-body::-webkit-scrollbar-track {
			background: rgba(255, 255, 255, 0.04);
		}

		.ant-drawer-body::-webkit-scrollbar-thumb {
			background: rgba(0, 163, 255, 0.28);
			border-radius: 999px;
		}
	}

	.ai-prompt-drawer {
		.ant-drawer-mask {
			background: rgba(2, 8, 23, 0.54);
			backdrop-filter: blur(4px);
		}

		.ant-drawer-content-wrapper {
			box-shadow: 18px 0 42px rgba(0, 0, 0, 0.34);
		}

		.ant-drawer-content {
			position: relative;
			background:
				radial-gradient(circle at top right, rgba(0, 228, 255, 0.08), transparent 24%),
				linear-gradient(180deg, rgba(8, 18, 34, 0.98), rgba(4, 12, 26, 0.98));
			border-right: 1px solid rgba(122, 218, 255, 0.14);
			color: #d6e3ff;
		}

		.ant-drawer-content::before {
			content: '';
			position: absolute;
			inset: 0;
			background:
				linear-gradient(180deg, rgba(255, 255, 255, 0.05), transparent 18%),
				radial-gradient(circle at top left, rgba(0, 163, 255, 0.08), transparent 26%);
			pointer-events: none;
		}

		.ant-drawer-header {
			position: relative;
			z-index: 1;
			padding: 18px 20px 16px;
			background: transparent;
			border-bottom: 1px solid rgba(122, 218, 255, 0.12);
		}

		.ant-drawer-header-title {
			align-items: center;
		}

		.ant-drawer-title {
			display: flex;
			align-items: center;
			gap: 10px;
			font-size: 15px;
			font-weight: 700;
			letter-spacing: 0.08em;
			color: #f3fbff;
		}

		.ant-drawer-title::before {
			content: '';
			width: 7px;
			height: 7px;
			border-radius: 999px;
			background: #00e4ff;
			box-shadow: 0 0 12px rgba(0, 228, 255, 0.72);
			flex: 0 0 auto;
		}

		.ant-drawer-close {
			display: inline-flex;
			align-items: center;
			justify-content: center;
			width: 32px;
			height: 32px;
			margin-inline-end: 0;
			border: 1px solid rgba(122, 218, 255, 0.16);
			border-radius: 999px;
			background: rgba(9, 23, 42, 0.72);
			color: rgba(214, 227, 255, 0.72);
			transition: all 0.2s ease;
		}

		.ant-drawer-close:hover {
			border-color: rgba(0, 228, 255, 0.3);
			background: rgba(0, 163, 255, 0.14);
			color: #ffffff;
		}

		.ant-drawer-body {
			position: relative;
			z-index: 1;
			padding: 18px 20px 20px;
			background: transparent;
			scrollbar-width: thin;
			scrollbar-color: rgba(0, 163, 255, 0.28) rgba(255, 255, 255, 0.04);
		}
	}
</style>
