package com.tradingview.lightweightcharts.example.app.api

import retrofit2.Response
import retrofit2.http.*
import kotlinx.coroutines.flow.Flow
import com.google.gson.annotations.SerializedName

/**
 * 한국투자증권 API 서비스 인터페이스
 */
interface KisApiService {

    /**
     * OAuth 토큰 발급
     */
    @POST("oauth2/tokenP")
    suspend fun getAccessToken(
        @Body tokenRequest: TokenRequest
    ): Response<TokenResponse>

    /**
     * 주식 현재가 시세 조회
     */
    @GET("uapi/domestic-stock/v1/quotations/inquire-price")
    suspend fun getCurrentPrice(
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String = "FHKST01010100",
        @Query("FID_COND_MRKT_DIV_CODE") marketCode: String = "J",
        @Query("FID_INPUT_ISCD") stockCode: String
    ): Response<CurrentPriceResponse>

    /**
     * 주식 분봉 차트 조회
     */
    @GET("uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
    suspend fun getMinuteChart(
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String = "FHKST03010200",
        @Query("FID_ETC_CLS_CODE") etcCode: String = "",
        @Query("FID_COND_MRKT_DIV_CODE") marketCode: String = "J",
        @Query("FID_INPUT_ISCD") stockCode: String,
        @Query("FID_INPUT_HOUR_1") timeFrame: String, // 01:1분, 03:3분, 05:5분, 10:10분, 15:15분, 30:30분, 60:60분
        @Query("FID_PW_DATA_INCU_YN") includePreview: String = "Y",
        @Query("FID_INPUT_DATE_1") inquiryDate: String? = null // 조회 날짜 (YYYYMMDD) - 선택적
    ): Response<MinuteChartResponse>

    /**
     * 주식 일봉 차트 조회
     */
    @GET("uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
    suspend fun getDailyChart(
        @Header("authorization") authorization: String,
        @Header("appkey") appKey: String,
        @Header("appsecret") appSecret: String,
        @Header("tr_id") trId: String = "FHKST03010100",
        @Query("FID_COND_MRKT_DIV_CODE") marketCode: String = "J",
        @Query("FID_INPUT_ISCD") stockCode: String,
        @Query("FID_INPUT_DATE_1") startDate: String,
        @Query("FID_INPUT_DATE_2") endDate: String,
        @Query("FID_PERIOD_DIV_CODE") periodCode: String = "D", // D:일봉, W:주봉, M:월봉
        @Query("FID_ORG_ADJ_PRC") orgAdjPrc: String = "1" // 0:수정주가, 1:원주가
    ): Response<DailyChartResponse>
}

/**
 * 토큰 요청 데이터
 */
data class TokenRequest(
    val grant_type: String = "client_credentials",
    val appkey: String,
    val appsecret: String
)

/**
 * 토큰 응답 데이터
 */
data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

/**
 * 현재가 응답 데이터
 */
data class CurrentPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output: CurrentPriceOutput?
)

data class CurrentPriceOutput(
    val stck_prpr: String, // 현재가
    val prdy_vrss: String, // 전일대비
    val prdy_vrss_sign: String, // 전일대비부호
    val prdy_ctrt: String, // 전일대비율
    val acml_vol: String, // 누적거래량
    val acml_tr_pbmn: String, // 누적거래대금
    val stck_oprc: String, // 시가
    val stck_hgpr: String, // 고가
    val stck_lwpr: String, // 저가
    val stck_prdy_clpr: String // 전일종가
)

/**
 * 분봉 차트 응답 데이터
 */
data class MinuteChartResponse(
    @SerializedName("rt_cd") val responseCode: String,
    @SerializedName("msg_cd") val messageCode: String,
    @SerializedName("msg1") val message: String,
    @SerializedName("output1") val metadata: MinuteChartMeta?,
    @SerializedName("output2") val chartData: List<MinuteChartOutput>?
)

data class MinuteChartOutput(
    @SerializedName("stck_bsop_date") val date: String, // 날짜 (YYYYMMDD)
    @SerializedName("stck_cntg_hour") val time: String, // 체결시간 (HHMMSS) - 공식 API 필드명
    @SerializedName("stck_prpr") val closePrice: String, // 현재가 (종가 대신)
    @SerializedName("stck_oprc") val openPrice: String, // 시가
    @SerializedName("stck_hgpr") val highPrice: String, // 고가
    @SerializedName("stck_lwpr") val lowPrice: String, // 저가
    @SerializedName("cntg_vol") val volume: String, // 거래량
    @SerializedName("acml_tr_pbmn") val tradingValue: String // 거래대금
)

data class MinuteChartMeta(
    @SerializedName("kor_isnm") val koreanStockName: String // 한글종목명
)

/**
 * 일봉 차트 응답 데이터 (공식 예제 기준)
 */
data class DailyChartResponse(
    @SerializedName("rt_cd") val rt_cd: String,
    @SerializedName("msg_cd") val msg_cd: String,
    @SerializedName("msg1") val msg1: String,
    @SerializedName("output1") val output1: DailyChartMeta?,
    @SerializedName("output2") val output2: List<DailyChartOutput>?
)

data class DailyChartMeta(
    @SerializedName("prdy_vrss") val prdy_vrss: String?, // 전일 대비
    @SerializedName("prdy_vrss_sign") val prdy_vrss_sign: String?, // 전일 대비 부호  
    @SerializedName("prdy_ctrt") val prdy_ctrt: String?, // 전일 대비율
    @SerializedName("hts_kor_isnm") val hts_kor_isnm: String? // HTS 한글 종목명
)

data class DailyChartOutput(
    @SerializedName("stck_bsop_date") val stck_bsop_date: String, // 날짜
    @SerializedName("stck_clpr") val stck_clpr: String, // 종가
    @SerializedName("stck_oprc") val stck_oprc: String, // 시가
    @SerializedName("stck_hgpr") val stck_hgpr: String, // 고가
    @SerializedName("stck_lwpr") val stck_lwpr: String, // 저가
    @SerializedName("acml_vol") val acml_vol: String, // 누적거래량
    @SerializedName("acml_tr_pbmn") val acml_tr_pbmn: String, // 누적거래대금
    @SerializedName("flng_cls_code") val flng_cls_code: String, // 등락구분코드
    @SerializedName("prtt_rate") val prtt_rate: String, // 분할비율
    @SerializedName("mod_yn") val mod_yn: String // 수정주가반영여부
)