package com.ishland.c2me.opts.dfc.common.ast.spline;

public class SplineSupport {

    public static int findRangeForLocation(float[] locations, float x) {
        int min = 0;
        int i = locations.length;

        while (i > 0) {
            int j = i / 2;
            int k = min + j;
            if (x < locations[k]) {
                i = j;
            } else {
                min = k + 1;
                i -= j + 1;
            }
        }

        return min - 1;
    }

    public static float sampleOutsideRange(float point, float[] locations, float value, float[] derivatives, int i) {
        float f = derivatives[i];
        return f == 0.0F ? value : value + f * (point - locations[i]);
    }

    public static float sampleInsideRange(float point, float[] locations, float[] derivatives, int i, float value0, float value1) {
        float loc0 = locations[i];
        float loc1 = locations[i + 1];
        float locDist = loc1 - loc0;
        float k = (point - loc0) / locDist;
        float onDist = value1 - value0;
        float p = derivatives[i] * locDist - onDist;
        float q = -derivatives[i + 1] * locDist + onDist;
        return value0 + k * (onDist + (1.0F - k) * (p + k * (q - p)));
    }

}
