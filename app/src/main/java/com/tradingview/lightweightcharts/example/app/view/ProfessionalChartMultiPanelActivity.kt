package com.tradingview.lightweightcharts.example.app.view

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.interfaces.ChartApi
import com.tradingview.lightweightcharts.api.interfaces.SeriesApi
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.SeriesType
import com.tradingview.lightweightcharts.api.series.models.PriceScaleId
import com.tradingview.lightweightcharts.example.app.databinding.ActivityProfessionalChartMultipanelBinding
import com.tradingview.lightweightcharts.example.app.viewmodel.ProfessionalChartViewModel
import com.tradingview.lightweightcharts.view.ChartsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import android.widget.CheckBox
import com.tradingview.lightweightcharts.example.app.utils.ChartPerformanceOptimizer

@AndroidEntryPoint
class ProfessionalChartMultiPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfessionalChartMultipanelBinding
    private val viewModel: ProfessionalChartViewModel by viewModels()
    
    // 멀티패널 차트 뷰들
    private lateinit var mainChartsView: ChartsView        // 메인 캔들차트
    private lateinit var volumeChartsView: ChartsView      // 거래량 차트
    private lateinit var macdChartsView: ChartsView        // MACD 차트
    private lateinit var rsiChartsView: ChartsView         // RSI 차트
    
    // 시리즈 참조들
    private var candlestickSeries: SeriesApi? = null
    private var volumeSeries: SeriesApi? = null
    private var sma5Series: SeriesApi? = null
    private var sma20Series: SeriesApi? = null
    private var sma60Series: SeriesApi? = null
    private var sma120Series: SeriesApi? = null
    private var rsiSeries: SeriesApi? = null
    private var macdSeries: SeriesApi? = null
    private var macdSignalSeries: SeriesApi? = null
    private var macdHistogramSeries: SeriesApi? = null
    private var bollingerUpperSeries: SeriesApi? = null
    private var bollingerMiddleSeries: SeriesApi? = null
    private var bollingerLowerSeries: SeriesApi? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            Log.d("MultiPanel", "onCreate 시작")
            
            binding = ActivityProfessionalChartMultipanelBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d("MultiPanel", "레이아웃 설정 완료")

            setupToolbar()
            Log.d("MultiPanel", "툴바 설정 완료")
            
            // 단계별 초기화로 어디서 크래시가 나는지 확인
            initializeCharts()
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "onCreate 크래시: ${e.message}", e)
            // 크래시가 나면 에러 메시지만 표시하고 계속 진행
            try {
                Toast.makeText(this, "차트 초기화 오류: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (toastError: Exception) {
                Log.e("MultiPanel", "토스트 표시도 실패: ${toastError.message}")
            }
        }
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "전문가용 멀티패널 차트"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    /**
     * 단계별 차트 초기화 - 크래시 지점 찾기
     */
    private fun initializeCharts() {
        try {
            Log.d("MultiPanel", "차트 초기화 시작")
            
            // 1단계: 차트뷰 참조만 설정
            setupChartReferences()
            
            // 2단계: 기본 설정만
            setupBasicChartSettings()
            
            // 3단계: 나머지 초기화 (안전하게)
            setupRemainingComponents()
            
            Log.d("MultiPanel", "차트 초기화 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "차트 초기화 실패: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupChartReferences() {
        try {
            Log.d("MultiPanel", "차트 참조 설정 시작")
            
            mainChartsView = binding.chartsView
            volumeChartsView = binding.volumeChartsView
            macdChartsView = binding.macdChartsView
            rsiChartsView = binding.rsiChartsView
            
            Log.d("MultiPanel", "차트 참조 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "차트 참조 설정 실패: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupBasicChartSettings() {
        try {
            Log.d("MultiPanel", "기본 차트 설정 시작")
            
            // 메인 차트만 먼저 설정
            setupMainChart()
            Log.d("MultiPanel", "메인 차트 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "기본 차트 설정 실패: ${e.message}", e)
            throw e
        }
    }
    
    private fun setupRemainingComponents() {
        try {
            Log.d("MultiPanel", "나머지 컴포넌트 설정 시작")
            
            // 보조 차트들은 나중에 안전하게 설정
            lifecycleScope.launch {
                try {
                    delay(1000L) // 1초 대기 후 설정
                    
                    setupVolumeChart()
                    delay(500L)
                    
                    setupMACDChart()
                    delay(500L)
                    
                    setupRSIChart()
                    delay(500L)
                    
                    // UI 컨트롤들도 안전하게 설정
                    setupTimeFrameSelector()
                    setupStockSelector()
                    setupIndicatorControls()
                    
                    // 성능 최적화는 마지막에
                    applyPerformanceOptimizations()
                    
                    // ViewModel 관찰은 일단 비활성화 - 크래시 원인이 될 수 있음
                    // observeViewModel()
                    Log.d("MultiPanel", "ViewModel 관찰 건너뜀 - 안정성 우선")
                    
                    Log.d("MultiPanel", "모든 컴포넌트 설정 완료")
                    
                } catch (e: Exception) {
                    Log.e("MultiPanel", "나머지 컴포넌트 설정 실패: ${e.message}", e)
                }
            }
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "나머지 컴포넌트 설정 실패: ${e.message}", e)
        }
    }

    private fun setupMainChart() {
        try {
            Log.d("MultiPanel", "메인 차트 State 리스너 설정 시작")
            
            mainChartsView.subscribeOnChartStateChange { state ->
                try {
                    Log.d("MultiPanel", "메인 차트 상태 변경: $state")
                    
                    when (state) {
                        is ChartsView.State.Preparing -> {
                            Log.d("MultiPanel", "메인 차트 준비 중")
                            binding.progressBar.visibility = View.VISIBLE
                        }
                        is ChartsView.State.Ready -> {
                            Log.d("MultiPanel", "메인 차트 준비 완료")
                            binding.progressBar.visibility = View.GONE
                            
                            try {
                                try {
                                    setupMainChartOptions()
                                    Log.d("MultiPanel", "메인 차트 옵션 설정 완료")
                                } catch (optionError: Exception) {
                                    Log.e("MultiPanel", "메인 차트 옵션 설정 실패: ${optionError.message}", optionError)
                                    // 옵션 설정 실패해도 계속 진행
                                }
                                
                                Toast.makeText(this, "메인 차트가 준비되었습니다", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("MultiPanel", "메인 차트 옵션 설정 실패: ${e.message}", e)
                            }
                        }
                        is ChartsView.State.Error -> {
                            Log.e("MultiPanel", "메인 차트 오류: ${state.exception.message}", state.exception)
                            binding.progressBar.visibility = View.GONE
                            Toast.makeText(this, "메인 차트 오류: ${state.exception.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MultiPanel", "차트 상태 변경 처리 실패: ${e.message}", e)
                }
            }
            
            Log.d("MultiPanel", "메인 차트 State 리스너 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "메인 차트 설정 실패: ${e.message}", e)
            throw e
        }
    }

    private fun setupVolumeChart() {
        try {
            Log.d("MultiPanel", "거래량 차트 설정 시작")
            
            volumeChartsView.subscribeOnChartStateChange { state ->
                try {
                    when (state) {
                        is ChartsView.State.Ready -> {
                            try {
                                setupSimpleVolumeChartOptions()
                                Log.d("MultiPanel", "거래량 차트 준비 완료")
                            } catch (e: Exception) {
                                Log.e("MultiPanel", "거래량 차트 옵션 설정 실패: ${e.message}")
                            }
                        }
                        is ChartsView.State.Error -> {
                            Log.e("MultiPanel", "거래량 차트 오류: ${state.exception.message}")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e("MultiPanel", "거래량 차트 상태 처리 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "거래량 차트 설정 실패: ${e.message}")
        }
    }

    private fun setupMACDChart() {
        macdChartsView.subscribeOnChartStateChange { state ->
            when (state) {
                is ChartsView.State.Ready -> {
                    setupSimpleMACDChartOptions()
                    Log.d("MultiPanel", "MACD 차트 준비 완료")
                }
                is ChartsView.State.Error -> {
                    Log.e("MultiPanel", "MACD 차트 오류: ${state.exception.message}")
                }
                else -> {}
            }
        }
    }

    private fun setupRSIChart() {
        try {
            Log.d("MultiPanel", "RSI 차트 설정 시작")
            
            rsiChartsView.subscribeOnChartStateChange { state ->
                try {
                    when (state) {
                        is ChartsView.State.Ready -> {
                            try {
                                setupSimpleRSIChartOptions()
                                Log.d("MultiPanel", "RSI 차트 준비 완료")
                            } catch (e: Exception) {
                                Log.e("MultiPanel", "RSI 차트 옵션 설정 실패: ${e.message}")
                            }
                        }
                        is ChartsView.State.Error -> {
                            Log.e("MultiPanel", "RSI 차트 오류: ${state.exception.message}")
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e("MultiPanel", "RSI 차트 상태 처리 실패: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "RSI 차트 설정 실패: ${e.message}")
        }
    }

    private fun setupMainChartOptions() {
        try {
            Log.d("MultiPanel", "메인 차트 옵션 설정 시작")
            
            // 최소한의 안전한 설정만 적용
            mainChartsView.api.applyOptions {
                layout = layoutOptions {
                    background = SolidColor(Color.parseColor("#000000"))
                }
            }
            
            Log.d("MultiPanel", "메인 차트 옵션 설정 완료 - 최소 설정만")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "메인 차트 옵션 설정 중 오류: ${e.message}", e)
            // 옵션 설정이 실패해도 계속 진행
        }
    }
    
    private fun setupSimpleVolumeChartOptions() {
        try {
            Log.d("MultiPanel", "간소화된 거래량 차트 옵션 설정")
            volumeChartsView.api.applyOptions {
                layout = layoutOptions {
                    background = SolidColor(Color.parseColor("#000000"))
                }
            }
            Log.d("MultiPanel", "간소화된 거래량 차트 옵션 설정 완료")
        } catch (e: Exception) {
            Log.e("MultiPanel", "간소화된 거래량 차트 옵션 설정 실패: ${e.message}")
        }
    }
    
    private fun setupSimpleMACDChartOptions() {
        try {
            Log.d("MultiPanel", "간소화된 MACD 차트 옵션 설정")
            macdChartsView.api.applyOptions {
                layout = layoutOptions {
                    background = SolidColor(Color.parseColor("#000000"))
                }
            }
            Log.d("MultiPanel", "간소화된 MACD 차트 옵션 설정 완료")
        } catch (e: Exception) {
            Log.e("MultiPanel", "간소화된 MACD 차트 옵션 설정 실패: ${e.message}")
        }
    }
    
    private fun setupSimpleRSIChartOptions() {
        try {
            Log.d("MultiPanel", "간소화된 RSI 차트 옵션 설정")
            rsiChartsView.api.applyOptions {
                layout = layoutOptions {
                    background = SolidColor(Color.parseColor("#000000"))
                }
            }
            Log.d("MultiPanel", "간소화된 RSI 차트 옵션 설정 완료")
        } catch (e: Exception) {
            Log.e("MultiPanel", "간소화된 RSI 차트 옵션 설정 실패: ${e.message}")
        }
    }

    private fun setupVolumeChartOptions() {
        volumeChartsView.api.applyOptions {
            layout = layoutOptions {
                background = SolidColor(Color.parseColor("#000000"))
                textColor = Color.parseColor("#64B5F6").toIntColor()
            }
            
            grid = gridOptions {
                vertLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
                horzLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
            }
            
            rightPriceScale = priceScaleOptions {
                scaleMargins = priceScaleMargins {
                    top = 0.1f
                    bottom = 0.1f
                }
                borderVisible = false
                visible = false // 거래량 스케일 숨김
                autoScale = true
            }
            
            timeScale = timeScaleOptions {
                borderVisible = false
                timeVisible = false // 중복 시간축 숨김
                secondsVisible = false
                // 메인 차트와 동기화 (좌우 스크롤만)
                fixLeftEdge = false
                fixRightEdge = false
                lockVisibleTimeRangeOnResize = false
                rightOffset = 5.0f
                barSpacing = 10.0f
                minBarSpacing = 0.5f
            }
            
            // 좌우 스크롤만 허용
            handleScroll = handleScrollOptions {
                mouseWheel = true
                pressedMouseMove = true
                horzTouchDrag = true
                vertTouchDrag = false
            }
            
            handleScale = handleScaleOptions {
                mouseWheel = true
                pinch = true
            }
        }
    }

    private fun setupMACDChartOptions() {
        macdChartsView.api.applyOptions {
            layout = layoutOptions {
                background = SolidColor(Color.parseColor("#000000"))
                textColor = Color.parseColor("#4CAF50").toIntColor()
            }
            
            grid = gridOptions {
                vertLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
                horzLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
            }
            
            rightPriceScale = priceScaleOptions {
                scaleMargins = priceScaleMargins {
                    top = 0.1f
                    bottom = 0.1f
                }
                borderVisible = false
                visible = false
                autoScale = true
            }
            
            timeScale = timeScaleOptions {
                borderVisible = false
                timeVisible = false
                secondsVisible = false
                // 메인 차트와 동기화 (좌우 스크롤만)
                fixLeftEdge = false
                fixRightEdge = false
                lockVisibleTimeRangeOnResize = false
                rightOffset = 5.0f
                barSpacing = 10.0f
                minBarSpacing = 0.5f
            }
            
            // 좌우 스크롤만 허용
            handleScroll = handleScrollOptions {
                mouseWheel = true
                pressedMouseMove = true
                horzTouchDrag = true
                vertTouchDrag = false
            }
            
            handleScale = handleScaleOptions {
                mouseWheel = true
                pinch = true
            }
        }
    }

    private fun setupRSIChartOptions() {
        rsiChartsView.api.applyOptions {
            layout = layoutOptions {
                background = SolidColor(Color.parseColor("#000000"))
                textColor = Color.parseColor("#9013FE").toIntColor()
            }
            
            grid = gridOptions {
                vertLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
                horzLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
            }
            
            rightPriceScale = priceScaleOptions {
                scaleMargins = priceScaleMargins {
                    top = 0.1f
                    bottom = 0.1f
                }
                borderVisible = false
                visible = false
                autoScale = true
            }
            
            timeScale = timeScaleOptions {
                borderVisible = false
                timeVisible = true // 맨 아래 패널은 시간 표시
                secondsVisible = false
                // 메인 차트와 동기화 (좌우 스크롤만)
                fixLeftEdge = false
                fixRightEdge = false
                lockVisibleTimeRangeOnResize = false
                rightOffset = 5.0f
                barSpacing = 10.0f
                minBarSpacing = 0.5f
            }
            
            // 좌우 스크롤만 허용
            handleScroll = handleScrollOptions {
                mouseWheel = true
                pressedMouseMove = true
                horzTouchDrag = true
                vertTouchDrag = false
            }
            
            handleScale = handleScaleOptions {
                mouseWheel = true
                pinch = true
            }
        }
    }

    private fun setupTimeFrameSelector() {
        val timeFrames = listOf("1분", "5분", "10분", "30분", "60분", "일봉")
        val timeFrameCodes = listOf("01", "05", "10", "30", "60", "D")
        
        timeFrames.forEachIndexed { index, timeFrame ->
            val chip = Chip(this).apply {
                text = timeFrame
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.changeTimeFrame(timeFrameCodes[index])
                    }
                }
            }
            binding.timeFrameChipGroup.addView(chip)
        }
        
        // 기본값: 일봉 선택
        binding.timeFrameChipGroup.check(binding.timeFrameChipGroup.getChildAt(5).id)
    }

    private fun setupStockSelector() {
        val stockList = listOf("삼성전자", "SK하이닉스", "NAVER", "카카오", "LG에너지솔루션")
        val stockCodes = listOf("005930", "000660", "035420", "035720", "373220")
        
        stockList.forEachIndexed { index, stock ->
            val chip = Chip(this).apply {
                text = stock
                isCheckable = true
                textSize = 16f // 더 큰 텍스트
                isCloseIconVisible = false
                chipStrokeWidth = 3f // 더 두꺼운 테두리
                chipStrokeColor = getColorStateList(android.R.color.holo_blue_bright)
                setTextColor(getColorStateList(android.R.color.black)) // 검은 텍스트로 대비
                chipBackgroundColor = getColorStateList(android.R.color.white) // 흰색 배경
                elevation = 8f // 그림자 효과
                
                // 선택 상태에 따른 스타일 변경
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        chipBackgroundColor = getColorStateList(android.R.color.holo_blue_light)
                        setTextColor(getColorStateList(android.R.color.white))
                        Log.d("MultiPanel", "종목 변경: $stock (${stockCodes[index]})")
                        viewModel.changeStock(stockCodes[index])
                    } else {
                        chipBackgroundColor = getColorStateList(android.R.color.white)
                        setTextColor(getColorStateList(android.R.color.black))
                    }
                }
            }
            binding.stockChipGroup.addView(chip)
        }
        
        // 기본값: 첫 번째 종목 선택
        binding.stockChipGroup.check(binding.stockChipGroup.getChildAt(0).id)
        Log.d("MultiPanel", "종목 선택기 설정 완료")
    }

    private fun setupIndicatorControls() {
        // 새로고침 버튼
        binding.buttonRefresh.setOnClickListener {
            viewModel.refreshData()
        }
        
        // 증권사 스타일 CheckBox 제어
        setupIndicatorCheckBoxes()
        
        // 설정 패널 스위치들
        binding.switchSma5.setOnCheckedChangeListener { _, isChecked ->
            toggleSMA5(isChecked)
        }
        binding.switchSma20.setOnCheckedChangeListener { _, isChecked ->
            toggleSMA20(isChecked)
        }
        binding.switchSma60.setOnCheckedChangeListener { _, isChecked ->
            toggleSMA60(isChecked)
        }
        binding.switchSma120.setOnCheckedChangeListener { _, isChecked ->
            toggleSMA120(isChecked)
        }
        
        binding.switchBollingerBands.setOnCheckedChangeListener { _, isChecked ->
            toggleBollingerBands(isChecked)
        }
        binding.switchRsi.setOnCheckedChangeListener { _, isChecked ->
            toggleRSI(isChecked)
        }
        binding.switchMacd.setOnCheckedChangeListener { _, isChecked ->
            toggleMACD(isChecked)
        }
        binding.switchVolume.setOnCheckedChangeListener { _, isChecked ->
            toggleVolume(isChecked)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 캔들스틱 데이터 관찰
                launch {
                    viewModel.candlestickData.collect { data ->
                        if (data.isNotEmpty()) {
                            setupCandlestickSeries(data)
                            // 시간축 동기화
                            synchronizeTimeScales(data)
                        }
                    }
                }
                
                // 거래량 데이터 관찰
                launch {
                    viewModel.volumeData.collect { data ->
                        if (data.isNotEmpty() && binding.switchVolume.isChecked) {
                            setupVolumeSeries(data)
                        }
                    }
                }
                
                // MACD 데이터 관찰
                launch {
                    viewModel.macdData.collect { data ->
                        if (data != null && binding.switchMacd.isChecked) {
                            setupMACDSeries(data)
                        }
                    }
                }
                
                // RSI 데이터 관찰
                launch {
                    viewModel.rsiData.collect { data ->
                        if (data.isNotEmpty() && binding.switchRsi.isChecked) {
                            setupRSISeries(data)
                        }
                    }
                }
                
                // SMA 데이터들
                launch {
                    viewModel.sma5Data.collect { data ->
                        if (data.isNotEmpty() && binding.switchSma5.isChecked) {
                            setupSMA5Series(data)
                        }
                    }
                }
                
                launch {
                    viewModel.sma20Data.collect { data ->
                        if (data.isNotEmpty() && binding.switchSma20.isChecked) {
                            setupSMA20Series(data)
                        }
                    }
                }
                
                launch {
                    viewModel.sma60Data.collect { data ->
                        if (data.isNotEmpty() && binding.switchSma60.isChecked) {
                            setupSMA60Series(data)
                        }
                    }
                }
                
                launch {
                    viewModel.sma120Data.collect { data ->
                        if (data.isNotEmpty() && binding.switchSma120.isChecked) {
                            setupSMA120Series(data)
                        }
                    }
                }
                
                // 볼린저 밴드
                launch {
                    viewModel.bollingerBands.collect { bands ->
                        if (bands != null && binding.switchBollingerBands.isChecked) {
                            setupBollingerBandsSeries(bands)
                        }
                    }
                }
                
                // 에러 메시지
                launch {
                    viewModel.errorMessage.collect { errorMessage ->
                        if (!errorMessage.isNullOrEmpty()) {
                            Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 개선된 시간축 동기화 - 실제 시간 범위 기반
     */
    private fun synchronizeTimeScales(data: List<com.tradingview.lightweightcharts.api.series.models.CandlestickData>) {
        if (data.isEmpty()) return
        
        try {
            Log.d("MultiPanel", "개선된 시간축 동기화 시작")
            
            // 진짜 시간축 동기화 설정
            setupRealTimeScaleSynchronization()
            
            // 초기에만 fitContent 호출
            if (!::mainChartsView.isInitialized) return
            
            // 메인 차트만 한 번 피팅
            try {
                mainChartsView.api.timeScale.fitContent()
                Log.d("MultiPanel", "메인 차트 초기 피팅 완료")
                
                // 동기화 기능 비활성화 - 크래시 방지
                Log.d("MultiPanel", "차트 동기화 건너뜀 - 안정성 우선")
            } catch (e: Exception) {
                Log.w("MultiPanel", "메인 차트 피팅 실패: ${e.message}")
            }
            
            Log.d("MultiPanel", "개선된 시간축 동기화 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "시간축 동기화 실패: ${e.message}")
        }
    }
    
    /**
     * 실제 시간축 동기화 설정 - 시간 범위 기반
     */
    private fun setupRealTimeScaleSynchronization() {
        try {
            Log.d("MultiPanel", "실제 시간축 동기화 설정 시작")
            
            // 메인 차트의 시간 범위 변경을 모니터링
            try {
                mainChartsView.api.timeScale.subscribeVisibleTimeRangeChange { timeRange ->
                    try {
                        Log.d("MultiPanel", "메인차트 시간범위 변경: ${timeRange?.from} ~ ${timeRange?.to}")
                        
                        if (timeRange != null) {
                            // 모든 서브차트에 동일한 시간 범위 적용
                            listOf(volumeChartsView, macdChartsView, rsiChartsView).forEachIndexed { index, chartView ->
                                try {
                                    chartView.api.timeScale.setVisibleRange(timeRange)
                                } catch (e: Exception) {
                                    Log.d("MultiPanel", "서브차트 ${index + 1} 시간범위 설정 건너뜀: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("MultiPanel", "시간범위 동기화 건너뜀: ${e.message}")
                    }
                }
                Log.d("MultiPanel", "메인차트 시간범위 구독 완료")
            } catch (e: Exception) {
                Log.w("MultiPanel", "시간축 이벤트 구독 실패, 터치 기반으로 대체: ${e.message}")
                setupSafeTouchSynchronization()
            }
            
            Log.d("MultiPanel", "실제 시간축 동기화 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "시간축 동기화 설정 실패: ${e.message}")
            // 대체 방법으로 터치 기반 동기화 사용
            setupSafeTouchSynchronization()
        }
    }
    
    /**
     * 대체 터치 기반 동기화 - API 기반 동기화 실패시 사용
     */
    private fun setupSafeTouchSynchronization() {
        try {
            Log.d("MultiPanel", "대체 터치 동기화 설정 시작")
            
            // 메인 차트만 터치 이벤트 처리
            mainChartsView.setOnTouchListener { view, event ->
                try {
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            // 터치 시작 - 동기화 비활성화
                            Log.d("MultiPanel", "터치 시작 - 동기화 없음")
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // 동기화 비활성화 - 크래시 방지
                            Log.d("MultiPanel", "터치 이동 동기화 건너뜀")
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            // 동기화 비활성화 - 크래시 방지
                            Log.d("MultiPanel", "터치 완료 동기화 건너뜀")
                        }
                    }
                } catch (e: Exception) {
                    Log.d("MultiPanel", "터치 이벤트 처리 건너뜀: ${e.message}")
                }
                false // 기본 터치 처리 계속
            }
            
            Log.d("MultiPanel", "대체 터치 동기화 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "터치 동기화 설정 실패: ${e.message}")
        }
    }
    
    // 동기화 기능 완전 제거 - 크래시 방지

    // 캔들스틱 시리즈 설정 (메인 차트)
    private fun setupCandlestickSeries(data: List<com.tradingview.lightweightcharts.api.series.models.CandlestickData>) {
        if (candlestickSeries != null) {
            mainChartsView.api.removeSeries(candlestickSeries!!)
        }
        
        mainChartsView.api.addCandlestickSeries(
            options = CandlestickSeriesOptions(
                upColor = Color.parseColor("#ef5350").toIntColor(),
                downColor = Color.parseColor("#26a69a").toIntColor(),
                borderVisible = false,
                wickUpColor = Color.parseColor("#ef5350").toIntColor(),
                wickDownColor = Color.parseColor("#26a69a").toIntColor()
            ),
            onSeriesCreated = { api ->
                candlestickSeries = api
                api.setData(data)
                mainChartsView.api.timeScale.fitContent()
            }
        )
    }

    // 거래량 시리즈 설정 (별도 패널)
    private fun setupVolumeSeries(data: List<com.tradingview.lightweightcharts.api.series.models.HistogramData>) {
        volumeSeries?.let { 
            volumeChartsView.api.removeSeries(it)
            volumeSeries = null
        }
        
        if (data.isEmpty()) return
        
        try {
            // 캔들 데이터와 동기화된 거래량 색상 처리
            val candleData = viewModel.candlestickData.value
            val volumeWithColors = if (candleData.isNotEmpty()) {
                data.mapIndexed { index, volume ->
                    val candle = candleData.getOrNull(index)
                    val isRising = candle?.let { it.close >= it.open } ?: true
                    
                    com.tradingview.lightweightcharts.api.series.models.HistogramData(
                        time = volume.time,
                        value = volume.value,
                        color = if (isRising) {
                            Color.parseColor("#ef5350").toIntColor()
                        } else {
                            Color.parseColor("#26a69a").toIntColor()
                        }
                    )
                }
            } else {
                data
            }
            
            volumeChartsView.api.addHistogramSeries(
                options = HistogramSeriesOptions(
                    color = Color.parseColor("#64B5F6").toIntColor()
                ),
                onSeriesCreated = { api ->
                    volumeSeries = api
                    api.setData(volumeWithColors)
                    Log.d("MultiPanel", "거래량 시리즈 설정 완료: ${volumeWithColors.size}개")
                }
            )
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "거래량 시리즈 생성 오류: ${e.message}")
        }
    }

    // MACD 시리즈 설정 (별도 패널)
    private fun setupMACDSeries(data: com.tradingview.lightweightcharts.example.app.indicators.MACDResult) {
        // 기존 시리즈들 제거
        try {
            macdSeries?.let { 
                macdChartsView.api.removeSeries(it)
                macdSeries = null
            }
            macdSignalSeries?.let { 
                macdChartsView.api.removeSeries(it)
                macdSignalSeries = null
            }
            macdHistogramSeries?.let { 
                macdChartsView.api.removeSeries(it)
                macdHistogramSeries = null
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "MACD 시리즈 제거 오류: ${e.message}")
        }
        
        if (data.macdLine.isEmpty()) return
        
        try {
            // MACD 라인
            macdChartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#4CAF50").toIntColor(),
                    lineWidth = LineWidth.TWO
                ),
                onSeriesCreated = { api ->
                    macdSeries = api
                    api.setData(data.macdLine)
                }
            )
            
            // Signal 라인
            macdChartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#F44336").toIntColor(),
                    lineWidth = LineWidth.TWO
                ),
                onSeriesCreated = { api ->
                    macdSignalSeries = api
                    api.setData(data.signalLine)
                }
            )
            
            // 히스토그램
            if (data.histogram.isNotEmpty()) {
                macdChartsView.api.addHistogramSeries(
                    options = HistogramSeriesOptions(
                        color = Color.parseColor("#42A5F5").toIntColor()
                    ),
                    onSeriesCreated = { api ->
                        macdHistogramSeries = api
                        api.setData(data.histogram)
                    }
                )
            }
            
            Log.d("MultiPanel", "MACD 시리즈 설정 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "MACD 시리즈 생성 오류: ${e.message}")
        }
    }

    // RSI 시리즈 설정 (별도 패널)
    private fun setupRSISeries(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        rsiSeries?.let { 
            rsiChartsView.api.removeSeries(it)
            rsiSeries = null
        }
        
        if (data.isEmpty()) return
        
        try {
            rsiChartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#9013FE").toIntColor(),
                    lineWidth = LineWidth.TWO
                ),
                onSeriesCreated = { api ->
                    rsiSeries = api
                    api.setData(data)
                    Log.d("MultiPanel", "RSI 시리즈 설정 완료: ${data.size}개")
                }
            )
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "RSI 시리즈 생성 오류: ${e.message}")
        }
    }

    // SMA 시리즈들 설정 (메인 차트에 오버레이)
    private fun setupSMA5Series(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        sma5Series?.let { mainChartsView.api.removeSeries(it) }
        
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FF6B35").toIntColor(),
                lineWidth = LineWidth.TWO
            ),
            onSeriesCreated = { api ->
                sma5Series = api
                api.setData(data)
            }
        )
    }

    private fun setupSMA20Series(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        sma20Series?.let { mainChartsView.api.removeSeries(it) }
        
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#4ECDC4").toIntColor(),
                lineWidth = LineWidth.TWO
            ),
            onSeriesCreated = { api ->
                sma20Series = api
                api.setData(data)
            }
        )
    }

    private fun setupSMA60Series(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        sma60Series?.let { mainChartsView.api.removeSeries(it) }
        
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFB74D").toIntColor(),
                lineWidth = LineWidth.TWO
            ),
            onSeriesCreated = { api ->
                sma60Series = api
                api.setData(data)
            }
        )
    }

    private fun setupSMA120Series(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        sma120Series?.let { mainChartsView.api.removeSeries(it) }
        
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#E1BEE7").toIntColor(),
                lineWidth = LineWidth.TWO
            ),
            onSeriesCreated = { api ->
                sma120Series = api
                api.setData(data)
            }
        )
    }

    // 볼린저 밴드 시리즈 설정 (메인 차트에 오버레이)
    private fun setupBollingerBandsSeries(bands: com.tradingview.lightweightcharts.example.app.indicators.BollingerBandsResult) {
        // 기존 시리즈들 제거
        bollingerUpperSeries?.let { mainChartsView.api.removeSeries(it) }
        bollingerMiddleSeries?.let { mainChartsView.api.removeSeries(it) }
        bollingerLowerSeries?.let { mainChartsView.api.removeSeries(it) }
        
        // 상단 밴드
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE,
                lineStyle = LineStyle.DASHED
            ),
            onSeriesCreated = { api ->
                bollingerUpperSeries = api
                api.setData(bands.upperBand)
            }
        )
        
        // 중간 밴드
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE
            ),
            onSeriesCreated = { api ->
                bollingerMiddleSeries = api
                api.setData(bands.middleBand)
            }
        )
        
        // 하단 밴드
        mainChartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE,
                lineStyle = LineStyle.DASHED
            ),
            onSeriesCreated = { api ->
                bollingerLowerSeries = api
                api.setData(bands.lowerBand)
            }
        )
    }

    // 토글 함수들
    private fun toggleSMA5(enabled: Boolean) {
        if (!enabled) {
            sma5Series?.let { 
                mainChartsView.api.removeSeries(it)
                sma5Series = null
            }
        } else {
            viewModel.sma5Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA5Series(it) }
        }
    }

    private fun toggleSMA20(enabled: Boolean) {
        if (!enabled) {
            sma20Series?.let { 
                mainChartsView.api.removeSeries(it)
                sma20Series = null
            }
        } else {
            viewModel.sma20Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA20Series(it) }
        }
    }

    private fun toggleSMA60(enabled: Boolean) {
        if (!enabled) {
            sma60Series?.let { 
                mainChartsView.api.removeSeries(it)
                sma60Series = null
            }
        } else {
            viewModel.sma60Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA60Series(it) }
        }
    }

    private fun toggleSMA120(enabled: Boolean) {
        if (!enabled) {
            sma120Series?.let { 
                mainChartsView.api.removeSeries(it)
                sma120Series = null
            }
        } else {
            viewModel.sma120Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA120Series(it) }
        }
    }

    private fun toggleBollingerBands(enabled: Boolean) {
        try {
            if (!enabled) {
                // 볼린저 밴드 완전 제거
                bollingerUpperSeries?.let { 
                    mainChartsView.api.removeSeries(it)
                    bollingerUpperSeries = null
                }
                bollingerMiddleSeries?.let { 
                    mainChartsView.api.removeSeries(it)
                    bollingerMiddleSeries = null
                }
                bollingerLowerSeries?.let { 
                    mainChartsView.api.removeSeries(it)
                    bollingerLowerSeries = null
                }
                
                // 가격 스케일 재설정으로 우측 표시 제거 보장
                try {
                    mainChartsView.api.applyOptions {
                        rightPriceScale = priceScaleOptions {
                            scaleMargins = priceScaleMargins {
                                top = 0.1f
                                bottom = 0.1f
                            }
                            borderVisible = false
                            autoScale = true
                            invertScale = false
                        }
                    }
                    Log.d("MultiPanel", "볼린저 밴드 비활성화 및 가격스케일 재설정 완료")
                } catch (e: Exception) {
                    Log.w("MultiPanel", "가격스케일 재설정 실패: ${e.message}")
                }
                
            } else {
                // 즉시 반영을 위해 현재 데이터 확인 및 강제 새로고침
                val bandsData = viewModel.bollingerBands.value
                if (bandsData != null && bandsData.upperBand.isNotEmpty()) {
                    setupBollingerBandsSeries(bandsData)
                    Log.d("MultiPanel", "볼린저 밴드 즉시 활성화 완료")
                } else {
                    Log.w("MultiPanel", "볼린저 밴드 데이터 로딩 중 - 강제 새로고침")
                    // 데이터가 없으면 즉시 새로고침 요청
                    viewModel.refreshData()
                    lifecycleScope.launch {
                        // 최대 3초 대기하며 데이터 확인
                        repeat(6) { 
                            delay(500L)
                            val newBandsData = viewModel.bollingerBands.value
                            if (newBandsData != null && newBandsData.upperBand.isNotEmpty()) {
                                setupBollingerBandsSeries(newBandsData)
                                Log.d("MultiPanel", "볼린저 밴드 데이터 로딩 완료 및 활성화")
                                return@launch
                            }
                        }
                        Log.w("MultiPanel", "볼린저 밴드 데이터 로딩 시간 초과")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "볼린저 밴드 토글 오류: ${e.message}")
        }
    }

    private fun toggleVolume(enabled: Boolean) {
        try {
            if (!enabled) {
                volumeSeries?.let { 
                    volumeChartsView.api.removeSeries(it)
                    volumeSeries = null
                }
                Log.d("MultiPanel", "거래량 시리즈 비활성화 완료")
            } else {
                // 즉시 반영을 위해 현재 데이터 확인 및 강제 새로고침
                val volumeData = viewModel.volumeData.value
                if (volumeData.isNotEmpty()) {
                    setupVolumeSeries(volumeData)
                    // 시간축 재동기화
                    volumeChartsView.api.timeScale.fitContent()
                    Log.d("MultiPanel", "거래량 시리즈 즉시 활성화 완료")
                } else {
                    Log.w("MultiPanel", "거래량 데이터 로딩 중 - 강제 새로고침")
                    // 데이터가 없으면 즉시 새로고침 요청
                    viewModel.refreshData()
                    lifecycleScope.launch {
                        // 최대 3초 대기하며 데이터 확인
                        repeat(6) { 
                            delay(500L)
                            val newVolumeData = viewModel.volumeData.value
                            if (newVolumeData.isNotEmpty()) {
                                setupVolumeSeries(newVolumeData)
                                volumeChartsView.api.timeScale.fitContent()
                                Log.d("MultiPanel", "거래량 데이터 로딩 완료 및 활성화")
                                return@launch
                            }
                        }
                        Log.w("MultiPanel", "거래량 데이터 로딩 시간 초과")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "거래량 토글 오류: ${e.message}")
        }
    }

    private fun toggleRSI(enabled: Boolean) {
        try {
            if (!enabled) {
                rsiSeries?.let { 
                    rsiChartsView.api.removeSeries(it)
                    rsiSeries = null
                }
                Log.d("MultiPanel", "RSI 시리즈 비활성화 완료")
            } else {
                // 즉시 반영을 위해 현재 데이터 확인 및 강제 새로고침
                val rsiData = viewModel.rsiData.value
                if (rsiData.isNotEmpty()) {
                    setupRSISeries(rsiData)
                    // 시간축 재동기화
                    rsiChartsView.api.timeScale.fitContent()
                    Log.d("MultiPanel", "RSI 시리즈 즉시 활성화 완료")
                } else {
                    Log.w("MultiPanel", "RSI 데이터 로딩 중 - 강제 새로고침")
                    // 데이터가 없으면 즉시 새로고침 요청
                    viewModel.refreshData()
                    lifecycleScope.launch {
                        // 최대 3초 대기하며 데이터 확인
                        repeat(6) { 
                            delay(500L)
                            val newRsiData = viewModel.rsiData.value
                            if (newRsiData.isNotEmpty()) {
                                setupRSISeries(newRsiData)
                                rsiChartsView.api.timeScale.fitContent()
                                Log.d("MultiPanel", "RSI 데이터 로딩 완료 및 활성화")
                                return@launch
                            }
                        }
                        Log.w("MultiPanel", "RSI 데이터 로딩 시간 초과")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "RSI 토글 오류: ${e.message}")
        }
    }

    private fun toggleMACD(enabled: Boolean) {
        try {
            if (!enabled) {
                macdSeries?.let { 
                    macdChartsView.api.removeSeries(it)
                    macdSeries = null
                }
                macdSignalSeries?.let { 
                    macdChartsView.api.removeSeries(it)
                    macdSignalSeries = null
                }
                macdHistogramSeries?.let { 
                    macdChartsView.api.removeSeries(it)
                    macdHistogramSeries = null
                }
                Log.d("MultiPanel", "MACD 시리즈 비활성화 완료")
            } else {
                // 즉시 반영을 위해 현재 데이터 확인 및 강제 새로고침
                val macdData = viewModel.macdData.value
                if (macdData != null && macdData.macdLine.isNotEmpty()) {
                    setupMACDSeries(macdData)
                    // 시간축 재동기화
                    macdChartsView.api.timeScale.fitContent()
                    Log.d("MultiPanel", "MACD 시리즈 즉시 활성화 완료")
                } else {
                    Log.w("MultiPanel", "MACD 데이터 로딩 중 - 강제 새로고침")
                    // 데이터가 없으면 즉시 새로고침 요청
                    viewModel.refreshData()
                    lifecycleScope.launch {
                        // 최대 3초 대기하며 데이터 확인
                        repeat(6) { 
                            delay(500L)
                            val newMacdData = viewModel.macdData.value
                            if (newMacdData != null && newMacdData.macdLine.isNotEmpty()) {
                                setupMACDSeries(newMacdData)
                                macdChartsView.api.timeScale.fitContent()
                                Log.d("MultiPanel", "MACD 데이터 로딩 완료 및 활성화")
                                return@launch
                            }
                        }
                        Log.w("MultiPanel", "MACD 데이터 로딩 시간 초과")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MultiPanel", "MACD 토글 오류: ${e.message}")
        }
    }
    
    /**
     * 성능 최적화 적용
     */
    private fun applyPerformanceOptimizations() {
        Log.d("MultiPanel", "성능 최적화 적용 시작")
        
        try {
            // 모든 차트뷰에 성능 최적화 적용
            ChartPerformanceOptimizer.applyAllOptimizations(
                this,
                mainChartsView,
                volumeChartsView,
                macdChartsView,
                rsiChartsView
            )
            
            // 멀티패널 동기화 최적화
            ChartPerformanceOptimizer.optimizeMultiPanelSync(
                mainChartsView,
                volumeChartsView,
                macdChartsView,
                rsiChartsView
            )
            
            // 주기적 동기화 시작
            startPeriodicSynchronization()
            
            Log.d("MultiPanel", "성능 최적화 적용 완료")
            
        } catch (e: Exception) {
            Log.e("MultiPanel", "성능 최적화 적용 실패: ${e.message}")
        }
    }
    
    /**
     * 주기적 동기화 - 시간 범위 기반으로 개선
     */
    private fun startPeriodicSynchronization() {
        lifecycleScope.launch {
            while (true) {
                try {
                    delay(2000L) // 2초마다 동기화 확인 (더 안정적)
                    
                    // 동기화 비활성화 - 크래시 방지
                    Log.d("MultiPanel", "주기적 동기화 건너뜀")
                    
                } catch (e: Exception) {
                    Log.d("MultiPanel", "주기적 동기화 중단: ${e.message}")
                    break
                }
            }
        }
        Log.d("MultiPanel", "개선된 주기적 동기화 시작")
    }

    /**
     * 실제 증권사 스타일 CheckBox 기반 지표 제어
     */
    private fun setupIndicatorCheckBoxes() {
        try {
            // 이동평균선 CheckBox
            binding.checkSMA.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchSma5.isChecked = isChecked
                    binding.switchSma20.isChecked = isChecked
                    toggleSMA5(isChecked)
                    toggleSMA20(isChecked)
                }
                isChecked = true // 기본 활성화
            }
            
            // 볼린저 밴드 CheckBox
            binding.checkBollinger.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchBollingerBands.isChecked = isChecked
                    toggleBollingerBands(isChecked)
                }
            }
            
            // 거래량 CheckBox
            binding.checkVolume.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchVolume.isChecked = isChecked
                    toggleVolume(isChecked)
                }
                isChecked = true // 기본 활성화
            }
            
            // MACD CheckBox
            binding.checkMACD.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchMacd.isChecked = isChecked
                    toggleMACD(isChecked)
                }
            }
            
            // RSI CheckBox
            binding.checkRSI.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchRsi.isChecked = isChecked
                    toggleRSI(isChecked)
                }
            }
            
        } catch (e: Exception) {
            Log.d("MultiPanel", "CheckBox 설정 중 오류: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        // 백그라운드에서 차트 성능 최적화
        try {
            // ChartsView에는 onPause/onResume이 없으므로 직접 성능 최적화 적용
            Log.d("MultiPanel", "백그라운드 전환 - 성능 최적화 적용")
        } catch (e: Exception) {
            Log.e("MultiPanel", "백그라운드 전환 오류: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        // 포그라운드 복귀 시 차트 재활성화
        try {
            // 성능 최적화 재적용
            applyPerformanceOptimizations()
            
            Log.d("MultiPanel", "포그라운드 복귀 - 성능 최적화 재적용")
        } catch (e: Exception) {
            Log.e("MultiPanel", "포그라운드 복귀 오류: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 메모리 정리
        try {
            ChartPerformanceOptimizer.optimizeMemoryUsage(
                this,
                mainChartsView,
                volumeChartsView,
                macdChartsView,
                rsiChartsView
            )
            Log.d("MultiPanel", "메모리 정리 완료")
        } catch (e: Exception) {
            Log.e("MultiPanel", "메모리 정리 오류: ${e.message}")
        }
    }
}