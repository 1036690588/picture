package com.yupi.yupicturebackend.util;

import java.awt.*;

public class ColorSimilarUtils {
    /**
     * 计算两个颜色的相似度
     * @param color1
     * @param color2
     * @return
     */
    public static double calculateSimilarity(Color color1, Color color2){
        int red1 = color1.getRed();
        int green1 = color1.getGreen();
        int blue1 = color1.getBlue();
        int red2 = color2.getRed();
        int green2 = color2.getGreen();
        int blue2 = color2.getBlue();

        double distance = Math.sqrt(Math.pow(red1 - red2, 2) + Math.pow(green1 - green2, 2) + Math.pow(blue1 - blue2, 2));
        return 1 - distance / Math.sqrt(3 * Math.pow(255,2));
    }


    /**
     * 计算两个16进制颜色之间的相似度
     * @param hexColor1 颜色的十六进制代码
     * @param hexColor2
     * @return 相似度 0-1 1表示完全相同
     */
    public static double calculateSimilarity(String hexColor1, String hexColor2){
        Color color1 = Color.decode(hexColor1);
        Color color2 = Color.decode(hexColor2);
        return calculateSimilarity(color1, color2);
    }
}
