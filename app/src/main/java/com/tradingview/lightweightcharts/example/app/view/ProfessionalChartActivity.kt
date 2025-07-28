package com.tradingview.lightweightcharts.example.app.view

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
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
import com.tradingview.lightweightcharts.example.app.databinding.ActivityProfessionalChartBinding
import com.tradingview.lightweightcharts.example.app.viewmodel.ProfessionalChartViewModel
import com.tradingview.lightweightcharts.view.ChartsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.util.Log
import android.widget.CheckBox

@AndroidEntryPoint
class ProfessionalChartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfessionalChartBinding
    private val viewModel: ProfessionalChartViewModel by viewModels()
    
    // 시리즈 참조들
    private var candlestickSeries: SeriesApi? = null
    private var volumeRedSeries: SeriesApi? = null
    private var volumeBlueSeries: SeriesApi? = null
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
        binding = ActivityProfessionalChartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupChart()
        setupTimeFrameSelector()
        setupStockSelector()
        setupIndicatorControls()
        observeViewModel()
    }

    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "전문가용 차트"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupChart() {
        // 차트 준비 상태 구독
        binding.chartsView.subscribeOnChartStateChange { state ->
            when (state) {
                is ChartsView.State.Preparing -> {
                    binding.progressBar.visibility = View.VISIBLE
                }
                is ChartsView.State.Ready -> {
                    binding.progressBar.visibility = View.GONE
                    setupChartOptions()
                    Toast.makeText(this, "차트가 준비되었습니다", Toast.LENGTH_SHORT).show()
                }
                is ChartsView.State.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "차트 로딩 오류: ${state.exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupChartOptions() {
        binding.chartsView.api.applyOptions {
            layout = layoutOptions {
                background = SolidColor(Color.parseColor("#1e1e1e"))
                textColor = Color.parseColor("#d1d4dc").toIntColor()
            }
            
            grid = gridOptions {
                vertLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
                horzLines = gridLineOptions {
                    color = Color.parseColor("#2B2B43").toIntColor()
                }
            }
            
            crosshair = crosshairOptions {
                vertLine = crosshairLineOptions {
                    color = Color.parseColor("#758696").toIntColor()
                    width = LineWidth.ONE
                    style = LineStyle.LARGE_DASHED
                }
                horzLine = crosshairLineOptions {
                    color = Color.parseColor("#758696").toIntColor()
                    width = LineWidth.ONE
                    style = LineStyle.LARGE_DASHED
                }
            }
            
            // 가격 스케일 설정 - 메인 차트용
            rightPriceScale = priceScaleOptions {
                scaleMargins = priceScaleMargins {
                    top = 0.1f
                    bottom = 0.4f  // 하단에 보조지표 공간 확보
                }
                borderVisible = false
            }
            
            // 시간 스케일 설정
            timeScale = timeScaleOptions {
                borderVisible = false
                timeVisible = true
                secondsVisible = false
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
        
        // 기본값: 일봉 선택 (인덱스 5)
        binding.timeFrameChipGroup.check(binding.timeFrameChipGroup.getChildAt(5).id)
    }

    private fun setupStockSelector() {
        val stockList = listOf("삼성전자", "SK하이닉스", "NAVER", "카카오", "LG에너지솔루션")
        val stockCodes = listOf("005930", "000660", "035420", "035720", "373220")
        
        stockList.forEachIndexed { index, stock ->
            val chip = Chip(this).apply {
                text = stock
                isCheckable = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.changeStock(stockCodes[index])
                    }
                }
            }
            binding.stockChipGroup.addView(chip)
        }
        
        // 기본값: 첫 번째 종목 선택
        binding.stockChipGroup.check(binding.stockChipGroup.getChildAt(0).id)
    }

    private fun setupIndicatorControls() {
        // 지표 설정 패널 토글
        binding.buttonIndicatorSettings.setOnClickListener {
            val panel = binding.indicatorSettingsPanel
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        
        // 새로고침 버튼
        binding.buttonRefresh.setOnClickListener {
            viewModel.refreshData()
        }
        
        // 증권사 스타일 CheckBox 기반 지표 제어
        setupIndicatorCheckBoxes()
        
        // 기존 스위치 리스너들 (설정 패널용)
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
        
        // 보조지표 스위치들
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
                        }
                    }
                }
                
                // SMA 데이터 관찰
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
                
                // 볼린저 밴드 데이터 관찰
                launch {
                    viewModel.bollingerBands.collect { bands ->
                        if (bands != null && binding.switchBollingerBands.isChecked) {
                            setupBollingerBandsSeries(bands)
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
                
                // RSI 데이터 관찰
                launch {
                    viewModel.rsiData.collect { data ->
                        if (data.isNotEmpty() && binding.switchRsi.isChecked) {
                            setupRSISeries(data)
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
                
                // 에러 메시지 관찰
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

    // 캔들스틱 시리즈 설정
    private fun setupCandlestickSeries(data: List<com.tradingview.lightweightcharts.api.series.models.CandlestickData>) {
        if (candlestickSeries != null) {
            binding.chartsView.api.removeSeries(candlestickSeries!!)
        }
        
        binding.chartsView.api.addCandlestickSeries(
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
                binding.chartsView.api.timeScale.fitContent()
            }
        )
    }

    // SMA 시리즈들 설정
    private fun setupSMA5Series(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        if (sma5Series != null) {
            binding.chartsView.api.removeSeries(sma5Series!!)
        }
        
        binding.chartsView.api.addLineSeries(
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
        if (sma20Series != null) {
            binding.chartsView.api.removeSeries(sma20Series!!)
        }
        
        binding.chartsView.api.addLineSeries(
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
        if (sma60Series != null) {
            binding.chartsView.api.removeSeries(sma60Series!!)
        }
        
        binding.chartsView.api.addLineSeries(
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
        if (sma120Series != null) {
            binding.chartsView.api.removeSeries(sma120Series!!)
        }
        
        binding.chartsView.api.addLineSeries(
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

    // 볼린저 밴드 시리즈 설정
    private fun setupBollingerBandsSeries(bands: com.tradingview.lightweightcharts.example.app.indicators.BollingerBandsResult) {
        // 기존 시리즈들 제거
        bollingerUpperSeries?.let { binding.chartsView.api.removeSeries(it) }
        bollingerMiddleSeries?.let { binding.chartsView.api.removeSeries(it) }
        bollingerLowerSeries?.let { binding.chartsView.api.removeSeries(it) }
        
        val upperData = bands.upperBand
        val middleData = bands.middleBand  
        val lowerData = bands.lowerBand
        
        // 상단 밴드
        binding.chartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE,
                lineStyle = LineStyle.DASHED
            ),
            onSeriesCreated = { api ->
                bollingerUpperSeries = api
                api.setData(upperData)
            }
        )
        
        // 중간 밴드
        binding.chartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE
            ),
            onSeriesCreated = { api ->
                bollingerMiddleSeries = api
                api.setData(middleData)
            }
        )
        
        // 하단 밴드
        binding.chartsView.api.addLineSeries(
            options = LineSeriesOptions(
                color = Color.parseColor("#FFCC02").toIntColor(),
                lineWidth = LineWidth.ONE,
                lineStyle = LineStyle.DASHED
            ),
            onSeriesCreated = { api ->
                bollingerLowerSeries = api
                api.setData(lowerData)
            }
        )
    }

    // 거래량 시리즈 설정 (증권사식 레이아웃)
    private fun setupVolumeSeries(data: List<com.tradingview.lightweightcharts.api.series.models.HistogramData>) {
        // 기존 시리즈들 안전하게 제거
        try {
            volumeRedSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                volumeRedSeries = null
            }
            volumeBlueSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                volumeBlueSeries = null
            }
        } catch (e: Exception) {
            Log.e("ChartActivity", "거래량 시리즈 제거 오류: ${e.message}")
        }
        
        if (data.isEmpty()) {
            Log.w("ChartActivity", "거래량 데이터가 비어있음")
            return
        }
        
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
                            Color.parseColor("#ef5350").toIntColor() // 상승: 빨간색
                        } else {
                            Color.parseColor("#26a69a").toIntColor() // 하락: 파란색
                        }
                    )
                }
            } else {
                data
            }
            
            // 증권사 스타일: 거래량을 메인 차트 하단에 오버레이 (동일 시간축)
            binding.chartsView.api.addHistogramSeries(
                options = HistogramSeriesOptions(
                    color = Color.parseColor("#64B5F6").toIntColor(),
                    // 메인 PriceScale 사용으로 시간축 동기화
                    priceScaleId = PriceScaleId.RIGHT
                ),
                onSeriesCreated = { api ->
                    volumeRedSeries = api
                    
                    // 거래량을 가격처럼 보이도록 스케일 조정
                    val scaledVolumeData = volumeWithColors.map { volumeData ->
                        com.tradingview.lightweightcharts.api.series.models.HistogramData(
                            time = volumeData.time,
                            value = volumeData.value / 100000f, // 거래량을 가격 범위로 축소
                            color = volumeData.color
                        )
                    }
                    
                    api.setData(scaledVolumeData)
                    
                    // 거래량을 차트 하단에 표시하도록 스케일 설정
                    api.priceScale().applyOptions(
                        priceScaleOptions {
                            scaleMargins = priceScaleMargins {
                                top = 0.6f     // 상단 60% 여백 (거래량이 하단에)
                                bottom = 0.0f   // 하단 여백 없음
                            }
                            visible = false  // 거래량 스케일 숨김
                            borderVisible = false
                        }
                    )
                    
                    Log.d("ChartActivity", "거래량 시리즈 설정 완료: ${scaledVolumeData.size}개")
                }
            )
            
            Log.d("ChartActivity", "거래량 시리즈 설정 완료: ${volumeWithColors.size}개")
            
        } catch (e: Exception) {
            Log.e("ChartActivity", "거래량 시리즈 생성 오류: ${e.message}", e)
        }
    }

    // RSI 시리즈 설정 (증권사식: 메인 차트 오버레이)
    private fun setupRSISeries(data: List<com.tradingview.lightweightcharts.api.series.models.LineData>) {
        try {
            rsiSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                rsiSeries = null
            }
        } catch (e: Exception) {
            Log.e("ChartActivity", "RSI 시리즈 제거 오류: ${e.message}")
        }
        
        if (data.isEmpty()) {
            Log.w("ChartActivity", "RSI 데이터가 비어있음")
            return
        }
        
        try {
            // 증권사 스타일: 메인 차트와 같은 시간축 사용 (오버레이)
            binding.chartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#9013FE").toIntColor(),
                    lineWidth = LineWidth.ONE,
                    // 메인 PriceScale 사용 (동일 시간축)
                    priceScaleId = PriceScaleId.RIGHT
                ),
                onSeriesCreated = { api ->
                    rsiSeries = api
                    
                    // RSI 값을 0-100 범위에서 가격처럼 보이도록 스케일 조정
                    val scaledData = data.map { rsiData ->
                        com.tradingview.lightweightcharts.api.series.models.LineData(
                            time = rsiData.time,
                            value = rsiData.value * 1000f // RSI 0-100을 0-100000으로 스케일링
                        )
                    }
                    
                    api.setData(scaledData)
                    Log.d("ChartActivity", "RSI 시리즈 설정 완료: ${scaledData.size}개")
                }
            )
            
        } catch (e: Exception) {
            Log.e("ChartActivity", "RSI 시리즈 생성 오류: ${e.message}", e)
        }
    }

    // MACD 시리즈 설정 (증권사식 레이아웃)
    private fun setupMACDSeries(data: com.tradingview.lightweightcharts.example.app.indicators.MACDResult) {
        // 기존 시리즈들 안전하게 제거
        try {
            macdSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                macdSeries = null
            }
            macdSignalSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                macdSignalSeries = null
            }
            macdHistogramSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                macdHistogramSeries = null
            }
        } catch (e: Exception) {
            Log.e("ChartActivity", "MACD 시리즈 제거 오류: ${e.message}")
        }
        
        if (data.macdLine.isEmpty() || data.signalLine.isEmpty()) {
            Log.w("ChartActivity", "MACD 데이터가 비어있음")
            return
        }
        
        val macdLineData = data.macdLine
        val signalLineData = data.signalLine
        val histogramData = data.histogram
        
        try {
            // 증권사 스타일: MACD도 메인 차트에 오버레이 (동일 시간축)
            
            // MACD 라인 (증권사 스타일: 가격처럼 표시)
            binding.chartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#4CAF50").toIntColor(),
                    lineWidth = LineWidth.ONE,
                    priceScaleId = PriceScaleId.RIGHT // 메인 스케일 사용
                ),
                onSeriesCreated = { api ->
                    macdSeries = api
                    
                    // MACD 값을 가격 범위로 스케일링
                    val scaledMacdData = macdLineData.map { macdData ->
                        com.tradingview.lightweightcharts.api.series.models.LineData(
                            time = macdData.time,
                            value = macdData.value * 10000f // MACD 값을 가격처럼 표시
                        )
                    }
                    
                    api.setData(scaledMacdData)
                }
            )
            
            // Signal 라인 (증권사 스타일)
            binding.chartsView.api.addLineSeries(
                options = LineSeriesOptions(
                    color = Color.parseColor("#F44336").toIntColor(),
                    lineWidth = LineWidth.ONE,
                    priceScaleId = PriceScaleId.RIGHT
                ),
                onSeriesCreated = { api ->
                    macdSignalSeries = api
                    
                    val scaledSignalData = signalLineData.map { signalData ->
                        com.tradingview.lightweightcharts.api.series.models.LineData(
                            time = signalData.time,
                            value = signalData.value * 10000f
                        )
                    }
                    
                    api.setData(scaledSignalData)
                }
            )
            
            // MACD 히스토그램 (배경에 연하게)
            binding.chartsView.api.addHistogramSeries(
                options = HistogramSeriesOptions(
                    color = Color.parseColor("#42A5F5").toIntColor(),
                    priceScaleId = PriceScaleId.RIGHT
                ),
                onSeriesCreated = { api ->
                    macdHistogramSeries = api
                    
                    if (histogramData.isNotEmpty()) {
                        val scaledHistogramData = histogramData.map { histData ->
                            com.tradingview.lightweightcharts.api.series.models.HistogramData(
                                time = histData.time,
                                value = histData.value * 5000f // 히스토그램은 좌 연하게
                            )
                        }
                        api.setData(scaledHistogramData)
                    }
                    
                    // 히스토그램을 배경에 연하게 표시
                    api.priceScale().applyOptions(
                        priceScaleOptions {
                            scaleMargins = priceScaleMargins {
                                top = 0.7f
                                bottom = 0.0f
                            }
                            visible = false
                            borderVisible = false
                        }
                    )
                }
            )
            
            Log.d("ChartActivity", "MACD 시리즈 설정 완료")
            
        } catch (e: Exception) {
            Log.e("ChartActivity", "MACD 시리즈 생성 오류: ${e.message}", e)
        }
    }

    // 토글 함수들
    private fun toggleSMA5(enabled: Boolean) {
        if (!enabled) {
            sma5Series?.let { 
                binding.chartsView.api.removeSeries(it)
                sma5Series = null
            }
        } else {
            viewModel.sma5Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA5Series(it) }
        }
    }

    private fun toggleSMA20(enabled: Boolean) {
        if (!enabled) {
            sma20Series?.let { 
                binding.chartsView.api.removeSeries(it)
                sma20Series = null
            }
        } else {
            viewModel.sma20Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA20Series(it) }
        }
    }

    private fun toggleSMA60(enabled: Boolean) {
        if (!enabled) {
            sma60Series?.let { 
                binding.chartsView.api.removeSeries(it)
                sma60Series = null
            }
        } else {
            viewModel.sma60Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA60Series(it) }
        }
    }

    private fun toggleSMA120(enabled: Boolean) {
        if (!enabled) {
            sma120Series?.let { 
                binding.chartsView.api.removeSeries(it)
                sma120Series = null
            }
        } else {
            viewModel.sma120Data.value.takeIf { it.isNotEmpty() }?.let { setupSMA120Series(it) }
        }
    }

    private fun toggleBollingerBands(enabled: Boolean) {
        if (!enabled) {
            bollingerUpperSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                bollingerUpperSeries = null
            }
            bollingerMiddleSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                bollingerMiddleSeries = null
            }
            bollingerLowerSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                bollingerLowerSeries = null
            }
        } else {
            viewModel.bollingerBands.value?.let { setupBollingerBandsSeries(it) }
        }
    }

    private fun toggleVolume(enabled: Boolean) {
        if (!enabled) {
            volumeRedSeries?.let { 
                binding.chartsView.api.removeSeries(it)
                volumeRedSeries = null
            }
        } else {
            viewModel.volumeData.value.takeIf { it.isNotEmpty() }?.let { setupVolumeSeries(it) }
        }
    }

    private fun toggleRSI(enabled: Boolean) {
        try {
            if (!enabled) {
                rsiSeries?.let { 
                    binding.chartsView.api.removeSeries(it)
                    rsiSeries = null
                }
                Log.d("ChartActivity", "RSI 시리즈 비활성화 완료")
            } else {
                val rsiData = viewModel.rsiData.value
                if (rsiData.isNotEmpty()) {
                    setupRSISeries(rsiData)
                    Log.d("ChartActivity", "RSI 시리즈 활성화 완룼")
                } else {
                    Log.w("ChartActivity", "RSI 데이터가 준비되지 않음")
                    // 데이터가 준비되지 않은 경우 잠시 후 재시도
                    lifecycleScope.launch {
                        delay(500L)
                        viewModel.rsiData.value.takeIf { it.isNotEmpty() }?.let { setupRSISeries(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChartActivity", "RSI 토글 오류: ${e.message}", e)
        }
    }

    private fun toggleMACD(enabled: Boolean) {
        try {
            if (!enabled) {
                // 안전한 MACD 시리즈 제거
                macdSeries?.let { 
                    binding.chartsView.api.removeSeries(it)
                    macdSeries = null
                }
                macdSignalSeries?.let { 
                    binding.chartsView.api.removeSeries(it)
                    macdSignalSeries = null
                }
                macdHistogramSeries?.let { 
                    binding.chartsView.api.removeSeries(it)
                    macdHistogramSeries = null
                }
                Log.d("ChartActivity", "MACD 시리즈 비활성화 완료")
            } else {
                // MACD 데이터 확인 후 시리즈 생성
                val macdData = viewModel.macdData.value
                if (macdData != null && macdData.macdLine.isNotEmpty()) {
                    setupMACDSeries(macdData)
                    Log.d("ChartActivity", "MACD 시리즈 활성화 완료")
                } else {
                    Log.w("ChartActivity", "MACD 데이터가 준비되지 않음")
                    // 데이터가 준비되지 않은 경우 잠시 후 재시도
                    lifecycleScope.launch {
                        delay(500L)
                        viewModel.macdData.value?.let { setupMACDSeries(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChartActivity", "MACD 토글 오류: ${e.message}", e)
        }
    }
    
    /**
     * 실제 증권사 스타일 CheckBox 기반 지표 제어
     */
    private fun setupIndicatorCheckBoxes() {
        try {
            // 이동평균선 CheckBox
            binding.root.findViewById<CheckBox>(com.tradingview.lightweightcharts.example.app.R.id.checkSMA)?.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchSma5.isChecked = isChecked
                    binding.switchSma20.isChecked = isChecked
                    toggleSMA5(isChecked)
                    toggleSMA20(isChecked)
                }
                isChecked = true // 기본 활성화
            }
            
            // 볼린저 밴드 CheckBox
            binding.root.findViewById<CheckBox>(com.tradingview.lightweightcharts.example.app.R.id.checkBollinger)?.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchBollingerBands.isChecked = isChecked
                    toggleBollingerBands(isChecked)
                }
            }
            
            // 거래량 CheckBox
            binding.root.findViewById<CheckBox>(com.tradingview.lightweightcharts.example.app.R.id.checkVolume)?.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchVolume.isChecked = isChecked
                    toggleVolume(isChecked)
                }
                isChecked = true // 기본 활성화
            }
            
            // MACD CheckBox
            binding.root.findViewById<CheckBox>(com.tradingview.lightweightcharts.example.app.R.id.checkMACD)?.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchMacd.isChecked = isChecked
                    toggleMACD(isChecked)
                }
            }
            
            // RSI CheckBox
            binding.root.findViewById<CheckBox>(com.tradingview.lightweightcharts.example.app.R.id.checkRSI)?.apply {
                setOnCheckedChangeListener { _, isChecked ->
                    binding.switchRsi.isChecked = isChecked
                    toggleRSI(isChecked)
                }
            }
            
        } catch (e: Exception) {
            Log.d("ChartActivity", "CheckBox 설정 생략 (기본 레이아웃 사용): ${e.message}")
        }
    }
}