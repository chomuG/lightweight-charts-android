package com.tradingview.lightweightcharts.example.app.indicators

import com.tradingview.lightweightcharts.api.series.models.*
import kotlin.math.*

/**
 * 기술적 지표 계산 클래스
 */
object TechnicalIndicators {

    /**
     * 단순 이동평균선 (SMA) 계산
     * @param data 캔들스틱 데이터 리스트
     * @param period 이동평균 기간 (5, 10, 20, 60, 120일 등)
     * @return LineData 리스트
     */
    fun calculateSMA(data: List<CandlestickData>, period: Int): List<LineData> {
        if (data.size < period) return emptyList()
        
        val result = mutableListOf<LineData>()
        
        for (i in period - 1 until data.size) {
            val sum = data.subList(i - period + 1, i + 1).sumOf { it.close.toDouble() }
            val average = (sum / period).toFloat()
            
            result.add(LineData(
                time = data[i].time,
                value = average
            ))
        }
        
        return result
    }

    /**
     * 지수 이동평균선 (EMA) 계산
     * @param data 캔들스틱 데이터 리스트
     * @param period EMA 기간
     * @return LineData 리스트
     */
    fun calculateEMA(data: List<CandlestickData>, period: Int): List<LineData> {
        if (data.size < period) return emptyList()
        
        val result = mutableListOf<LineData>()
        val multiplier = 2.0 / (period + 1)
        
        // 첫 번째 EMA는 SMA로 계산
        val firstSMA = data.take(period).sumOf { it.close.toDouble() } / period
        result.add(LineData(
            time = data[period - 1].time,
            value = firstSMA.toFloat()
        ))
        
        // 나머지 EMA 계산
        for (i in period until data.size) {
            val ema = (data[i].close * multiplier) + (result.last().value * (1 - multiplier))
            result.add(LineData(
                time = data[i].time,
                value = ema.toFloat()
            ))
        }
        
        return result
    }

    /**
     * RSI (Relative Strength Index) 계산
     * @param data 캔들스틱 데이터 리스트
     * @param period RSI 계산 기간 (기본 14)
     * @return LineData 리스트
     */
    fun calculateRSI(data: List<CandlestickData>, period: Int = 14): List<LineData> {
        if (data.size <= period) return emptyList()
        
        val result = mutableListOf<LineData>()
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()
        
        // 첫 번째 기간의 gain/loss 계산
        for (i in 1 until data.size) {
            val change = data[i].close - data[i - 1].close
            gains.add(maxOf(change.toDouble(), 0.0))
            losses.add(maxOf(-change.toDouble(), 0.0))
        }
        
        // 첫 번째 RS와 RSI 계산
        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()
        
        for (i in period until gains.size) {
            val rs = if (avgLoss != 0.0) avgGain / avgLoss else Double.MAX_VALUE
            val rsi = 100.0 - (100.0 / (1.0 + rs))
            
            result.add(LineData(
                time = data[i + 1].time,
                value = rsi.toFloat()
            ))
            
            // 다음 평균 계산 (Wilder's smoothing)
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
        }
        
        return result
    }

    /**
     * MACD 계산
     * @param data 캔들스틱 데이터 리스트
     * @param fastPeriod 빠른 EMA 기간 (기본 12)
     * @param slowPeriod 느린 EMA 기간 (기본 26)
     * @param signalPeriod 신호선 EMA 기간 (기본 9)
     * @return MACDResult 객체
     */
    fun calculateMACD(
        data: List<CandlestickData>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MACDResult {
        val fastEMA = calculateEMA(data, fastPeriod)
        val slowEMA = calculateEMA(data, slowPeriod)
        
        if (fastEMA.isEmpty() || slowEMA.isEmpty()) {
            return MACDResult(emptyList(), emptyList(), emptyList())
        }
        
        // MACD 라인 계산 (빠른 EMA - 느린 EMA)
        val macdLine = mutableListOf<LineData>()
        val minSize = minOf(fastEMA.size, slowEMA.size)
        
        for (i in 0 until minSize) {
            macdLine.add(LineData(
                time = fastEMA[i].time,
                value = fastEMA[i].value - slowEMA[i].value
            ))
        }
        
        // 신호선 계산 (MACD의 EMA)
        val signalLine = calculateEMAFromLineData(macdLine, signalPeriod)
        
        // 히스토그램 계산 (MACD - 신호선)
        val histogram = mutableListOf<HistogramData>()
        val histogramMinSize = minOf(macdLine.size, signalLine.size)
        
        for (i in 0 until histogramMinSize) {
            histogram.add(HistogramData(
                time = macdLine[i].time,
                value = macdLine[i].value - signalLine[i].value
            ))
        }
        
        return MACDResult(macdLine, signalLine, histogram)
    }

    /**
     * 볼린저 밴드 계산
     * @param data 캔들스틱 데이터 리스트
     * @param period 이동평균 기간 (기본 20)
     * @param multiplier 표준편차 배수 (기본 2.0)
     * @return BollingerBandsResult 객체
     */
    fun calculateBollingerBands(
        data: List<CandlestickData>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): BollingerBandsResult {
        if (data.size < period) {
            return BollingerBandsResult(emptyList(), emptyList(), emptyList())
        }
        
        val middleBand = mutableListOf<LineData>() // SMA
        val upperBand = mutableListOf<LineData>()
        val lowerBand = mutableListOf<LineData>()
        
        for (i in period - 1 until data.size) {
            val subset = data.subList(i - period + 1, i + 1)
            val sma = subset.sumOf { it.close.toDouble() } / period
            
            // 표준편차 계산
            val variance = subset.sumOf { (it.close - sma).pow(2) } / period
            val stdDev = sqrt(variance)
            
            middleBand.add(LineData(
                time = data[i].time,
                value = sma.toFloat()
            ))
            
            upperBand.add(LineData(
                time = data[i].time,
                value = (sma + stdDev * multiplier).toFloat()
            ))
            
            lowerBand.add(LineData(
                time = data[i].time,
                value = (sma - stdDev * multiplier).toFloat()
            ))
        }
        
        return BollingerBandsResult(upperBand, middleBand, lowerBand)
    }

    /**
     * 스토캐스틱 계산
     * @param data 캔들스틱 데이터 리스트
     * @param kPeriod %K 계산 기간 (기본 14)
     * @param dPeriod %D 계산 기간 (기본 3)
     * @return StochasticResult 객체
     */
    fun calculateStochastic(
        data: List<CandlestickData>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): StochasticResult {
        if (data.size < kPeriod) {
            return StochasticResult(emptyList(), emptyList())
        }
        
        val kPercent = mutableListOf<LineData>()
        
        // %K 계산
        for (i in kPeriod - 1 until data.size) {
            val subset = data.subList(i - kPeriod + 1, i + 1)
            val lowestLow = subset.minOf { it.low }
            val highestHigh = subset.maxOf { it.high }
            val currentClose = data[i].close
            
            val k = if (highestHigh != lowestLow) {
                ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100
            } else {
                50.0 // 중간값
            }
            
            kPercent.add(LineData(
                time = data[i].time,
                value = k.toFloat()
            ))
        }
        
        // %D 계산 (%K의 이동평균)
        val dPercent = calculateSMAFromLineData(kPercent, dPeriod)
        
        return StochasticResult(kPercent, dPercent)
    }

    /**
     * LineData에서 EMA 계산하는 헬퍼 함수
     */
    private fun calculateEMAFromLineData(data: List<LineData>, period: Int): List<LineData> {
        if (data.size < period) return emptyList()
        
        val result = mutableListOf<LineData>()
        val multiplier = 2.0 / (period + 1)
        
        // 첫 번째 EMA는 SMA로 계산
        val firstSMA = data.take(period).sumOf { it.value.toDouble() } / period
        result.add(LineData(
            time = data[period - 1].time,
            value = firstSMA.toFloat()
        ))
        
        // 나머지 EMA 계산
        for (i in period until data.size) {
            val ema = (data[i].value * multiplier) + (result.last().value * (1 - multiplier))
            result.add(LineData(
                time = data[i].time,
                value = ema.toFloat()
            ))
        }
        
        return result
    }

    /**
     * LineData에서 SMA 계산하는 헬퍼 함수
     */
    private fun calculateSMAFromLineData(data: List<LineData>, period: Int): List<LineData> {
        if (data.size < period) return emptyList()
        
        val result = mutableListOf<LineData>()
        
        for (i in period - 1 until data.size) {
            val sum = data.subList(i - period + 1, i + 1).sumOf { it.value.toDouble() }
            val average = (sum / period).toFloat()
            
            result.add(LineData(
                time = data[i].time,
                value = average
            ))
        }
        
        return result
    }
}

/**
 * MACD 결과 데이터 클래스
 */
data class MACDResult(
    val macdLine: List<LineData>,
    val signalLine: List<LineData>,
    val histogram: List<HistogramData>
)

/**
 * 볼린저 밴드 결과 데이터 클래스
 */
data class BollingerBandsResult(
    val upperBand: List<LineData>,
    val middleBand: List<LineData>,
    val lowerBand: List<LineData>
)

/**
 * 스토캐스틱 결과 데이터 클래스
 */
data class StochasticResult(
    val kPercent: List<LineData>,
    val dPercent: List<LineData>
)