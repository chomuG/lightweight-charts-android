package com.tradingview.lightweightcharts.example.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tradingview.lightweightcharts.api.series.models.*
import com.tradingview.lightweightcharts.example.app.repository.KisRepository
import com.tradingview.lightweightcharts.example.app.indicators.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.util.Log

@HiltViewModel
class ProfessionalChartViewModel @Inject constructor(
    private val kisRepository: KisRepository
) : ViewModel() {

    // 현재 선택된 종목 코드
    private val _stockCode = MutableStateFlow("005930") // 삼성전자 기본값
    val stockCode: StateFlow<String> = _stockCode.asStateFlow()

    // 차트 설정 (일봉을 기본값으로 설정 - 증권사 차트 스타일)
    private val _timeFrame = MutableStateFlow("D") // 01:1분, 05:5분, 10:10분, 60:60분, D:일봉
    val timeFrame: StateFlow<String> = _timeFrame.asStateFlow()

    // 메인 캔들스틱 데이터
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

    private val _sma60Data = MutableStateFlow<List<LineData>>(emptyList())
    val sma60Data: StateFlow<List<LineData>> = _sma60Data.asStateFlow()

    private val _sma120Data = MutableStateFlow<List<LineData>>(emptyList())
    val sma120Data: StateFlow<List<LineData>> = _sma120Data.asStateFlow()

    // RSI 데이터
    private val _rsiData = MutableStateFlow<List<LineData>>(emptyList())
    val rsiData: StateFlow<List<LineData>> = _rsiData.asStateFlow()

    // MACD 데이터
    private val _macdData = MutableStateFlow<MACDResult?>(null)
    val macdData: StateFlow<MACDResult?> = _macdData.asStateFlow()

    // 볼린저 밴드 데이터
    private val _bollingerBands = MutableStateFlow<BollingerBandsResult?>(null)
    val bollingerBands: StateFlow<BollingerBandsResult?> = _bollingerBands.asStateFlow()

    // 스토캐스틱 데이터
    private val _stochasticData = MutableStateFlow<StochasticResult?>(null)
    val stochasticData: StateFlow<StochasticResult?> = _stochasticData.asStateFlow()

    // 지표 표시 상태
    private val _showSMA5 = MutableStateFlow(true)
    val showSMA5: StateFlow<Boolean> = _showSMA5.asStateFlow()

    private val _showSMA20 = MutableStateFlow(true)
    val showSMA20: StateFlow<Boolean> = _showSMA20.asStateFlow()

    private val _showSMA60 = MutableStateFlow(false)
    val showSMA60: StateFlow<Boolean> = _showSMA60.asStateFlow()

    private val _showSMA120 = MutableStateFlow(false)
    val showSMA120: StateFlow<Boolean> = _showSMA120.asStateFlow()

    private val _showBollingerBands = MutableStateFlow(false)
    val showBollingerBands: StateFlow<Boolean> = _showBollingerBands.asStateFlow()

    private val _showRSI = MutableStateFlow(false)
    val showRSI: StateFlow<Boolean> = _showRSI.asStateFlow()

    private val _showMACD = MutableStateFlow(false)
    val showMACD: StateFlow<Boolean> = _showMACD.asStateFlow()

    private val _showStochastic = MutableStateFlow(false)
    val showStochastic: StateFlow<Boolean> = _showStochastic.asStateFlow()

    private val _showVolume = MutableStateFlow(true)
    val showVolume: StateFlow<Boolean> = _showVolume.asStateFlow()

    // 로딩 상태
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 에러 상태
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // 실시간 변경 감지를 위한 데이터 클래스
    private data class CandleChange(
        val type: ChangeType,
        val index: Int,
        val newCandle: CandlestickData
    )
    
    private enum class ChangeType {
        UPDATE, ADD
    }
    
    // 증분 업데이트 최적화를 위한 상태 관리
    private var lastProcessedTimestamp: Long = 0L
    private val updateBatchSize = 5 // 배치 단위
    private val pendingUpdates = mutableListOf<CandleChange>()
    private var batchUpdateJob: kotlinx.coroutines.Job? = null

    init {
        Log.d("ChartViewModel", "=== ProfessionalChartViewModel init 시작 ===")
        // 토큰 테스트 제거 - loadInitialData에서 필요할 때 자동으로 발급됨
        loadInitialData()
    }
    
    /**
     * 2주치 데이터를 위한 일수 계산
     */
    private fun calculateDaysForTwoWeeks(timeFrame: String): Int {
        return when (timeFrame) {
            "01" -> 14  // 1분봉: 2주
            "05" -> 14  // 5분봉: 2주  
            "10" -> 14  // 10분봉: 2주
            "30" -> 14  // 30분봉: 2주
            "60" -> 14  // 60분봉: 2주
            else -> 14  // 기본 2주
        }
    }
    

    /**
     * 초기 데이터 로드
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            println("loadInitialData 시작 - 종목: ${_stockCode.value}, 시간대: ${_timeFrame.value}")
            _isLoading.value = true
            try {
                val initialData = try {
                    when (_timeFrame.value) {
                        "D" -> {
                            // 일봉: 200일치 히스토리 (장기 차트)
                            Log.d("ChartViewModel", "일봉 데이터 요청 (200일 - 장기 히스토리)")
                            kisRepository.getDailyChartData(_stockCode.value, 200)
                        }
                        else -> {
                            // 분봉: 한국투자증권 API는 당일 데이터만 제공 (최대 30개씩)
                            Log.d("ChartViewModel", "당일 분봉 데이터 요청: ${_timeFrame.value}분봉 (한국투자증권 API 제한)")
                            kisRepository.getMinuteChartData(_stockCode.value, _timeFrame.value)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChartViewModel", "데이터 로드 실패: ${e.message}", e)
                    emptyList()
                }
                
                Log.d("ChartViewModel", "초기 데이터 로드 완료: ${initialData.size}개")
                if (initialData.isNotEmpty()) {
                    Log.d("ChartViewModel", "첫 번째 데이터: ${initialData.first()}")
                    Log.d("ChartViewModel", "마지막 데이터: ${initialData.last()}")
                    
                    // 데이터 범위 로그
                    if (_timeFrame.value == "D") {
                        Log.d("ChartViewModel", "일봉 데이터 범위: 총 ${initialData.size}일")
                    } else {
                        Log.d("ChartViewModel", "분봉 데이터 범위: 총 ${initialData.size}개 (${_timeFrame.value}분봉)")
                    }
                } else {
                    Log.w("ChartViewModel", "데이터가 비어있음")
                }
                
                if (initialData.isNotEmpty()) {
                    updateChartData(initialData)
                } else {
                    Log.w("ChartViewModel", "비어있는 데이터로 인해 차트 업데이트 생략")
                    _errorMessage.value = "데이터를 불러올 수 없습니다. 다시 시도해주세요."
                }
                
                // 효율적인 실시간 업데이트 시작 (증분 업데이트 방식)
                if (_timeFrame.value != "D") {
                    viewModelScope.launch {
                        delay(2000) // 2초 대기 후 실시간 업데이트 시작
                        startRealTimeUpdates()
                    }
                }
            } catch (e: Exception) {
                println("데이터 로드 실패: ${e.message}")
                _errorMessage.value = "데이터 로드 실패: ${e.message}"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 효율적인 실시간 업데이트 시작 (증분 업데이트)
     */
    private fun startRealTimeUpdates() {
        if (_timeFrame.value != "D") { // 일봉이 아닐 때만 실시간 업데이트
            viewModelScope.launch {
                Log.d("ChartViewModel", "효율적 실시간 업데이트 시작")
                kisRepository.getRealTimeCandles(_stockCode.value)
                    .catch { e -> 
                        Log.e("ChartViewModel", "실시간 업데이트 실패: ${e.message}", e)
                        _errorMessage.value = "실시간 업데이트 실패: ${e.message}" 
                    }
                    .collect { candleList ->
                        Log.d("ChartViewModel", "실시간 업데이트 수신: ${candleList.size}개 캔들")
                        
                        if (candleList.isNotEmpty()) {
                            val currentData = _candlestickData.value.toMutableList()
                            
                            if (currentData.isEmpty()) {
                                // 초기 데이터셋인 경우 전체 교체
                                Log.d("ChartViewModel", "초기 데이터셋 설정: ${candleList.size}개")
                                updateChartData(candleList)
                            } else {
                                // 효율적 배치 업데이트 시스템
                                processBatchUpdate(currentData, candleList)
                            }
                        } else {
                            Log.d("ChartViewModel", "실시간 데이터 없음 - 업데이트 생략")
                        }
                    }
            }
        }
    }
    
    /**
     * 배치 업데이트 처리 시스템 (성능 최적화)
     */
    private suspend fun processBatchUpdate(
        currentData: MutableList<CandlestickData>,
        newData: List<CandlestickData>
    ) {
        // 기존 배치 작업 취소
        batchUpdateJob?.cancel()
        
        val changesDetected = detectRealTimeChanges(currentData, newData)
        
        if (changesDetected.isEmpty()) {
            Log.d("ChartViewModel", "⚡ 변경사항 없음 - 업데이트 생략")
            return
        }
        
        // 배치 업데이트에 추가
        pendingUpdates.addAll(changesDetected)
        
        // 즉시 업데이트 또는 배치 업데이트 결정
        if (pendingUpdates.size >= updateBatchSize || 
            changesDetected.any { it.type == ChangeType.ADD }) {
            // 즉시 업데이트 (새 캔들 추가 또는 배치 크기 도달)
            executeBatchUpdate(currentData)
        } else {
            // 지연 배치 업데이트 (100ms 대기)
            batchUpdateJob = viewModelScope.launch {
                delay(100L) // 100ms 대기
                if (pendingUpdates.isNotEmpty()) {
                    executeBatchUpdate(currentData)
                }
            }
        }
    }
    
    /**
     * 배치 업데이트 실행
     */
    private suspend fun executeBatchUpdate(currentData: MutableList<CandlestickData>) {
        if (pendingUpdates.isEmpty()) return
        
        val updatesToProcess = pendingUpdates.toList()
        pendingUpdates.clear()
        
        Log.d("ChartViewModel", "🚀 배치 업데이트 시작: ${updatesToProcess.size}개 변경")
        
        try {
            // 변경사항 일괄 적용
            updatesToProcess.forEach { change ->
                when (change.type) {
                    ChangeType.UPDATE -> {
                        if (change.index >= 0 && change.index < currentData.size) {
                            currentData[change.index] = change.newCandle
                        }
                    }
                    ChangeType.ADD -> {
                        currentData.add(change.newCandle)
                    }
                }
            }
            
            // 시간 순서 정렬 (필요시만)
            val sortedData = if (updatesToProcess.any { it.type == ChangeType.ADD }) {
                currentData.sortedBy { (it.time as Time.Utc).timestamp }
            } else {
                currentData.toList()
            }
            
            // 지표 재계산 여부 결정 (데이터 추가 또는 대량 변경시만)
            val shouldRecalculateIndicators = updatesToProcess.any { it.type == ChangeType.ADD } ||
                                            updatesToProcess.size > 3
            
            if (shouldRecalculateIndicators) {
                Log.d("ChartViewModel", "📊 지표 재계산 수행")
                updateChartData(sortedData)
            } else {
                Log.d("ChartViewModel", "⚡ 빠른 데이터 업데이트 (지표 재계산 생략)")
                _candlestickData.value = sortedData
            }
            
            Log.d("ChartViewModel", "✅ 배치 업데이트 완료: ${sortedData.size}개 캔들")
            
        } catch (e: Exception) {
            Log.e("ChartViewModel", "배치 업데이트 실패: ${e.message}", e)
            _errorMessage.value = "데이터 업데이트 오류: ${e.message}"
        }
    }
    
    /**
     * 고성능 실시간 데이터 변경사항 감지 (타임스탬프 기반 최적화)
     */
    private fun detectRealTimeChanges(
        currentData: List<CandlestickData>,
        newData: List<CandlestickData>
    ): List<CandleChange> {
        val changes = mutableListOf<CandleChange>()
        
        try {
            // 성능 최적화: 마지막 처리 시점 이후 데이터만 확인
            val filteredNewData = if (lastProcessedTimestamp > 0L) {
                newData.filter { candle ->
                    val timestamp = (candle.time as Time.Utc).timestamp
                    timestamp >= lastProcessedTimestamp
                }
            } else {
                newData
            }
            
            if (filteredNewData.isEmpty()) {
                Log.d("ChartViewModel", "새로운 데이터 없음 - 업데이트 스킵")
                return changes
            }
            
            // 현재 데이터의 마지막 3개만 맵핑 (최신 데이터 위주 최적화)
            val recentCurrentData = currentData.takeLast(10)
            val currentMap = recentCurrentData.associateBy { 
                (it.time as Time.Utc).timestamp 
            }
            
            filteredNewData.forEach { newCandle ->
                val timestamp = (newCandle.time as Time.Utc).timestamp
                val existingCandle = currentMap[timestamp]
                
                if (existingCandle != null) {
                    // 실제 변경 확인 (더 정밀한 비교)
                    if (hasCandleChangedPrecise(existingCandle, newCandle)) {
                        val index = currentData.indexOfFirst { 
                            (it.time as Time.Utc).timestamp == timestamp 
                        }
                        if (index != -1) {
                            changes.add(CandleChange(ChangeType.UPDATE, index, newCandle))
                            Log.d("ChartViewModel", "🔄 정밀 변경 감지: $timestamp, ${existingCandle.close} → ${newCandle.close}")
                        }
                    }
                } else {
                    // 새로운 캔들 추가
                    changes.add(CandleChange(ChangeType.ADD, -1, newCandle))
                    Log.d("ChartViewModel", "✅ 새 캔들 추가: $timestamp, close=${newCandle.close}")
                }
                
                // 마지막 처리 타임스탬프 업데이트
                lastProcessedTimestamp = maxOf(lastProcessedTimestamp, timestamp)
            }
            
            Log.d("ChartViewModel", "⚡ 고성능 변경 감지: ${changes.size}개 변경 (필터링된 ${filteredNewData.size}개 중)")
            
        } catch (e: Exception) {
            Log.e("ChartViewModel", "변경 감지 실패: ${e.message}", e)
        }
        
        return changes
    }
    
    /**
     * 개별 캔들의 변경 여부 확인 (기본)
     */
    private fun hasCandleChanged(existing: CandlestickData, new: CandlestickData): Boolean {
        return existing.open != new.open ||
               existing.high != new.high ||
               existing.low != new.low ||
               existing.close != new.close
    }
    
    /**
     * 정밀한 캔들 변경 감지 (소수점 오차 고려)
     */
    private fun hasCandleChangedPrecise(existing: CandlestickData, new: CandlestickData): Boolean {
        val threshold = 0.01f // 1원 이하 차이는 무시
        return kotlin.math.abs(existing.open - new.open) > threshold ||
               kotlin.math.abs(existing.high - new.high) > threshold ||
               kotlin.math.abs(existing.low - new.low) > threshold ||
               kotlin.math.abs(existing.close - new.close) > threshold
    }

    /**
     * 차트 데이터 업데이트 및 지표 계산
     */
    private fun updateChartData(candleData: List<CandlestickData>) {
        try {
            Log.d("ChartViewModel", "차트 데이터 업데이트 호출: ${candleData.size}개 데이터")
            
            // 데이터 유효성 검사
            if (candleData.isEmpty()) {
                Log.w("ChartViewModel", "비어있는 데이터로 업데이트 생략")
                return
            }
            
            // 개선된 데이터 순서 검사 (중복 및 역순 문제 해결)
            val timeConflicts = mutableListOf<String>()
            val timestampSet = mutableSetOf<Long>()
            
            for (i in candleData.indices) {
                val currTime = when (val currTimeData = candleData[i].time) {
                    is Time.Utc -> currTimeData.timestamp
                    is Time.BusinessDay -> currTimeData.year * 10000L + currTimeData.month * 100L + currTimeData.day
                    else -> 0L
                }
                
                // 중복 타임스탬프 검사
                if (timestampSet.contains(currTime)) {
                    timeConflicts.add("중복 타임스탬프: index=$i, time=$currTime")
                } else {
                    timestampSet.add(currTime)
                }
                
                // 순서 검사 (이전 데이터와 비교)
                if (i > 0) {
                    val prevTime = when (val prevTimeData = candleData[i-1].time) {
                        is Time.Utc -> prevTimeData.timestamp
                        is Time.BusinessDay -> prevTimeData.year * 10000L + prevTimeData.month * 100L + prevTimeData.day
                        else -> 0L
                    }
                    
                    if (prevTime >= currTime) {
                        timeConflicts.add("순서 오류: index=${i-1}->${i}, time=$prevTime->$currTime")
                    }
                }
            }
            
            // 시간 충돌이 있는 경우 로그 출력하되 처리 계속
            if (timeConflicts.isNotEmpty()) {
                Log.w("ChartViewModel", "시간 데이터 문제 발견 (${timeConflicts.size}개):")
                timeConflicts.take(5).forEach { conflict ->
                    Log.w("ChartViewModel", "  - $conflict")
                }
                
                // 중복 제거 및 재정렬로 문제 해결 시도
                val cleanedData = try {
                    candleData
                        .distinctBy { 
                            when (val timeData = it.time) {
                                is Time.Utc -> timeData.timestamp
                                is Time.BusinessDay -> timeData.year * 10000L + timeData.month * 100L + timeData.day
                                else -> 0L
                            }
                        }
                        .sortedBy { 
                            when (val timeData = it.time) {
                                is Time.Utc -> timeData.timestamp
                                is Time.BusinessDay -> timeData.year * 10000L + timeData.month * 100L + timeData.day
                                else -> 0L
                            }
                        }
                } catch (e: Exception) {
                    Log.e("ChartViewModel", "데이터 정리 실패: ${e.message}", e)
                    candleData // 원본 데이터 유지
                }
                
                Log.d("ChartViewModel", "데이터 정리 완료: ${candleData.size}개 -> ${cleanedData.size}개")
                
                // 정리된 데이터로 재귀 호출
                if (cleanedData.size != candleData.size) {
                    updateChartData(cleanedData)
                    return
                }
            }
            
            _candlestickData.value = candleData
            Log.d("ChartViewModel", "캔들스틱 데이터 업데이트 완료")

            if (candleData.size >= 5) { // 최소 5개 데이터 필요
                Log.d("ChartViewModel", "📊 고성능 지표 계산 시작...")
                
                // 비동기 지표 계산 (성능 최적화)
                viewModelScope.launch {
                    calculateIndicatorsAsync(candleData)
                }
            } else {
                Log.w("ChartViewModel", "데이터가 부족하여 지표 계산 생략: ${candleData.size}개")
                clearIndicators()
            }
        } catch (e: Exception) {
            Log.e("ChartViewModel", "차트 데이터 업데이트 실패: ${e.message}", e)
            _errorMessage.value = "차트 데이터 업데이트 중 오류가 발생했습니다: ${e.message}"
        }
    }
    
    /**
     * 비동기 지표 계산 (백그라운드 처리로 UI 블로킹 방지)
     */
    private suspend fun calculateIndicatorsAsync(candleData: List<CandlestickData>) {
        try {
            // CPU 집약적 작업을 Default 디스패처에서 실행
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                // 순차적 지표 계산 (메모리 사용량 최적화)
                val sma5 = TechnicalIndicators.calculateSMA(candleData, 5)
                val sma20 = TechnicalIndicators.calculateSMA(candleData, 20)
                val sma60 = TechnicalIndicators.calculateSMA(candleData, 60)
                val sma120 = TechnicalIndicators.calculateSMA(candleData, 120)
                val rsi = TechnicalIndicators.calculateRSI(candleData, 14)
                val macd = TechnicalIndicators.calculateMACD(candleData)
                val bollinger = TechnicalIndicators.calculateBollingerBands(candleData)
                val stochastic = TechnicalIndicators.calculateStochastic(candleData)
                
                // UI 스레드에서 결과 업데이트
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _sma5Data.value = sma5
                    _sma20Data.value = sma20
                    _sma60Data.value = sma60
                    _sma120Data.value = sma120
                    _rsiData.value = rsi
                    _macdData.value = macd
                    _bollingerBands.value = bollinger
                    _stochasticData.value = stochastic
                    
                    Log.d("ChartViewModel", "⚡ 지표 계산 완료")
                }
            }
            
            // 거래량 데이터 로드 (별도 코루틴)
            viewModelScope.launch { loadVolumeData() }
            
        } catch (e: Exception) {
            Log.e("ChartViewModel", "지표 계산 실패: ${e.message}", e)
            _errorMessage.value = "지표 계산 중 오류가 발생했습니다: ${e.message}"
        }
    }
    
    /**
     * 지표 데이터 초기화
     */
    private fun clearIndicators() {
        _sma5Data.value = emptyList()
        _sma20Data.value = emptyList()
        _sma60Data.value = emptyList()
        _sma120Data.value = emptyList()
        _rsiData.value = emptyList()
        _macdData.value = null
        _bollingerBands.value = null
        _stochasticData.value = null
        
        // 거래량은 별도 처리
        viewModelScope.launch { loadVolumeData() }
    }

    /**
     * 거래량 데이터 로드
     */
    private fun loadVolumeData() {
        viewModelScope.launch {
            try {
                Log.d("ChartViewModel", "거래량 데이터 로드 시작")
                val volumeData = kisRepository.getVolumeData(_stockCode.value)
                
                if (volumeData.isNotEmpty()) {
                    _volumeData.value = volumeData
                    Log.d("ChartViewModel", "거래량 데이터 로드 완료: ${volumeData.size}개")
                } else {
                    Log.w("ChartViewModel", "거래량 데이터가 비어있음")
                    _volumeData.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("ChartViewModel", "거래량 데이터 로드 실패: ${e.message}", e)
                _errorMessage.value = "거래량 데이터 로드 실패: ${e.message}"
                _volumeData.value = emptyList()
            }
        }
    }

    /**
     * 종목 변경
     */
    fun changeStock(newStockCode: String) {
        Log.d("ChartViewModel", "종목 변경: ${_stockCode.value} -> $newStockCode")
        
        // 실시간 업데이트 상태 초기화
        kisRepository.resetRealTimeState()
        
        _stockCode.value = newStockCode
        loadInitialData()
        if (_timeFrame.value != "D") {
            startRealTimeUpdates()
        }
    }

    /**
     * 시간 프레임 변경
     */
    fun changeTimeFrame(newTimeFrame: String) {
        Log.d("ChartViewModel", "시간프레임 변경: ${_timeFrame.value} -> $newTimeFrame")
        
        // 실시간 업데이트 상태 초기화
        kisRepository.resetRealTimeState()
        
        _timeFrame.value = newTimeFrame
        loadInitialData()
        if (newTimeFrame != "D") {
            startRealTimeUpdates()
        }
    }

    /**
     * 지표 표시/숨김 토글 함수들
     */
    fun toggleSMA5() { _showSMA5.value = !_showSMA5.value }
    fun toggleSMA20() { _showSMA20.value = !_showSMA20.value }
    fun toggleSMA60() { _showSMA60.value = !_showSMA60.value }
    fun toggleSMA120() { _showSMA120.value = !_showSMA120.value }
    fun toggleBollingerBands() { _showBollingerBands.value = !_showBollingerBands.value }
    fun toggleRSI() { _showRSI.value = !_showRSI.value }
    fun toggleMACD() { _showMACD.value = !_showMACD.value }
    fun toggleStochastic() { _showStochastic.value = !_showStochastic.value }
    fun toggleVolume() { _showVolume.value = !_showVolume.value }

    /**
     * 지표 설정
     */
    fun setSMA5Visible(visible: Boolean) { _showSMA5.value = visible }
    fun setSMA20Visible(visible: Boolean) { _showSMA20.value = visible }
    fun setSMA60Visible(visible: Boolean) { _showSMA60.value = visible }
    fun setSMA120Visible(visible: Boolean) { _showSMA120.value = visible }
    fun setBollingerBandsVisible(visible: Boolean) { _showBollingerBands.value = visible }
    fun setRSIVisible(visible: Boolean) { _showRSI.value = visible }
    fun setMACDVisible(visible: Boolean) { _showMACD.value = visible }
    fun setStochasticVisible(visible: Boolean) { _showStochastic.value = visible }
    fun setVolumeVisible(visible: Boolean) { _showVolume.value = visible }

    /**
     * 에러 메시지 클리어
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * 데이터 새로고침
     */
    fun refreshData() {
        loadInitialData()
    }
}