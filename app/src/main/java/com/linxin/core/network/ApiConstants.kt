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

    /** 抓包里安小信打开完美校园时用的学校标识（light.action 的 flag 参数） */
    const val ECARD_FLAG = "ahxx_ecardh5_1000538"

    // FIF AI课堂
    const val BASE_FIF_SSO = "https://aiitpass.fifedu.com"
    const val BASE_FIF = "https://sttp.fifedu.com"

    // 爱作业平台
    const val BASE_IZUOYE = "https://izuoye.fifedu.com"

    const val APP_ID = "11111111111111111111111111111111"
}
