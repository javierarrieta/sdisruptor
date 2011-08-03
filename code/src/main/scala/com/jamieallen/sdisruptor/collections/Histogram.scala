/*
 * Copyright 2011 LMAX Ltd., modified by Jamie Allen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jamieallen.sdisruptor.collections

import java.math.BigDecimal
import java.math.RoundingMode

/** Histogram for tracking the frequency of observations of values below interval upper bounds.
 *
 *  This class is useful for recording timings in nanoseconds across a large number of observations
 *  when high performance is required.
 */
class Histogram(origUpperBounds: Array[Long]) {
  validateBounds(origUpperBounds)

  val upperBounds: Array[Long]
  Array.copy(origUpperBounds, 0, upperBounds, 0, origUpperBounds.length)

  val counts = new Array[Long](upperBounds.length)
  var minValue = Long.MaxValue
  var maxValue = 0L


  private def validateBounds(upperBounds: Array[Long]) {
    var lastBound = -1L
    upperBounds.map(bound => {
      bound match {
        case bound if (bound <= 0L) => throw new IllegalArgumentException("Bounds must be positive values")
        case bound if (bound <= lastBound) => throw new IllegalArgumentException("bound " + bound + " is not greater than " + lastBound)
      }
      lastBound = bound
    })
  }

  /** Size of the list of interval bars.
   *
   *  @return size of the interval bar list.
   */
  def size = upperBounds.length

  /** Get the upper bound of an interval for an index.
   *
   * @param index of the upper bound.
   * @return the interval upper bound for the index.
   */
  def getUpperBoundAt(index: Int) = upperBounds(index)

  /** Get the count of observations at a given index.
   *
   * @param index of the observations counter.
   * @return the count of observations at a given index.
   */
  def getCountAt(index: Int) = counts(index)

  /** Add an observation to the histogram and increment the counter for the interval it matches.
   *
   * @param value for the observation to be added.
   * @return return true if in the range of intervals otherwise false.
   */
  def addObservation(value: Long): Boolean = {
    var low = 0
    var high = upperBounds.length - 1

    while (low < high) {
      val mid = low + ((high - low) >> 1)
      if (upperBounds(mid) < value) low = mid + 1
      else high = mid
    }

    if (value <= upperBounds(high)) {
    	counts(high) += 1
    	trackRange(value)

      return true
    }

    false
  }

  def trackRange(value: Long) =
    if (value < minValue) minValue = value
    else if (value > maxValue) maxValue = value

    /**
     * Add observations from another Histogram into this one.
     * Histograms must have the same intervals.
     *
     * @param histogram from which to add the observation counts.
     */
    public void addObservations(final Histogram histogram)
    {
        if (upperBounds.length != histogram.upperBounds.length)
        {
            throw new IllegalArgumentException("Histograms must have matching intervals");
        }

        for (int i = 0, size = upperBounds.length; i < size; i++)
        {
            if (upperBounds[i] != histogram.upperBounds[i])
            {
                throw new IllegalArgumentException("Histograms must have matching intervals");
            }
        }

        for (int i = 0, size = counts.length; i < size; i++)
        {
            counts[i] += histogram.counts[i];
        }

        trackRange(histogram.minValue);
        trackRange(histogram.maxValue);
    }

    /**
     * Clear the list of interval counters.
     */
    public void clear()
    {
        maxValue = 0L;
        minValue = Long.MAX_VALUE;

        for (int i = 0, size = counts.length; i < size; i++)
        {
            counts[i] = 0L;
        }
    }

    /**
     * Count total number of recorded observations.
     *
     * @return the total number of recorded observations.
     */
    public long getCount()
    {
        long count = 0L;

        for (int i = 0, size = counts.length; i < size; i++)
        {
            count += counts[i];
        }

        return count;
    }

    /**
     * Get the minimum observed value.
     *
     * @return the minimum value observed.
     */
    public long getMin()
    {
        return minValue;
    }

    /**
     * Get the maximum observed value.
     *
     * @return the maximum of the observed values;
     */
    public long getMax()
    {
        return maxValue;
    }

    /**
     * Calculate the mean of all recorded observations.
     *
     * The mean is calculated by the summing the mid points of each interval multiplied by the count
     * for that interval, then dividing by the total count of observations.  The max and min are
     * considered for adjusting the top and bottom bin when calculating the mid point.
     *
     * @return the mean of all recorded observations.
     */
    public BigDecimal getMean()
    {
        if (0L == getCount())
        {
            return BigDecimal.ZERO;
        }

        long lowerBound = counts[0] > 0L ? minValue : 0L;
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0, size = upperBounds.length; i < size; i++)
        {
            if (0L != counts[i])
            {
                final long upperBound = Math.min(upperBounds[i], maxValue);
                final long midPoint = lowerBound + ((upperBound - lowerBound) / 2L);

                final BigDecimal intervalTotal = new BigDecimal(midPoint).multiply(new BigDecimal(counts[i]));
                total = total.add(intervalTotal);
            }

            lowerBound = Math.max(upperBounds[i] + 1L, minValue);
        }

        return total.divide(new BigDecimal(getCount()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate the upper bound within which 99% of observations fall.
     *
     * @return the upper bound for 99% of observations.
     */
    public long getTwoNinesUpperBound()
    {
        return getUpperBoundForFactor(0.99d);
    }

    /**
     * Calculate the upper bound within which 99.99% of observations fall.
     *
     * @return the upper bound for 99.99% of observations.
     */
    public long getFourNinesUpperBound()
    {
        return getUpperBoundForFactor(0.9999d);
    }

    /**
     * Get the interval upper bound for a given factor of the observation population.
     *
     * @param factor representing the size of the population.
     * @return the interval upper bound.
     */
    public long getUpperBoundForFactor(final double factor)
    {
        if (0.0d >= factor || factor >= 1.0d)
        {
            throw new IllegalArgumentException("factor must be >= 0.0 and <= 1.0");
        }

        final long totalCount = getCount();
        final long tailTotal = totalCount - Math.round(totalCount * factor);
        long tailCount = 0L;

        for (int i = counts.length - 1; i >= 0; i--)
        {
            if (0L != counts[i])
            {
                tailCount += counts[i];
                if (tailCount >= tailTotal)
                {
                    return upperBounds[i];
                }
            }
        }

        return 0L;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append("Histogram{");

        sb.append("min=").append(getMin()).append(", ");
        sb.append("max=").append(getMax()).append(", ");
        sb.append("mean=").append(getMean()).append(", ");
        sb.append("99%=").append(getTwoNinesUpperBound()).append(", ");
        sb.append("99.99%=").append(getFourNinesUpperBound()).append(", ");

        sb.append('[');
        for (int i = 0, size = counts.length; i < size; i++)
        {
            sb.append(upperBounds[i]).append('=').append(counts[i]).append(", ");
        }

        if (counts.length > 0)
        {
            sb.setLength(sb.length() - 2);
        }
        sb.append(']');

        sb.append('}');

        return sb.toString();
    }
}