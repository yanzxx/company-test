import { baseRequest } from '@/utils/request'

const request = (url, ...arg) => baseRequest(`/biz/emergency/drill/${url}`, ...arg)

/**
 * 应急演练大屏接口
 *
 * @author Codex
 * @date 2026-03-19
 */
export default {
	// 获取模拟POI
	getMockPoiList(data) {
		return request('poi/mockData', data, 'get')
	},
	// 查询圆形范围内POI
	queryInRange(data) {
		return request('poi/queryInRange', data)
	},
	// 获取当前灾情态势
	getDisasterState(data) {
		return request('disaster/state', data, 'get')
	},
	// 更新受灾半径
	updateRadius(data) {
		return request('disaster/updateRadius', data)
	},
	// 获取演练日志
	getLogList(data) {
		return request('log/list', data, 'get')
	},
	// 记录演练日志
	recordLog(data) {
		return request('log/record', data)
	}
}
