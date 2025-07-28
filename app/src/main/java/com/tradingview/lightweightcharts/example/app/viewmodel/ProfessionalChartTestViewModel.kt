package com.tradingview.lightweightcharts.example.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingview.lightweightcharts.api.series.models.*
import com.tradingview.lightweightcharts.example.app.indicators.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.*
import kotlin.random.Random

class ProfessionalChartTestViewModel : ViewModel() {

    // 캔들스틱 데이터
    private val _candlestickData = MutableStateFlow<List<CandlestickData>>(emptyList())
    val candlestickData: StateFlow<List<CandlestickData>> = _candlestickData.asStateFlow()

    // 거래량 데이터
    private val _volumeData = MutableStateFlow<List<HistogramData>>(emptyList())
    val volumeData: StateFlow<List<HistogramData>> = _volumeData.asStateFlow()

    // 이동평균선 데이터
    private val _sma5Data = MutableStateFlow<List<LineData>>(emptyList())
    val sma5Data: StateFlow<List<LineData>> = _sma5Data.asStateFlow()

    private val _sma20Data = MutableStateFlow<List<LineData>>(emptyList())
    val sma20Data: StateFlow<List<LineData>> = _sma20Data.asStateFlow()

    // RSI 데이터
    private val _rsiData = MutableStateFlow<List<LineData>>(emptyList())
    val rsiData: StateFlow<List<LineData>> = _rsiData.asStateFlow()

    // 볼린저 밴드 데이터
    private val _bollingerBands = MutableStateFlow<BollingerBandsResult?>(null)
    val bollingerBands: StateFlow<BollingerBandsResult?> = _bollingerBands.asStateFlow()

    // 지표 표시 상태
    private val _showSMA5 = MutableStateFlow(false)
    val showSMA5: StateFlow<Boolean> = _showSMA5.asStateFlow()

    private val _showSMA20 = MutableStateFlow(false)
    val showSMA20: StateFlow<Boolean> = _showSMA20.asStateFlow()

    private val _showRSI = MutableStateFlow(false)
    val showRSI: StateFlow<Boolean> = _showRSI.asStateFlow()

    private val _showBollingerBands = MutableStateFlow(false)
    val showBollingerBands: StateFlow<Boolean> = _showBollingerBands.asStateFlow()

    private val _showVolume = MutableStateFlow(false)
    val showVolume: StateFlow<Boolean> = _showVolume.asStateFlow()

    // 통계 정보
    private val _statisticsInfo = MutableStateFlow(StatisticsInfo())
    val statisticsInfo: StateFlow<StatisticsInfo> = _statisticsInfo.asStateFlow()

    // 실시간 업데이트 Job
    private var realTimeJob: Job? = null

    /**
     * 기본 테스트 데이터 생성
     */
    fun generateTestData() {
        generateRandomWalkData(100, 50000f)
    }

    /**
     * 상승 추세 데이터 생성
     */
    fun generateUptrendData() {
        val dataPoints = 200
        val startPrice = 45000f
        val data = mutableListOf<CandlestickData>()
        val volumeData = mutableListOf<HistogramData>()
        
        var currentPrice = startPrice
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -dataPoints)

        for (i in 0 until dataPoints) {
            val dateStr = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // 상승 추세: 기본적으로 상승하되 약간의 변동성 추가
            val trendFactor = 1.002f + Random.nextFloat() * 0.004f // 0.2% ~ 0.6% 상승
            val volatility = Random.nextFloat() * 0.03f // 최대 3% 변동성
            
            val open = currentPrice
            val trend = currentPrice * trendFactor
            val high = trend * (1 + volatility)
            val low = trend * (1 - volatility * 0.5f)
            val close = low + (high - low) * Random.nextFloat()
            
            currentPrice = close

            data.add(CandlestickData(
                time = Time.StringTime(dateStr),
                open = open,
                high = high,
                low = low,
                close = close
            ))

            // 거래량 (상승 시 거래량 증가)
            val baseVolume = 100000f
            val volumeMultiplier = if (close > open) 1.2f else 0.8f
            val volume = baseVolume * volumeMultiplier * (0.5f + Random.nextFloat())
            
            volumeData.add(HistogramData(
                time = Time.StringTime(dateStr),
                value = volume
            ))

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        updateChartData(data, volumeData)
    }

    /**
     * 하락 추세 데이터 생성
     */
    fun generateDowntrendData() {
        val dataPoints = 200
        val startPrice = 55000f
        val data = mutableListOf<CandlestickData>()
        val volumeData = mutableListOf<HistogramData>()
        
        var currentPrice = startPrice
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -dataPoints)

        for (i in 0 until dataPoints) {
            val dateStr = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // 하락 추세: 기본적으로 하락하되 약간의 변동성 추가
            val trendFactor = 0.996f - Random.nextFloat() * 0.006f // 0.4% ~ 1.0% 하락
            val volatility = Random.nextFloat() * 0.04f // 최대 4% 변동성
            
            val open = currentPrice
            val trend = currentPrice * trendFactor
            val high = trend * (1 + volatility * 0.6f)
            val low = trend * (1 - volatility)
            val close = low + (high - low) * Random.nextFloat()
            
            currentPrice = close

            data.add(CandlestickData(
                time = Time.StringTime(dateStr),
                open = open,
                high = high,
                low = low,
                close = close
            ))

            // 거래량 (하락 시 거래량 증가)
            val baseVolume = 120000f
            val volumeMultiplier = if (close < open) 1.3f else 0.7f
            val volume = baseVolume * volumeMultiplier * (0.5f + Random.nextFloat())
            
            volumeData.add(HistogramData(
                time = Time.StringTime(dateStr),
                value = volume
            ))

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        updateChartData(data, volumeData)
    }

    /**
     * 고변동성 데이터 생성
     */
    fun generateVolatileData() {
        val dataPoints = 150
        val startPrice = 50000f
        val data = mutableListOf<CandlestickData>()
        val volumeData = mutableListOf<HistogramData>()
        
        var currentPrice = startPrice
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -dataPoints)

        for (i in 0 until dataPoints) {
            val dateStr = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // 고변동성: 큰 폭의 상승/하락
            val volatility = 0.05f + Random.nextFloat() * 0.08f // 5% ~ 13% 변동성
            val direction = if (Random.nextBoolean()) 1f else -1f
            
            val open = currentPrice
            val changePercent = direction * volatility * Random.nextFloat()
            val midPrice = currentPrice * (1 + changePercent)
            
            val high = midPrice * (1 + volatility * 0.3f)
            val low = midPrice * (1 - volatility * 0.3f)
            val close = low + (high - low) * Random.nextFloat()
            
            currentPrice = close

            data.add(CandlestickData(
                time = Time.StringTime(dateStr),
                open = open,
                high = high,
                low = low,
                close = close
            ))

            // 거래량 (변동성이 클 때 거래량 증가)
            val baseVolume = 80000f
            val volatilityMultiplier = 1 + abs(close - open) / open * 10
            val volume = baseVolume * volatilityMultiplier * (0.3f + Random.nextFloat())
            
            volumeData.add(HistogramData(
                time = Time.StringTime(dateStr),
                value = volume
            ))

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        updateChartData(data, volumeData)
    }

    /**
     * 랜덤 워크 데이터 생성
     */
    fun generateRandomData() {
        generateRandomWalkData(250, 48000f)
    }

    private fun generateRandomWalkData(dataPoints: Int, startPrice: Float) {
        val data = mutableListOf<CandlestickData>()
        val volumeData = mutableListOf<HistogramData>()
        
        var currentPrice = startPrice
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -dataPoints)

        for (i in 0 until dataPoints) {
            val dateStr = String.format("%04d-%02d-%02d", 
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH)
            )

            // 랜덤 워크: 완전 랜덤한 움직임
            val changePercent = (Random.nextFloat() - 0.5f) * 0.06f // -3% ~ +3%
            val volatility = Random.nextFloat() * 0.025f // 최대 2.5% 일중 변동성
            
            val open = currentPrice
            val close = currentPrice * (1f + changePercent)
            val high = maxOf(open, close) * (1f + volatility)
            val low = minOf(open, close) * (1f - volatility)
            
            currentPrice = close

            data.add(CandlestickData(
                time = Time.StringTime(dateStr),
                open = open,
                high = high,
                low = low,
                close = close
            ))

            // 거래량 (랜덤)
            val baseVolume = 90000f
            val volume = baseVolume * (0.2f + Random.nextFloat() * 1.8f)
            
            volumeData.add(HistogramData(
                time = Time.StringTime(dateStr),
                value = volume
            ))

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        updateChartData(data, volumeData)
    }

    /**
     * 차트 데이터 업데이트 및 지표 계산
     */
    private fun updateChartData(candleData: List<CandlestickData>, volumeData: List<HistogramData>) {
        _candlestickData.value = candleData
        _volumeData.value = volumeData

        if (candleData.isNotEmpty()) {
            // 이동평균선 계산
            _sma5Data.value = TechnicalIndicators.calculateSMA(candleData, 5)
            _sma20Data.value = TechnicalIndicators.calculateSMA(candleData, 20)

            // RSI 계산
            _rsiData.value = TechnicalIndicators.calculateRSI(candleData, 14)

            // 볼린저 밴드 계산
            _bollingerBands.value = TechnicalIndicators.calculateBollingerBands(candleData)

            // 통계 정보 업데이트
            updateStatistics(candleData)
        }
    }

    /**
     * 통계 정보 업데이트
     */
    private fun updateStatistics(data: List<CandlestickData>) {
        if (data.isEmpty()) return

        val currentPrice = data.last().close
        val highPrice = data.maxOf { it.high }
        val lowPrice = data.minOf { it.low }
        val rsiValue = _rsiData.value.lastOrNull()?.value ?: 0f

        _statisticsInfo.value = StatisticsInfo(
            dataPoints = data.size,
            currentPrice = currentPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            rsi = rsiValue
        )
    }

    /**
     * 실시간 업데이트 시작
     */
    fun startRealTimeUpdates() {
        stopRealTimeUpdates() // 기존 Job 중지
        
        realTimeJob = viewModelScope.launch {
            while (true) {
                val currentData = _candlestickData.value.toMutableList()
                if (currentData.isNotEmpty()) {
                    // 마지막 캔들 업데이트
                    val lastCandle = currentData.last()
                    val changePercent = (Random.nextFloat() - 0.5f) * 0.02f // -1% ~ +1%
                    
                    val newClose = lastCandle.close * (1f + changePercent)
                    val newHigh = maxOf(lastCandle.high, newClose)
                    val newLow = minOf(lastCandle.low, newClose)
                    
                    val updatedCandle = lastCandle.copy(
                        high = newHigh,
                        low = newLow,
                        close = newClose
                    )
                    
                    currentData[currentData.size - 1] = updatedCandle
                    updateChartData(currentData, _volumeData.value)
                }
                
                delay(2000) // 2초마다 업데이트 (API 제한 고려)
            }
        }
    }

    /**
     * 실시간 업데이트 중지
     */
    fun stopRealTimeUpdates() {
        realTimeJob?.cancel()
        realTimeJob = null
    }

    /**
     * 지표 토글 함수들
     */
    fun toggleSMA5(show: Boolean) { _showSMA5.value = show }
    fun toggleSMA20(show: Boolean) { _showSMA20.value = show }
    fun toggleRSI(show: Boolean) { _showRSI.value = show }
    fun toggleBollingerBands(show: Boolean) { _showBollingerBands.value = show }
    fun toggleVolume(show: Boolean) { _showVolume.value = show }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeUpdates()
    }
}

/**
 * 통계 정보 데이터 클래스
 */
data class StatisticsInfo(
    val dataPoints: Int = 0,
    val currentPrice: Float = 0f,
    val highPrice: Float = 0f,
    val lowPrice: Float = 0f,
    val rsi: Float = 0f
)