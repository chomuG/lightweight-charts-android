package com.tradingview.lightweightcharts.example.app.repository

import com.tradingview.lightweightcharts.api.series.models.*
import com.tradingview.lightweightcharts.example.app.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
// import com.tradingview.lightweightcharts.example.app.database.ChartDataDao
// import com.tradingview.lightweightcharts.example.app.database.ChartDataConverter

@Singleton
class KisRepository @Inject constructor(
    private val apiService: KisApiService,
    // private val chartDataDao: ChartDataDao, // Room 문제로 임시 비활성화
    @ApplicationContext private val context: Context
) {

    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0L // 토큰 만료 시간
    private val tokenMutex = Mutex() // 토큰 발급 동시성 제어
    
    // SharedPreferences for token persistence
    private val tokenPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("kis_token", Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_TOKEN_EXPIRE_TIME = "token_expire_time"
    }
    
    // 한국투자증권 API 설정 (모의투자 키 - 실제 사용 시 운영 키로 교체 필요)
    private val appKey = "PSwIa25iUQ1nrCs1REa08PDXjNdJWenYDfZ3" 
    private val appSecret = "CfNNHB5JCMRw7v5XtYcq8IjHB9AVuM5DEeJ53eZe5lgkTDwsLKq3llJ1C0q5LbOW+htbioNOIFLIeN7+FYp5A31t1W/ZRzr4qcWXgAZYT/NhMEAEfVn+hexk5Tbg90R+j735lxrVErNwvyVHYkor9gVupsmVLULwS3SQj9gBR31WcXaPWY4="
    
    // API 요청 제한: 한국투자증권 모의투자 API는 초당 2회 조회 가능
    private var lastRequestTime = 0L
    private val minRequestInterval = 500L // 0.5초 간격 (초당 2회)
    private var cachedChartData: List<CandlestickData>? = null
    private var cachedVolumeData: List<HistogramData>? = null
    private var cacheExpireTime = 0L
    private val cacheValidDuration = 30000L // 30초간 캐시 유효

    // 종목별, 시간프레임별 데이터 캐시 관리
    private val chartDataCache = mutableMapOf<String, List<CandlestickData>>()
    private val volumeDataCache = mutableMapOf<String, List<HistogramData>>()
    private val lastDataFetchTimeCache = mutableMapOf<String, Long>()
    private val DATA_CACHE_DURATION = 30_000L // 30초 캐시
    
    // 현재 종목 코드 (종목 변경 시 캐시 초기화용)
    private var currentStockCode: String? = null
    
    // 현재 시간프레임 (데이터 변환 시 사용)
    private var currentTimeFrame: String = "01"

    // 실시간 업데이트를 위한 상태 추적
    private var lastKnownTimestamp: Long = 0L
    private var isInitialDataSent = false

    /**
     * 실시간 업데이트 상태 초기화 (종목 변경 시 호출)
     */
    fun resetRealTimeState() {
        Log.d("KisAPI", "실시간 업데이트 상태 초기화")
        lastKnownTimestamp = 0L
        isInitialDataSent = false
        currentTimeFrame = "01" // 기본값으로 초기화
    }
    
    /**
     * 저장된 토큰 로드
     */
    private fun loadTokenFromPrefs() {
        accessToken = tokenPrefs.getString(PREF_ACCESS_TOKEN, null)
        tokenExpireTime = tokenPrefs.getLong(PREF_TOKEN_EXPIRE_TIME, 0L)
        
        Log.d("KisAPI", "저장된 토큰 로드: ${if (accessToken != null) "토큰 있음" else "토큰 없음"}")
        if (accessToken != null) {
            val remainingTime = (tokenExpireTime - System.currentTimeMillis()) / 1000
            Log.d("KisAPI", "토큰 남은 시간: ${remainingTime}초")
        }
    }
    
    /**
     * 토큰을 SharedPreferences에 저장
     */
    private fun saveTokenToPrefs(token: String, expireTime: Long) {
        tokenPrefs.edit()
            .putString(PREF_ACCESS_TOKEN, token)
            .putLong(PREF_TOKEN_EXPIRE_TIME, expireTime)
            .apply()
        Log.d("KisAPI", "토큰 저장 완료 - 만료시간: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(expireTime))}")
    }

    /**
     * 토큰 발급 (1분당 1회 제한으로 토큰 재사용, Mutex로 동시성 제어)
     */
    private suspend fun getAccessToken(): String {
        return tokenMutex.withLock {
            val currentTime = System.currentTimeMillis()
            
            // 메모리에 토큰이 없으면 저장된 토큰 로드
            if (accessToken == null || tokenExpireTime == 0L) {
                loadTokenFromPrefs()
            }
            
            // 토큰이 있고 아직 만료되지 않은 경우 재사용
            if (accessToken != null && currentTime < tokenExpireTime) {
                Log.d("KisAPI", "기존 토큰 재사용 (남은 시간: ${(tokenExpireTime - currentTime)/1000}초)")
                return@withLock accessToken!!
            }
            
            // 토큰이 없거나 만료된 경우에만 새로 발급
            Log.d("KisAPI", "새 토큰 발급 시작")
            
            val tokenRequest = TokenRequest(
                appkey = appKey,
                appsecret = appSecret
            )
            
            val response = apiService.getAccessToken(tokenRequest)
            
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                val token = tokenResponse.access_token
                accessToken = "Bearer $token"
                
                // 토큰 만료 시간 설정 (응답에서 받은 만료시간 - 60초 여유)
                val expiresIn = tokenResponse.expires_in.toLong()
                tokenExpireTime = currentTime + (expiresIn - 60) * 1000L // 60초 여유
                
                // 토큰을 SharedPreferences에 저장
                saveTokenToPrefs(accessToken!!, tokenExpireTime)
                
                Log.d("KisAPI", "토큰 발급 성공!")
            } else {
                Log.e("KisAPI", "토큰 발급 실패: ${response.code()}")
                throw Exception("토큰 발급 실패: ${response.code()} - ${response.message()}")
            }
            
            return@withLock accessToken!!
        }
    }

    /**
     * 분봉 차트 데이터 가져오기 (한국투자증권 API 제한: 당일만, 최대 30개씩)
     */
    suspend fun getMinuteChartData(
        stockCode: String, 
        timeFrame: String = "01"
    ): List<CandlestickData> {
        currentTimeFrame = timeFrame
        return try {
            Log.d("KisAPI", "📊 당일 분봉 데이터 요청: $stockCode, $timeFrame (한국투자증권 API)")
            
            val cacheKey = "${stockCode}_${timeFrame}_today"
            val currentTime = System.currentTimeMillis()
            
            // 캐시 확인 (30초 캐시)
            val cachedData = chartDataCache[cacheKey]
            val lastFetchTime = lastDataFetchTimeCache[cacheKey] ?: 0L
            
            if (cachedData != null && (currentTime - lastFetchTime) < DATA_CACHE_DURATION) {
                Log.d("KisAPI", "📦 캐시된 데이터 반환: ${cachedData.size}개")
                return cachedData
            }
            
            // 한국투자증권 API: 30개씩 여러 번 호출하여 당일 전체 데이터 수집
            val allTodayData = mutableListOf<CandlestickData>()
            val maxAttempts = 15 // 15:30부터 09:00까지 30분씩 (최대 13-14회)
            var attempts = 0
            
            // 현재 시간부터 역순으로 30분씩 조회
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val currentMinute = java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
            
            // 장 시간 확인 (9:00-15:30)
            val startTime = if (currentHour >= 15 || (currentHour == 15 && currentMinute >= 30)) {
                "153000" // 장 마감 시간
            } else if (currentHour >= 9) {
                String.format("%02d%02d00", currentHour, currentMinute) // 현재 시간
            } else {
                "153000" // 장 전이면 전일 마감 시간
            }
            
            Log.d("KisAPI", "⏰ 시작 시간: $startTime (현재: ${currentHour}:${currentMinute})")
            
            while (attempts < maxAttempts) {
                try {
                    // API 호출 제한 준수
                    if (attempts > 0) {
                        delay(500) // 0.5초 대기 (초당 2회 제한)
                    }
                    
                    val token = getAccessToken()
                    val response = apiService.getMinuteChart(
                        authorization = token,
                        appKey = appKey,
                        appSecret = appSecret,
                        stockCode = stockCode,
                        timeFrame = timeFrame,
                        inquiryDate = null // 당일 데이터
                    )
                    
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        Log.d("KisAPI", "📥 API 응답 #${attempts + 1}: responseCode=${responseBody?.responseCode}, 데이터=${responseBody?.chartData?.size}개")
                        
                        if (responseBody?.chartData?.isNotEmpty() == true) {
                            val chartDataList = responseBody.chartData!!
                            
                            val batchData = chartDataList.mapIndexedNotNull { index, data -> 
                                try {
                                    convertToCandlestickData(data, index, timeFrame)
                                } catch (e: Exception) {
                                    Log.e("KisAPI", "❌ 데이터 변환 실패: ${e.message}")
                                    null
                                }
                            }
                            
                            if (batchData.isNotEmpty()) {
                                allTodayData.addAll(batchData)
                                Log.d("KisAPI", "✅ 배치 #${attempts + 1}: ${batchData.size}개 추가 (총 ${allTodayData.size}개)")
                            }
                            
                            // 30개 미만이면 더 이상 데이터 없음
                            if (chartDataList.size < 30) {
                                Log.d("KisAPI", "📝 마지막 배치 (${chartDataList.size}개 < 30)")
                                break
                            }
                        } else {
                            Log.d("KisAPI", "📝 빈 응답 - 데이터 수집 완료")
                            break
                        }
                    } else {
                        Log.w("KisAPI", "⚠️ API 호출 실패: ${response.code()}")
                        break
                    }
                    
                    attempts++
                } catch (e: Exception) {
                    Log.e("KisAPI", "❌ 배치 #${attempts + 1} 실패: ${e.message}")
                    break
                }
            }
            
            // 중복 제거 및 시간 순 정렬
            val sortedData = allTodayData.distinctBy { 
                (it.time as Time.Utc).timestamp 
            }.sortedBy { 
                (it.time as Time.Utc).timestamp 
            }
            
            // 시간 순서 검증
            validateTimeSequence(sortedData)
            
            // 캐시 저장
            chartDataCache[cacheKey] = sortedData
            lastDataFetchTimeCache[cacheKey] = currentTime
            
            Log.d("KisAPI", "✅ 당일 분봉 데이터 수집 완료: ${sortedData.size}개 (${attempts}회 API 호출)")
            
            if (sortedData.isNotEmpty()) {
                val firstTime = sortedData.first().time
                val lastTime = sortedData.last().time
                Log.d("KisAPI", "📅 시간 범위: $firstTime ~ $lastTime")
            }
            
            return sortedData
            
        } catch (e: Exception) {
            Log.e("KisAPI", "❌ 분봉 데이터 가져오기 실패: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * 시간 순서 검증 (TradingView 요구사항)
     */
    private fun validateTimeSequence(candleData: List<CandlestickData>) {
        if (candleData.size < 2) return

        for (i in 1 until candleData.size) {
            val prevTime = (candleData[i-1].time as Time.Utc).timestamp
            val currTime = (candleData[i].time as Time.Utc).timestamp
            
            if (prevTime >= currTime) {
                Log.w("KisAPI", "⚠️ 시간 순서 위반 감지: index=${i-1}->${i}, time=$prevTime->$currTime")
                Log.w("KisAPI", "TradingView는 이전 값보다 작은 시간을 허용하지 않습니다!")
            }
        }
        
        Log.d("KisAPI", "✅ 시간 순서 검증 완료: ${candleData.size}개 데이터")
    }
    
    /**
     * API 호출 제한 준수
     */
    private suspend fun enforceApiRateLimit() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastRequest = currentTime - lastRequestTime
        
        if (timeSinceLastRequest < minRequestInterval) {
            val delayTime = minRequestInterval - timeSinceLastRequest
            Log.d("KisAPI", "API 호출 제한 - ${delayTime}ms 대기")
            delay(delayTime)
        }
        
        lastRequestTime = System.currentTimeMillis()
    }

    /**
     * 2주치 분봉 데이터 수집 (시간 순서 보장)
     */
    suspend fun getMinuteChartDataForTwoWeeks(
        stockCode: String, 
        timeFrame: String = "01"
    ): List<CandlestickData> {
        return try {
            Log.d("KisAPI", "2주치 분봉 데이터 수집 시작: $stockCode, timeFrame: $timeFrame")
            
            val allData = mutableListOf<CandlestickData>()
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            
            // 2주 전부터 현재까지의 데이터 수집
            val daysToCollect = 14 // 2주
            var successfulDays = 0
            
            for (daysBack in daysToCollect downTo 0) {
                try {
                    // API 호출 제한 준수 (0.5초 대기)
                    if (successfulDays > 0) {
                        delay(500)
                    }
                    
                    // 날짜 계산
                    calendar.time = Date()
                    calendar.add(Calendar.DAY_OF_MONTH, -daysBack)
                    val targetDate = dateFormat.format(calendar.time)
                    
                    // 주말 제외 (토요일: 7, 일요일: 1)
                    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                        Log.d("KisAPI", "주말 제외: $targetDate")
                        continue
                    }
                    
                    Log.d("KisAPI", "날짜별 데이터 요청: $targetDate")
                    
                    // 해당 날짜의 분봉 데이터 요청
                    val dailyData = getMinuteChartDataForDate(stockCode, timeFrame, targetDate)
                    if (dailyData.isNotEmpty()) {
                        allData.addAll(dailyData)
                        successfulDays++
                        Log.d("KisAPI", "$targetDate 데이터 추가: ${dailyData.size}개")
                    }
                    
                    // 최대 5일 성공하면 충분
                    if (successfulDays >= 5) break
                    
                } catch (e: Exception) {
                    Log.e("KisAPI", "날짜별 데이터 수집 실패 (${daysBack}일 전): ${e.message}")
                }
            }
            
            // 중복 제거 및 시간 순 정렬
            val sortedData = allData.distinctBy { 
                (it.time as Time.Utc).timestamp 
            }.sortedBy { 
                (it.time as Time.Utc).timestamp 
            }
            
            // 시간 순서 검증
            validateTimeSequence(sortedData)
            
            Log.d("KisAPI", "2주치 분봉 데이터 수집 완료: ${sortedData.size}개")
            
            if (sortedData.size < 50) {
                Log.w("KisAPI", "데이터 부족, 기본 데이터로 보완")
                val fallbackData = getMinuteChartData(stockCode, timeFrame)
                val combinedData = (sortedData + fallbackData).distinctBy { 
                    (it.time as Time.Utc).timestamp 
                }.sortedBy { 
                    (it.time as Time.Utc).timestamp 
                }
                validateTimeSequence(combinedData)
                return combinedData
            }
            
            sortedData
            
        } catch (e: Exception) {
            Log.e("KisAPI", "2주치 분봉 데이터 수집 실패: ${e.message}", e)
            // fallback으로 기본 분봉 데이터 반환
            getMinuteChartData(stockCode, timeFrame)
        }
    }
    
    
    /**
     * 특정 날짜의 분봉 데이터 가져오기 (시간 순서 보장)
     */
    private suspend fun getMinuteChartDataForDate(
        stockCode: String, 
        timeFrame: String,
        date: String
    ): List<CandlestickData> {
        return try {
            Log.d("KisAPI", "특정 날짜 분봉 데이터 요청: $stockCode, $date, $timeFrame")
            
            enforceApiRateLimit()
            
            val token = getAccessToken()
            val response = apiService.getMinuteChart(
                authorization = token,
                appKey = appKey,
                appSecret = appSecret,
                stockCode = stockCode,
                timeFrame = timeFrame,
                inquiryDate = date
            )
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                Log.d("KisAPI", "📅 $date API 응답: responseCode=${responseBody?.responseCode}, 데이터 개수=${responseBody?.chartData?.size}")
                
                if (responseBody?.chartData?.isNotEmpty() == true) {
                    val chartDataList = responseBody.chartData!!
                    
                    // 날짜별 데이터 샘플 로깅
                    if (chartDataList.isNotEmpty()) {
                        Log.d("KisAPI", "📊 $date 첫 번째 데이터: ${chartDataList.first()}")
                    }
                    
                    val convertedData = chartDataList.mapIndexedNotNull { index, data ->
                        try {
                            convertToCandlestickData(data, index, timeFrame, date)
                        } catch (e: Exception) {
                            Log.e("KisAPI", "❌ $date 데이터 변환 실패 (index=$index): ${e.message}")
                            Log.e("KisAPI", "❌ 실패한 데이터: $data")
                            null
                        }
                    }
                    
                    val sortedData = convertedData.sortedBy { 
                        (it.time as Time.Utc).timestamp 
                    }
                    
                    // 시간 순서 검증
                    validateTimeSequence(sortedData)
                    
                    Log.d("KisAPI", "$date 분봉 데이터 로드 완료: ${sortedData.size}개")
                    return sortedData
                }
            }
            
            Log.w("KisAPI", "$date 분봉 데이터 로드 실패")
            emptyList()
            
        } catch (e: Exception) {
            Log.e("KisAPI", "$date 분봉 데이터 가져오기 실패: ${e.message}", e)
            emptyList()
        }
    }
    

    /**
     * 일봉 차트 데이터 가져오기
     */
    suspend fun getDailyChartData(
        stockCode: String,
        days: Int = 200
    ): List<CandlestickData> {
        return try {
            Log.d("KisAPI", "📊 일봉 데이터 요청: $stockCode, ${days}일")
            
            // API 호출 제한 준수
            enforceApiRateLimit()
            
            val token = getAccessToken()
            val endDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
            val startDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(
                Date(System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L))
            )
            
            Log.d("KisAPI", "📊 일봉 API 호출: startDate=$startDate, endDate=$endDate, token=${token.take(20)}...")
            
            val response = apiService.getDailyChart(
                authorization = token,
                appKey = appKey,
                appSecret = appSecret,
                stockCode = stockCode,
                startDate = startDate,
                endDate = endDate
            )

            Log.d("KisAPI", "📥 일봉 API 응답: success=${response.isSuccessful}, code=${response.code()}")

            if (response.isSuccessful) {
                val responseBody = response.body()
                
                if (responseBody == null) {
                    Log.e("KisAPI", "❌ 일봉 응답 body가 null입니다")
                    
                    // Raw 응답 확인
                    try {
                        val errorBody = response.errorBody()?.string() ?: "에러 본문 없음"
                        Log.e("KisAPI", "❌ 에러 본문: $errorBody")
                    } catch (e: Exception) {
                        Log.e("KisAPI", "❌ 에러 본문 읽기 실패: ${e.message}")
                    }
                } else {
                    Log.d("KisAPI", "📊 일봉 응답 상세: rt_cd='${responseBody.rt_cd}', msg_cd='${responseBody.msg_cd}', msg1='${responseBody.msg1}'")
                    Log.d("KisAPI", "📊 일봉 output1: ${responseBody.output1}")
                    Log.d("KisAPI", "📊 일봉 output2: ${responseBody.output2?.size}개")
                    
                    // 전체 응답 로그 (디버깅용)
                    Log.d("KisAPI", "📊 전체 응답 객체: $responseBody")
                    
                    // rt_cd가 비어있는 경우 실제 필드명 확인
                    if (responseBody.rt_cd.isNullOrEmpty()) {
                        Log.w("KisAPI", "⚠️ rt_cd가 비어있음 - JSON 파싱 문제일 수 있음")
                        Log.w("KisAPI", "⚠️ 응답 클래스: ${responseBody::class.simpleName}")
                    }
                }
                
                if (responseBody?.rt_cd == "0" && responseBody.output2?.isNotEmpty() == true) {
                    val dailyData = responseBody.output2!!
                    
                    // 메타데이터 로깅
                    responseBody.output1?.let { meta ->
                        Log.d("KisAPI", "📊 일봉 메타데이터: 종목명=${meta.hts_kor_isnm}, 전일대비=${meta.prdy_vrss}")
                    }
                    
                    // 첫 번째 데이터 샘플 확인
                    if (dailyData.isNotEmpty()) {
                        Log.d("KisAPI", "📊 일봉 첫 번째 데이터: ${dailyData.first()}")
                        Log.d("KisAPI", "📊 일봉 마지막 데이터: ${dailyData.last()}")
                    }
                    
                    val convertedData = dailyData.mapNotNull { data ->
                        try {
                            convertToCandlestickData(data)
                        } catch (e: Exception) {
                            Log.e("KisAPI", "❌ 일봉 데이터 변환 실패: ${e.message}")
                            Log.e("KisAPI", "❌ 실패한 일봉 데이터: $data")
                            null
                        }
                    }
                    
                    // 중요: TradingView 요구사항에 따라 시간 순으로 오름차순 정렬
                    val sortedData = convertedData.sortedBy { 
                        (it.time as Time.Utc).timestamp 
                    }
                    
                    // 정렬 후 시간 순서 검증
                    if (sortedData.size > 1) {
                        for (i in 1 until sortedData.size) {
                            val prevTime = (sortedData[i-1].time as Time.Utc).timestamp
                            val currTime = (sortedData[i].time as Time.Utc).timestamp
                            if (prevTime >= currTime) {
                                Log.e("KisAPI", "❌ 일봉 데이터 시간 순서 오류: index=${i-1}->${i}, time=$prevTime->$currTime")
                            }
                        }
                    }
                    
                    Log.d("KisAPI", "✅ 일봉 데이터 로드 완료: ${sortedData.size}개 (시간 순 정렬 완료)")
                    if (sortedData.isNotEmpty()) {
                        val firstTime = (sortedData.first().time as Time.Utc).timestamp
                        val lastTime = (sortedData.last().time as Time.Utc).timestamp
                        Log.d("KisAPI", "📅 일봉 시간 범위: $firstTime ~ $lastTime")
                    }
                    
                    return sortedData
                } else {
                    Log.w("KisAPI", "❌ 일봉 응답 오류: rt_cd=${responseBody?.rt_cd}, msg=${responseBody?.msg1}")
                }
            } else {
                Log.e("KisAPI", "❌ 일봉 API 호출 실패: ${response.code()} - ${response.message()}")
                Log.e("KisAPI", "❌ 응답 본문: ${response.errorBody()?.string()}")
            }
            
            Log.w("KisAPI", "❌ 일봉 데이터 로드 실패")
            emptyList()
        } catch (e: Exception) {
            Log.e("KisAPI", "일봉 데이터 가져오기 실패: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 거래량 데이터 가져오기
     */
    suspend fun getVolumeData(stockCode: String): List<HistogramData> {
        return try {
            val cacheKey = "${stockCode}_${currentTimeFrame}"
            
            volumeDataCache[cacheKey]?.let { data ->
                if (data.isNotEmpty()) {
                    Log.d("KisAPI", "캐시된 볼륨 데이터 반환: ${data.size}개")
                    return data
                }
            }
            
            // 차트 데이터와 함께 볼륨 데이터 생성
            val chartData = getMinuteChartData(stockCode, currentTimeFrame)
            val volumeData = chartData.map { candleData ->
                HistogramData(
                    time = candleData.time,
                    value = (1000000..5000000).random().toFloat() // 임시 볼륨 데이터
                )
            }
            
            volumeDataCache[cacheKey] = volumeData
            Log.d("KisAPI", "볼륨 데이터 생성 완료: ${volumeData.size}개")
            
            volumeData
        } catch (e: Exception) {
            Log.e("KisAPI", "거래량 데이터 가져오기 실패: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * 실시간 캔들 데이터 스트림 (한국투자증권 API: 당일 데이터만)
     */
    fun getRealTimeCandles(stockCode: String): Flow<List<CandlestickData>> = flow {
        Log.d("KisAPI", "📡 실시간 업데이트 시작: $stockCode (당일 데이터)")
        
        try {
            // 초기 당일 데이터 로드 및 전송
            val initialData = getMinuteChartData(stockCode, currentTimeFrame)
            if (initialData.isNotEmpty()) {
                Log.d("KisAPI", "📊 초기 당일 데이터 전송: ${initialData.size}개")
                validateTimeSequence(initialData)
                emit(initialData)
                
                // 마지막 타임스탬프 기록
                lastKnownTimestamp = (initialData.last().time as Time.Utc).timestamp
                isInitialDataSent = true
            }
            
            // 실시간 업데이트 루프 (한국투자증권 API 제한에 맞게)
            while (true) {
                delay(30000) // 30초마다 체크 (API 제한 고려)
                
                try {
                    // 새로운 당일 데이터 확인
                    val newData = getMinuteChartData(stockCode, currentTimeFrame)
                    if (newData.isNotEmpty()) {
                        // 기존 데이터와 크기 비교 또는 마지막 캔들 변경 확인
                        val hasNewData = if (isInitialDataSent) {
                            newData.size > 0 && (newData.last().time as Time.Utc).timestamp > lastKnownTimestamp
                        } else {
                            true
                        }
                        
                        if (hasNewData) {
                            Log.d("KisAPI", "🔄 새로운 당일 데이터 발견: ${newData.size}개")
                            
                            // 시간 순서 검증
                            validateTimeSequence(newData)
                            
                            // 전체 당일 데이터를 다시 전송 (한국투자증권 API 특성상)
                            emit(newData)
                            
                            if (newData.isNotEmpty()) {
                                lastKnownTimestamp = (newData.last().time as Time.Utc).timestamp
                            }
                        } else {
                            Log.d("KisAPI", "📊 새로운 데이터 없음")
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("KisAPI", "❌ 실시간 업데이트 실패: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e("KisAPI", "❌ 실시간 스트림 시작 실패: ${e.message}", e)
            
            // 실패 시 기본 데이터라도 전송
            try {
                val fallbackData = getMinuteChartData(stockCode, currentTimeFrame)
                if (fallbackData.isNotEmpty()) {
                    validateTimeSequence(fallbackData)
                    emit(fallbackData)
                }
            } catch (fallbackError: Exception) {
                Log.e("KisAPI", "❌ Fallback 데이터 전송 실패: ${fallbackError.message}", fallbackError)
            }
        }
    }

    /**
     * MinuteChartOutput을 CandlestickData로 변환 (시간프레임별 처리)
     */
    private fun convertToCandlestickData(data: MinuteChartOutput, index: Int = 0, timeFrame: String = "01", specificDate: String? = null): CandlestickData {
        try {
            Log.d("KisAPI", "🔄 데이터 변환: date=${data.date}, time=${data.time}, timeFrame=$timeFrame")
            Log.d("KisAPI", "📊 원시 가격 데이터: open=${data.openPrice}, high=${data.highPrice}, low=${data.lowPrice}, close=${data.closePrice}")
            
            // 가격 데이터 파싱 개선 - 한국투자증권 API는 빈 문자열이나 "0"을 반환할 수 있음
            val openPrice = data.openPrice?.takeIf { it.isNotEmpty() && it != "0" }?.toFloatOrNull() ?: 0f
            val closePrice = data.closePrice?.takeIf { it.isNotEmpty() && it != "0" }?.toFloatOrNull() ?: 0f
            val highPrice = data.highPrice?.takeIf { it.isNotEmpty() && it != "0" }?.toFloatOrNull() ?: 0f
            val lowPrice = data.lowPrice?.takeIf { it.isNotEmpty() && it != "0" }?.toFloatOrNull() ?: 0f
            
            // 유효한 가격 데이터 확인 - 실제 주가는 0이 될 수 없음
            if (openPrice <= 0f && closePrice <= 0f && highPrice <= 0f && lowPrice <= 0f) {
                Log.w("KisAPI", "❌ 유효하지 않은 가격 데이터 - 건너뜀: $data")
                throw IllegalArgumentException("Invalid price data: all prices are zero or negative")
            }
            
            // 가격 데이터 정합성 확인 및 보정
            val validOpenPrice = if (openPrice > 0f) openPrice else closePrice
            val validClosePrice = if (closePrice > 0f) closePrice else openPrice
            val validHighPrice = maxOf(highPrice, validOpenPrice, validClosePrice).takeIf { it > 0f } ?: validClosePrice
            val validLowPrice = if (lowPrice > 0f) minOf(lowPrice, validOpenPrice, validClosePrice) else minOf(validOpenPrice, validClosePrice)
            
            Log.d("KisAPI", "✅ 보정된 가격: open=$validOpenPrice, high=$validHighPrice, low=$validLowPrice, close=$validClosePrice")
            
            // 날짜 파싱
            val dateString = specificDate ?: data.date
            if (dateString.length != 8) {
                throw IllegalArgumentException("Invalid date format: $dateString")
            }
            
            val year = dateString.substring(0, 4).toInt()
            val month = dateString.substring(4, 6).toInt() - 1 // Calendar는 0부터 시작
            val day = dateString.substring(6, 8).toInt()
            
            // 시간프레임에 따른 처리 - 30분봉과 일봉을 구분하여 처리
            val timeData = when (timeFrame) {
                "D" -> {
                    // 일봉은 BusinessDay 사용
                    Log.d("KisAPI", "📅 일봉 시간 처리: $year-${month+1}-$day")
                    Time.BusinessDay(year, month + 1, day) // BusinessDay는 1부터 시작
                }
                "30", "60" -> {
                    // 30분봉, 1시간봉은 실제 시간을 사용하되 간격을 맞춤
                    val intervalMinutes = when (timeFrame) {
                        "30" -> 30
                        "60" -> 60
                        else -> 30
                    }
                    
                    val hour: Int
                    val minute: Int
                    
                    if (!data.time.isNullOrEmpty() && data.time.length >= 4) {
                        // 실제 시간 데이터가 있는 경우
                        val timeStr = if (data.time.length == 6) data.time.substring(0, 4) else data.time
                        hour = timeStr.substring(0, 2).toInt()
                        minute = timeStr.substring(2, 4).toInt()
                        
                        // 시간프레임에 맞게 분을 조정 (예: 30분봉이면 0분 또는 30분으로)
                        val adjustedMinute = (minute / intervalMinutes) * intervalMinutes
                        
                        Log.d("KisAPI", "⏰ ${timeFrame}분봉 시간 처리: 원시시간=${hour}:${minute} -> 조정시간=${hour}:${adjustedMinute}")
                        
                        val calendar = Calendar.getInstance().apply {
                            set(year, month, day, hour, adjustedMinute, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        Time.Utc(calendar.timeInMillis / 1000)
                    } else {
                        Log.w("KisAPI", "⚠️ 시간 데이터 없음 - 기본 시간 사용")
                        val calendar = Calendar.getInstance().apply {
                            set(year, month, day, 9, 0, 0) // 기본 9시
                            set(Calendar.MILLISECOND, 0)
                        }
                        Time.Utc(calendar.timeInMillis / 1000)
                    }
                }
                else -> {
                    // 1분, 5분, 10분봉 등 - 실제 체결 시간 사용
                    val hour: Int
                    val minute: Int
                    
                    if (!data.time.isNullOrEmpty() && data.time.length >= 4) {
                        when (data.time.length) {
                            6 -> { // HHMMSS
                                hour = data.time.substring(0, 2).toInt()
                                minute = data.time.substring(2, 4).toInt()
                            }
                            4 -> { // HHMM
                                hour = data.time.substring(0, 2).toInt()
                                minute = data.time.substring(2, 4).toInt()
                            }
                            else -> {
                                Log.w("KisAPI", "알 수 없는 시간 형식: ${data.time}")
                                hour = 9
                                minute = 0
                            }
                        }
                    } else {
                        // 시간 데이터가 없으면 기본값
                        hour = 9
                        minute = 0
                    }
                    
                    // 장 시간 유효성 검사 (9:00-15:30)
                    val validHour = kotlin.math.max(9, kotlin.math.min(15, hour))
                    val validMinute = when {
                        validHour == 15 -> kotlin.math.min(30, minute) // 3시30분까지
                        else -> kotlin.math.max(0, kotlin.math.min(59, minute))
                    }
                    
                    Log.d("KisAPI", "⏰ ${timeFrame}분봉 시간: ${validHour}:${validMinute}")
                    
                    val calendar = Calendar.getInstance().apply {
                        set(year, month, day, validHour, validMinute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    Time.Utc(calendar.timeInMillis / 1000)
                }
            }
            
            Log.d("KisAPI", "✅ 변환 완료: time=$timeData, open=$validOpenPrice, high=$validHighPrice, low=$validLowPrice, close=$validClosePrice")

            return CandlestickData(
                time = timeData,
                open = validOpenPrice,
                high = validHighPrice,
                low = validLowPrice,
                close = validClosePrice
            )
        } catch (e: Exception) {
            Log.e("KisAPI", "❌ 데이터 변환 실패: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * 시간프레임과 인덱스로 분 단위 계산
     */
    private fun calculateTimeFrameMinutes(timeFrame: String, index: Int): Int {
        val intervalMinutes = when (timeFrame) {
            "01" -> 1
            "05" -> 5
            "10" -> 10
            "30" -> 30
            "60" -> 60
            else -> 1
        }
        return index * intervalMinutes
    }

    /**
     * DailyChartOutput을 CandlestickData로 변환 (일봉은 BusinessDay 사용)
     */
    private fun convertToCandlestickData(data: DailyChartOutput): CandlestickData {
        try {
            Log.d("KisAPI", "🔄 일봉 데이터 변환: date=${data.stck_bsop_date}")
            Log.d("KisAPI", "📊 일봉 가격: open=${data.stck_oprc}, high=${data.stck_hgpr}, low=${data.stck_lwpr}, close=${data.stck_clpr}")
            
            // 날짜 형식 검증
            if (data.stck_bsop_date.length != 8) {
                throw IllegalArgumentException("Invalid date format: ${data.stck_bsop_date}")
            }
            
            // YYYYMMDD 형식을 BusinessDay로 변환
            val year = data.stck_bsop_date.substring(0, 4).toInt()
            val month = data.stck_bsop_date.substring(4, 6).toInt()
            val day = data.stck_bsop_date.substring(6, 8).toInt()
            
            // 가격 데이터 안전하게 파싱
            val openPrice = data.stck_oprc?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 0f
            val closePrice = data.stck_clpr?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 0f
            val highPrice = data.stck_hgpr?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 0f
            val lowPrice = data.stck_lwpr?.takeIf { it.isNotEmpty() }?.toFloatOrNull() ?: 0f
            
            // 가격 유효성 검사
            if (openPrice <= 0f || closePrice <= 0f || highPrice <= 0f || lowPrice <= 0f) {
                Log.w("KisAPI", "⚠️ 일봉 가격 데이터 이상: open=$openPrice, high=$highPrice, low=$lowPrice, close=$closePrice")
            }
            
            // 일봉도 UTC 형식으로 변경 (BusinessDay 대신)
            val calendar = Calendar.getInstance().apply {
                set(year, month - 1, day, 9, 0, 0) // 9시로 설정
                set(Calendar.MILLISECOND, 0)
            }
            
            val candleData = CandlestickData(
                time = Time.Utc(calendar.timeInMillis / 1000),
                open = openPrice,
                high = highPrice,
                low = lowPrice,
                close = closePrice
            )
            
            Log.d("KisAPI", "✅ 일봉 변환 완료: $year-$month-$day, close=$closePrice")
            return candleData
            
        } catch (e: Exception) {
            Log.e("KisAPI", "❌ 일봉 데이터 변환 실패: ${e.message}", e)
            Log.e("KisAPI", "❌ 실패한 일봉 데이터: $data")
            
            // 안전한 fallback 데이터
            return CandlestickData(
                time = Time.BusinessDay(2025, 1, 1),
                open = 50000f,
                high = 50000f,
                low = 50000f,
                close = 50000f
            )
        }
    }
}