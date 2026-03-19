<template>
	<div class="storm-login-page">
		<div class="storm-login-page__bg"></div>
		<div class="storm-login-page__overlay"></div>
		<div class="storm-login-page__glow storm-login-page__glow--left"></div>
		<div class="storm-login-page__glow storm-login-page__glow--right"></div>
		<div class="storm-login-page__flash" :class="{ 'is-active': flashActive }"></div>
		<svg class="storm-login-page__lightning" viewBox="0 0 1000 1000" preserveAspectRatio="none">
			<polyline
				v-for="(path, index) in lightningPaths"
				:key="`${path}-${index}`"
				:points="path"
				class="storm-login-page__bolt"
			/>
		</svg>

		<div class="login_config">
			<a-dropdown placement="bottomRight">
				<div class="storm-lang-switch">
					<global-outlined />
					<span>{{ config.lang === 'en' ? 'EN' : '中' }}</span>
				</div>
				<template #overlay>
					<a-menu>
						<a-menu-item
							v-for="item in lang"
							:key="item.value"
							:command="item"
							:class="{ selected: config.lang === item.value }"
							@click="configLang(item.value)"
						>
							{{ item.name }}
						</a-menu-item>
					</a-menu>
				</template>
			</a-dropdown>
		</div>

		<div class="storm-login-shell">
			<section class="storm-login-hero">
				<div class="storm-login-hero__badge">
					<span class="storm-login-hero__badge-dot"></span>
					{{ sysBaseConfig.SNOWY_SYS_NAME || 'GAGO FAST' }} · 雷暴推演控制台
				</div>
				<h1 class="storm-login-hero__title">
					<span>自然灾害</span>
					<span>预警模拟系统</span>
				</h1>
				<div class="storm-login-hero__accent"></div>
				<p class="storm-login-hero__desc">
					面向雷暴、积涝与强对流等场景的监测预警与应急演练平台，支持风险联动、态势研判和应急处置模拟。
				</p>
				<div class="storm-login-hero__meta">
					<span>雷暴推演</span>
					<span>城市风控</span>
					<span>实时态势</span>
				</div>
			</section>

			<section class="storm-login-card">
				<div class="storm-login-card__header">
					<div class="storm-login-card__eyebrow">
						<span class="storm-login-card__eyebrow-line"></span>
						系统授权验证
					</div>
					<!-- <p class="storm-login-card__subtitle">请输入操作员凭据以进入模拟环境</p> -->
				</div>

				<div class="storm-login-switch">
					<button
						type="button"
						:class="{ active: activeKey === 'userAccount' }"
						@click="handleModeChange('userAccount')"
					>
						{{ $t('login.accountPassword') }}
					</button>
					<button
						type="button"
						:class="{ active: activeKey === 'userSms' }"
						@click="handleModeChange('userSms')"
					>
						{{ $t('login.phoneSms') }}
					</button>
				</div>

				<div v-if="activeKey === 'userAccount'" class="storm-login-pane">
					<a-form ref="loginForm" :model="ruleForm" :rules="rules" class="storm-login-form">
						<div class="storm-login-field">
							<label class="storm-login-field__label">用户名</label>
							<a-form-item name="account">
								<a-input
									v-model:value="ruleForm.account"
									:placeholder="$t('login.accountPlaceholder')"
									allow-clear
									size="large"
									@keyup.enter="login"
								>
									<template #prefix>
										<UserOutlined class="login-icon-gray" />
									</template>
								</a-input>
							</a-form-item>
						</div>

						<div class="storm-login-field">
							<label class="storm-login-field__label">密码</label>
							<a-form-item name="password">
								<a-input-password
									v-model:value="ruleForm.password"
									:placeholder="$t('login.PWPlaceholder')"
									autocomplete="off"
									size="large"
									@keyup.enter="login"
								>
									<template #prefix>
										<LockOutlined class="login-icon-gray" />
									</template>
								</a-input-password>
							</a-form-item>
						</div>

						<div v-if="captchaOpen === 'true'" class="storm-login-field">
							<label class="storm-login-field__label">图形验证码</label>
							<a-form-item name="validCode">
								<a-row :gutter="10">
									<a-col :span="16">
										<a-input
											v-model:value="ruleForm.validCode"
											:placeholder="$t('login.validLaceholder')"
											size="large"
											@keyup.enter="login"
										>
											<template #prefix>
												<verified-outlined class="login-icon-gray" />
											</template>
										</a-input>
									</a-col>
									<a-col :span="8">
										<img :src="validCodeBase64" class="login-validCode-img" @click="loginCaptcha" />
									</a-col>
								</a-row>
							</a-form-item>
						</div>

						<div class="storm-login-links">
							<a href="/findpwd" class="storm-login-link">{{ $t('login.forgetPassword') }}？</a>
							<!-- <a href="#" class="storm-login-link" @click.prevent="ssoLogin">使用单点登录</a> -->
						</div>

						<a-form-item class="storm-login-submit">
							<a-button
								type="primary"
								block
								class="storm-login-submit__btn"
								:loading="loading"
								size="large"
								@click="login"
							>
								授权访问
							</a-button>
						</a-form-item>
					</a-form>
				</div>

				<div v-else class="storm-login-pane storm-login-pane--sms">
					<phone-login-form />
					<div class="storm-login-links storm-login-links--compact">
						<a href="/findpwd" class="storm-login-link">{{ $t('login.forgetPassword') }}？</a>
						<a href="#" class="storm-login-link" @click.prevent="ssoLogin">使用单点登录</a>
					</div>
				</div>

				<div class="storm-login-card__footer">
					<span>安全等级: 5 级</span>
					<span>加密连接已激活</span>
				</div>
			</section>
		</div>
	</div>
</template>

<script>
	import loginApi from '@/api/auth/loginApi'
	import phoneLoginForm from './phoneLoginForm.vue'
	import smCrypto from '@/utils/smCrypto'
	import { required } from '@/utils/formRules'
	import { afterLogin } from './util'
	import config from '@/config'
	import configApi from '@/api/dev/configApi'
	import tool from '@/utils/tool'
	import { globalStore, iframeStore, keepAliveStore, viewTagsStore } from '@/store'
	import { mapActions, mapState } from 'pinia'
	import ssoCasApi from '@/api/auth/ssoCasApi'

	export default {
		name: 'Login',
		components: {
			phoneLoginForm
		},
		data() {
			return {
				activeKey: 'userAccount',
				captchaOpen: config.SYS_BASE_CONFIG.SNOWY_SYS_DEFAULT_CAPTCHA_OPEN,
				validCodeBase64: '',
				flashActive: false,
				lightningPaths: [],
				lightningTimer: null,
				lightningEchoTimer: null,
				lightningFlashTimer: null,
				lightningCleanupTimer: null,
				ruleForm: {
					account: '',
					password: '',
					validCode: '',
					validCodeReqNo: '',
					autologin: false
				},
				rules: {
					account: [required(this.$t('login.accountError'), 'blur')],
					password: [required(this.$t('login.PWError'), 'blur')]
				},
				loading: false,
				config: {
					lang: tool.data.get('APP_LANG') || this.$CONFIG.LANG,
					theme: tool.data.get('APP_THEME') || 'default'
				},
				lang: [
					{
						name: '简体中文',
						value: 'zh-cn'
					},
					{
						name: 'English',
						value: 'en'
					}
				]
			}
		},
		computed: {
			...mapState(globalStore, ['sysBaseConfig'])
		},
		watch: {
			'config.theme': function (val) {
				document.body.setAttribute('data-theme', val)
			},
			'config.lang': function (val) {
				this.$i18n.locale = val
				tool.data.set('APP_LANG', val)
			}
		},
		created() {
			this.clearViewTags()
			this.clearKeepLive()
			this.clearIframeList()
		},
		mounted() {
			this.startLightningStorm()
			const formData = { ...config.SYS_BASE_CONFIG }

			configApi
				.configSysBaseList()
				.then((data) => {
					if (data) {
						data.forEach((item) => {
							formData[item.configKey] = item.configValue
						})
					}
				})
				.finally(() => {
					this.applyBaseConfig(formData)
				})
		},
		beforeUnmount() {
			this.stopLightningStorm()
		},
		methods: {
			...mapActions(keepAliveStore, ['clearKeepLive']),
			...mapActions(viewTagsStore, ['clearViewTags']),
			...mapActions(iframeStore, ['clearIframeList']),
			...mapActions(globalStore, ['setSysBaseConfig']),
			applyBaseConfig(formData) {
				this.captchaOpen = formData.SNOWY_SYS_DEFAULT_CAPTCHA_OPEN
				tool.data.set('SNOWY_SYS_BASE_CONFIG', formData)
				this.setSysBaseConfig(formData)
				this.refreshSwitch()
			},
			handleModeChange(key) {
				this.activeKey = key
				if (key === 'userAccount' && this.captchaOpen === 'true' && !this.validCodeBase64) {
					this.loginCaptcha()
				}
			},
			// 通过开关加载内容
			refreshSwitch() {
				if (this.captchaOpen === 'true') {
					this.loginCaptcha()
					this.rules.validCode = [required(this.$t('login.validError'), 'blur')]
				} else {
					delete this.rules.validCode
				}
			},
			startLightningStorm() {
				this.stopLightningStorm()
				this.scheduleLightning()
			},
			stopLightningStorm() {
				;['lightningTimer', 'lightningEchoTimer', 'lightningFlashTimer', 'lightningCleanupTimer'].forEach((timerKey) => {
					if (this[timerKey]) {
						window.clearTimeout(this[timerKey])
						this[timerKey] = null
					}
				})
				this.flashActive = false
				this.lightningPaths = []
			},
			scheduleLightning() {
				this.lightningTimer = window.setTimeout(() => {
					this.createLightning()
					if (Math.random() > 0.72) {
						this.lightningEchoTimer = window.setTimeout(() => {
							this.createLightning(true)
						}, 140)
					}
					this.scheduleLightning()
				}, Math.random() * 4200 + 2600)
			},
			createLightning(isEcho = false) {
				const points = []
				let curX = 120 + Math.random() * 760
				let curY = 0

				while (curY < 1000) {
					points.push(`${Math.round(curX)},${Math.round(curY)}`)
					curX = Math.max(40, Math.min(960, curX + (Math.random() - 0.5) * 130))
					curY += Math.random() * 110 + 70
				}

				this.lightningPaths = [points.join(' ')]
				this.flashActive = true

				if (this.lightningFlashTimer) {
					window.clearTimeout(this.lightningFlashTimer)
				}
				if (this.lightningCleanupTimer) {
					window.clearTimeout(this.lightningCleanupTimer)
				}

				this.lightningFlashTimer = window.setTimeout(() => {
					this.flashActive = false
				}, isEcho ? 90 : 140)
				this.lightningCleanupTimer = window.setTimeout(() => {
					this.lightningPaths = []
				}, isEcho ? 170 : 260)
			},
			// 获取验证码
			loginCaptcha() {
				loginApi.getPicCaptcha().then((data) => {
					this.validCodeBase64 = data.validCodeBase64
					this.ruleForm.validCodeReqNo = data.validCodeReqNo
				})
			},
			// 用户名密码登录
			async login() {
				this.$refs.loginForm.validate().then(async () => {
					this.loading = true
					const loginData = {
						account: this.ruleForm.account,
						password: smCrypto.doSm2Encrypt(this.ruleForm.password),
						validCode: this.ruleForm.validCode,
						validCodeReqNo: this.ruleForm.validCodeReqNo
					}
					try {
						const loginToken = await loginApi.login(loginData)
						await afterLogin(loginToken)
					} catch (err) {
						this.loading = false
						if (this.captchaOpen === 'true') {
							this.loginCaptcha()
						}
					}
				})
			},
			configLang(key) {
				this.config.lang = key
			},
			// 单点登录
			async ssoLogin() {
				try {
					const url = await ssoCasApi.getSSOLoginRedirectUrl()
					if (url) {
						window.location.href = url
					} else {
						this.$message.error('获取单点登录地址失败')
					}
				} catch (error) {
					console.error('SSO登录失败:', error)
					this.$message.error('单点登录服务暂不可用，请稍后重试')
				}
			}
		}
	}
</script>

<style lang="less">
	@import 'login';
</style>
