package com.linxin.core.network

object ApiConstants {
    const val BASE_MAIN = "https://in.aiit.edu.cn"
    const val BASE_AUTH = "$BASE_MAIN/zhxy-information"
    const val BASE_SCPS = "$BASE_MAIN/zhxy-new-scps"
    const val BASE_CSH = "$BASE_MAIN/zhxy-csh"
    const val BASE_CHECKIN = "https://fdygl.aiit.edu.cn"
    const val BASE_SPORTS = "http://sports.aiit.edu.cn:8082"
    const val BASE_LABOR = "https://ldjy.aiit.edu.cn"

    // 素质学分
    const val BASE_CREDIT = "https://cqc.aiit.edu.cn"

    // 报修平台（H5 在 /dist-app/，接口挂在根路径 /bxjlSjd/ 下）
    const val BASE_REPAIR = "https://repair.aiit.edu.cn"

    // 请假等审批流（业务流转引擎）。H5 页面在 /mobile/，接口挂在 /zhxy-bfc/mobile/ 下
    const val BASE_LEAVE = "https://ywlz.aiit.edu.cn/zhxy-bfc"

    // 完美校园 / 一卡通：换会话跨三个子域，业务调用都落在 ecardh5
    const val BASE_WANXIAO_HUB = "https://hub.17wanxiao.com"
    const val BASE_ECARD_H5 = "https://ecardh5.17wanxiao.com"

    /**
     * 完美校园自己的网页收银台。`pay` 回 `ecardh5type==2` 时，它 H5 调的 `epaySdk.callPay`
     * 其实就是把 orderInfo 拼成这个地址然后 `location.href` 过去（那份 SDK 在
     * `wapnew.17wanxiao.com` 上，里面没有任何原生桥，只有 cookie + 跳转），
     * 所以我们在 app 内的 WebView 里打开同一个地址就是等价行为。
     */
    const val BASE_WAP_CASHIER = "https://wapnew.17wanxiao.com/WapCashDesk/e-pay"

    /** 抓包里安小信打开完美校园时用的学校标识（light.action 的 flag 参数） */
    const val ECARD_FLAG = "ahxx_ecardh5_1000538"

    /**
     * 「学生请假申请」的流程 id，来自 2026-10-08 抓的 37 项服务清单里那条入口 URL
     * （ywlz.aiit.edu.cn/mobile/#/create?processId=…）。
     */
    const val LEAVE_PROCESS_ID = "e77db6c3ae0a4911a42280f98ce45ebc"

    // FIF AI课堂
    const val BASE_FIF_SSO = "https://aiitpass.fifedu.com"
    const val BASE_FIF = "https://sttp.fifedu.com"

    // 爱作业平台
    const val BASE_IZUOYE = "https://izuoye.fifedu.com"

    const val APP_ID = "11111111111111111111111111111111"
}
