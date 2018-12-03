package tools;

import java.text.DecimalFormat;

public class ProgressBar {

    private long minimum = 0; // 进度条起始值

    private long maximum = 100; // 进度条最大值

    private long barLen = 100; // 进度条长度

    private char showChar = '='; // 用于进度条显示的字符
    private boolean isShown = false; // 百分百只能显示一次
    private DecimalFormat formater = new DecimalFormat("#.##%");

    /**
     * 使用系统标准输出，显示字符进度条及其百分比。
     */
    public ProgressBar() {
    }

    /**
     * 使用系统标准输出，显示字符进度条及其百分比。
     *
     * @param minimum 进度条起始值
     * @param maximum 进度条最大值
     * @param barLen  进度条长度
     */
    public ProgressBar(long minimum, long maximum,
                       long barLen) {
        this(minimum, maximum, barLen, '=');
    }

    /**
     * 使用系统标准输出，显示字符进度条及其百分比。
     *
     * @param minimum  进度条起始值
     * @param maximum  进度条最大值
     * @param barLen   进度条长度
     * @param showChar 用于进度条显示的字符
     */
    public ProgressBar(long minimum, long maximum,
                       long barLen, char showChar) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.barLen = barLen;
        this.showChar = showChar;
    }

    /**
     * 显示进度条。
     *
     * @param value 当前进度。进度必须大于或等于起始点且小于等于结束点（start <= current <= end）。
     */
    public void show(long value, float sendingRate) {
        if (value < minimum || value > maximum) {
            return;
        }
        // 避免多次显示百分百
        if(value == maximum && isShown) return;
        if(value == maximum && !isShown) isShown = true;

        reset();
        minimum = value;
        float rate = (float) (minimum * 1.0 / maximum);
        long len = (long) (rate * barLen);
        draw(len, rate, sendingRate);
        if (minimum == maximum) {
            afterComplete();
        }
    }

    private void draw(long len, float rate, float sendingRate) {
        System.out.print("Progress: ");
        for (int i = 0; i < len; i++) {
            System.out.print(showChar);
        }
        System.out.print(' ');
        System.out.print(format(rate));
        // 最大值不显示速度
        if(!isShown || rate != 0){
            System.out.print(' ');
            // 显示小数点后两位
            DecimalFormat fmt = new DecimalFormat("##0.0");
            if(sendingRate >= 1000 * 1000){
                System.out.print("| Speed:" + fmt.format((sendingRate / (float)(100 * 1000))) + "GB/S");
            } else if(sendingRate >= 1000){
                System.out.print("| Speed:" + fmt.format((sendingRate / (float)1000)) + "MB/S");
            }
            else System.out.print("| Speed:" + fmt.format(sendingRate) + "KB/S");
        }
    }


    private void reset() {
        System.out.print('\r'); //光标移动到行首
    }

    private void afterComplete() {
        System.out.print('\n');
    }

    private String format(float num) {
        return formater.format(num);
    }
}