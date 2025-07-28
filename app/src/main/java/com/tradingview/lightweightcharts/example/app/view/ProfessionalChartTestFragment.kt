package com.tradingview.lightweightcharts.example.app.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.chip.Chip
import com.tradingview.lightweightcharts.api.options.models.ChartOptions
import com.tradingview.lightweightcharts.api.options.models.*
import com.tradingview.lightweightcharts.api.options.models.LineSeriesOptions
import com.tradingview.lightweightcharts.api.chart.models.color.IntColor
import com.tradingview.lightweightcharts.api.chart.models.color.surface.SolidColor
import com.tradingview.lightweightcharts.api.chart.models.color.toIntColor
import com.tradingview.lightweightcharts.api.series.enums.LineWidth
import com.tradingview.lightweightcharts.api.series.enums.LineStyle
import com.tradingview.lightweightcharts.example.app.databinding.FragmentProfessionalChartTestBinding
import com.tradingview.lightweightcharts.example.app.viewmodel.ProfessionalChartTestViewModel
import kotlinx.coroutines.launch

class ProfessionalChartTestFragment : Fragment() {

    private var _binding: FragmentProfessionalChartTestBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProfessionalChartTestViewModel by viewModels()
    
    // 차트 시리즈 참조
    private var candlestickSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var volumeSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var sma5Series: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var sma20Series: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var rsiSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var bollingerUpperSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var bollingerMiddleSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null
    private var bollingerLowerSeries: com.tradingview.lightweightcharts.api.interfaces.SeriesApi? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfessionalChartTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupChart()
        setupControls()
        observeViewModel()
        
        // 테스트 데이터 생성
        viewModel.generateTestData()
    }

    private fun setupChart() {
        // 차트 기본 설정 (다크 테마)
        val chartOptions = ChartOptions().apply {
            layout = LayoutOptions().apply {
                background = SolidColor("#1e1e1e".toIntColor())
                textColor = "#ffffff".toIntColor()
            }
            grid = GridOptions().apply {
                vertLines = GridLineOptions().apply {
                    color = "#2B2B43".toIntColor()
                }
                horzLines = GridLineOptions().apply {
                    color = "#2B2B43".toIntColor()
                }
            }
            crosshair = CrosshairOptions().apply {
                vertLine = CrosshairLineOptions().apply {
                    color = "#758696".toIntColor()
                    width = LineWidth.ONE
                    style = LineStyle.LARGE_DASHED // 점선
                }
                horzLine = CrosshairLineOptions().apply {
                    color = "#758696".toIntColor()
                    width = LineWidth.ONE
                    style = LineStyle.LARGE_DASHED // 점선
                }
            }
            rightPriceScale = PriceScaleOptions().apply {
                borderColor = "#485c7b".toIntColor()
            }
            timeScale = TimeScaleOptions().apply {
                borderColor = "#485c7b".toIntColor()
                timeVisible = true
                secondsVisible = false
            }
        }

        binding.chartsView.api.applyOptions(chartOptions)

        // 메인 캔들스틱 시리즈 생성
        binding.chartsView.api.addCandlestickSeries { series ->
            candlestickSeries = series
        }
    }

    private fun setupControls() {
        binding.apply {
            // 테스트 데이터 생성 버튼들
            buttonGenerateUptrend.setOnClickListener {
                viewModel.generateUptrendData()
            }
            
            buttonGenerateDowntrend.setOnClickListener {
                viewModel.generateDowntrendData()
            }
            
            buttonGenerateVolatile.setOnClickListener {
                viewModel.generateVolatileData()
            }
            
            buttonGenerateRandom.setOnClickListener {
                viewModel.generateRandomData()
            }

            // 지표 토글 스위치들
            switchSma5.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleSMA5(isChecked)
            }
            
            switchSma20.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleSMA20(isChecked)
            }
            
            switchRsi.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleRSI(isChecked)
            }
            
            switchBollingerBands.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleBollingerBands(isChecked)
            }
            
            switchVolume.setOnCheckedChangeListener { _, isChecked ->
                viewModel.toggleVolume(isChecked)
            }

            // 실시간 업데이트 토글
            switchRealtime.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.startRealTimeUpdates()
                } else {
                    viewModel.stopRealTimeUpdates()
                }
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                // 캔들스틱 데이터 관찰
                launch {
                    viewModel.candlestickData.collect { data ->
                        if (data.isNotEmpty()) {
                            candlestickSeries?.setData(data)
                        }
                    }
                }

                // 거래량 데이터 관찰
                launch {
                    viewModel.volumeData.collect { data ->
                        if (data.isNotEmpty() && viewModel.showVolume.value) {
                            if (volumeSeries == null) {
                                binding.chartsView.api.addHistogramSeries { series ->
                                    volumeSeries = series
                                }
                            }
                            volumeSeries?.setData(data)
                        } else if (!viewModel.showVolume.value) {
                            volumeSeries?.let { 
                                binding.chartsView.api.removeSeries(it)
                                volumeSeries = null
                            }
                        }
                    }
                }

                // SMA5 데이터 관찰
                launch {
                    viewModel.sma5Data.collect { data ->
                        if (data.isNotEmpty() && viewModel.showSMA5.value) {
                            if (sma5Series == null) {
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#FF6B35".toIntColor() // 주황색
                                        lineWidth = LineWidth.TWO
                                    }
                                ) { series ->
                                    sma5Series = series
                                }
                            }
                            sma5Series?.setData(data)
                        } else if (!viewModel.showSMA5.value) {
                            sma5Series?.let {
                                binding.chartsView.api.removeSeries(it)
                                sma5Series = null
                            }
                        }
                    }
                }

                // SMA20 데이터 관찰
                launch {
                    viewModel.sma20Data.collect { data ->
                        if (data.isNotEmpty() && viewModel.showSMA20.value) {
                            if (sma20Series == null) {
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#4ECDC4".toIntColor() // 민트색
                                        lineWidth = LineWidth.TWO
                                    }
                                ) { series ->
                                    sma20Series = series
                                }
                            }
                            sma20Series?.setData(data)
                        } else if (!viewModel.showSMA20.value) {
                            sma20Series?.let {
                                binding.chartsView.api.removeSeries(it)
                                sma20Series = null
                            }
                        }
                    }
                }

                // RSI 데이터 관찰
                launch {
                    viewModel.rsiData.collect { data ->
                        if (data.isNotEmpty() && viewModel.showRSI.value) {
                            if (rsiSeries == null) {
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#9013FE".toIntColor() // 보라색
                                        lineWidth = LineWidth.TWO
                                    }
                                ) { series ->
                                    rsiSeries = series
                                }
                            }
                            rsiSeries?.setData(data)
                        } else if (!viewModel.showRSI.value) {
                            rsiSeries?.let {
                                binding.chartsView.api.removeSeries(it)
                                rsiSeries = null
                            }
                        }
                    }
                }

                // 볼린저 밴드 관찰
                launch {
                    viewModel.bollingerBands.collect { bollingerData ->
                        if (bollingerData != null && viewModel.showBollingerBands.value) {
                            if (bollingerUpperSeries == null) {
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#FFB74D".toIntColor() // 연한 주황색
                                        lineWidth = LineWidth.ONE
                                    }
                                ) { series ->
                                    bollingerUpperSeries = series
                                }
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#FFCC02".toIntColor() // 노란색
                                        lineWidth = LineWidth.TWO
                                    }
                                ) { series ->
                                    bollingerMiddleSeries = series
                                }
                                binding.chartsView.api.addLineSeries(
                                    LineSeriesOptions().apply {
                                        color = "#FFB74D".toIntColor() // 연한 주황색
                                        lineWidth = LineWidth.ONE
                                    }
                                ) { series ->
                                    bollingerLowerSeries = series
                                }
                            }
                            bollingerUpperSeries?.setData(bollingerData.upperBand)
                            bollingerMiddleSeries?.setData(bollingerData.middleBand)
                            bollingerLowerSeries?.setData(bollingerData.lowerBand)
                        } else if (!viewModel.showBollingerBands.value) {
                            bollingerUpperSeries?.let { binding.chartsView.api.removeSeries(it); bollingerUpperSeries = null }
                            bollingerMiddleSeries?.let { binding.chartsView.api.removeSeries(it); bollingerMiddleSeries = null }
                            bollingerLowerSeries?.let { binding.chartsView.api.removeSeries(it); bollingerLowerSeries = null }
                        }
                    }
                }

                // 통계 정보 관찰
                launch {
                    viewModel.statisticsInfo.collect { stats ->
                        binding.textStatistics.text = """
                            데이터 포인트: ${stats.dataPoints}개
                            현재가: ${String.format("%.0f", stats.currentPrice)}원
                            최고가: ${String.format("%.0f", stats.highPrice)}원
                            최저가: ${String.format("%.0f", stats.lowPrice)}원
                            RSI: ${String.format("%.1f", stats.rsi)}
                        """.trimIndent()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}